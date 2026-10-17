package com.abdev.partituraspdf.storage.saf

import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.foundation.*
import com.abdev.partituraspdf.core.result.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real framework URI interpretation; synthetic catalog/grant doubles do not establish real SAF grants. */
@RunWith(AndroidJUnit4::class)
class FoundationFolderNavigationTest {
    private val root=FolderRef("saf","content://synthetic/tree/root")
    private fun entry(parent: FolderRef,id: String)=DocumentEntry(StorageDocumentRef("saf","content://synthetic/tree/root/document/$id"),DocumentInfo(id,DocumentsContract.Document.MIME_TYPE_DIR),parent)
    @Test fun confirmedChildGrandchildAndBackStayInsideRoot() { runBlocking {
        var listing=emptyList<DocumentEntry>()
        val catalog=object : DocumentCatalog {
            override suspend fun list(folder: FolderRef)=Outcome.Success(DocumentListing(listing,true))
            override suspend fun inspect(document: StorageDocumentRef): Outcome<DocumentInfo> = error("must use confirmed listing")
        }
        val nav=SafFolderNavigation(catalog,PersistedRootAccess { Outcome.Success(true) })
        val initial=(nav.select(root) as Outcome.Success).value
        val child=entry(root,"child");listing=listOf(child)
        val next=(nav.enter(child,initial.version) as Outcome.Success).value
        listing=listOf(entry(next.current,"grandchild"))
        val grand=(nav.enter(listing.single(),next.version) as Outcome.Success).value
        assertEquals(3,grand.breadcrumbs.size)
        val back=(nav.back(grand.version) as Outcome.Success).value
        assertEquals(next.current,back.current)
        val home=(nav.back(back.version) as Outcome.Success).value
        assertEquals(root,home.current);assertEquals(home,(nav.back(home.version) as Outcome.Success).value)
    } }
    @Test fun maliciousMismatchedAndUnconfirmedDirectoriesAreRejected() { runBlocking {
        var items=emptyList<DocumentEntry>()
        val catalog=object : DocumentCatalog {
            override suspend fun list(folder: FolderRef)=Outcome.Success(DocumentListing(items,true))
            override suspend fun inspect(document: StorageDocumentRef): Outcome<DocumentInfo> = error("unused")
        }
        val nav=SafFolderNavigation(catalog,PersistedRootAccess { Outcome.Success(true) })
        val initial=(nav.select(root) as Outcome.Success).value
        val child=entry(root,"child")
        for (bad in listOf(child.copy(folder=FolderRef("saf","wrong")),child.copy(info=DocumentInfo("pdf","application/pdf")),
            child.copy(storage=StorageDocumentRef("saf","content://other/tree/root/document/x")),
            child.copy(storage=StorageDocumentRef("saf","content://synthetic/tree/foreign/document/x")),
            entry(root,"root"),child.copy(storage=StorageDocumentRef("saf","malformed")))) {
            items=listOf(bad);assertEquals(Outcome.Failure(AppError.Validation.INVALID_DOCUMENT),nav.enter(bad,initial.version))
        }
        items=emptyList();assertTrue(nav.enter(child,initial.version) is Outcome.Failure)
        assertEquals(root,nav.state()!!.current)
    } }
    @Test fun providerErrorsRevocationAndStaleResponsesDoNotChangeBreadcrumbs() { runBlocking {
        var allowed=true;var failure: AppError?=AppError.Access.PROVIDER_UNAVAILABLE
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();val child=entry(root,"child")
        val catalog=object : DocumentCatalog {
            override suspend fun list(folder: FolderRef): Outcome<DocumentListing> {
                failure?.let { return Outcome.Failure(it) }
                entered.complete(Unit);release.await();return Outcome.Success(DocumentListing(listOf(child),true))
            }
            override suspend fun inspect(document: StorageDocumentRef): Outcome<DocumentInfo> = error("unused")
        }
        val nav=SafFolderNavigation(catalog,PersistedRootAccess { Outcome.Success(allowed) })
        var initial=(nav.select(root) as Outcome.Success).value
        for (error in listOf(AppError.Access.PROVIDER_UNAVAILABLE, AppError.Access.PROVIDER_IO_FAILURE, AppError.Access.PERMISSION_DENIED)) {
            failure=error; assertEquals(Outcome.Failure(error),nav.enter(child,initial.version))
        }
        allowed=false;assertEquals(Outcome.Failure(AppError.Access.PERMISSION_REVOKED),nav.back(initial.version));allowed=true
        failure=null
        val pending=async { nav.enter(child,initial.version) };entered.await()
        initial=(nav.select(root) as Outcome.Success).value;release.complete(Unit)
        assertEquals(Outcome.Failure(AppError.Conflict.STALE_SESSION),pending.await());assertEquals(initial,nav.state())
    } }
    @Test fun realOsGrantQueryNeverInventsAuthorizationForSyntheticRoot() {
        val access=SafPersistedRootAccess(InstrumentationRegistry.getInstrumentation().targetContext.contentResolver)
        assertEquals(Outcome.Success(false),access.hasRead(root))
        assertEquals(Outcome.Failure(AppError.Validation.INVALID_DOCUMENT),access.hasRead(root.copy(value="file:///private")))
    }
    @Test fun cancelledCatalogDoesNotTurnIntoDeletionOrChangeFolder() { runBlocking {
        val cancelled=CancellationException("provider cancelled")
        val catalog=object : DocumentCatalog {
            override suspend fun list(folder: FolderRef): Outcome<DocumentListing> = throw cancelled
            override suspend fun inspect(document: StorageDocumentRef): Outcome<DocumentInfo> = error("unused")
        }
        val nav=SafFolderNavigation(catalog,PersistedRootAccess { Outcome.Success(true) })
        val before=(nav.select(root) as Outcome.Success).value
        try { nav.enter(entry(root,"child"),before.version); fail() }
        catch (failure: CancellationException) { assertSame(cancelled,failure) }
        assertEquals(before,nav.state())
    } }

}
