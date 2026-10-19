package com.abdev.partituraspdf.app.navigation

internal enum class AppDestination { LIBRARY, SETTINGS, READER }

/** Revision belongs to a rendered route, so late callbacks cannot navigate a newer route. */
internal data class AppNavigation(
    val destination: AppDestination = AppDestination.LIBRARY,
    val revision: Long = 0,
) {
    fun settings(expected: Long): AppNavigation =
        if (expected == revision && destination == AppDestination.LIBRARY)
            AppNavigation(AppDestination.SETTINGS, revision + 1) else this

    fun back(expected: Long): AppNavigation =
        if (expected == revision && destination == AppDestination.SETTINGS)
            AppNavigation(AppDestination.LIBRARY, revision + 1) else this
    // READER is reserved; no transition can enter it until I05 is implemented.
}
