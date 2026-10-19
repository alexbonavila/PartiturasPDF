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
    fun reader(expected: Long): AppNavigation =
        if (expected == revision && destination == AppDestination.LIBRARY)
            AppNavigation(AppDestination.READER, revision + 1) else this

    /** Only the completed retirement barrier may use this transition. Ordinary Back cannot. */
    fun readerFinished(expected: Long): AppNavigation =
        if (expected == revision && destination == AppDestination.READER)
            AppNavigation(AppDestination.LIBRARY, revision + 1) else this
}
