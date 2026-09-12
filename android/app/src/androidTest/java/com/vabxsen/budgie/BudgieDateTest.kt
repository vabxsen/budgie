package com.vabxsen.budgie

import android.view.View
import android.widget.DatePicker
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vabxsen.budgie.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matcher
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BudgieDateTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val app get() = ui.activity.application as BudgieApplication
    private fun field(text: String) {
        ui.onNodeWithTag("subscription-editor").performScrollToNode(hasText(text))
    }
    private fun chooseDate(shown: LocalDate, date: LocalDate, confirm: Boolean = true) {
        val text = shown.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))
        field(text)
        ui.onNodeWithText(text).performClick()
        onView(isAssignableFrom(DatePicker::class.java)).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(DatePicker::class.java)
            override fun getDescription() = "Choose a date in Android's native date picker"
            override fun perform(controller: UiController, view: View) {
                (view as DatePicker).updateDate(date.year, date.monthValue - 1, date.dayOfMonth)
                controller.loopMainThreadUntilIdle()
            }
        })
        onView(withText(if (confirm) android.R.string.ok else android.R.string.cancel)).perform(click())
    }

    @Test fun datePickerCancelTrialValidationAndAllCycleCategoryReminderChoices() {
        ui.waitUntil(10_000) { app.repository.state.value != null }
        runBlocking { app.repository.update { BudgieCollection(preferences = Preferences(onboarded = true)) } }
        ui.onAllNodesWithText("Add subscription")[0].performClick()
        field("Service name")
        ui.onNodeWithText("Service name").performTextReplacement("My weekly class")
        field("Amount (₹)")
        ui.onNodeWithText("Amount (₹)").performTextReplacement("543.21")
        for (cycle in BillingCycle.entries) {
            field(cycle.label)
            ui.onNodeWithText(cycle.label).performClick()
        }
        for (category in Category.entries) {
            field("Category")
            ui.onNodeWithText(category.label).performScrollTo().performClick()
        }
        for (days in listOf(0,1,3,7)) {
            field("Remind me")
            ui.onNodeWithText(if (days == 0) "On the day" else "$days days before")
                .performScrollTo().performClick()
        }
        val tomorrow = LocalDate.now().plusDays(1)
        chooseDate(tomorrow, tomorrow.plusDays(10), false)
        ui.onNodeWithText(tomorrow.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))).assertExists()
        val past = LocalDate.now().minusDays(1)
        chooseDate(tomorrow, past)
        field("Currently on a free trial?")
        ui.onNodeWithContentDescription("Free trial").performClick()
        field("Save subscription")
        ui.onNodeWithText("Save subscription").performClick()
        ui.onNodeWithText("Choose today or a future date for the trial ending.")
            .performScrollTo().assertIsDisplayed()
        assertTrue(app.repository.state.value!!.getOrThrow().subscriptions.isEmpty())
        chooseDate(past, tomorrow)
        field("Save subscription")
        ui.onNodeWithText("Save subscription").performClick()
        ui.waitUntil(5_000) { app.repository.state.value!!.getOrThrow().subscriptions.size == 1 }
        val sub = app.repository.state.value!!.getOrThrow().subscriptions.single()
        assertEquals(tomorrow, sub.anchorDate)
        assertEquals(BillingCycle.WEEKLY, sub.cycle)
        assertEquals(Category.OTHER, sub.category)
        assertEquals(7, sub.reminderDays)
        assertEquals(54321L, sub.priceMinor)
    }
}
