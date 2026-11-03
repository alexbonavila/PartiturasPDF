package com.abdev.partituraspdf.pdf.engine.spike

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/** Candidate tests only. No production engine adapter, validation authority or public output. */
@RunWith(AndroidJUnit4::class)
class PdfboxAndroidSpikeTest {
    private fun scenario(
        sandbox: PdfboxSpikeSandbox, id: String, selection: List<Int>? = null,
        turns: Int = 0, useImport: Boolean = false,
    ) {
        val (source, entry) = sandbox.fixture(id)
        val output = sandbox.fresh("result")
        var expected = emptyList<SpikeSemantics.Page>()
        var indices = emptyList<Int>()
        var sourceRasters = emptyList<String>()
        sandbox.document(source) { document ->
            val original = sandbox.phase("inspectSource") { SpikeSemantics.snapshot(document) }
            sandbox.verifyManifest(document, entry, original)
            if (id in setOf("02", "03", "04", "05")) {
                for ((index, page) in original.withIndex()) {
                    assertTrue("Source page-label oracle", page.text.contains("PAGE-ID ${(index + 1).toString().padStart(3, '0')}"))
                }
                assertEquals(original.size, original.map { it.text }.distinct().size)
            }
            indices = selection ?: original.indices.toList()
            expected = SpikeSemantics.selected(original, indices, turns)
            // The source and output are independently rendered by Android/PDFium, not PDFBox.
            // Bounded thumbnails are supporting evidence, not a complete fidelity guarantee.
            if (turns == 0) sourceRasters = indices.map { sandbox.raster(source, it) }
            if (useImport) sandbox.emptyDocument { destination ->
                sandbox.phase("transformImport") {
                    for (index in indices) {
                        // Deliberately evaluate stock importPage; do not repair/drop resources or annotations.
                        val page = destination.importPage(document.getPage(index))
                        page.rotation = (original[index].rotation + 90 * turns) % 360
                    }
                }
                sandbox.save(destination, output)
            } else {
                sandbox.phase("transformWorkingCopy") {
                    val selected = indices.map { document.getPage(it) }
                    // Materialize inherited properties before detaching pages from their old tree.
                    for (page in selected) {
                        page.resources = page.resources; page.mediaBox = page.mediaBox
                        page.cropBox = page.cropBox; page.rotation = page.rotation
                    }
                    while (document.numberOfPages > 0) document.removePage(0)
                    for ((position, page) in selected.withIndex()) {
                        page.rotation = expected[position].rotation; document.addPage(page)
                    }
                }
                sandbox.save(document, output)
            }
        }
        // Record the recipe even if a later semantic assertion fails; this is not an acceptance receipt.
        sandbox.record(output, entry, indices, turns, if (useImport) "STOCK_IMPORT" else "WORKING_COPY")
        // Producer handles are closed before a new parser opens the actual on-disk output.
        sandbox.document(output, "reopen") { reopened ->
            val actual = sandbox.phase("inspectSavedOutput") { SpikeSemantics.snapshot(reopened) }
            assertEquals("Persisted selection/order/rotation/resources/annotations", expected, actual)
            if (id == "12") {
                assertTrue(actual.flatMap { it.annotationTypes }.toSet().containsAll(setOf("Highlight", "Underline", "Ink", "FreeText")))
                assertTrue(actual.flatMap { it.appearances }.all { it != SpikeSemantics.fingerprint(null) })
            }
            if (id == "10") assertTrue((0 until reopened.numberOfPages).all { page ->
                val resources = reopened.getPage(page).resources
                resources.xObjectNames.any { resources.isImageXObject(it) }
            })
            if (id == "11" || id == "13") assertTrue((0 until reopened.numberOfPages).all { page ->
                val resources = reopened.getPage(page).resources
                resources.fontNames.any { resources.getFont(it).isEmbedded }
            })
        }
        if (turns == 0) assertEquals("Independent PDFium raster/order", sourceRasters, indices.indices.map { sandbox.raster(output, it) })
        sandbox.assertNoOwnedHandles()
    }

