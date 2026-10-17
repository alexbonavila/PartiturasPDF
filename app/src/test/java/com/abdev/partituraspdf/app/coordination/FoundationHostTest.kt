package com.abdev.partituraspdf.app.coordination

import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.foundation.*
import com.abdev.partituraspdf.core.result.*
import com.abdev.partituraspdf.storage.api.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FoundationHostTest {
    @Test fun hostCallbacksAndRootRestoreUseTheSameInjectedNavigationOwner() { runBlocking {
        val root=FolderRef("saf","opaque-root")
        var selected: FolderRef?=null
        val roots=object : SelectedRootRepository {
            override suspend fun select(root: FolderRef): Outcome<Unit> { selected=root;return Outcome.Success(Unit) }
            override suspend fun restore(): Outcome<FolderRef?> = Outcome.Success(selected)
            override suspend fun clear(): Outcome<Unit> { selected=null;return Outcome.Success(Unit) }
        }
        var state: FolderNavigationState?=null
        val folders=object : FolderNavigation {
            override suspend fun state()=state
            override suspend fun select(root: FolderRef): Outcome<FolderNavigationState> = Outcome.Success(FolderNavigationState(root,listOf(root),1).also { state=it })
            override suspend fun enter(entry: DocumentEntry,expectedVersion: Long): Outcome<FolderNavigationState> = Outcome.Failure(AppError.Validation.INVALID_DOCUMENT)
            override suspend fun back(expectedVersion: Long): Outcome<FolderNavigationState> = Outcome.Success(requireNotNull(state))
        }
        val registry=RegistryFixture();var picks=0;var opened: DocumentOpenRequest?=null
        val host: FeatureHost=FoundationHost(roots,folders,RegistrationCoordinator(registry),{ picks++;Outcome.Success(Unit) },{ opened=it;Outcome.Success(Unit) })
        assertEquals(Outcome.Success(null),host.selectedRoot())
        host.library(LibraryEvent.ChooseRoot);assertEquals(1,picks)
        host.acceptPersistedRoot(root);assertEquals(root,folders.state()!!.current)
        assertEquals(Outcome.Success(root),host.selectedRoot())
        val request=DocumentOpenRequest.Transient(registry.storage)
        host.library(LibraryEvent.Open(request));assertSame(request,opened)
        host.library(LibraryEvent.Register(registry.storage));assertNotNull(registry.row)
        assertEquals(Outcome.Success(Unit),host.library(LibraryEvent.Back(1)))
        val entry=DocumentEntry(registry.storage,DocumentInfo(null,null),root)
        assertEquals(Outcome.Failure(AppError.Validation.INVALID_DOCUMENT),host.library(LibraryEvent.Enter(entry,1)))
    } }
    @Test fun invalidTrailCannotBeCreatedAndInputListCannotMutateState() {
        val root=FolderRef("saf","root");val items=mutableListOf(root)
        val state=FolderNavigationState(root,items,0);items.clear();assertEquals(listOf(root),state.breadcrumbs)
        assertThrows(UnsupportedOperationException::class.java) { (state.breadcrumbs as MutableList).clear() }
        assertThrows(IllegalArgumentException::class.java) { FolderNavigationState(root,emptyList(),0) }
        assertThrows(IllegalArgumentException::class.java) { FolderNavigationState(root,listOf(FolderRef("saf","other")),0) }
        assertThrows(IllegalArgumentException::class.java) { FolderNavigationState(root,listOf(root),-1) }
    }
}
