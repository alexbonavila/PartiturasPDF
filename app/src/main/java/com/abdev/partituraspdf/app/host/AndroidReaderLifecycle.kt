package com.abdev.partituraspdf.app.host

import android.content.ComponentCallbacks2
import com.abdev.partituraspdf.core.foundation.ReaderTrim

/** Named API26 levels; unknown/moderate-running hints are not invented severity thresholds. */
internal fun readerTrimForAndroid(level: Int, configurationChange: Boolean = false): ReaderTrim? = when (level) {
    ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> ReaderTrim.LOW_MEMORY
    ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
    ComponentCallbacks2.TRIM_MEMORY_MODERATE,
    ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> ReaderTrim.CRITICAL
    ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> if (configurationChange) null else ReaderTrim.BACKGROUND
    ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> ReaderTrim.BACKGROUND
    else -> null
}
