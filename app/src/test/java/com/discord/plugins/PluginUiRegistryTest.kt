package com.discord.plugins

import mmaarij.axycord.plugins.api.PluginContextMenuSurface
import mmaarij.axycord.plugins.api.PluginMessagePlacement
import mmaarij.axycord.plugins.api.PluginMessageInterceptionType
import mmaarij.axycord.plugins.api.pluginContextMenuItem
import mmaarij.axycord.plugins.api.pluginHeaderContribution
import mmaarij.axycord.plugins.api.pluginSlashCommand
import mmaarij.axycord.plugins.api.pluginMessageContribution
import mmaarij.axycord.plugins.api.pluginMessageDeleteInterceptor
import mmaarij.axycord.plugins.api.pluginMessageInterceptor
import mmaarij.axycord.plugins.api.pluginMessageUpdateInterceptor
import com.discord.models.message.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginUiRegistryTest {
    @Test
    fun deleteAndUpdateInterceptorsKeepMessagesAndSeeTheOldVersion() {
        val original = Message(1L, channelId = 2L, content = "before")
        assertFalse(PluginUiRegistry.hasMessageDeleteInterceptors())
        assertNull(PluginUiRegistry.interceptMessageDelete(original))

        val letGo = PluginUiRegistry.registerMessageInterceptor(
            "logger-test", pluginMessageDeleteInterceptor("let_go", onDelete = { null }),
        )
        val keep = PluginUiRegistry.registerMessageInterceptor(
            "logger-test", pluginMessageDeleteInterceptor("keep", onDelete = { it.copy(content = "kept") }),
        )
        val history = mutableListOf<String?>()
        val update = PluginUiRegistry.registerMessageInterceptor(
            "logger-test",
            pluginMessageUpdateInterceptor("history", onUpdate = { old, new -> history += old.content; new }),
        )
        try {
            assertTrue(PluginUiRegistry.hasMessageDeleteInterceptors())
            // One interceptor keeping the message is enough; the others do not undo it.
            assertEquals("kept", PluginUiRegistry.interceptMessageDelete(original)?.content)
            // These are not send/edit/receive interceptors, so those paths stay free.
            assertFalse(PluginUiRegistry.hasMessageInterceptors(PluginMessageInterceptionType.RECEIVE))
            val edited = original.copy(content = "after")
            assertEquals(edited, PluginUiRegistry.interceptMessageUpdate(original, edited))
            assertEquals(listOf<String?>("before"), history)
        } finally {
            letGo.unregister()
            keep.unregister()
            update.unregister()
        }
        assertFalse(PluginUiRegistry.hasMessageUpdateInterceptors())
    }

    @Test
    fun contentListsOnlyOwnedRegistrations() {
        val header = PluginUiRegistry.registerHeader(
            "inventory-test",
            pluginHeaderContribution("header", "Header", { error("not inflated") }),
        )
        val command = PluginUiRegistry.registerSlashCommand(
            "inventory-test",
            pluginSlashCommand("echo", "echo", "Echo", { "" }) { string("text", "Text") },
        )
        val menu = PluginUiRegistry.registerContextMenuItem(
            "other-plugin",
            pluginContextMenuItem("raw", "View Raw", PluginContextMenuSurface.MESSAGE, onClick = {}),
        )
        val message = PluginUiRegistry.registerMessage(
            "inventory-test",
            pluginMessageContribution(
                "card",
                "Card",
                PluginMessagePlacement.STANDALONE,
                { error("not inflated") },
            ),
        )
        val interceptor = PluginUiRegistry.registerMessageInterceptor(
            "inventory-test",
            pluginMessageInterceptor("lifecycle", PluginMessageInterceptionType.SEND, transform = { it }),
        )

        try {
            assertEquals(
                listOf(
                    PluginContentInfo("Header action", "Header"),
                    PluginContentInfo("Slash command", "/echo", "text"),
                    PluginContentInfo("Message standalone", "Card"),
                    PluginContentInfo("Message interceptor", "send"),
                ),
                PluginUiRegistry.content("inventory-test"),
            )
        } finally {
            header.unregister()
            command.unregister()
            menu.unregister()
            message.unregister()
            interceptor.unregister()
        }
    }
}
