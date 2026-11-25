package com.discord.widgets.settings

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class SettingsSectionsLayoutTest {
    private fun elements(file: String): List<Element> {
        val relative = "src/main/res/$file"
        val path = File(relative).takeIf(File::isFile) ?: File("app/$relative")
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val nodes = factory.newDocumentBuilder().parse(path).getElementsByTagName("*")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.id() = getAttributeNS("http://schemas.android.com/apk/res/android", "id").substringAfterLast('/')

    @Test fun billingFollowsUserSettingsAndPreservesRequestedOrder() {
        val ids = elements("layout/widget_settings.xml").map { it.id() }
        val billing = ids.indexOf("billing_settings_header")
        assertTrue(ids.indexOf("user_settings_header") < billing)
        assertEquals(listOf("settings_billing_quests", "settings_billing_orbs_shop", "settings_billing_server_boosts"),
            ids.subList(billing + 1, billing + 4))
        assertTrue(ids.indexOf("settings_billing_server_boosts") < ids.indexOf("app_settings_header"))
        assertEquals("settings_switch_accounts", ids[ids.indexOf("account") + 1])
    }

    @Test fun toolbarContainsOnlySearchAndDebugIsLastAccessibilityRow() {
        assertEquals(listOf("menu_settings_search"), elements("menu/menu_settings.xml").filter { it.tagName == "item" }.map { it.id() })
        val rows = elements("layout/widget_settings_accessibility.xml").filter { it.tagName == "TextView" }
        assertEquals("settings_accessibility_debug", rows.last().id())
    }

    @Test fun logoutFollowsAppInformationAndBothTextAndIconAreRed() {
        val rows = elements("layout/widget_settings.xml")
        assertTrue(rows.indexOfFirst { it.id() == "settings_logout" } > rows.indexOfFirst { it.id() == "app_info_header" })
        val logout = rows.single { it.id() == "settings_logout" }
        assertEquals("@color/status_red_500", logout.getAttributeNS("http://schemas.android.com/apk/res/android", "textColor"))
        assertEquals("@color/status_red_500", logout.getAttributeNS("http://schemas.android.com/apk/res-auto", "drawableTint"))
        assertEquals("@drawable/ic_exit_to_app_light_24dp", logout.getAttributeNS("http://schemas.android.com/apk/res-auto", "drawableStartCompat"))
    }
}
