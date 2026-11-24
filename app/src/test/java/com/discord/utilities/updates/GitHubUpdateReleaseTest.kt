package com.discord.utilities.updates

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class GitHubUpdateReleaseTest {
    private val sha = "a".repeat(64)
    private val source = "b".repeat(40)
    private val manifest = AppUpdate(null, null, sha, 8, source, 1_800_000_000L,
        222_163_200L, "1.0", "mmaarij.axycord")

    private fun asset(name: String, size: Long = 8, digest: String? = "sha256:$sha") =
        GitHubUpdateRelease.Asset(name, "uploaded", size, digest,
            "https://github.com/Axycord/Axycord/releases/download/v1.0/$name")

    private fun release() = GitHubUpdateRelease("v1.0", false, false, "2026-11-25T00:00:00Z", "First Axycord release",
        listOf(asset("app-universal-release.apk"), asset("update-manifest.json", 512)))

    @Test fun `github JSON maps to the Axycord package and version`() {
        val gson = Gson()
        val wire = gson.toJson(release())
        assertTrue(wire.contains("browser_download_url"))
        assertTrue(wire.contains("tag_name"))
        val parsed = gson.fromJson(wire, GitHubUpdateRelease::class.java)
        assertEquals("update-manifest.json", parsed.manifestAsset().name)
        val update = parsed.toUpdate(manifest)
        assertEquals("v1.0", update.release)
        assertEquals("1.0", update.versionName)
        assertEquals("mmaarij.axycord", update.packageName)
        assertEquals("First Axycord release", update.releaseNotes)
        assertTrue(update.isNewerThan("c".repeat(40), 1_799_999_999L, 1))
        assertEquals("https://api.github.com/repos/Axycord/Axycord/releases/latest", UPDATE_FEED_URL)
        assertEquals("https://api.github.com/repos/Axycord/Axycord/releases/tags/v1.0", update.changesUrl)
    }

    @Test fun `a published release and matching manifest are required`() {
        listOf(release().copy(draft = true), release().copy(prerelease = true),
            release().copy(publishedAt = null), release().copy(tag = "../v1.0"),
            release().copy(assets = emptyList()), release().copy(assets = release().assets!! + asset("update-manifest.json")),
        ).forEach { assertThrows(Exception::class.java) { it.manifestAsset() } }
        listOf(manifest.copy(size = 7), manifest.copy(sha256 = "c".repeat(64)),
            manifest.copy(packageName = "other.app"), manifest.copy(commitSha = null),
            manifest.copy(versionCode = 1),
        ).forEach { assertThrows(Exception::class.java) { release().toUpdate(it) } }
        assertThrows(Exception::class.java) {
            release().copy(assets = listOf(asset("app-release.apk", digest = "invalid"))).toUpdate(manifest)
        }
    }

    @Test fun `only assets in this repository and release are accepted`() {
        val good = asset("app-release.apk")
        listOf(good.url!!.replace("https:", "http:"), good.url.replace("Axycord/Axycord", "other/repo"),
            good.url.replace("v1.0/", "v2.0/"), "${good.url}?x=1", "${good.url}#x",
            good.url.replace("github.com", "user@github.com"), good.url.replace("github.com", "github.com.evil.example"),
            good.url.replace("app-release", "nested/app-release"), good.url.replace("app-release", "%2e%2e/app-release"),
        ).forEach { url ->
            assertFalse(url, isGitHubReleaseAsset(url, "v1.0"))
            assertThrows(Exception::class.java) {
                release().copy(assets = listOf(good.copy(url = url))).toUpdate(manifest)
            }
        }
    }

    @Test fun `ABI selection and baseline profiles keep their digests`() {
        val split = asset("app-arm64-v8a-release.apk", 12, "sha256:${"c".repeat(64)}")
        val update = release().copy(assets = release().assets!! + listOf(split,
            asset("app-arm64-v8a-release-api31.dm"), asset("app-release-api28.dm"))).toUpdate(manifest)
        val arm64 = update.forAbis(listOf("arm64-v8a")).validate()
        assertEquals(split.url, arm64.apkUrl)
        assertEquals(12L, arm64.size)
        assertEquals("c".repeat(64), arm64.sha256)
        assertNotNull(arm64.dexMetadataFor(36))
        assertNull(arm64.dexMetadataFor(30))
        val universal = update.forAbis(listOf("x86")).validate()
        assertEquals(manifest.sha256, universal.sha256)
        assertNotNull(universal.dexMetadataFor(28))
        assertNull(universal.dexMetadataFor(36))
    }

    @Test fun `missing optional asset digests fall back to the universal APK`() {
        val update = release().copy(assets = listOf(asset("app-release.apk", digest = null),
            asset("app-x86-release.apk", digest = null), asset("app-release-api31.dm", digest = null))).toUpdate(manifest)
        assertEquals(asset("app-release.apk").url, update.forAbis(listOf("x86")).apkUrl)
        assertNull(update.dexMetadataFor(36))
    }

    @Test fun `release notes and comparison entries are bounded`() {
        val update = release().copy(body = "x".repeat(4_001)).toUpdate(manifest)
        assertEquals(4_000, update.releaseNotes!!.length)
        assertTrue(update.releaseNotesTruncated)
        val commits = listOf(GitHubUpdateComparison.Commit(source, GitHubUpdateComparison.Detail("fix: updater\nDetails")))
        val changes = GitHubUpdateComparison(1, commits).toChanges(update, "c".repeat(40))
        assertEquals("fix: updater", changes.commits!!.single().title)
        assertTrue(changes.notesTruncated!!)
        assertThrows(Exception::class.java) { GitHubUpdateComparison(0, commits).toChanges(update, "c".repeat(40)) }
    }
}
