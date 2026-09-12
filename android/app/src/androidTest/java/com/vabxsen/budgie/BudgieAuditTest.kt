package com.vabxsen.budgie

import android.app.NotificationManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vabxsen.budgie.domain.*
import com.vabxsen.budgie.notifications.ReminderScheduler
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BudgieAuditTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val app get() = ui.activity.application as BudgieApplication
    private val today = LocalDate.now()
    private fun collection() = app.repository.state.value!!.getOrThrow()
    private fun seed(vararg entries: Subscription) {
        ui.waitUntil(10_000) { app.repository.state.value != null }
        runBlocking { app.repository.update { BudgieCollection(entries.toList(), Preferences(onboarded = true)) } }
        ui.waitForIdle()
    }
    private fun reveal(text: String): SemanticsNodeInteraction { ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text)); return ui.onAllNodesWithText(text)[0] }
    private fun click(text: String) = reveal(text).performScrollTo().performClick()
    private fun tab(text: String) = ui.onNodeWithText(text, useUnmergedTree = true).performClick()
    private fun editor(text: String) {
        ui.waitUntil(5_000) {
            ui.onAllNodesWithTag("subscription-editor").fetchSemanticsNodes().size == 1
        }
        ui.onNodeWithTag("subscription-editor").performScrollToNode(hasText(text))
    }
    private fun input(label: String, value: String) {
        editor(label)
        ui.onNodeWithText(label).performTextReplacement(value)
    }
    private fun sample(name: String, status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
                       category: Category = Category.OTHER, offset: Long = 0) =
        Subscription(name = name, plan = "Membership", priceMinor = 12345, anchorDate = today.plusDays(offset),
            category = category, status = status)

    @Test fun budgetStartsUnsetValidatesPersistsAndCanBeRemoved() {
        seed()
        assertEquals(0L, collection().preferences.budgetMinor)
        click("Set monthly budget")
        ui.onNodeWithText("Save budget").performClick()
        ui.onNodeWithText("Enter a valid budget of at least ₹1.").assertIsDisplayed()
        ui.onNodeWithText("Monthly budget (₹)").performTextInput("1234.56")
        ui.onNodeWithText("Save budget").performClick()
        ui.waitUntil(5_000) { collection().preferences.budgetMinor == 123456L }
        tab("Settings")
        click("Monthly budget")
        ui.onNodeWithText("Monthly budget (₹)").performTextReplacement("9")
        ui.onNodeWithText("Cancel").performClick()
        assertEquals(123456L, collection().preferences.budgetMinor)
        click("Monthly budget")
        ui.onNodeWithText("Remove budget").performClick()
        ui.waitUntil(5_000) { collection().preferences.budgetMinor == 0L }
        ui.activityRule.scenario.recreate()
        ui.onNodeWithText("Not set").performScrollTo().assertIsDisplayed()
    }

    @Test fun editorValidationTemplatesFieldsAndDiscardPreserveRealInput() {
        seed()
        ui.onAllNodesWithText("Add subscription")[0].performClick()
        editor("Save subscription")
        ui.onNodeWithText("Save subscription").performClick()
        assertTrue(collection().subscriptions.isEmpty())
        ui.onNodeWithText("Add a service name and a valid amount with up to two decimal places.")
            .performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("subscription-editor").performScrollToIndex(1)
        ui.onNodeWithContentDescription("Choose Netflix").performClick()
        editor("Service name")
        ui.onNodeWithText("Service name").assertTextContains("Netflix")
        input("Service name", "My course")
        input("Plan name (optional)", "Annual membership")
        input("Amount (₹)", "123.456")
        editor("Save subscription")
        ui.onNodeWithText("Save subscription").performClick()
        assertTrue(collection().subscriptions.isEmpty())
        input("Amount (₹)", "321.09")
        editor("Yearly")
        ui.onNodeWithText("Yearly").performClick()
        editor("Remind me")
        ui.onNodeWithText("On the day").performClick()
        editor("Currently on a free trial?")
        ui.onNodeWithContentDescription("Free trial").performClick()
        input("A little note (optional)", "Entered by me")
        ui.onNodeWithContentDescription("Back").performClick()
        ui.onNodeWithText("Keep editing").performClick()
        editor("Save subscription")
        ui.onNodeWithText("Save subscription").performClick()
        ui.waitUntil(5_000) { collection().subscriptions.size == 1 }
        val sub = collection().subscriptions.single()
        assertEquals("My course", sub.name)
        assertEquals("Annual membership", sub.plan)
        assertEquals(32109L, sub.priceMinor)
        assertEquals(BillingCycle.YEARLY, sub.cycle)
        assertEquals(SubscriptionStatus.TRIAL, sub.status)
        assertEquals("custom", sub.brand)
        assertEquals(0, sub.reminderDays)
        assertEquals("Entered by me", sub.notes)
        ui.waitUntil(12_000) {
            ui.onAllNodesWithText("My course saved. All in a good place.").fetchSemanticsNodes().isEmpty()
        }
        click("Edit subscription")
        input("Service name", "Discarded name")
        ui.onNodeWithContentDescription("Back").performClick()
        ui.onNodeWithText("Discard changes").performClick()
        assertEquals("My course", collection().subscriptions.single().name)
    }

    @Test fun collectionFiltersSearchAndBothLayoutsOpenCorrectRecord() {
        seed(sample("Music plan", category = Category.MUSIC),
            sample("Trial plan", SubscriptionStatus.TRIAL), sample("Old plan", SubscriptionStatus.ARCHIVED))
        tab("Subscriptions")
        click("Free trials")
        ui.onNodeWithText("Trial plan").performScrollTo().assertIsDisplayed()
        click("Archived")
        ui.onNodeWithText("Old plan").performScrollTo().assertIsDisplayed()
        click("All")
        ui.onNodeWithText("Search subscriptions").performScrollTo().performTextInput("no-match")
        ui.onNodeWithText("No matches this time.").performScrollTo().assertIsDisplayed()
        ui.onNodeWithContentDescription("Clear search").performScrollTo().performClick()
        click("Music")
        ui.onNodeWithText("Music plan").performScrollTo().assertIsDisplayed()
        ui.onNodeWithContentDescription("Switch to list").performScrollTo().performClick()
        ui.onNodeWithContentDescription("Switch to grid").assertExists()
        click("Music plan")
        ui.onNodeWithText("Subscription details").assertIsDisplayed()
        ui.onNodeWithText("Music plan").assertIsDisplayed()
    }

    @Test fun calendarMonthAndDayFiltersAndReturnNavigation() {
        seed(sample("Due today"), sample("Archived today", SubscriptionStatus.ARCHIVED))
        tab("Calendar")
        ui.onNodeWithContentDescription("Next month").performClick()
        val fmt = DateTimeFormatter.ofPattern("MMMM yyyy")
        ui.onNodeWithText(YearMonth.from(today).plusMonths(1).format(fmt)).assertIsDisplayed()
        ui.onNodeWithContentDescription("Previous month").performClick()
        click("Back to today")
        click("Due today")
        ui.onNodeWithContentDescription("Back").performClick()
        ui.onNodeWithText("A date with your dues.").assertIsDisplayed()
        ui.onNodeWithContentDescription("$today, 1 payments").performScrollTo().performClick()
        click("Show all")
        ui.onNodeWithText("1 renewals this month").performScrollTo().assertIsDisplayed()
        ui.onNodeWithContentDescription("Open reminders").performClick()
        ui.onNodeWithContentDescription("Back").performClick()
        ui.onNodeWithText("A date with your dues.").assertIsDisplayed()
    }

    @Test fun homeAndInsightsUseEnteredAmountsAndTrialsAppearOnHome() {
        seed(sample("Only mine", SubscriptionStatus.TRIAL, offset = 2))
        reveal("Only mine").assertIsDisplayed()
        tab("Insights")
        ui.onNodeWithText("Your bigger picture starts here.").performScrollTo().assertIsDisplayed()
        seed(sample("Only mine"))
        ui.onNodeWithText("Monthly").performScrollTo().performClick()
        ui.onNodeWithText(money(12345)).assertIsDisplayed()
        click("Yearly")
        ui.onNodeWithText(money(148140)).assertIsDisplayed()
        click("Only mine")
        ui.onNodeWithText("Subscription details").assertIsDisplayed()
        ui.onNodeWithContentDescription("Back").performClick()
        tab("Home")
        click("Year")
        ui.onNodeWithText("per year · current estimate").assertIsDisplayed()
        click("Month")
        ui.onNodeWithText("per month").assertIsDisplayed()
        click("View all")
        ui.onNodeWithText("Your subscriptions.").assertIsDisplayed()
    }

    @Test fun reminderFilteringSettingsAndDefaultForNewSubscription() {
        seed(sample("Active plan"), sample("Trial only", SubscriptionStatus.TRIAL))
        ui.onNodeWithContentDescription("Open reminders").performClick()
        click("Free trials")
        ui.onNodeWithText("Trial only").performScrollTo().assertIsDisplayed()
        click("Upcoming")
        ui.onNodeWithText("Active plan").performScrollTo().assertIsDisplayed()
        click("Reminder settings")
        click("Default reminder")
        ui.onNodeWithText("7 days before").performClick()
        ui.waitUntil(5_000) { collection().preferences.reminderDays == 7 }
        for (appearance in Appearance.entries) {
            click("Appearance")
            ui.onNode(hasText(appearance.label) and hasAnyAncestor(isDialog())).performClick()
            ui.waitUntil(5_000) { collection().preferences.appearance == appearance }
        }
        tab("Home")
        ui.onAllNodesWithText("Add subscription")[0].performClick()
        input("Service name", "Default reminder plan")
        input("Amount (₹)", "45")
        editor("Save subscription")
        ui.onNodeWithText("Save subscription").performClick()
        ui.waitUntil(5_000) { collection().subscriptions.size == 3 }
        assertEquals(7, collection().subscriptions.last().reminderDays)
    }

    @Test fun collidingIdsHaveSeparateNotificationsAndArchivingClearsOnlyItsReminder() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant com.vabxsen.budgie android.permission.POST_NOTIFICATIONS").close()
        assertEquals("Aa".hashCode(), "BB".hashCode())
        seed(sample("First").copy(id = "Aa"), sample("Second").copy(id = "BB"))
        runBlocking { app.repository.update { it.copy(preferences = it.preferences.copy(notifications = true)) } }
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        ReminderScheduler.checkNow(app)
        ui.waitUntil(20_000) { manager.activeNotifications.size == 2 }
        assertNotEquals(manager.activeNotifications[0].notification.contentIntent,
            manager.activeNotifications[1].notification.contentIntent)
        runBlocking { app.repository.update { c -> c.copy(subscriptions = c.subscriptions.map {
            if (it.id == "Aa") it.copy(status = SubscriptionStatus.ARCHIVED) else it.copy(priceMinor = 9900)
        }) } }
        ReminderScheduler.checkNow(app)
        ui.waitUntil(20_000) { manager.activeNotifications.size == 1 &&
            manager.activeNotifications.single().notification.extras.getString("android.text")!!.contains(money(9900)) }
        assertEquals("budgie:BB", manager.activeNotifications.single().tag)
    }
    @Test fun remainingNavigationFiltersAndArchiveCancel() {
        seed(*Category.entries.map { sample("Record ${it.name}", category = it) }.toTypedArray())
        click("See the breakdown")
        ui.onNodeWithText("Know where it goes.").assertIsDisplayed()
        tab("Home")
        click("Calendar")
        ui.onNodeWithText("A date with your dues.").assertIsDisplayed()
        tab("Subscriptions")
        click("Active")
        for (category in Category.entries) {
            reveal("All categories")
            ui.onNodeWithText(category.label).performScrollTo().performClick()
            reveal("Record ${category.name}").assertIsDisplayed()
        }
        click("All categories")
        ui.onNodeWithContentDescription("Switch to list").performScrollTo().performClick()
        ui.onNodeWithContentDescription("Switch to grid").performClick()
        ui.onNodeWithContentDescription("Switch to list").assertExists()
        click("Record ENTERTAINMENT")
        click("Archive subscription")
        ui.onNodeWithText("Keep subscription").performClick()
        assertTrue(collection().subscriptions.all { it.status == SubscriptionStatus.ACTIVE })
        click("Archive subscription")
        ui.onNodeWithText("Archive", useUnmergedTree = true).performClick()
        ui.waitUntil(5_000) { collection().subscriptions.first().status == SubscriptionStatus.ARCHIVED }
        reveal("Archived in Budgie. Excluded from spending totals and reminders.").assertIsDisplayed()
        ui.onNodeWithText("NEXT RENEWAL").assertDoesNotExist()
    }

    @Test fun everyServiceShortcutRequiresTheUsersPriceAndDialogsCanCloseUnchanged() {
        seed()
        tab("Settings")
        click("Appearance")
        ui.onNodeWithText("Done").performClick()
        assertEquals(Appearance.SYSTEM, collection().preferences.appearance)
        for (days in listOf(0, 1, 3, 7)) {
            click("Default reminder")
            val label = if (days == 0) "On renewal day" else "$days days before"
            ui.onNode(hasText(label) and hasAnyAncestor(isDialog())).performClick()
            ui.waitUntil(5_000) { collection().preferences.reminderDays == days }
        }
        click("Default reminder")
        ui.onNodeWithText("Done").performClick()
        assertEquals(7, collection().preferences.reminderDays)
        tab("Subscriptions")
        click("Add subscription")
        for (service in ServiceCatalog.services) {
            ui.onNodeWithTag("subscription-editor").performScrollToIndex(1)
            ui.onNodeWithContentDescription("Choose ${service.name}").performScrollTo().performClick()
            editor("Service name")
            ui.onNodeWithText("Service name").assertTextContains(service.name)
            editor("Amount (₹)")
            assertEquals("", ui.onNodeWithText("Amount (₹)").fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text)
        }
        ui.onNodeWithContentDescription("Back").performClick()
        ui.onNodeWithText("Discard changes").performClick()
        assertTrue(collection().subscriptions.isEmpty())
    }

}
