package com.vabxsen.budgie

import android.content.ContextWrapper
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vabxsen.budgie.data.BudgieRepository
import com.vabxsen.budgie.data.CollectionCodec
import com.vabxsen.budgie.domain.*
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BudgieStorageTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    @Before fun signedOut() = assumeSignedOut()
    private val app get() = ui.activity.application as BudgieApplication
    private fun vm() = ViewModelProvider(ui.activity)[BudgieViewModel::class.java]
    private fun ownData() = BudgieCollection(listOf(Subscription(name = "My membership",
        priceMinor = 45678, plan = "Personal", anchorDate = LocalDate.now(), notes = "My note")),
        Preferences(onboarded = true, budgetMinor = 0))
    private fun seed(data: BudgieCollection) {
        ui.waitUntil(10_000) { app.repository.state.value != null }
        runBlocking { app.repository.update { data } }
    }

    @Test fun backupExportImportAndRestoreRoundTripUserDataAndKeepNotificationConsent() {
        val original = ownData()
        seed(original)
        val dir = File(app.cacheDir, "audit-${System.nanoTime()}").apply { mkdirs() }
        val backup = File(dir, "backup.json")
        val csv = File(dir, "subscriptions.csv")
        ui.runOnUiThread { vm().export(Uri.fromFile(backup), false) }
        ui.waitUntil(5_000) { backup.exists() && runCatching { CollectionCodec.decode(backup.readText()) == original }.getOrDefault(false) }
        ui.runOnUiThread { vm().export(Uri.fromFile(csv), true) }
        ui.waitUntil(5_000) { csv.exists() && csv.readText().contains("My membership") }
        assertTrue(csv.readText().contains("456.78"))
        seed(BudgieCollection(preferences = Preferences(onboarded = true, notifications = true)))
        var imported: BudgieCollection? = null
        ui.runOnUiThread { vm().import(Uri.fromFile(backup)) { imported = it } }
        ui.waitUntil(5_000) { imported != null }
        assertTrue(app.repository.state.value!!.getOrThrow().subscriptions.isEmpty())
        ui.runOnUiThread { vm().restore(imported!!) }
        ui.waitUntil(5_000) { app.repository.state.value!!.getOrThrow().subscriptions == original.subscriptions }
        assertTrue(app.repository.state.value!!.getOrThrow().preferences.notifications)
        val reloaded = BudgieRepository(app)
        runBlocking { reloaded.load() }
        assertEquals(original.subscriptions, reloaded.state.value!!.getOrThrow().subscriptions)
    }

    @Test fun corruptedStorageIsReportedAndNeverOverwritten() {
        val dir = File(app.cacheDir, "audit-corrupt-${System.nanoTime()}").apply { mkdirs() }
        val file = File(dir, "collection.json").apply { writeText("broken user file") }
        val isolatedContext = object : ContextWrapper(app) { override fun getFilesDir() = dir }
        val repository = BudgieRepository(isolatedContext)
        runBlocking {
            repository.load()
            assertTrue(repository.state.value!!.isFailure)
            assertTrue(runCatching { repository.update { ownData() } }.isFailure)
        }
        assertEquals("broken user file", file.readText())
        file.writeText(CollectionCodec.encode(ownData()))
        runBlocking { repository.load() }
        assertEquals("My membership", repository.state.value!!.getOrThrow().subscriptions.single().name)
    }

    @Test fun newStorageIsEmptyWithNoPresetBudget() {
        val dir = File(app.cacheDir, "audit-new-${System.nanoTime()}").apply { mkdirs() }
        val repository = BudgieRepository(object : ContextWrapper(app) { override fun getFilesDir() = dir })
        runBlocking { repository.load() }
        val result = repository.state.value!!.getOrThrow()
        assertTrue(result.subscriptions.isEmpty())
        assertEquals(0L, result.preferences.budgetMinor)
        assertFalse(result.preferences.onboarded)
    }
    @Test fun invalidImportAndUnwritableExportShowErrorsWithoutChangingCollection() {
        val original = ownData()
        seed(original)
        val invalid = File(app.cacheDir, "invalid-backup.json").apply { writeText("{}") }
        var confirmed = false
        ui.runOnUiThread { vm().import(Uri.fromFile(invalid)) { confirmed = true } }
        val error = "This isn’t a valid Budgie Android backup. Your collection is unchanged."
        ui.waitUntil(5_000) { ui.onAllNodesWithText(error).fetchSemanticsNodes().isNotEmpty() }
        assertFalse(confirmed)
        assertEquals(original, app.repository.state.value!!.getOrThrow())
        ui.waitUntil(12_000) { ui.onAllNodesWithText(error).fetchSemanticsNodes().isEmpty() }
        ui.runOnUiThread { vm().export(Uri.fromFile(app.cacheDir), false) }
        val exportError = "Couldn’t save that file. Please choose another location."
        ui.waitUntil(5_000) { ui.onAllNodesWithText(exportError).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(original, app.repository.state.value!!.getOrThrow())
    }

    @Test fun retryButtonReloadsRecoveredDataWithoutReplacingTheUsersCollection() {
        val original = ownData()
        seed(original)
        val file = app.repository.activeCollectionFile()
        try {
            file.writeText("unreadable audit fixture")
            runBlocking { app.repository.load() }
            ui.onNodeWithText("Try again").assertIsDisplayed()
            assertEquals("unreadable audit fixture", file.readText())
            file.writeText(CollectionCodec.encode(original))
            ui.onNodeWithText("Try again").performClick()
            ui.waitUntil(5_000) { app.repository.state.value?.getOrNull() == original }
            ui.onNodeWithText("Good things.\nOn repeat.").assertIsDisplayed()
            assertEquals(original, app.repository.state.value!!.getOrThrow())
        } finally {
            file.writeText(CollectionCodec.encode(original))
            runBlocking { app.repository.load() }
        }
    }

}
