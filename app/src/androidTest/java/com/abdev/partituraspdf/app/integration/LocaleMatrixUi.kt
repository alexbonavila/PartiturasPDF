package com.abdev.partituraspdf.app.integration

import android.app.Instrumentation
import android.content.Intent
import android.content.res.Resources
import android.graphics.Point
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.regex.Pattern

/** Explicit instrumentation arguments only. No settings operations in ordinary test runs. */
internal object LocaleMatrixUi {
    private val instrumentation: Instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val arguments: Bundle get() = InstrumentationRegistry.getArguments()
    val enabled: Boolean get() = arguments.getString("jLocaleRun") != null
    private val device get() = UiDevice.getInstance(instrumentation)
    private val directory: File get() {
        val token = arguments.getString("jLocaleRun") ?: error("No locale run")
        require(token.matches(Regex("[a-f0-9]{32}")))
        return File(instrumentation.targetContext.filesDir, "j-locale-$token").apply { mkdirs() }
    }
    fun locales(): List<String> = Resources.getSystem().configuration.locales.let { list ->
        (0 until list.size()).map { list[it].toLanguageTag() }
    }
    fun expectedLanguage(): String {
        val tags = if (arguments.getString("jLocaleAction") == "probe") locales()
            else arguments.getString("jLocaleTags")?.split(',') ?: locales()
        return when (Locale.forLanguageTag(tags.first()).language) { "es" -> "es"; "ca" -> "ca"; "en" -> "en"; else -> "es" }
    }
    fun expectedString(context: android.content.Context, id: Int, vararg values: Any): String {
        val config = android.content.res.Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(expectedLanguage()))
        }
        return context.createConfigurationContext(config).getString(id, *values)
    }
    fun capture(name: String, extra: JSONObject = JSONObject()) {
        if (!enabled) return
        require(name.matches(Regex("[a-z0-9-]+")))
        if (!device.takeScreenshot(File(directory, "$name.png"))) throw AssertionError("J_LOCALE_BLOCKED: screenshot failed")
        device.dumpWindowHierarchy(File(directory, "$name.xml"))
        extra.put("systemLocales", JSONArray(locales())).put("pid", Process.myPid())
        File(directory, "$name.json").writeText(extra.toString(2))
    }
    fun blocked(reason: String): Nothing {
        runCatching { capture("blocked", JSONObject().put("reason", reason)) }
        throw AssertionError("J_LOCALE_BLOCKED: $reason")
    }
    private fun foregroundSettings() {
        if (device.currentPackageName != "com.android.settings") blocked("Settings lost foreground")
    }
    private fun awaitState(reason: String, condition: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < until) {
            if (condition()) return
            SystemClock.sleep(100) // Bounded observation polling, not a functional retry.
        }
        blocked(reason)
    }
    private fun named(text: String): UiObject2? {
        foregroundSettings()
        val selector = By.text(Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE))
        device.wait(Until.findObject(selector), 3000)?.let { return it }
        val list = device.findObject(By.scrollable(true)) ?: return null
        repeat(8) {
            if (!list.scroll(androidx.test.uiautomator.Direction.DOWN, 0.7f)) return device.findObject(selector)
            device.findObject(selector)?.let { return it }
        }
        return null
    }
    private fun openSettings() {
        instrumentation.targetContext.startActivity(Intent(Settings.ACTION_LOCALE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        if (!device.wait(Until.hasObject(By.pkg("com.android.settings").depth(0)), 10_000)) blocked("No language Settings")
        foregroundSettings()
    }
    private fun row(tag: String): UiObject2? {
        val locale = Locale.forLanguageTag(tag)
        // AOSP LocaleListEditor native-language label; never coordinates or translated menu prose.
        return device.findObjects(By.res("com.android.settings", "label"))
            .firstOrNull { it.text?.equals(locale.getDisplayName(locale), ignoreCase = true) == true }
            ?.parent
    }
    private fun promote(tag: String) {
        openSettings()
        if (row(tag) == null) {
            val add = device.findObject(By.res("com.android.settings", "add_language"))
                ?: blocked("Unsupported language-list UI: missing add_language")
            add.click()
            val locale = Locale.forLanguageTag(tag)
            val language = named(locale.getDisplayLanguage(locale)) ?: blocked("Missing native language $tag")
            language.click()
            // A region page is optional, but selection must ultimately be independently observed.
            val country = locale.getDisplayCountry(locale)
            if (device.currentPackageName == "com.android.settings" && row(tag) == null && country.isNotEmpty()) {
                named(country)?.click()
            }
            awaitState("Added language not present: $tag") { row(tag) != null }
        }
        val desired = row(tag) ?: blocked("Missing locale row $tag")
        val handles = device.findObjects(By.res("com.android.settings", "dragHandle"))
        val handle = desired.findObject(By.res("com.android.settings", "dragHandle"))
            ?: blocked("Unsupported locale row/dragHandle")
        if (handles.isEmpty()) blocked("No language ordering handles")
        val target = handles.first().visibleCenter
        if (handle.visibleCenter != target) handle.drag(Point(target.x, target.y))
        awaitState("Primary locale not applied: $tag") { locales().firstOrNull() == tag }
    }
    private fun settingsAction(resource: String): UiObject2? {
        foregroundSettings()
        device.findObject(By.res("com.android.settings", resource))?.let { return it }
        val settings = instrumentation.targetContext.createPackageContext("com.android.settings", 0)
        val config = android.content.res.Configuration(settings.resources.configuration).apply {
            setLocales(Resources.getSystem().configuration.locales)
        }
        val resources = settings.createConfigurationContext(config).resources
        val id = resources.getIdentifier(resource, "string", "com.android.settings")
        if (id == 0) return null
        val label = resources.getString(id)
        return device.findObject(By.text(label)) ?: device.findObject(By.desc(label))
    }
    private fun removeExtra(tag: String) {
        openSettings()
        val overflow = settingsAction("locale_remove_menu")
            ?: run {
                // ActionBar overflow description comes from Android's own current localized resources.
                val id = Resources.getSystem().getIdentifier("action_menu_overflow_description", "string", "android")
                if (id == 0) blocked("No system overflow description")
                device.findObject(By.desc(Resources.getSystem().getString(id)))?.also { it.click() }
                device.waitForIdle(2000)
                settingsAction("locale_remove_menu")
            } ?: blocked("Unsupported remove-language menu")
        overflow.click()
        val extra = row(tag) ?: blocked("Missing extra locale $tag")
        val checkbox = extra.findObject(By.clazz("android.widget.CheckBox"))
            ?: blocked("Unsupported language removal checkbox")
        checkbox.click()
        (settingsAction("locale_remove_menu")
            ?: blocked("Missing remove action")).click()
        device.wait(Until.findObject(By.res("android", "button1")), 3000)?.click()
        awaitState("Extra locale not removed: $tag") { tag !in locales() }
    }
    fun changeWhileActivityRunning() {
        if (!enabled) return
        val before = locales()
        capture("before", JSONObject().put("action", arguments.getString("jLocaleAction", "change")))
        if (arguments.getString("jLocaleAction") == "probe") return
        val target = arguments.getString("jLocaleTags")?.split(',') ?: blocked("No target tags")
        if (target.isEmpty() || target.distinct().size != target.size || target.any { !it.matches(Regex("[a-z]{2}-[A-Z]{2}")) }) blocked("Invalid target locale list")
        try {
            // Promote in reverse order to establish exact priority without relying on translated labels.
            target.asReversed().forEach(::promote)
            (locales() - target.toSet()).forEach(::removeExtra)
            awaitState("Exact ordered locales not established") { locales() == target }
            capture("settings-applied", JSONObject().put("previous", JSONArray(before)))
            repeat(6) {
                if (device.currentPackageName != "com.android.settings") return@repeat
                device.pressBack()
                device.waitForIdle(2000)
            }
            if (!device.wait(Until.hasObject(By.pkg(instrumentation.targetContext.packageName).depth(0)), 10_000)) blocked("Existing app not resumed; no relaunch substituted")
            capture("returned-to-existing-app")
        } catch (e: AssertionError) { throw e }
        catch (e: Exception) { blocked("Locale UI failed: ${e.javaClass.simpleName}") }
    }
}
