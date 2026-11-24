package com.discord.plugins

import mmaarij.axycord.plugins.api.PLUGIN_API_LEVEL
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.view.LayoutInflater
import dalvik.system.PathClassLoader
import mmaarij.axycord.plugins.api.AxycordPlugin
import mmaarij.axycord.plugins.api.PluginLoadMode
import mmaarij.axycord.plugins.api.PluginManifest
import mmaarij.axycord.plugins.api.PluginResources
import mmaarij.axycord.plugins.api.pluginManifest
import mmaarij.axycord.plugins.api.pluginRequirement
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

internal data class DiscoveredPlugin(
    val plugin: AxycordPlugin,
    val manifest: PluginManifest,
    val sourcePackage: String?,
    val resources: PluginResources,
    val codeLoadDurationMs: Long,
)

internal data class ExternalPluginDescriptor(
    val packageName: String,
    val label: String,
    val entrypoint: String,
    val signerSha256: String,
    val icon: Drawable?,
    val manifest: PluginManifest,
)

internal data class PluginHandle(
    val manifest: PluginManifest,
    val load: () -> DiscoveredPlugin?,
)

internal fun interface PluginSource {
    fun discover(context: Context): List<PluginHandle>
}

internal data class BundledPlugin(
    val plugin: AxycordPlugin,
    val manifest: PluginManifest,
)

internal class BundledPluginSource(
    private val plugins: List<BundledPlugin>,
) : PluginSource {
    override fun discover(context: Context): List<PluginHandle> = plugins.map { bundled ->
        PluginHandle(bundled.manifest) {
            DiscoveredPlugin(bundled.plugin, bundled.manifest, null, EmptyPluginResources, 0L)
        }
    }
}

internal class ExternalPluginSource : PluginSource {
    private val failures = ConcurrentHashMap<String, String>()

    override fun discover(context: Context): List<PluginHandle> {
        return PluginLoadingPolicy.enabledPackages()
            .flatMap { packageName -> catalog(context, packageName, loadIcons = false) }
            .filter { descriptor ->
                PluginLoadingPolicy.approvedSigner(descriptor.packageName) == descriptor.signerSha256
            }
            .filter { descriptor ->
                PLUGIN_API_LEVEL >= descriptor.manifest.axycordVersionMinimum
            }
            .map { descriptor -> PluginHandle(descriptor.manifest) { load(context, descriptor) } }
    }

    fun catalog(context: Context): List<ExternalPluginDescriptor> = catalog(context, null, loadIcons = true)

    fun failure(packageName: String): String? = failures[packageName]

