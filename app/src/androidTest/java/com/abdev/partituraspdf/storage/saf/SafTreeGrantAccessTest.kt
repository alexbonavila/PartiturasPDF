package com.abdev.partituraspdf.storage.saf

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.abdev.partituraspdf.core.document.FolderRef
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated picker-grant behavior; actual human picker selection is a separate acceptance check. */
@RunWith(AndroidJUnit4::class)
class SafTreeGrantAccessTest {
    private val root = Uri.parse("content://test.documents/tree/primary%3AMusic")
    private val nested = DocumentsContract.buildDocumentUriUsingTree(root, "primary:Music/Concert")
    private fun folder(uri: Uri) = FolderRef("saf", uri.toString())

    private class FakeGrants {
        val granted = linkedSetOf<Uri>()
        val takes = mutableListOf<Pair<Uri, Int>>()
        val releases = mutableListOf<Pair<Uri, Int>>()
        var takeError: RuntimeException? = null
        var releaseError: RuntimeException? = null
        var listError: RuntimeException? = null
        var autoPersist = true
        fun access() = SafTreeGrantAccess(
            { uri, flags ->
                takes += uri to flags
                takeError?.let { throw it }
                if (autoPersist) granted += uri
            },
            { uri, flags ->
                releases += uri to flags
                releaseError?.let { throw it }
                granted -= uri
            },
            {
                listError?.let { throw it }
                granted.toList()
            },
        )
    }

    @Test fun createsAndroidTreePickerWithoutRequestingWrite(): Unit {
        val intent = FakeGrants().access().treePickerIntent()
        assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, intent.action)
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    @Test fun validPickerResultPersistsReadAndReturnsOpaqueFolder(): Unit {
        val fake = FakeGrants()
        val result = fake.access().persistPickerReadGrant(root,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertEquals(Outcome.Success(folder(root)), result)
        assertEquals(listOf(root to Intent.FLAG_GRANT_READ_URI_PERMISSION), fake.takes)
        assertEquals(setOf(root), fake.granted)
    }

    @Test fun missingReadFlagNeverCallsPlatform(): Unit {
        val fake = FakeGrants()
        assertEquals(Outcome.Failure(AppError.Access.PERMISSION_DENIED),
            fake.access().persistPickerReadGrant(root, Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
        assertTrue(fake.takes.isEmpty())
    }

    @Test fun invalidOrNullPickerUriNeverCallsPlatform(): Unit {
        val fake = FakeGrants()
        val access = fake.access()
        for (uri in listOf<Uri?>(null,
            Uri.parse("file:///tmp/Music"),
            Uri.parse("content://test.documents/document/score"),
            nested,
            Uri.parse("content:///tree/primary%3AMusic"))) {
            assertEquals(Outcome.Failure(AppError.Validation.INVALID_DOCUMENT),
                access.persistPickerReadGrant(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }
        assertTrue(fake.takes.isEmpty())
    }

    @Test fun androidRejectingGrantIsNotMistakenForDeletedDocument(): Unit {
        for (error in listOf(SecurityException("missing grant"), IllegalArgumentException("expired"))) {
            val fake = FakeGrants().apply { takeError = error }
            assertEquals(Outcome.Failure(AppError.Access.PERMISSION_DENIED),
                fake.access().persistPickerReadGrant(root, Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }
    }

    @Test fun takeWithoutOSPersistedConfirmationIsNotSuccess(): Unit {
        val fake = FakeGrants().apply { autoPersist = false }
        assertEquals(Outcome.Failure(AppError.Access.PERMISSION_DENIED),
            fake.access().persistPickerReadGrant(root, Intent.FLAG_GRANT_READ_URI_PERMISSION))
        assertFalse(fake.access().hasPersistedReadGrant(folder(root)).let { (it as Outcome.Success).value })
    }

    @Test fun grantCheckUsesSelectedTreeForNestedFolder(): Unit {
        val fake = FakeGrants().apply { granted += root }
        assertEquals(Outcome.Success(true), fake.access().hasPersistedReadGrant(folder(nested)))
        assertEquals(Outcome.Success(true), fake.access().hasPersistedReadGrant(folder(root)))
    }

    @Test fun missingGrantReturnsFalseNotPermissionRevokedOrDeletion(): Unit {
        val fake = FakeGrants()
        assertEquals(Outcome.Success(false), fake.access().hasPersistedReadGrant(folder(root)))
    }

    @Test fun previouslyObservedGrantDisappearsAfterSimulatedRevocation(): Unit {
        val fake = FakeGrants().apply { granted += root }
        val access = fake.access()
        assertEquals(Outcome.Success(true), access.hasPersistedReadGrant(folder(root)))
        fake.granted.remove(root) // Models Android removing a persisted grant externally.
        assertEquals(Outcome.Success(false), access.hasPersistedReadGrant(folder(root)))
    }

    @Test fun invalidFolderNeverInspectsGrants(): Unit {
        val fake = FakeGrants().apply { listError = SecurityException("should not run") }
        assertEquals(Outcome.Failure(AppError.Validation.INVALID_DOCUMENT),
            fake.access().hasPersistedReadGrant(FolderRef("other", root.toString())))
    }

    @Test fun platformCannotListGrantsReportsPermissionError(): Unit {
        val fake = FakeGrants().apply { listError = SecurityException("os denial") }
        assertEquals(Outcome.Failure(AppError.Access.PERMISSION_DENIED),
            fake.access().hasPersistedReadGrant(folder(root)))
        assertEquals(Outcome.Failure(AppError.Access.PERMISSION_DENIED),
            fake.access().persistPickerReadGrant(root, Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    @Test fun releaseRemovesOnlyReadGrantAndNestedUsesRoot(): Unit {
        val fake = FakeGrants().apply { granted += root }
        assertEquals(Outcome.Success(Unit), fake.access().releasePersistedReadGrant(folder(nested)))
        assertEquals(listOf(root to Intent.FLAG_GRANT_READ_URI_PERMISSION), fake.releases)
        assertEquals(Outcome.Success(false), fake.access().hasPersistedReadGrant(folder(root)))
    }

    @Test fun releaseDenialIsClassifiedWithoutExposingProviderData(): Unit {
        val fake = FakeGrants().apply { releaseError = SecurityException("provider path") }
        assertEquals(Outcome.Failure(AppError.Access.PERMISSION_DENIED),
            fake.access().releasePersistedReadGrant(folder(root)))
        assertEquals(Outcome.Failure(AppError.Validation.INVALID_DOCUMENT),
            fake.access().releasePersistedReadGrant(FolderRef("wrong", root.toString())))
    }

    @Test fun realResolverDoesNotMistakeUnknownTreeForPersistedGrant(): Unit {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val unknown = FolderRef("saf", "content://com.abdev.partituraspdf.unregistered/tree/no-grant")
        assertEquals(Outcome.Success(false), SafTreeGrantAccess(resolver).hasPersistedReadGrant(unknown))
    }
}
