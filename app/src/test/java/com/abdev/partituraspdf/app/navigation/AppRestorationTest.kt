package com.abdev.partituraspdf.app.navigation

import com.abdev.partituraspdf.core.document.*
import org.junit.Assert.*
import org.junit.Test

class AppRestorationTest {
    @Test fun stableVersionedIdentifiersRoundTripWithoutOrdinals() {
        for (state in listOf(AppRestoration.Library, AppRestoration.Settings, AppRestoration.Reader(DocumentRef(DocumentId("opaque:Case sensitive")))))
            assertEquals(state, AppRestoration.decode(state.encode()))
    }
    @Test fun missingWrongVersionDestinationExtraFieldsAndTransientFallback() {
        for (raw in listOf(null, "", "2:reader:id", "1:transient:content://old", "1:settings:extra", "1:library:extra", "1:READER:id"))
            assertEquals(AppRestoration.Library, AppRestoration.decode(raw))
    }
    @Test fun blankControlAndOversizeIdsCannotEnterReader() {
        for (id in listOf("", " ", "\n", "abc\u0000def", "x".repeat(257))) {
            assertEquals(AppRestoration.Library, AppRestoration.decode("1:reader:$id"))
            if (id.isNotBlank()) assertEquals("1:library", AppRestoration.Reader(DocumentRef(DocumentId(id))).encode())
        }
    }
    @Test fun largestAcceptedOpaqueIdentityIsPreservedExactly() {
        val state = AppRestoration.Reader(DocumentRef(DocumentId("x".repeat(256))))
        assertEquals(state, AppRestoration.decode(state.encode()))
    }
}
