package com.vabxsen.budgie.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReleaseRulesTest {
    @Test
    fun versionsAreComparedNumerically() {
        assertEquals(1, ReleaseRules.compare("1.10.0", "1.9.9"))
        assertEquals(-1, ReleaseRules.compare("v1.0.0", "2.0.0"))
        assertEquals(0, ReleaseRules.compare("v3.4.5", "3.4.5"))
    }

    @Test
    fun unsupportedVersionFormatsAreRejected() {
        listOf("1", "1.2", "1.2.3-beta", "01.2.3", "latest").forEach { version ->
            assertThrows(IllegalStateException::class.java) {
                ReleaseRules.normalizedVersion(version)
            }
        }
    }

    @Test
    fun exactSignedApkAssetIsParsed() {
        val release = ReleaseRules.parseLatestRelease(releaseJson())

        assertEquals("1.2.3", release.version)
        assertEquals("v1.2.3", release.tag)
        assertEquals("Budgie-v1.2.3.apk", release.assetName)
        assertEquals(2_224_185, release.sizeBytes)
        assertEquals("a".repeat(64), release.sha256)
    }

    @Test
    fun assetFromAnotherRepositoryIsRejected() {
        assertThrows(IllegalStateException::class.java) {
            ReleaseRules.parseLatestRelease(
                releaseJson(
                    downloadUrl = "https://github.com/attacker/budgie/releases/download/v1.2.3/Budgie-v1.2.3.apk"
                )
            )
        }
    }

    @Test
    fun insecureAssetUrlIsRejected() {
        assertThrows(IllegalStateException::class.java) {
            ReleaseRules.parseLatestRelease(
                releaseJson(
                    downloadUrl = "http://github.com/vabxsen/budgie/releases/download/v1.2.3/Budgie-v1.2.3.apk"
                )
            )
        }
    }

    @Test
    fun missingDigestIsRejected() {
        assertThrows(IllegalStateException::class.java) {
            ReleaseRules.parseLatestRelease(releaseJson(digest = ""))
        }
    }

    @Test
    fun ambiguousApkAssetsAreRejected() {
        val asset = assetJson()
        val json = releaseJson(assets = "$asset,$asset")
        assertThrows(IllegalStateException::class.java) {
            ReleaseRules.parseLatestRelease(json)
        }
    }

    private fun releaseJson(
        downloadUrl: String = "https://github.com/vabxsen/budgie/releases/download/v1.2.3/Budgie-v1.2.3.apk",
        digest: String = "sha256:${"a".repeat(64)}",
        assets: String? = null,
    ): String = """
        {
          "tag_name": "v1.2.3",
          "draft": false,
          "prerelease": false,
          "body": "A polished release.",
          "assets": [${assets ?: assetJson(downloadUrl, digest)}]
        }
    """.trimIndent()

    private fun assetJson(
        downloadUrl: String = "https://github.com/vabxsen/budgie/releases/download/v1.2.3/Budgie-v1.2.3.apk",
        digest: String = "sha256:${"a".repeat(64)}",
    ): String = """
        {
          "name": "Budgie-v1.2.3.apk",
          "content_type": "application/vnd.android.package-archive",
          "size": 2224185,
          "digest": "$digest",
          "browser_download_url": "$downloadUrl"
        }
    """.trimIndent()
}
