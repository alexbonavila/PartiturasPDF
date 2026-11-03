package com.abdev.partituraspdf.pdf.engine.spike

import com.tom_roush.pdfbox.cos.*
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.IdentityHashMap

/** Deliberately conservative TEST oracle. Unknown/cyclic structures fail, never become equivalent.
 * Object numbers and compression are not semantics. This is NOT an independent PDF validator.
 */
internal object SpikeSemantics {
    private const val MAX_DECODED = 128L * 1024 * 1024
    internal data class Page(
        val media: List<Float>, val crop: List<Float>, val rotation: Int,
        val contents: String, val resources: String, val text: String,
        val annotations: List<String>, val annotationTypes: List<String>,
        val hasPageAssociations: List<Boolean>, val appearances: List<String>,
    )

    fun snapshot(document: PDDocument): List<Page> {
        require(!document.isEncrypted) { "Encrypted fixture is outside scope" }
        require(document.numberOfPages in 1..100) { "Unbounded page tree" }
        return (0 until document.numberOfPages).map { index ->
            val page = document.getPage(index)
            val annotations = page.annotations
            require(annotations.size <= 64) { "Annotation bound exceeded" }
            val stripper = PDFTextStripper().apply { startPage = index + 1; endPage = index + 1 }
            Page(box(page.mediaBox), box(page.cropBox), normalized(page.rotation),
                page.contents.use { digest(it) }, fingerprint(page.resources?.cosObject),
                stripper.getText(document), annotations.map { fingerprint(it.cosObject, setOf(COSName.P)) },
                annotations.map { it.subtype }, annotations.map { annotation ->
                    val associated = annotation.page
                    require(associated == null || associated.cosObject === page.cosObject) {
                        "Annotation points outside its containing page"
                    }
                    associated != null
                }, annotations.map { fingerprint(it.cosObject.getDictionaryObject(COSName.AP)) })
        }
    }

    fun selected(source: List<Page>, indices: List<Int>, turns: Int): List<Page> {
        require(turns in 0..3 && indices.isNotEmpty() && indices.distinct().size == indices.size)
        return indices.map { source[it].let { page -> page.copy(rotation = (page.rotation + 90 * turns) % 360) } }
    }

    fun normalized(rotation: Int): Int = ((rotation % 360) + 360) % 360
    private fun box(box: PDRectangle) = listOf(box.lowerLeftX, box.lowerLeftY, box.upperRightX, box.upperRightY)

    fun digest(input: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(count > 0 && count <= MAX_DECODED - total) { "Decoded stream bound exceeded" }
            md.update(buffer, 0, count); total += count
        }
        return hex(md.digest())
    }

    fun fingerprint(base: COSBase?, ignoredAtRoot: Set<COSName> = emptySet()): String {
        val md = MessageDigest.getInstance("SHA-256")
        val active = Collections.newSetFromMap(IdentityHashMap<COSBase, Boolean>())
        var nodes = 0
        var decodedBytes = 0L
        fun token(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            md.update(bytes.size.toString().toByteArray(Charsets.US_ASCII)); md.update(0.toByte()); md.update(bytes)
        }
        fun walk(raw: COSBase?, depth: Int) {
            require(++nodes <= 100_000 && depth <= 64) { "Semantic graph bound exceeded" }
            if (raw == null || raw is COSNull) { token("null"); return }
            require(active.add(raw)) { "Cyclic semantic graph: UNVERIFIED" }
            try {
                when (raw) {
                    is COSObject -> walk(raw.`object`, depth + 1)
                    is COSName -> { token("name"); token(raw.name) }
                    is COSString -> { token("string"); token(hex(raw.bytes)) }
                    is COSNumber -> {
                        val value = raw.doubleValue(); require(value.isFinite()) { "Nonfinite PDF number" }
                        token("number"); token(value.toString())
                    }
                    is COSBoolean -> { token("boolean"); token(raw.value.toString()) }
                    is COSArray -> {
                        token("array"); token(raw.size().toString())
                        for (i in 0 until raw.size()) walk(raw.get(i), depth + 1)
                    }
                    is COSDictionary -> {
                        token(if (raw is COSStream) "stream" else "dictionary")
                        val ignored = if (depth == 0) ignoredAtRoot else emptySet()
                        val keys = raw.keySet().filter { it !in ignored &&
                            !(raw is COSStream && it in setOf(COSName.LENGTH, COSName.FILTER, COSName.DECODE_PARMS))
                        }.sortedBy { it.name }
                        token(keys.size.toString())
                        for (key in keys) { token(key.name); walk(raw.getDictionaryObject(key), depth + 1) }
                        if (raw is COSStream) {
                            // Do not let the absent optional JPX codec silently omit pixels.
                            val filter = raw.getDictionaryObject(COSName.FILTER)
                            require(filter != COSName.JPX_DECODE &&
                                !(filter is COSArray && (0 until filter.size()).any { filter.getObject(it) == COSName.JPX_DECODE })) {
                                "JPX unsupported; no optional codec approved"
                            }
                            val data = MessageDigest.getInstance("SHA-256")
                            raw.createInputStream().use { input ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    val n = input.read(buffer); if (n < 0) break
                                    require(n > 0 && n <= MAX_DECODED - decodedBytes) { "Decoded resource bound exceeded" }
                                    data.update(buffer, 0, n); decodedBytes += n
                                }
                            }
                            token(hex(data.digest()))
                        }
                    }
                    else -> error("Unsupported COS value: UNVERIFIED")
                }
            } finally { active.remove(raw) }
        }
        walk(base, 0)
        return hex(md.digest())
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
}
