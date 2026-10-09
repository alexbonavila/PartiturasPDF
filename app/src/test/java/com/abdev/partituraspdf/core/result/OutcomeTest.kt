package com.abdev.partituraspdf.core.result

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class OutcomeTest {
    @Test fun successAndFailureRemainTypedAndCovariant() {
        val success: Outcome<Number> = Outcome.Success(7)
        assertEquals(7, (success as Outcome.Success).value)
        val failure: Outcome<Number> = Outcome.Failure(AppError.Access.PERMISSION_REVOKED)
        assertEquals(AppError.Access.PERMISSION_REVOKED, (failure as Outcome.Failure).error)
        assertNotEquals(success, failure)
        assertEquals(AppError.Validation.INVALID_PAGE, Outcome.Failure(AppError.Validation.INVALID_PAGE).error)
        assertEquals(AppError.Unsupported.PASSWORD_PROTECTED, Outcome.Failure(AppError.Unsupported.PASSWORD_PROTECTED).error)
        assertEquals(AppError.Resource.INPUT_LIMIT, Outcome.Failure(AppError.Resource.INPUT_LIMIT).error)
        assertEquals(AppError.Conflict.REVISION_CHANGED, Outcome.Failure(AppError.Conflict.REVISION_CHANGED).error)
    }

    @Test fun expectedFailuresDoNotAbsorbCancellationOrProgrammingDefects() {
        suspend fun representativeQuery(failure: Throwable): Outcome<Unit> {
            return try {
                throw failure
            } catch (_: SecurityException) {
                Outcome.Failure(AppError.Access.PERMISSION_REVOKED)
            }
        }
        assertEquals(Outcome.Failure(AppError.Access.PERMISSION_REVOKED), runBlocking {
            representativeQuery(SecurityException())
        })
        val cancellation = CancellationException("test cancellation")
        assertSame(cancellation, assertThrows(CancellationException::class.java) {
            runBlocking { representativeQuery(cancellation) }
        })
        val defect = IllegalStateException("test defect")
        assertSame(defect, assertThrows(IllegalStateException::class.java) {
            runBlocking { representativeQuery(defect) }
        })
    }

    @Test fun observationsDistinguishInitialStateDataAndErrors() {
        assertNotEquals(Observation.Loading, Observation.Ready("value"))
        assertNotEquals(Observation.Loading, Observation.Failed(AppError.Access.PROVIDER_UNAVAILABLE))
        assertEquals("value", Observation.Ready("value").value)
        assertEquals(AppError.Access.PROVIDER_UNAVAILABLE, Observation.Failed(AppError.Access.PROVIDER_UNAVAILABLE).error)
    }
}
