package com.abdev.partituraspdf.app.navigation

import org.junit.Assert.*
import org.junit.Test

class ReaderNavigationTest {
    @Test fun onlyCurrentLibraryRevisionCanEnterReaderAndOrdinaryBackCannotBypassRetirement() {
        val library = AppNavigation()
        assertSame(library, library.reader(-1))
        val reader = library.reader(0)
        assertEquals(AppDestination.READER, reader.destination); assertEquals(1L, reader.revision)
        assertSame(reader, reader.back(1)); assertSame(reader, reader.reader(1))
        assertSame(reader, reader.readerFinished(0))
        val returned = reader.readerFinished(1)
        assertEquals(AppDestination.LIBRARY, returned.destination); assertEquals(2L, returned.revision)
        assertSame(returned, returned.readerFinished(2))
    }
    @Test fun settingsCannotOpenOrCompleteAReaderDestination() {
        val settings = AppNavigation().settings(0)
        assertSame(settings, settings.reader(1)); assertSame(settings, settings.readerFinished(1))
    }
    @Test fun obsoleteReaderCompletionCannotLeaveANewerReaderLifetime() {
        val first = AppNavigation().reader(0)
        val second = first.readerFinished(1).reader(2)
        assertSame(second, second.readerFinished(1))
        assertEquals(AppDestination.READER, second.destination)
        assertEquals(3L, second.revision)
    }
}
