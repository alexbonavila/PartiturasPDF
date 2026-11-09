package com.abdev.partituraspdf.storage.transaction

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.abdev.partituraspdf.pdf.api.*
import com.abdev.partituraspdf.pdf.editing.*
import com.abdev.partituraspdf.pdf.workflow.*
import com.abdev.partituraspdf.storage.api.*
import com.tom_roush.pdfbox.cos.*
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MProductionPdfEngineTest {
    private suspend fun sandbox(fixture:String="02",body:suspend(MPdfboxIntegrationRig)->Unit) {
        val rig=MPdfboxIntegrationRig(fixture,useProduction=true);var primary:Throwable?=null
        try{body(rig)}catch(t:Throwable){primary=t;throw t}
        finally{withContext(NonCancellable){try{rig.close()}catch(t:Throwable){if(primary!=null)primary.addSuppressed(t)else throw t}}}
    }
    @Test fun productionFactoryReordersRotatesPublishesAndCleans() { runBlocking {
        sandbox { r ->
            val coordinator=MTransactionCoordinator.production(InstrumentationRegistry.getInstrumentation().targetContext,r.gate,
                TransactionSnapshotCapturer(r.access,r.store),r.store,r.journal)
            val ready=coordinator.prepare(r.source,r.plan) as MPreparation.Ready
            val destination=r.Destination(ready.request.operation)
            val result=ready.session.complete(ready.request.token,destination.capability()) as PublicationResult.VerifiedNewDocument
            assertEquals(result.evidence,destination.evidence());assertEquals(1,destination.writes)
            assertEquals(SnapshotCleanupStatus.COMPLETE,result.cleanup)
            assertEquals(MState.CLEANED,r.journal.read(ready.request.operation).last().state)
            assertSame(PublicationResult.NeedsOwnerResolution,ready.session.complete(ready.request.token,destination.capability()))
            assertEquals(1,destination.writes);r.unchanged()
        }
    } }
    @Test fun supportedCorpusReorderExtractionGeometryAndAllQuarterTurns() { runBlocking {
        for(id in listOf("01","02","03","06","07","08","09","10","11","12","13")) sandbox(id){ r ->
            val count=r.fixture.getInt("expected_pages")
            for(turns in 0..3){
                val selected=if(count==1)listOf(0)else listOf(count-1,0)
                val plan=OrderedPagePlan(selected.map{PageSelection(it,turns)})
                val ready=r.coordinator().prepare(r.source,plan) as MPreparation.Ready
                val destination=r.Destination(ready.request.operation)
                val result=ready.session.complete(ready.request.token,destination.capability())
                assertTrue("Supported case $id/$turns",result is PublicationResult.VerifiedNewDocument)
                assertEquals(1,destination.writes)
            }
        }
    } }
    @Test fun generalNoncanonicalDrawingAndInheritedResourcesWorkWithoutFixturePrivilege() { runBlocking {
        sandbox { r ->
            val original=r.original.readBytes()
            try {
                val generated=ByteArrayOutputStream()
                PDDocument().use{pdf ->
                    val page=PDPage(PDRectangle(200f,300f));pdf.addPage(page)
                    page.resources=PDResources()
                    PDPageContentStream(pdf,page).use{it.addRect(20f,30f,70f,80f);it.fill()}
                    // Move resources to the parent to exercise real inheritance resolution.
                    val resources=page.resources
                    pdf.pages.cosObject.setItem(COSName.RESOURCES,resources)
                    page.cosObject.removeItem(COSName.RESOURCES)
                    pdf.save(generated)
                }
                r.original.writeBytes(generated.toByteArray())
                val plan=OrderedPagePlan(listOf(PageSelection(0,1)))
                val ready=r.coordinator().prepare(r.source,plan) as MPreparation.Ready
                assertTrue(ready.session.complete(ready.request.token,r.Destination(ready.request.operation).capability()) is PublicationResult.VerifiedNewDocument)
            } finally { r.original.writeBytes(original) }
        }
    } }
    @Test fun productionValidatorRejectsWrongPlan() { runBlocking {
        sandbox { r ->
            val id=TransactionId(UUID.randomUUID().toString())
            val source=(TransactionSnapshotCapturer(r.access,r.store).captureForTransaction(r.source,SnapshotCaptureLimits(64L*1024*1024),id) as SnapshotCaptureResult.Captured).snapshot
            val output=(r.adapters.transform(source,r.plan,id,r.store) as EngineResult.Produced).output
            try {
                val wrong=OrderedPagePlan(listOf(PageSelection(0,0),PageSelection(1,0)))
                assertTrue(r.adapters.validate(source,output,wrong) is ValidationResult.Refused)
            }finally{for(s in listOf(output,source)){s.requestClose();assertSame(SnapshotReleaseResult.Released,s.awaitReleased())}}
        }
    } }
    @Test fun malformedOutputAndCorruptedSnapshotCannotMintArtifacts() { runBlocking {
        sandbox { r ->
            val id=TransactionId(UUID.randomUUID().toString())
            val source=(TransactionSnapshotCapturer(r.access,r.store).captureForTransaction(r.source,SnapshotCaptureLimits(64L*1024*1024),id) as SnapshotCaptureResult.Captured).snapshot
            val output=(r.store.stage(id){it.write(byteArrayOf(1,2,3))} as SnapshotCaptureResult.Captured).snapshot
            try{assertTrue(r.adapters.validate(source,output,r.plan) is ValidationResult.Refused)}
            finally{for(s in listOf(output,source)){s.requestClose();assertSame(SnapshotReleaseResult.Released,s.awaitReleased())}}
        }
        sandbox { r ->
            val id=TransactionId(UUID.randomUUID().toString())
            val source=(TransactionSnapshotCapturer(r.access,r.store).captureForTransaction(r.source,SnapshotCaptureLimits(64L*1024*1024),id) as SnapshotCaptureResult.Captured).snapshot
            try {
                val file=java.io.File(r.root.directory,"tx-${id.value}/source.sealed")
                check(file.setWritable(true)) // Explicit corruption injection on private TEST bytes only.
                java.io.RandomAccessFile(file,"rw").use{it.seek(0);it.write(0)}
                assertTrue(r.adapters.transform(source,r.plan,id,r.store) is EngineResult.Rejected)
            }finally{source.requestClose();assertSame(SnapshotReleaseResult.Released,source.awaitReleased())}
        }
    } }
    @Test fun actualProtectedCorruptAndOutOfRangeRequestsRejectWithoutPicker() { runBlocking {
        for(id in listOf("14","15")) sandbox(id){r ->
            val result=r.coordinator().prepare(r.source,OrderedPagePlan(listOf(PageSelection(0,0))))
            assertTrue(result is MPreparation.Rejected)
            if(id=="14")assertEquals(PdfOperationFailure.ProtectedDocument,(result as MPreparation.Rejected).reason)
        }
        sandbox{r->assertEquals(PdfOperationFailure.InvalidPlan,
            (r.coordinator().prepare(r.source,OrderedPagePlan(listOf(PageSelection(20,0)))) as MPreparation.Rejected).reason)}
    } }
    @Test fun objectGraphPolicyRejectsIndirectJpxFormsActionsSignaturesAndInlineImages() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        ProductionPdfAdapters(context)
        for(key in listOf("AcroForm","OpenAction","Names","Perms","OCProperties")) {
            PDDocument().use{pdf->pdf.addPage(PDPage());pdf.documentCatalog.cosObject.setItem(COSName.getPDFName(key),COSDictionary())
                assertThrows(UnsupportedPdf::class.java){ProductionPdfPolicy.requireSupported(pdf)}}
        }
        for (filter in listOf<COSBase>(COSName.JPX_DECODE, COSObject(COSName.JPX_DECODE),
            COSArray().apply { add(COSName.FLATE_DECODE); add(COSObject(COSName.JPX_DECODE)) })) {
            PDDocument().use { pdf ->
                val page=PDPage();pdf.addPage(page);page.resources=PDResources()
                val stream=pdf.document.createCOSStream();stream.setItem(COSName.FILTER,filter)
                page.resources.cosObject.setItem(COSName.XOBJECT,COSDictionary().apply { setItem(COSName.getPDFName("Image"),COSObject(stream)) })
                val failure=assertThrows(UnsupportedPdf::class.java) { ProductionPdfPolicy.requireSupported(pdf) }
                assertEquals("Unsupported stream filter including JPX",failure.message)
            }
        }
        PDDocument().use { pdf ->
            val page=PDPage();pdf.addPage(page);page.resources=PDResources()
            val form=pdf.document.createCOSStream()
            form.setItem(COSName.SUBTYPE,COSName.getPDFName("Form"))
            form.createOutputStream().use { it.write("BI /W 1 /H 1 /F /JPXDecode ID x EI".toByteArray()) }
            page.resources.cosObject.setItem(COSName.XOBJECT,COSDictionary().apply { setItem(COSName.getPDFName("Nested"),form) })
            val failure=assertThrows(UnsupportedPdf::class.java) { ProductionPdfPolicy.requireSupported(pdf) }
            assertEquals("Inline image/unknown operator unsupported",failure.message)
        }
        PDDocument().use{pdf->
            val page=PDPage();pdf.addPage(page);page.resources=PDResources()
            val stream=pdf.document.createCOSStream()
            stream.createOutputStream().use{it.write("BI /W 1 /H 1 /BPC 8 /CS /G ID x EI".toByteArray())}
            page.cosObject.setItem(COSName.CONTENTS,stream)
            assertThrows(UnsupportedPdf::class.java){ProductionPdfPolicy.requireSupported(pdf)}
        }
    }
    @Test fun policyRejectsInvalidTreesMissingResourcesAndForeignAnnotationPages() {
        ProductionPdfAdapters(InstrumentationRegistry.getInstrumentation().targetContext)
        PDDocument().use { pdf ->
            val page=PDPage(); pdf.addPage(page); page.resources=PDResources()
            pdf.pages.cosObject.setInt(COSName.COUNT,2)
            assertThrows(UnsupportedPdf::class.java) { ProductionPdfPolicy.requireSupported(pdf) }
        }
        PDDocument().use { pdf ->
            val page=PDPage(); pdf.addPage(page); page.resources=PDResources()
            val stream=pdf.document.createCOSStream()
            stream.createOutputStream().use { it.write("BT /MissingFont 12 Tf (text) Tj ET".toByteArray()) }
            page.cosObject.setItem(COSName.CONTENTS,stream)
            assertThrows(UnsupportedPdf::class.java) { ProductionPdfPolicy.requireSupported(pdf) }
        }
        PDDocument().use { pdf ->
            val page=PDPage(); pdf.addPage(page); page.resources=PDResources()
            val other=PDPage(); pdf.addPage(other); other.resources=PDResources()
            val annotation=com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup("Highlight")
            annotation.page=other
            annotation.cosObject.setItem(COSName.AP,COSDictionary())
            page.annotations.add(annotation)
            assertThrows(UnsupportedPdf::class.java) { ProductionPdfPolicy.requireSupported(pdf) }
            annotation.page=page
            val failure=assertThrows(UnsupportedPdf::class.java) { ProductionPdfPolicy.requireSupported(pdf) }
            assertEquals("Normal annotation appearance unavailable/stateful",failure.message)
        }
    }
    @Test fun alteredResourceGraphCannotReceiveValidationAuthority() { runBlocking {
        sandbox { r ->
            val id=TransactionId(UUID.randomUUID().toString())
            val source=(TransactionSnapshotCapturer(r.access,r.store).captureForTransaction(r.source,SnapshotCaptureLimits(64L*1024*1024),id) as SnapshotCaptureResult.Captured).snapshot
            val output=(r.adapters.transform(source,r.plan,id,r.store) as EngineResult.Produced).output
            var changed:WorkingSnapshot?=null
            try {
                val bytes=ByteArrayOutputStream()
                val lease=(output.acquireReadLease() as SnapshotLeaseResult.Acquired).lease
                lease.use { android.os.ParcelFileDescriptor.AutoCloseInputStream(android.os.ParcelFileDescriptor.dup(it.descriptor.fileDescriptor)).use { input ->
                    PDDocument.load(input).use { pdf ->
                        pdf.getPage(0).resources.cosObject.setString(COSName.getPDFName("InjectedResourceMutation"),"different")
                        pdf.save(bytes)
                    }
                } }
                val changedOutput=(r.store.stage(TransactionId(UUID.randomUUID().toString())) { it.write(bytes.toByteArray()) } as SnapshotCaptureResult.Captured).snapshot
                changed=changedOutput
                assertTrue(r.adapters.validate(source,changedOutput,r.plan) is ValidationResult.Refused)
            } finally { for(owner in listOfNotNull(changed,output,source)) { owner.requestClose(); assertSame(SnapshotReleaseResult.Released,owner.awaitReleased()) } }
        }
    } }
    @Test fun cancellationDuringProductionSourceLeaseClosesPartialOutput() { runBlocking {
        sandbox { r ->
            val id=TransactionId(UUID.randomUUID().toString())
            val source=(TransactionSnapshotCapturer(r.access,r.store).captureForTransaction(r.source,SnapshotCaptureLimits(64L*1024*1024),id) as SnapshotCaptureResult.Captured).snapshot
            val entered=CompletableDeferred<Unit>()
            val barrier=CompletableDeferred<Unit>()
            val gated=WorkingSnapshot(object:SnapshotOwnerPort {
                override val id get()=source.id
                override val origin get()=source.origin
                override val evidence get()=source.evidence
                override suspend fun acquire():SnapshotLeaseResult { entered.complete(Unit); barrier.await(); return source.acquireReadLease() }
                override fun requestClose()=source.requestClose()
                override suspend fun awaitReleased()=source.awaitReleased()
            })
            try {
                val cancellation=CancellationException("Injected production cancellation")
                val attempt=async { r.adapters.transform(gated,r.plan,id,r.store) }
                entered.await(); attempt.cancel(cancellation)
                try { attempt.await(); fail("Cancellation must propagate") }
                catch(expected:CancellationException) { assertSame(cancellation,expected) }
                assertFalse(r.root.directory.walkTopDown().any { it.name=="output.part" || it.name=="output.sealed" })
                r.unchanged()
            } finally { source.requestClose(); assertSame(SnapshotReleaseResult.Released,source.awaitReleased()) }
        }
    } }
    @Test fun realProductionSealCheckpointAndCleanupFaultsNeverInventSuccess() {runBlocking{
        sandbox{r->r.failSeal=true;assertTrue(r.coordinator().prepare(r.source,r.plan) is MPreparation.Rejected)}
        sandbox{r->r.failCheckpoint="000000000005.tmp";assertTrue(r.coordinator().prepare(r.source,r.plan) is MPreparation.Rejected)}
        sandbox{r->
            val ready=r.ready();val destination=r.Destination(ready.request.operation)
            r.cleanupFault=java.io.IOException("Injected cleanup debt")
            val result=ready.session.complete(ready.request.token,destination.capability()) as PublicationResult.VerifiedNewDocument
            assertEquals(SnapshotCleanupStatus.PENDING,result.cleanup);assertEquals(result.evidence,destination.evidence())
            assertTrue(r.budget.used()>0)
        }
    }}
}
