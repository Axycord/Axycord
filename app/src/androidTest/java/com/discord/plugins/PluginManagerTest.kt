package com.discord.plugins

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import mmaarij.axycord.plugins.api.AxycordPlugin
import mmaarij.axycord.plugins.api.PluginComposerContribution
import mmaarij.axycord.plugins.api.PluginComposerZone
import mmaarij.axycord.plugins.api.PluginManifest
import mmaarij.axycord.plugins.api.PluginScope
import mmaarij.axycord.plugins.api.pluginComposerContribution
import mmaarij.axycord.plugins.api.pluginManifest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import rx.Completable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PluginManagerTest {
    private val pluginId = "test.runtime"

    @After
    fun cleanRegistry() {
        PluginUiRegistry.removeOwner(pluginId)
    }

    @Test
    fun asyncPluginStartsAndShutdownRemovesContributions() {
        val started = CountDownLatch(1)
        val manifest = pluginManifest(pluginId, "Runtime Test", "1", 1)
        val plugin = object : AxycordPlugin {
            override fun start(scope: PluginScope): Completable = Completable.fromAction {
                scope.ui.registerComposer(
                    pluginComposerContribution(
                        "button",
                        PluginComposerZone.LEFT,
                        { android.view.View(it) },
                    ),
                )
                started.countDown()
            }
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = PluginManager(BundledPluginSource(listOf(BundledPlugin(plugin, manifest))), loadingEnabled = { true })

        manager.initialize(context)

        assertTrue(started.await(2, TimeUnit.SECONDS))
        assertEquals(listOf("button"), PluginUiRegistry.composers().map { it.contribution.id })
        manager.shutdown()
        assertTrue(PluginUiRegistry.composers().isEmpty())
    }
}
