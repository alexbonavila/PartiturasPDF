package com.abdev.partituraspdf.data.metadata

import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class MetadataDatabaseOperationTest {
    @Test fun operationReturnsTypedResultsWithoutChangingTheirMeaning() {
        runTest {
            val success = Outcome.Success("persisted result")
            val failure = Outcome.Failure(AppError.Access.NOT_REGISTERED)
            assertSame(success, metadataDatabaseOperation { success })
            assertSame(failure, metadataDatabaseOperation { failure })
        }
    }

    @Test fun programmingErrorsAndCancellationAreNeverTranslatedToStorageFailure() {
        runTest {
            for (failure in listOf(IllegalStateException("defect"), IllegalArgumentException("invalid input"),
                CancellationException("cancelled operation"))) {
                try {
                    metadataDatabaseOperation<Unit> { throw failure }
                    fail("Exception must propagate")
                } catch (actual: RuntimeException) { assertSame(failure, actual) }
                try {
                    metadataDatabaseFailure(failure)
                    fail("Non-SQL failure must propagate")
                } catch (actual: RuntimeException) { assertSame(failure, actual) }
            }
        }
    }

    @Test fun cancelledCoroutineCannotEnterOperationOrTurnFailureIntoAnOutcome() {
        runTest {
            var entered = false
            var operationCancelled = false
            var failureCancelled = false
            launch {
                coroutineContext.cancel()
                try {
                    metadataDatabaseOperation { entered = true; Outcome.Success(Unit) }
                } catch (_: CancellationException) { operationCancelled = true }
                try {
                    metadataDatabaseFailure(IllegalStateException("defect"))
                } catch (_: CancellationException) { failureCancelled = true }
            }.join()
            assertFalse(entered)
            assertTrue(operationCancelled)
            assertTrue(failureCancelled)
        }
    }
}