    /** Startup discovery never shows an icon, and decoding one out of another APK costs milliseconds. */
    private fun catalog(context: Context, packageName: String?, loadIcons: Boolean): List<ExternalPluginDescriptor> {
        val packageManager = context.packageManager
        val intent = Intent(PLUGIN_ACTION).apply { `package` = packageName }
        val matches = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentServices(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentServices(intent, PackageManager.GET_META_DATA)
        }
        return matches.mapNotNull { match ->
            runCatching {
            val service = match.serviceInfo ?: return@mapNotNull null
            val metadata = service.metaData ?: return@mapNotNull null
            val entrypoint = metadata.getString(ENTRYPOINT_METADATA) ?: return@mapNotNull null
            val pluginId = metadata.getString(ID_METADATA) ?: return@mapNotNull null
            val axycordMinimum = metadata.getInt(AXYCORD_MINIMUM_METADATA, 0).takeIf { it > 0 }
                ?: return@mapNotNull null
            val axycordMaximum = metadata.getInt(AXYCORD_MAXIMUM_METADATA, 0).takeIf { it > 0 }
            val loadMode = metadata.getString(LOAD_MODE_METADATA)
                ?.let { runCatching { PluginLoadMode.valueOf(it) }.getOrNull() }
                ?: return@mapNotNull null
            val packageInfo = if (Build.VERSION.SDK_INT >= 33) {
                packageManager.getPackageInfo(service.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(service.packageName, 0)
            }
            val versionCode = if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode.toInt()
            else @Suppress("DEPRECATION") packageInfo.versionCode
            val requirements = metadata.getString(REQUIREMENTS_METADATA).orEmpty()
                .split(';')
                .filter(String::isNotBlank)
                .mapNotNull(::parseRequirement)
            val label = service.applicationInfo.loadLabel(packageManager).toString()
            val manifest = pluginManifest(
                pluginId,
                label,
                packageInfo.versionName.orEmpty().ifBlank { versionCode.toString() },
                versionCode,
            ) {
                description = metadata.getString(DESCRIPTION_METADATA).orEmpty()
                axycordVersionMinimum = axycordMinimum
                axycordVersionMaximumExclusive = axycordMaximum
                priority = metadata.getInt(PRIORITY_METADATA, 0)
                this.loadMode = loadMode
                usesResources = metadata.getBoolean(USES_RESOURCES_METADATA, false)
                requirements.forEach(::requires)
            }
            ExternalPluginDescriptor(
                packageName = service.packageName,
                label = label,
                entrypoint = entrypoint,
                signerSha256 = signerFingerprint(packageManager, service.packageName),
                icon = if (loadIcons) runCatching { service.applicationInfo.loadIcon(packageManager) }.getOrNull() else null,
                manifest = manifest,
            )
            }.getOrNull()
        }.distinctBy(ExternalPluginDescriptor::packageName)
    }

    private fun load(context: Context, descriptor: ExternalPluginDescriptor): DiscoveredPlugin? =
        runCatching {
                val startedAt = SystemClock.elapsedRealtimeNanos()
                val packageManager = context.packageManager
                val application = if (Build.VERSION.SDK_INT >= 33) {
                    packageManager.getApplicationInfo(
                        descriptor.packageName,
                        PackageManager.ApplicationInfoFlags.of(0),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getApplicationInfo(descriptor.packageName, 0)
                }
                val classLoader = PathClassLoader(
                    application.sourceDir,
                    application.nativeLibraryDir,
                    context.classLoader,
                )
                val plugin = classLoader.loadClass(descriptor.entrypoint)
                    .asSubclass(AxycordPlugin::class.java)
                    .getDeclaredConstructor()
                    .newInstance()
                val resources = if (descriptor.manifest.usesResources) {
                    val packageContext = context.createPackageContext(
                        application.packageName,
                        Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
                    )
                    val wrapped = PluginResourceContext(packageContext, classLoader)
                    LoadedPluginResources(wrapped, LayoutInflater.from(packageContext).cloneInContext(wrapped))
                } else {
                    EmptyPluginResources
                }
                DiscoveredPlugin(
                    plugin,
                    descriptor.manifest,
                    descriptor.packageName,
                    resources,
                    (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000L,
                )
            }.onSuccess {
                failures.remove(descriptor.packageName)
            }.onFailure { failure ->
                failures[descriptor.packageName] = failure.message ?: failure::class.java.simpleName
            }.getOrNull()

    private fun parseRequirement(value: String) = value.split(':').takeIf { it.size == 3 }?.let { parts ->
        val minimum = parts[1].toIntOrNull() ?: return@let null
        val maximum = parts[2].toIntOrNull() ?: return@let null
        runCatching {
            pluginRequirement(parts[0], minimum, maximum.takeIf { it > 0 })
        }.getOrNull()
    }

    private fun signerFingerprint(packageManager: PackageManager, packageName: String): String = runCatching {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            val info = if (Build.VERSION.SDK_INT >= 33) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            }
            info.signingInfo?.apkContentsSigners.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures.orEmpty()
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(signatures.first().toByteArray())
        digest.joinToString(":") { "%02X".format(it.toInt() and 0xff) }
    }.getOrDefault("Unknown")

    private class PluginResourceContext(
        base: Context,
        private val pluginClassLoader: ClassLoader,
    ) : ContextWrapper(base) {
        override fun getClassLoader(): ClassLoader = pluginClassLoader
    }

    private data class LoadedPluginResources(
        override val context: Context,
        override val inflater: LayoutInflater,
    ) : PluginResources

    companion object {
        const val PLUGIN_ACTION = "mmaarij.axycord.PLUGIN"
        const val ENTRYPOINT_METADATA = "mmaarij.axycord.plugin.ENTRYPOINT"
        const val ID_METADATA = "mmaarij.axycord.plugin.ID"
        const val DESCRIPTION_METADATA = "mmaarij.axycord.plugin.DESCRIPTION"
        const val USES_RESOURCES_METADATA = "mmaarij.axycord.plugin.USES_RESOURCES"
        const val AXYCORD_MINIMUM_METADATA = "mmaarij.axycord.plugin.AXYCORD_VERSION_MINIMUM"
        const val AXYCORD_MAXIMUM_METADATA = "mmaarij.axycord.plugin.AXYCORD_VERSION_MAXIMUM_EXCLUSIVE"
        const val LOAD_MODE_METADATA = "mmaarij.axycord.plugin.LOAD_MODE"
        const val PRIORITY_METADATA = "mmaarij.axycord.plugin.PRIORITY"
        const val REQUIREMENTS_METADATA = "mmaarij.axycord.plugin.REQUIREMENTS"
    }
}

internal data object EmptyPluginResources : PluginResources {
    override val context: Context? = null
    override val inflater: LayoutInflater? = null
}

internal class CompositePluginSource(
    private vararg val sources: PluginSource,
) : PluginSource {
    override fun discover(context: Context): List<PluginHandle> = sources.flatMap { it.discover(context) }
}
