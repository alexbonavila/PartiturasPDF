package com.abdev.partituraspdf.app.integration

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.xml.sax.InputSource

/** Minimal synthetic API26 hierarchy; contains no owner evidence or private device data. */
class LocaleRemovalCheckboxTest {
    private val spanish = "Español (España)"
    private val english = "English (United States)"
    private fun checkbox(name: String, checked: Boolean = false, enabled: Boolean = true) =
        """<node resource-id="com.android.settings:id/checkbox" class="android.widget.CheckBox"
            text="$name" enabled="$enabled" visible-to-user="true" checked="$checked"/>"""

    private fun nodes(xml: String): List<LocaleRemovalCheckbox<Element>> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(InputSource(StringReader("<hierarchy>$xml</hierarchy>")))
        val list = document.getElementsByTagName("node")
        return (0 until list.length).map { index ->
            val node = list.item(index) as Element
            LocaleRemovalCheckbox(node, node.getAttribute("resource-id"), node.getAttribute("class"),
                node.getAttribute("text"), node.getAttribute("enabled") == "true",
                node.getAttribute("visible-to-user") == "true", node.getAttribute("checked") == "true")
        }
    }

    @Test fun normalLabelAndDragHandleAreNeverRemovalCheckboxes() {
        val normal = """<node resource-id="com.android.settings:id/label" class="android.widget.TextView"
            text="$spanish" enabled="true" visible-to-user="true"/>
            <node resource-id="com.android.settings:id/dragHandle" class="android.widget.ImageView"/>"""
        assertNull(selectLocaleRemovalCheckbox(nodes(normal), spanish))
    }

    @Test fun removalModeWithoutLabelSelectsExactSpanishNotFirstEnglish() {
        val selected = selectLocaleRemovalCheckbox(nodes(checkbox(english) + checkbox(spanish)), spanish)!!
        assertEquals(spanish, selected.text)
        assertEquals("com.android.settings:id/checkbox", selected.target.getAttribute("resource-id"))
        assertFalse(selected.checked)
    }

    @Test fun absentOrWrongLanguageNeverSelectsAnotherCheckbox() {
        assertNull(selectLocaleRemovalCheckbox(nodes(""), spanish))
        assertNull(selectLocaleRemovalCheckbox(nodes(checkbox(english)), spanish))
        assertNull(selectLocaleRemovalCheckbox(nodes(checkbox("Español (México)")), spanish))
    }

    @Test fun ambiguousMatchesIncludingDisabledDuplicateAreRejected() {
        for (duplicate in listOf(checkbox(spanish), checkbox(spanish, enabled = false))) {
            assertNull(selectLocaleRemovalCheckbox(nodes(checkbox(spanish) + duplicate), spanish))
        }
    }

    @Test fun disabledHiddenWrongResourceAndWrongClassAreRejected() {
        val valid = checkbox(spanish)
        for (invalid in listOf(checkbox(spanish, enabled = false),
            valid.replace("visible-to-user=\"true\"", "visible-to-user=\"false\""),
            valid.replace("id/checkbox", "id/label"),
            valid.replace("android.widget.CheckBox", "android.widget.TextView"))) {
            assertNull(selectLocaleRemovalCheckbox(nodes(invalid), spanish))
        }
    }

    @Test fun checkedStateRequiresFreshObservationOfTheSameExactLanguage() {
        val before = nodes(checkbox(english) + checkbox(spanish))
        assertFalse(selectLocaleRemovalCheckbox(before, spanish)!!.checked)
        val wrongChecked = nodes(checkbox(english, checked = true) + checkbox(spanish))
        assertFalse(selectLocaleRemovalCheckbox(wrongChecked, spanish)!!.checked)
        val after = nodes(checkbox(english) + checkbox(spanish, checked = true))
        assertTrue(selectLocaleRemovalCheckbox(after, spanish)!!.checked)
        assertFalse(selectLocaleRemovalCheckbox(before, spanish)!!.checked)
        assertNull(selectLocaleRemovalCheckbox(nodes(checkbox(english, checked = true)), spanish))
    }
}
