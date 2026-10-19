package com.abdev.partituraspdf.app.localization

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import com.abdev.partituraspdf.core.foundation.DeliveredLanguages
import com.abdev.partituraspdf.core.foundation.DeviceLocalePolicy
import java.util.Locale

/** Read the system configuration, never an already localized Activity or legacy preference. */
internal fun systemAppLanguage(): String {
    val locales = Resources.getSystem().configuration.locales
    return appLanguage((0 until locales.size()).map { locales[it].toLanguageTag() })
}
internal fun appLanguage(systemLanguages: List<String>): String =
    DeviceLocalePolicy.resolve(systemLanguages, DeliveredLanguages(english = true, catalan = true))

/** API26-safe, Activity-owned override. No global Locale/resources/device state is mutated. */
internal fun localizedActivityContext(base: Context, language: String = systemAppLanguage()): Context {
    val locale = Locale.forLanguageTag(language)
    val configuration = Configuration(base.resources.configuration).apply {
        setLocales(LocaleList(locale))
        setLayoutDirection(locale)
    }
    return base.createConfigurationContext(configuration)
}
