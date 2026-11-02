package com.abdev.partituraspdf.app.integration

/** Snapshot of a removal-mode node. Kept Android-free for synthetic hierarchy regressions. */
internal data class LocaleRemovalCheckbox<T>(
    val target: T,
    val resourceName: String?,
    val className: String?,
    val text: String?,
    val enabled: Boolean,
    val visible: Boolean,
    val checked: Boolean,
)

internal fun <T> selectLocaleRemovalCheckbox(
    nodes: List<LocaleRemovalCheckbox<T>>,
    expectedDisplayName: String,
): LocaleRemovalCheckbox<T>? {
    val matches = nodes.filter {
        it.resourceName == "com.android.settings:id/checkbox" &&
            it.className == "android.widget.CheckBox" && it.text == expectedDisplayName
    }
    // Count all exact matches before checking readiness: a disabled duplicate is ambiguous too.
    return matches.singleOrNull()?.takeIf { it.enabled && it.visible }
}
