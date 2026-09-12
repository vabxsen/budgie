package com.vabxsen.budgie

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vabxsen.budgie.updates.UpdateRelease
import com.vabxsen.budgie.updates.verifyDownloadedApk
import java.io.File
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateVerifierTest {
    @Test
    fun currentlyInstalledApkCannotBeReinstalledAsAnUpdate() {
        val context = ApplicationProvider.getApplicationContext<BudgieApplication>()
        val installedApk = File(context.applicationInfo.sourceDir)
        val release = UpdateRelease(
            version = BuildConfig.VERSION_NAME,
            tag = "v${BuildConfig.VERSION_NAME}",
            assetName = "Budgie-v${BuildConfig.VERSION_NAME}.apk",
            downloadUrl = "https://github.com/vabxsen/budgie/releases/download/v${BuildConfig.VERSION_NAME}/Budgie-v${BuildConfig.VERSION_NAME}.apk",
            sizeBytes = installedApk.length(),
            sha256 = "0".repeat(64),
            notes = "",
        )

        assertThrows(IllegalStateException::class.java) {
            verifyDownloadedApk(context, installedApk, release)
        }
    }
}
