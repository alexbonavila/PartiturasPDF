package com.abdev.partituraspdf.app.navigation

import org.junit.Assert.*
import org.junit.Test

class AppNavigationTest {
    @Test fun libraryIsRootAndRootBackDoesNotCreateATransition() {
        val root = AppNavigation()
        assertEquals(AppDestination.LIBRARY, root.destination)
        assertSame(root, root.back(0))
    }
    @Test fun settingsBackAndRepeatedOldCallbacksDoNotDuplicateRoutes() {
        val root = AppNavigation()
        val settings = root.settings(0)
        assertEquals(AppDestination.SETTINGS, settings.destination)
        assertSame(settings, settings.settings(1))
        assertSame(settings, settings.settings(0))
        assertSame(settings, settings.back(0))
        val returned = settings.back(1)
        assertEquals(AppDestination.LIBRARY, returned.destination)
        assertEquals(2L, returned.revision)
        assertSame(returned, returned.back(1))
        assertSame(returned, returned.settings(0))
        assertEquals(AppDestination.SETTINGS, returned.settings(2).destination)
    }
    @Test fun noSupportedTransitionCanEnterReservedReader() {
        var state = AppNavigation()
        repeat(20) {
            state = state.settings(state.revision)
            assertNotEquals(AppDestination.READER, state.destination)
            state = state.back(state.revision)
            assertNotEquals(AppDestination.READER, state.destination)
        }
        val reserved = AppNavigation(AppDestination.READER, 8)
        assertSame(reserved, reserved.settings(8))
        assertSame(reserved, reserved.back(8))
    }
}
