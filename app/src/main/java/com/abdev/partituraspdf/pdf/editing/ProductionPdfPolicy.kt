package com.abdev.partituraspdf.pdf.editing

import com.tom_roush.pdfbox.cos.*
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.contentstream.operator.Operator
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.IdentityHashMap

internal class UnsupportedPdf(message: String) : IOException(message)

/** Conservative supported subset. Inspects all resolved COS objects, not raw keyword searches.
 * Bounds here protect our traversal/decoded buffers, NOT all allocations of the upstream parser.
 */
internal object ProductionPdfPolicy {
    const val PAGE_LIMIT = 1000
    const val DECODED_LIMIT = 8 * 1024 * 1024
    private val dangerous = setOf("A", "AA", "JS", "JavaScript", "EmbeddedFiles", "EF", "AF", "XFA", "AcroForm", "Perms", "ByteRange", "Sig", "RichMediaContent")
    private val filters = setOf("FlateDecode", "DCTDecode", "ASCII85Decode")
    private val catalogKeys = setOf("Type", "Pages", "Metadata", "Lang", "Version", "PageMode", "PageLayout")
    private val pageKeys = setOf("Type", "Parent", "Resources", "MediaBox", "CropBox", "BleedBox", "TrimBox", "ArtBox", "Rotate", "Contents", "Annots", "UserUnit", "Trans")
    private val operators = ("q Q cm w J j M d ri i gs m l c v y h re S s f F f* B B* b b* n W W* " +
        "BT ET Tc Tw Tz TL Tf Tr Ts Td TD Tm T* Tj TJ ' \" d0 d1 CS cs SC SCN sc scn G g RG rg K k sh Do").split(' ').toSet()
    fun requireSupported(document: PDDocument, checkActive: () -> Unit = {}) {
        checkActive()
        if (document.isEncrypted) throw UnsupportedPdf("Protected PDF")
        val treeVisited = Collections.newSetFromMap(IdentityHashMap<COSDictionary, Boolean>())
        fun checkTree(node: COSDictionary, parent: COSDictionary?, depth: Int): Int {
            checkActive()
            if (depth > 32 || !treeVisited.add(node)) throw UnsupportedPdf("Invalid page tree")
            if (parent != null && node.getDictionaryObject(COSName.PARENT) !== parent)
                throw UnsupportedPdf("Invalid page parent")
            return when (node.getNameAsString(COSName.TYPE)) {
                "Page" -> 1
                "Pages" -> {
                    val kids = node.getDictionaryObject(COSName.KIDS) as? COSArray ?: throw UnsupportedPdf("Missing page kids")
                    if (kids.size() !in 1..PAGE_LIMIT) throw UnsupportedPdf("Invalid page kids")
                    var count = 0
                    for (i in 0 until kids.size()) {
                        count += checkTree(kids.getObject(i) as? COSDictionary ?: throw UnsupportedPdf("Invalid page child"), node, depth + 1)
                        if (count > PAGE_LIMIT) throw UnsupportedPdf("Page count bound")
                    }
                    if (node.getInt(COSName.COUNT, -1) != count) throw UnsupportedPdf("Page count mismatch")
                    count
                }
                else -> throw UnsupportedPdf("Invalid page node type")
            }
        }
        val tree = document.documentCatalog.cosObject.getDictionaryObject(COSName.PAGES) as? COSDictionary
            ?: throw UnsupportedPdf("Missing page tree")
        val verifiedCount = checkTree(tree, null, 0)
        if (verifiedCount != document.numberOfPages || document.numberOfPages !in 1..PAGE_LIMIT) throw UnsupportedPdf("Page count outside supported bounds")
        if (document.documentCatalog.cosObject.keySet().any { it.name !in catalogKeys }) throw UnsupportedPdf("Unsupported catalog structure")
        val visited = Collections.newSetFromMap(IdentityHashMap<COSBase, Boolean>())
        var nodes = 0
        val contentStreams = mutableListOf<COSStream>()
        fun inspect(raw: COSBase?, depth: Int) {
            checkActive()
            if (raw == null || raw is COSNull || !visited.add(raw)) return
            if (++nodes > 100_000 || depth > 64) throw UnsupportedPdf("Object graph bound")
            when(raw) {
                is COSObject -> inspect(raw.`object` ?: throw UnsupportedPdf("Unresolved object"), depth + 1)
                is COSDictionary -> {
                    if (raw.keySet().any { it.name in dangerous }) throw UnsupportedPdf("Actions/forms/signatures/attachments unsupported")
                    if (raw is COSStream) {
                        if (raw.containsKey(COSName.F) || raw.containsKey(COSName.getPDFName("FFilter")) || raw.containsKey(COSName.getPDFName("FDecodeParms")))
                            throw UnsupportedPdf("External stream unsupported")
                        if (raw.getNameAsString(COSName.SUBTYPE) == "Image") {
                            val width=raw.getInt(COSName.WIDTH); val height=raw.getInt(COSName.HEIGHT)
                            if(width<=0 || height<=0 || width>4000 || height>4000 || width.toLong()*height>4_000_000)
                                throw UnsupportedPdf("Image dimensions outside supported bounds")
                        }
                        val subtype = raw.getNameAsString(COSName.SUBTYPE)
                        if (raw.getNameAsString(COSName.TYPE) == "XObject" && subtype !in setOf("Image", "Form"))
                            throw UnsupportedPdf("Unsupported XObject")
                        val filter = raw.getDictionaryObject(COSName.FILTER)
                        val names = when(filter) {
                            null, is COSNull -> emptyList()
                            is COSName -> listOf(filter)
                            is COSArray -> {
                                if (filter.size() > 8) throw UnsupportedPdf("Filter chain bound")
                                (0 until filter.size()).map { filter.getObject(it) as? COSName ?: throw UnsupportedPdf("Unknown filter representation") }
                            }
                            else -> throw UnsupportedPdf("Unknown filter representation")
                        }
                        if(names.any { it.name !in filters }) throw UnsupportedPdf("Unsupported stream filter including JPX")
                        if(raw.getDictionaryObject(COSName.DECODE_PARMS) != null) throw UnsupportedPdf("Decode parameters unsupported")
                        if (subtype == "Form" || raw.getInt(COSName.getPDFName("PatternType"), 0) == 1) {
                            // Decode only after ALL loaded graph filters/external streams qualify.
                            contentStreams.add(raw)
                        }
                    }
                    for (key in raw.keySet()) inspect(raw.getDictionaryObject(key), depth + 1)
                }
                is COSArray -> for(i in 0 until raw.size()) inspect(raw.getObject(i), depth + 1)
                is COSNumber -> if(!raw.doubleValue().isFinite()) throw UnsupportedPdf("Nonfinite PDF number")
                is COSName, is COSString, is COSBoolean -> Unit
                else -> throw UnsupportedPdf("Unknown PDF object")
            }
        }
        inspect(document.document.trailer,0)
        for(obj in document.document.objects) inspect(obj,0) // Includes resolved orphan/indirect representations.
        for (stream in contentStreams) {
            checkActive()
            // Includes annotation appearances, nested Forms and tiling patterns.
            val ownResources = stream.getDictionaryObject(COSName.RESOURCES) as? COSDictionary
            content(stream.createInputStream(), ownResources, checkActive)
            if (ownResources != null) fonts(PDResources(ownResources))
        }
        val pages = Collections.newSetFromMap(IdentityHashMap<COSDictionary,Boolean>())
        for(index in 0 until document.numberOfPages) {
            checkActive()
            val page = document.getPage(index)
            if(!pages.add(page.cosObject) || page.cosObject.keySet().any { it.name !in pageKeys }) throw UnsupportedPdf("Invalid/shared page or unsupported page structure")
            // An empty transition dictionary has only PDF defaults (Replace/no effect).
            // ReportLab emits it on ordinary pages. Do NOT accept a transition program,
            // an unknown representation or even explicit nonempty transition options.
            if (page.cosObject.containsKey(COSName.TRANS)) {
                val transition = page.cosObject.getDictionaryObject(COSName.TRANS) as? COSDictionary
                    ?: throw UnsupportedPdf("Unsupported page transition")
                if (transition is COSStream || transition.keySet().isNotEmpty())
                    throw UnsupportedPdf("Unsupported page transition")
            }
            if(page.userUnit != 1f || page.rotation !in listOf(0,90,180,270)) throw UnsupportedPdf("Unsupported page units/rotation")
            for(box in listOf(page.mediaBox,page.cropBox,page.bleedBox,page.trimBox,page.artBox)) {
                if(listOf(box.lowerLeftX,box.lowerLeftY,box.upperRightX,box.upperRightY).any { !it.isFinite() } || box.width <= 0 || box.height <= 0)
                    throw UnsupportedPdf("Invalid page geometry")
            }
            val resources = page.resources ?: throw UnsupportedPdf("Missing page resources")
            content(page.contents, resources.cosObject, checkActive)
            val annotations = page.annotations
            if(annotations.size > 256) throw UnsupportedPdf("Annotation bound")
            for(annotation in annotations) {
                if(annotation.subtype !in setOf("Highlight","Underline","Ink","FreeText")) throw UnsupportedPdf("Unsupported annotation")
                val associated = annotation.cosObject.getDictionaryObject(COSName.P)
                if (associated != null && associated !== page.cosObject) throw UnsupportedPdf("Annotation page association invalid")
                val appearance = annotation.cosObject.getDictionaryObject(COSName.AP) as? COSDictionary
                    ?: throw UnsupportedPdf("Annotation appearance unavailable")
                val normal = appearance.getDictionaryObject(COSName.N) as? COSStream
                    ?: throw UnsupportedPdf("Normal annotation appearance unavailable/stateful")
                if (normal.getNameAsString(COSName.SUBTYPE) != "Form")
                    throw UnsupportedPdf("Unsupported annotation appearance representation")
            }
            fonts(resources)
        }
    }
    private fun fonts(resources: PDResources) {
        val standard14=setOf("Courier","Courier-Bold","Courier-Oblique","Courier-BoldOblique", "Helvetica","Helvetica-Bold","Helvetica-Oblique","Helvetica-BoldOblique", "Times-Roman","Times-Bold","Times-Italic","Times-BoldItalic","Symbol","ZapfDingbats")
        for (name in resources.fontNames) {
            val font = resources.getFont(name) ?: throw UnsupportedPdf("Unavailable font")
            if (font.cosObject.getNameAsString(COSName.SUBTYPE) == "Type3" || (!font.isEmbedded && font.name !in standard14))
                throw UnsupportedPdf("Unsupported/missing font")
        }
    }
    private fun content(input: InputStream, resources: COSDictionary?, checkActive: () -> Unit) {
        val parser = PDFStreamParser(input.use { bounded(it) })
        var tokens = 0
        val operands = mutableListOf<Any>()
        while (true) {
            checkActive()
            val token = parser.parseNextToken() ?: break
            if (++tokens > 200_000) throw UnsupportedPdf("Content token bound")
            if (token is Operator) {
                if (token.name !in operators) throw UnsupportedPdf("Inline image/unknown operator unsupported")
                var resourceName = operands.firstOrNull() as? COSName
                val category = when (token.name) {
                    "Tf" -> COSName.FONT
                    "Do" -> COSName.XOBJECT
                    "gs" -> COSName.EXT_G_STATE
                    "sh" -> COSName.SHADING
                    "CS", "cs" -> if (resourceName?.name in setOf("DeviceRGB", "DeviceGray", "DeviceCMYK", "Pattern")) null else COSName.COLORSPACE
                    "SCN", "scn" -> {
                        resourceName = operands.lastOrNull() as? COSName
                        if (resourceName == null) null else COSName.PATTERN
                    }
                    else -> null
                }
                if (category != null) {
                    val key = resourceName ?: throw UnsupportedPdf("Missing resource operand")
                    val values = resources?.getDictionaryObject(category) as? COSDictionary
                        ?: throw UnsupportedPdf("Missing resource category")
                    if (values.getDictionaryObject(key) == null) throw UnsupportedPdf("Missing referenced resource")
                }
                operands.clear()
            } else {
                if (operands.size >= 256) throw UnsupportedPdf("Operand bound")
                operands.add(token)
            }
        }
    }
    fun bounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream(); val buffer = ByteArray(64*1024)
        while(true) {
            val n = input.read(buffer); if(n < 0) break
            if(n == 0 || n > DECODED_LIMIT-output.size()) throw UnsupportedPdf("Decoded stream bound")
            output.write(buffer,0,n)
        }
        return output.toByteArray()
    }
    private fun box(page: PDPage) = listOf(page.mediaBox,page.cropBox,page.bleedBox,page.trimBox,page.artBox).map {
        listOf(it.lowerLeftX,it.lowerLeftY,it.upperRightX,it.upperRightY)
    }
    data class Page(val boxes: List<List<Float>>, val rotation: Int, val contents: String,
                    val resources: String, val annotations: List<String>, val explicitAssociations: List<Boolean>)
    fun pages(document: PDDocument, checkActive: () -> Unit = {}): List<Page> = (0 until document.numberOfPages).map { index ->
        checkActive()
        val page = document.getPage(index)
        Page(box(page),page.rotation,page.contents.use { hash(bounded(it)) },fingerprint(page.resources.cosObject,checkActive=checkActive),
            page.annotations.map { fingerprint(it.cosObject,setOf(COSName.P),checkActive) },page.annotations.map { it.page != null })
    }
    fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){ "%02x".format(it.toInt() and 255) }
    fun fingerprint(root:COSBase?, ignore:Set<COSName> = emptySet(), checkActive: () -> Unit = {}):String {
        val digest=MessageDigest.getInstance("SHA-256")
        val active=Collections.newSetFromMap(IdentityHashMap<COSBase,Boolean>())
        var nodes=0;var decoded=0L
        fun token(text:String) { val bytes=text.toByteArray(Charsets.UTF_8);digest.update(bytes.size.toString().toByteArray());digest.update(0.toByte());digest.update(bytes) }
        fun walk(raw:COSBase?,depth:Int) {
            checkActive()
            if(++nodes>100_000 || depth>64)throw UnsupportedPdf("Fingerprint graph bound")
            if(raw==null || raw is COSNull){token("null");return}
            if(!active.add(raw))throw UnsupportedPdf("Cyclic semantic resource")
            try { when(raw) {
                is COSObject -> walk(raw.`object`,depth+1)
                is COSDictionary -> {
                    token(if(raw is COSStream)"stream" else "dict")
                    val keys=raw.keySet().filter { !(depth==0 && it in ignore) && !(raw is COSStream && it in setOf(COSName.LENGTH,COSName.FILTER,COSName.DECODE_PARMS)) }.sortedBy { it.name }
                    token(keys.size.toString())
                    for(key in keys){token(key.name);walk(raw.getDictionaryObject(key),depth+1)}
                    if(raw is COSStream){val bytes=raw.createInputStream().use { bounded(it) };decoded+=bytes.size
                        if(decoded>64L*1024*1024)throw UnsupportedPdf("Decoded graph byte bound");token(hash(bytes))}
                }
                is COSArray -> {token("array");token(raw.size().toString());for(i in 0 until raw.size())walk(raw.getObject(i),depth+1)}
                is COSName -> {token("name");token(raw.name)}
                is COSString -> {token("string");token(raw.bytes.joinToString(""){"%02x".format(it.toInt() and 255)})}
                is COSNumber -> {token("number");token(raw.doubleValue().toString())}
                is COSBoolean -> {token("boolean");token(raw.value.toString())}
                else -> throw UnsupportedPdf("Unknown semantic value")
            } } finally { active.remove(raw) }
        }
        walk(root,0);return digest.digest().joinToString(""){"%02x".format(it.toInt() and 255)}
    }
}
