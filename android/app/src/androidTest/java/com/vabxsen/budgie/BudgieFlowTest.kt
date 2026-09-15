package com.vabxsen.budgie

import android.app.NotificationManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vabxsen.budgie.data.BudgieRepository
import com.vabxsen.budgie.domain.*
import com.vabxsen.budgie.notifications.ReminderScheduler
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BudgieFlowTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    @Before fun signedOut() = assumeSignedOut()
    private val app
        get() = ui.activity.application as BudgieApplication

    private fun seed(collection: BudgieCollection) {
        ui.waitUntil(10_000) { app.repository.state.value != null }
        runBlocking { app.repository.update { collection } }
        ui.waitForIdle()
    }

    @Test
    fun firstRunStartsEmptyAndDoesNotInjectSampleData() {
        seed(BudgieCollection())
        ui.onNodeWithText("Start my collection").performScrollTo().performClick()
        ui.waitUntil(5_000) {
            app.repository.state.value?.getOrNull()?.preferences?.onboarded == true
        }
        assertTrue(app.repository.state.value!!.getOrThrow().subscriptions.isEmpty())
        ui.onNodeWithText("Good things.\nOn repeat.").assertIsDisplayed()
    }

    @Test
    fun createEditArchiveRestoreAndDiskReload() {
        seed(BudgieCollection(preferences = Preferences(onboarded = true)))
        ui.onAllNodesWithText("Add subscription")[0].performClick()
        ui.onNodeWithTag("subscription-editor").performScrollToNode(hasText("Service name"))
        ui.onNodeWithText("Service name").performTextInput("Test service")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        ui.onNodeWithTag("subscription-editor").performScrollToNode(hasText("Amount (₹)"))
        ui.onNodeWithText("Amount (₹)").performClick()
        ui.waitForIdle()
        ui.onNodeWithText("Amount (₹)").performTextReplacement("123.45")
        ui.onNodeWithTag("subscription-editor").performScrollToNode(hasText("Save subscription"))
        ui.onNodeWithText("Save subscription").performClick()
        ui.waitUntil(5_000) { app.repository.state.value?.getOrNull()?.subscriptions?.size == 1 }
        ui.onNodeWithText("Test service").assertIsDisplayed()
        waitForSaveMessage()
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Edit subscription"))
        ui.onNodeWithText("Edit subscription").performScrollTo().performClick()
        ui.onNodeWithTag("subscription-editor").performScrollToNode(hasText("Amount (₹)"))
        ui.onNodeWithText("Amount (₹)").performTextReplacement("199.99")
        ui.onNodeWithTag("subscription-editor").performScrollToNode(hasText("Save changes"))
        ui.onNodeWithText("Save changes").performClick()
        ui.waitUntil(5_000) {
            app.repository.state.value?.getOrNull()?.subscriptions?.singleOrNull()?.priceMinor ==
                19999L
        }
        waitForSaveMessage()
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Archive subscription"))
        ui.onNodeWithText("Archive subscription").performScrollTo().performClick()
        ui.onNodeWithText("Archive", useUnmergedTree = true).performClick()
        ui.waitUntil(5_000) {
            app.repository.state.value?.getOrNull()?.subscriptions?.singleOrNull()?.status ==
                SubscriptionStatus.ARCHIVED
        }
        ui.waitUntil(12_000) {
            ui.onAllNodesWithText("Archived in Budgie. Your provider subscription is unchanged.")
                .fetchSemanticsNodes()
                .isEmpty()
        }
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Restore subscription"))
        ui.onNodeWithText("Restore subscription").performScrollTo().performClick()
        ui.waitUntil(5_000) {
            app.repository.state.value?.getOrNull()?.subscriptions?.singleOrNull()?.status ==
                SubscriptionStatus.ACTIVE
        }
        val reopened = BudgieRepository(app)
        runBlocking { reopened.load() }
        val saved = reopened.state.value!!.getOrThrow().subscriptions.single()
        assertEquals("Test service", saved.name)
        assertEquals(19999L, saved.priceMinor)
        assertEquals(SubscriptionStatus.ACTIVE, saved.status)
        ui.activityRule.scenario.recreate()
        ui.onNodeWithText("Subscription details").assertIsDisplayed()
    }

    @Test
    fun navigationSearchAndAppearancePersist() {
        seed(BudgieCollection(listOf(Subscription(name = "Netflix", priceMinor = 10000, anchorDate = LocalDate.now()), Subscription(name = "Spotify", priceMinor = 20000, anchorDate = LocalDate.now().plusDays(2))), Preferences(onboarded = true)))
        ui.onNodeWithText("Subscriptions", useUnmergedTree = true).performClick()
        ui.onNodeWithText("Search subscriptions").performScrollTo().performTextInput("Spotify")
        ui.onNode(hasText("Spotify") and !hasSetTextAction()).performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Calendar", useUnmergedTree = true).performClick()
        ui.onNodeWithText("A date with your dues.").assertIsDisplayed()
        ui.onNodeWithText("Insights", useUnmergedTree = true).performClick()
        ui.onNodeWithText("Settings", useUnmergedTree = true).performClick()
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Appearance"))
        ui.onNodeWithText("Appearance").performScrollTo().performClick()
        ui.onNodeWithText("After hours").performClick()
        ui.waitUntil(5_000) {
            app.repository.state.value?.getOrNull()?.preferences?.appearance == Appearance.DARK
        }
        ui.onNodeWithText("Home", useUnmergedTree = true).performClick()
        ui.onNodeWithContentDescription("Open reminders").performClick()
        ui.onNodeWithText("Netflix").performScrollTo().performClick()
        ui.onNodeWithContentDescription("Back").performClick()
        ui.onNodeWithContentDescription("Back").performClick()
        ui.onNodeWithText("Good things.\nOn repeat.").assertIsDisplayed()
    }

    @Test
    fun enabledReminderPostsAndOpensTheSubscription() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val launchedActivity = ui.activity
        val originalIntent = launchedActivity.intent
        if (android.os.Build.VERSION.SDK_INT >= 33)
            instrumentation.uiAutomation
                .executeShellCommand(
                    "pm grant com.vabxsen.budgie android.permission.POST_NOTIFICATIONS"
                )
                .close()
        val sub =
            Subscription(
                name = "Reminder check",
                priceMinor = 4500,
                anchorDate = LocalDate.now(),
                reminderDays = 0,
            )
        seed(BudgieCollection(listOf(sub), Preferences(onboarded = true, notifications = true)))
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        ReminderScheduler.checkNow(app)
        ui.waitUntil(20_000) { manager.activeNotifications.any { it.tag == "budgie:${sub.id}" } }
        val notification =
            manager.activeNotifications.first { it.tag == "budgie:${sub.id}" }.notification
        assertEquals("Reminder check renews today", notification.extras.getString("android.title"))
        notification.contentIntent.send()
        ui.waitForIdle()
        ui.onNodeWithText("Subscription details").assertIsDisplayed()
        ui.onNodeWithText("Reminder check").assertIsDisplayed()
        runBlocking {
            app.repository.update {
                it.copy(preferences = it.preferences.copy(notifications = false))
            }
        }
        ReminderScheduler.checkNow(app)
        ui.waitUntil(20_000) { manager.activeNotifications.isEmpty() }
        // ActivityScenario matches lifecycle events against its original launch intent.
        instrumentation.runOnMainSync {
            launchedActivity.intent = originalIntent
        }
    }

    private fun waitForSaveMessage() {
        ui.waitUntil(12_000) {
            ui.onAllNodesWithText("Test service saved. All in a good place.")
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }
}
