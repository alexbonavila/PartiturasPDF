package com.abdev.partituraspdf.library

import com.abdev.partituraspdf.core.foundation.LibraryEvent
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibrarySelectionUiRegressionTest {
    @Test fun failedReplacementSelectionRestoresExistingContentWithoutClaimingAuthorization() = runTest {
        for (launchFailure in listOf(false, true)) LibraryFixture(this).use { f ->
            f.controller.start(); runCurrent()
            val original = f.ready.rows.map { it.entry }
            f.event = { command ->
                if (launchFailure && command == LibraryEvent.ChooseRoot) Outcome.Failure(AppError.Access.PERMISSION_DENIED) else Outcome.Success(Unit)
            }
            f.controller.dispatch(LibraryAction.ChooseRoot); runCurrent()
            if (!launchFailure) { f.controller.onRootSelectionFinished(Outcome.Failure(AppError.Access.PERMISSION_DENIED)); runCurrent() }
            assertEquals(original, f.ready.rows.map { it.entry })
            assertEquals(root, f.available.snapshot.root)
            assertEquals(AppError.Access.PERMISSION_DENIED, (f.state.rootSelection as LibraryOperation.Failed).failure.error)
            assertEquals(1, f.restores) // No root reset or invented picker acceptance.
        }
    }
    @Test fun initialPickerFailureDoesNotInventASelectedFolderToReload() = runTest {
        LibraryFixture(this).use { f ->
            f.restore = { Outcome.Success(null) }
            f.event = { Outcome.Failure(AppError.Access.PERMISSION_DENIED) }
            f.controller.start(); runCurrent()
            f.controller.dispatch(LibraryAction.ChooseRoot); runCurrent()
            assertEquals(LibraryLocation.NoRoot, f.state.location)
            assertTrue(f.calls.isEmpty())
            assertTrue(f.state.rootSelection is LibraryOperation.Failed)
        }
    }
}
