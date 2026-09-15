package com.vabxsen.budgie.updates

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateDownloadTest {
    @get:Rule val folder = TemporaryFolder()

    private val apk = "pretend apk contents".toByteArray()
    private val assetUrl = "https://github.com/vabxsen/budgie/releases/download/v1.2.3/Budgie-v1.2.3.apk"
    private val storageUrl = "https://release-assets.githubusercontent.com/github-production-release-asset/1"

    private val release =
        UpdateRelease(
            version = "1.2.3",
            tag = "v1.2.3",
            assetName = "Budgie-v1.2.3.apk",
            downloadUrl = assetUrl,
            sizeBytes = apk.size.toLong(),
            sha256 = MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) },
            notes = "",
        )

    private class FakeConnection(
        uri: URI,
        private val code: Int,
        private val location: String? = null,
        private val body: ByteArray = ByteArray(0),
    ) : HttpURLConnection(uri.toURL()) {
        override fun getResponseCode(): Int = code

        override fun getHeaderField(name: String?): String? = if (name == "Location") location else null

        override fun getContentLengthLong(): Long = if (code == HTTP_OK) body.size.toLong() else -1L

        override fun getInputStream(): InputStream = ByteArrayInputStream(body)

        override fun connect() {}

        override fun disconnect() {}

        override fun usingProxy() = false
    }

    private fun files() = folder.root.list()!!.toList()

    @Test
    fun followsGitHubsRedirectAndKeepsTheVerifiedFile() {
        var progress = 0f
        val file =
            downloadRelease(
                release,
                folder.root,
                { uri ->
                    when (uri.toString()) {
                        assetUrl -> FakeConnection(uri, 302, location = storageUrl)
                        storageUrl -> FakeConnection(uri, 200, body = apk)
                        else -> error("Unexpected request to $uri")
                    }
                },
            ) { progress = it }

        assertEquals("pretend apk contents", file.readText())
        assertEquals(1f, progress)
        assertEquals(listOf("Budgie-v1.2.3.apk"), files())
    }

    @Test
    fun redirectToAnotherHostIsRefused() {
        assertThrows(IllegalStateException::class.java) {
            downloadRelease(release, folder.root, { FakeConnection(it, 302, location = "https://example.com/Budgie.apk") }) {}
        }
        assertTrue(files().isEmpty())
    }

    @Test
    fun tamperedFileFailsTheIntegrityCheckAndIsDeleted() {
        val tampered = "pretend apk content!".toByteArray()
        val error =
            assertThrows(IllegalStateException::class.java) {
                downloadRelease(release, folder.root, { FakeConnection(it, 200, body = tampered) }) {}
            }
        assertTrue(error.message!!.contains("integrity"))
        assertTrue(files().isEmpty())
    }

    @Test
    fun unexpectedSizeIsRefused() {
        assertThrows(IllegalStateException::class.java) {
            downloadRelease(release, folder.root, { FakeConnection(it, 200, body = apk + apk) }) {}
        }
        assertTrue(files().isEmpty())
    }
}