    @Test fun identitySinglePageSavedAndIndependentlyRendered() {
        PdfboxSpikeSandbox("identity01").use { scenario(it, "01") }
    }
    @Test fun reversedAndSelectedPagesRetainDistinctPageLabels() {
        PdfboxSpikeSandbox("order02").use {
            scenario(it, "02", listOf(1, 0)); scenario(it, "02", listOf(1))
        }
    }
    @Test fun eachQuarterTurnIsPersistedForEachSelectedPage() {
        PdfboxSpikeSandbox("quarterTurns02").use { sandbox ->
            for (turns in 1..3) for (index in 0..1) scenario(sandbox, "02", listOf(index), turns)
        }
    }
    @Test fun tenPageSelectionRetainsOrderAndContent() {
        PdfboxSpikeSandbox("selection03").use { scenario(it, "03", listOf(9, 4, 0)) }
    }
    @Test fun a3AndA5GeometryRemainsIntact() {
        PdfboxSpikeSandbox("geometry0607").use { scenario(it, "06"); scenario(it, "07") }
    }
    @Test fun mixedBoxesSurviveSelectionAndAdditionalRotation() {
        PdfboxSpikeSandbox("mixed08").use { scenario(it, "08", listOf(4, 1, 2), 1) }
    }
    @Test fun additionalQuarterTurnComposesWithAllOriginalRotations() {
        PdfboxSpikeSandbox("rotation09").use { scenario(it, "09", listOf(3, 2, 1, 0), 1) }
    }
    @Test fun scannedImageXObjectsAndPixelsSurvive() {
        PdfboxSpikeSandbox("scanned10").use { scenario(it, "10", listOf(1, 0)) }
    }
    @Test fun vectorContentEmbeddedFontsAndGlyphPixelsSurvive() {
        PdfboxSpikeSandbox("vector11").use { scenario(it, "11", listOf(1, 0)) }
    }
    @Test fun annotationTypesAssociationsAndAppearancesSurvive() {
        PdfboxSpikeSandbox("annotations12").use { scenario(it, "12", listOf(1, 0)) }
    }
    @Test fun complexResourcesTransparencyAndPixelsSurvive() {
        PdfboxSpikeSandbox("complex13").use { scenario(it, "13", listOf(2, 0, 1)) }
    }
    @Test fun stockImportComparedWithWorkingCopyWithoutSilentRepairs() {
        PdfboxSpikeSandbox("importComparison").use { sandbox ->
            // Separate from the working-copy cases: import failure identifies that approach's limit.
            for (id in listOf("02", "08", "10", "11", "12", "13")) {
                val count = when (id) { "08" -> 5; "13" -> 3; else -> 2 }
                scenario(sandbox, id, (count - 1 downTo 0).toList(), useImport = true)
            }
        }
    }
    @Test fun passwordProtectedAndCorruptedInputsCannotProduceOutputs() {
        PdfboxSpikeSandbox("reject1415").use { sandbox ->
            val (protectedFile, _) = sandbox.fixture("14")
            assertThrows(InvalidPasswordException::class.java) { sandbox.document(protectedFile) { fail("Protected input accepted") } }
            val (corrupt, _) = sandbox.fixture("15")
            assertThrows(IOException::class.java) { sandbox.document(corrupt) { fail("Corrupt input accepted") } }
            // No save call or output file is created on either rejection path. Password never supplied.
            sandbox.assertNoOwnedHandles()
        }
    }
    @Test fun fiftyAndHundredPagesHaveBoundedRepeatedResourceObservations() {
        PdfboxSpikeSandbox("stress0405").use { sandbox ->
            // Exactly two operations per fixture. No GC coercion, sleep, retry or memory-budget claim.
            repeat(2) { for (id in listOf("04", "05")) scenario(sandbox, id) }
        }
    }
    @Test fun limitedOutputAndTruncatedCopyCloseWithoutSuccessfulResult() {
        PdfboxSpikeSandbox("faults02").use { sandbox ->
            val (source, _) = sandbox.fixture("02")
            val partial = sandbox.fresh("limited")
            sandbox.document(source) { document ->
                assertThrows(SpikeOutputLimit::class.java) { sandbox.save(document, partial, 64) }
            }
            assertTrue(partial.length() <= 64)
            val truncated = sandbox.fresh("truncated")
            source.inputStream().use { input -> truncated.outputStream().use { output ->
                val prefix = ByteArray(64); java.io.DataInputStream(input).readFully(prefix); output.write(prefix) } }
            assertThrows(IOException::class.java) { sandbox.document(truncated) { fail("Truncated input accepted") } }
            sandbox.assertNoOwnedHandles()
        }
    }

    private fun negative(id: String, mutation: (PDDocument) -> Unit) {
        PdfboxSpikeSandbox("negative$id").use { sandbox ->
            val (source, _) = sandbox.fixture(id)
            val output = sandbox.fresh("mutated")
            var expected = emptyList<SpikeSemantics.Page>()
            sandbox.document(source) { document ->
                expected = SpikeSemantics.snapshot(document)
                mutation(document); sandbox.save(document, output)
            }
            sandbox.document(output, "reopenNegative") { document ->
                // A corrupt/unparseable mutation is NOT substituted for a successful oracle check.
                val actual = SpikeSemantics.snapshot(document)
                assertThrows(AssertionError::class.java) { assertEquals(expected, actual) }
            }
        }
    }
    @Test fun semanticChecksDetectMissingPageAndWrongRotation() {
        negative("02") { it.removePage(1) }
        negative("09") { it.getPage(0).rotation = 90 }
    }
    @Test fun semanticChecksDetectMissingAnnotationAndAppearance() {
        negative("12") { document ->
            val page = document.getPage(0); page.annotations = page.annotations.drop(1)
        }
        negative("12") { it.getPage(0).annotations[0].cosObject.removeItem(COSName.AP) }
    }
    @Test fun semanticChecksDetectDroppedFontsImagesAndTransparency() {
        // Resource graph fingerprint is computed before text decoding: missing fonts need not parse well.
        for ((id, name) in listOf("11" to COSName.FONT, "10" to COSName.XOBJECT, "13" to COSName.EXT_G_STATE)) {
            PdfboxSpikeSandbox("negativeResource$id").use { sandbox ->
                val (source, _) = sandbox.fixture(id); val output = sandbox.fresh("mutated")
                var original = ""
                sandbox.document(source) { document ->
                    val resources = document.getPage(0).resources.cosObject
                    assertNotNull("Fixture must exercise the removed category", resources.getDictionaryObject(name))
                    original = SpikeSemantics.fingerprint(resources)
                    resources.removeItem(name); sandbox.save(document, output)
                }
                sandbox.document(output, "reopenNegative") { document ->
                    val changed = SpikeSemantics.fingerprint(document.getPage(0).resources.cosObject)
                    assertThrows(AssertionError::class.java) { assertEquals(original, changed) }
                }
            }
        }
    }
    @Test fun syntheticJpxFilterIsRejectedBeforeResourceDecoding() {
        // Synthetic dictionary only, NOT a real JPX fidelity/detection acceptance fixture.
        PdfboxSpikeSandbox("syntheticJpx").use {
            val stream = com.tom_roush.pdfbox.cos.COSStream()
            try {
                stream.setItem(COSName.FILTER, COSName.JPX_DECODE)
                assertThrows(IllegalArgumentException::class.java) { SpikeSemantics.fingerprint(stream) }
            } finally { stream.close() }
        }
    }
}
