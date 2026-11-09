package com.abdev.partituraspdf.pdf.editing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.abdev.partituraspdf.pdf.api.*
import com.abdev.partituraspdf.storage.api.*
import com.abdev.partituraspdf.storage.transaction.PrivateSnapshotStore
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.io.ScratchFile
import com.tom_roush.pdfbox.pdfparser.PDFParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.*
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest

/** Concrete production boundary, explicitly constructed after owner security/packaging disposition.
 * No fixture hashes/test code. Host defaults remain EngineApprovalRequired until that disposition.
 */
internal class ProductionPdfAdapters(context: Context) : PdfModificationAdapters {
    init { PDFBoxResourceLoader.init(context.applicationContext) }
    private suspend fun verify(snapshot:WorkingSnapshot) {
        val maximum=if(snapshot.origin is SnapshotOrigin.Source)64L*1024*1024 else 128L*1024*1024
        if(snapshot.evidence.byteCount !in 1..maximum)throw IOException("Snapshot size invalid")
        val lease=(snapshot.acquireReadLease() as? SnapshotLeaseResult.Acquired)?.lease ?: throw IOException("Snapshot unavailable")
        lease.use { ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(it.descriptor.fileDescriptor)).use { input ->
            val digest=MessageDigest.getInstance("SHA-256");val buffer=ByteArray(64*1024);var count=0L
            while(true){currentCoroutineContext().ensureActive();val n=input.read(buffer);if(n<0)break
                if(n==0 || n>snapshot.evidence.byteCount-count)throw IOException("Snapshot byte mismatch")
                digest.update(buffer,0,n);count+=n}
            val hash=digest.digest().joinToString(""){"%02x".format(it.toInt() and 255)}
            if(count!=snapshot.evidence.byteCount || hash!=snapshot.evidence.sha256)throw IOException("Snapshot integrity mismatch")
        } }
    }
    private suspend inline fun <T> parsed(snapshot:WorkingSnapshot, body:(PDDocument)->T):T {
        verify(snapshot)
        val lease=(snapshot.acquireReadLease() as? SnapshotLeaseResult.Acquired)?.lease ?: throw IOException("Snapshot unavailable")
        val caller = currentCoroutineContext()
        return lease.use {
            ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(it.descriptor.fileDescriptor)).use { input ->
                SnapshotRandomAccess(input.channel, snapshot.evidence.byteCount) { caller.ensureActive() }.use { access ->
                    // Strict xref/page parsing; no lenient repair or unowned temporary PDF.
                    // Scratch bound is not a whole-parser heap or CPU guarantee.
                    ScratchFile(MemoryUsageSetting.setupMainMemoryOnly(32L * 1024 * 1024)).use { scratch ->
                        val parser = PDFParser(access, "", scratch)
                        parser.setLenient(false)
                        parser.parse()
                        parser.getPDDocument().use { document ->
                            caller.ensureActive()
                            ProductionPdfPolicy.requireSupported(document) { caller.ensureActive() }
                            body(document)
                        }
                    }
                }
            }
        }
    }

    override suspend fun transform(source:WorkingSnapshot,plan:OrderedPagePlan,transaction:TransactionId,store:PrivateSnapshotStore):EngineResult {
        if(source.origin !is SnapshotOrigin.Source)return EngineResult.Rejected(PdfOperationFailure.InvalidInput)
        var reason=PdfOperationFailure.TransformationFailure
        val caller=currentCoroutineContext()
        val result=store.stage(transaction) { writer ->
            try { parsed(source) { document ->
                if(plan.selections.any { it.sourcePageIndex>=document.numberOfPages }) {
                    reason=PdfOperationFailure.InvalidPlan;throw IOException("Invalid selection")
                }
                val selected=plan.selections.map { document.getPage(it.sourcePageIndex) }
                for(page in selected) {
                    page.resources=page.resources;page.mediaBox=page.mediaBox;page.cropBox=page.cropBox
                    page.bleedBox=page.bleedBox;page.trimBox=page.trimBox;page.artBox=page.artBox
                    page.rotation=page.rotation
                }
                while(document.numberOfPages>0)document.removePage(0)
                for((index,page) in selected.withIndex()) {
                    page.rotation=(page.rotation+90*plan.selections[index].addedClockwiseQuarterTurns)%360
                    document.addPage(page)
                }
                ProductionPdfPolicy.requireSupported(document) { caller.ensureActive() }
                val stream=object:OutputStream(){
                    override fun write(value:Int)=write(byteArrayOf(value.toByte()),0,1)
                    override fun write(bytes:ByteArray,offset:Int,length:Int){caller.ensureActive();runBlocking{writer.write(bytes,offset,length)}}
                }
                document.save(stream);caller.ensureActive()
            } } catch(protected:InvalidPasswordException){reason=PdfOperationFailure.ProtectedDocument;throw protected}
            catch(unsupported:UnsupportedPdf){reason=PdfOperationFailure.UnsupportedDocument;throw unsupported}
            // Parser/stream close completes before store sealing. Store owns partial cleanup/debt.
        }
        return when(result){
            is SnapshotCaptureResult.Captured -> EngineResult.Produced(result.snapshot)
            is SnapshotCaptureResult.Rejected -> EngineResult.Rejected(
                if(result.reason==SnapshotFailure.InputLimitExceeded)PdfOperationFailure.OutputLimit else reason,result.cleanup)
        }
    }
    private suspend fun text(document:PDDocument,index:Int,rotation:Int):String {
        currentCoroutineContext().ensureActive()
        val page=document.getPage(index);val actual=page.rotation
        try {page.rotation=rotation;return PDFTextStripper().apply{startPage=index+1;endPage=index+1}.getText(document)}
        finally{page.rotation=actual}
    }
    private suspend inline fun <T> bitmap(snapshot:WorkingSnapshot,index:Int,body:(Bitmap)->T):T {
        val lease=(snapshot.acquireReadLease() as? SnapshotLeaseResult.Acquired)?.lease ?: throw IOException("Raster source unavailable")
        return lease.use { ParcelFileDescriptor.dup(it.descriptor.fileDescriptor).use { fd -> PdfRenderer(fd).use { renderer ->
            renderer.openPage(index).use { page ->
                if(page.width<=0 || page.height<=0 || page.width>4000 || page.height>4000 || page.width.toLong()*page.height>4_000_000)
                    throw UnsupportedPdf("Native verification dimensions outside bounds")
                val image=Bitmap.createBitmap(page.width,page.height,Bitmap.Config.ARGB_8888)
                try {image.eraseColor(Color.WHITE);page.render(image,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);body(image)}
                finally{image.recycle()}
            }
        } } }
    }
    override suspend fun validate(source:WorkingSnapshot,output:WorkingSnapshot,plan:OrderedPagePlan):ValidationResult = withContext(Dispatchers.IO) {
        if(source.origin !is SnapshotOrigin.Source || output.origin !is SnapshotOrigin.Produced || source===output)
            return@withContext ValidationResult.Refused(PdfValidationReport.Rejected)
        val validationContext = currentCoroutineContext()
        try { parsed(source) { original -> parsed(output) { actual ->
            if(plan.selections.any{it.sourcePageIndex>=original.numberOfPages} || actual.numberOfPages!=plan.selections.size)
                return@withContext ValidationResult.Refused(PdfValidationReport.Rejected)
            val sourcePages=ProductionPdfPolicy.pages(original) { validationContext.ensureActive() };val outputPages=ProductionPdfPolicy.pages(actual) { validationContext.ensureActive() }
            if(ProductionPdfPolicy.fingerprint(original.documentCatalog.cosObject,setOf(com.tom_roush.pdfbox.cos.COSName.PAGES)) !=
                ProductionPdfPolicy.fingerprint(actual.documentCatalog.cosObject,setOf(com.tom_roush.pdfbox.cos.COSName.PAGES)))
                return@withContext ValidationResult.Refused(PdfValidationReport.Rejected)
            if(ProductionPdfPolicy.fingerprint(original.documentInformation.cosObject)!=ProductionPdfPolicy.fingerprint(actual.documentInformation.cosObject))
                return@withContext ValidationResult.Refused(PdfValidationReport.Rejected)
            for((index,selection) in plan.selections.withIndex()) {
                currentCoroutineContext().ensureActive()
                val old=sourcePages[selection.sourcePageIndex]
                val expected=old.copy(rotation=(old.rotation+90*selection.addedClockwiseQuarterTurns)%360)
                if(outputPages[index]!=expected || text(original,selection.sourcePageIndex,old.rotation)!=text(actual,index,old.rotation))
                    return@withContext ValidationResult.Refused(PdfValidationReport.Rejected)
                val matches=bitmap(source,selection.sourcePageIndex) { reference -> bitmapCompare(reference,output,index,selection.addedClockwiseQuarterTurns) }
                if(!matches)return@withContext ValidationResult.Refused(PdfValidationReport.Rejected)
            }
            verify(source);verify(output);currentCoroutineContext().ensureActive()
            ValidationResult.Accepted(ValidatedPdfArtifact(output,plan))
        } } } catch(cancel:CancellationException){throw cancel}
        catch(unsupported:UnsupportedPdf){ValidationResult.Refused(PdfValidationReport.Unsupported)}
        catch(io:IOException){ValidationResult.Refused(PdfValidationReport.Inconclusive)}
    }
    private suspend fun bitmapCompare(reference:Bitmap,output:WorkingSnapshot,index:Int,turns:Int):Boolean {
        val expected=if(turns==0)reference else Bitmap.createBitmap(reference,0,0,reference.width,reference.height,
            Matrix().apply { postRotate(90f*turns) },false)
        return try {bitmap(output,index){image->expected.sameAs(image)}} finally {if(expected!==reference)expected.recycle()}
    }
}
