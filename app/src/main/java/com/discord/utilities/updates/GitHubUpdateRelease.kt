package com.discord.utilities.updates

import com.google.gson.annotations.SerializedName
import java.net.URI

/** Public GitHub release metadata, read without the Discord client's credentials. */
internal data class GitHubUpdateRelease(
    @SerializedName("tag_name") val tag: String? = null,
    val draft: Boolean? = null,
    val prerelease: Boolean? = null,
    @SerializedName("published_at") val publishedAt: String? = null,
    val body: String? = null,
    val assets: List<Asset>? = null,
) {
    data class Asset(
        val name: String? = null,
        val state: String? = null,
        val size: Long? = null,
        val digest: String? = null,
        @SerializedName("browser_download_url") val url: String? = null,
    ) {
        fun sha256(): String? = digest?.removePrefix("sha256:")?.takeIf { Regex("[0-9a-f]{64}").matches(it) }
    }

    fun manifestAsset(): Asset {
        validate()
        return asset("update-manifest.json").also { require(requireNotNull(it.size) <= 16_384) }
    }

    fun toUpdate(manifest: AppUpdate): AppUpdate {
        validate()
        val universal = assets.orEmpty().firstOrNull { it.name == "app-universal-release.apk" }
            ?.let { asset(requireNotNull(it.name)) } ?: asset("app-release.apk")
        require(universal.size == manifest.size)
        if (universal.digest != null) require(requireNotNull(universal.sha256()) == manifest.sha256)
        val splits = UPDATE_ABIS.mapNotNull { abi ->
            val name = "app-$abi-release.apk"
            if (assets.orEmpty().none { it.name == name }) return@mapNotNull null
            val apk = asset(name)
            val sha = apk.sha256() ?: return@mapNotNull null
            abi to AppUpdate.Apk(apk.url, sha, apk.size, profiles(name.removeSuffix(".apk")))
        }.toMap()
        return manifest.copy(
            release = tag,
            apkUrl = universal.url,
            apks = splits.takeIf { it.isNotEmpty() },
            dexMetadata = profiles("app-release"),
            releaseNotes = body.orEmpty().take(4_000),
            releaseNotesTruncated = body.orEmpty().length > 4_000,
        ).validate().also { require(it.hasBuildMetadata) }
    }

    private fun profiles(prefix: String): List<AppUpdate.DexMetadata> = listOf(28 to 30, 31 to null).mapNotNull { (min, max) ->
        val name = "$prefix-api$min.dm"
        if (assets.orEmpty().none { it.name == name }) return@mapNotNull null
        // Profiles are optional, so malformed metadata falls back to installing only the APK.
        runCatching {
            val profile = asset(name)
            AppUpdate.DexMetadata(profile.url, requireNotNull(profile.sha256()), profile.size, min, max)
        }.getOrNull()
    }

    private fun asset(name: String): Asset = requireNotNull(assets).single { it.name == name }.also {
        require(it.state == "uploaded" && it.size != null && it.size in 1..1_073_741_824L)
        require(isGitHubReleaseAsset(it.url, tag))
        require(URI(requireNotNull(it.url)).rawPath.substringAfterLast('/') == name)
    }

    private fun validate() {
        require(tag != null && Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,99}").matches(tag))
        require(draft == false && prerelease == false && !publishedAt.isNullOrBlank())
    }
}

internal fun isGitHubReleaseAsset(url: String?, tag: String?): Boolean = runCatching {
    val uri = URI(requireNotNull(url))
    uri.scheme == "https" && uri.host == "github.com" && uri.port in listOf(-1, 443) &&
        uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
        uri.rawPath.startsWith("/$UPDATE_REPOSITORY/releases/download/$tag/") &&
        uri.rawPath.substringAfterLast('/').let { it.isNotEmpty() && !it.contains('%') } &&
        uri.rawPath.removePrefix("/$UPDATE_REPOSITORY/releases/download/$tag/").none { it == '/' }
}.getOrDefault(false)

internal data class GitHubUpdateComparison(
    @SerializedName("total_commits") val total: Long? = null,
    val commits: List<Commit>? = null,
) {
    data class Commit(val sha: String? = null, val commit: Detail? = null)
    data class Detail(val message: String? = null)

    fun toChanges(update: AppUpdate, from: String): AppUpdateChanges {
        val entries = requireNotNull(commits).take(100).map {
            AppUpdateChanges.Commit(it.sha, it.commit?.message?.lineSequence()?.firstOrNull()?.take(200))
        }
        return AppUpdateChanges(update.release, from, update.commitSha, update.releaseNotes.orEmpty(), update.releaseNotesTruncated,
            entries, AppUpdateChanges.Comparison.COMPARED, total, requireNotNull(total) > entries.size)
            .validate(update, from)
    }
}
