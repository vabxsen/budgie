package com.vabxsen.budgie.notifications

import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietHoursTest {
    private val token = "2026-09-16|1"

    @Test
    fun remindersStayQuietOvernightOnly() {
        listOf("22:00", "23:59", "00:00", "06:59").forEach {
            assertTrue(it, isQuietHours(LocalTime.parse(it)))
        }
        listOf("07:00", "12:00", "21:59").forEach {
            assertFalse(it, isQuietHours(LocalTime.parse(it)))
        }
    }

    @Test
    fun quietHoursEndAtTheNextSevenAm() {
        assertEquals(LocalDateTime.parse("2026-09-16T07:00"), quietHoursEnd(LocalDateTime.parse("2026-09-15T23:30")))
        assertEquals(LocalDateTime.parse("2026-09-15T07:00"), quietHoursEnd(LocalDateTime.parse("2026-09-15T02:00")))
        assertEquals(LocalDateTime.parse("2026-09-16T07:00"), quietHoursEnd(LocalDateTime.parse("2026-09-15T07:00")))
    }

    @Test
    fun overnightReminderSoundsOnceAfterQuietHoursWhileUnread() {
        val night = reminderDelivery(null, token, showing = false, quiet = true)
        assertEquals(ReminderDelivery(post = true, silent = true, ledgerToken = "$token|quiet"), night)

        val morning = reminderDelivery(night.ledgerToken, token, showing = true, quiet = false)
        assertEquals(ReminderDelivery(post = true, alertAgain = true, ledgerToken = token), morning)

        val refresh = reminderDelivery(morning.ledgerToken, token, showing = true, quiet = false)
        assertEquals(ReminderDelivery(post = true, ledgerToken = token), refresh)
    }

    @Test
    fun dismissedRemindersAreNeverPostedAgain() {
        val dismissedAfterNight = reminderDelivery("$token|quiet", token, showing = false, quiet = false)
        assertFalse(dismissedAfterNight.post)
        assertEquals(token, dismissedAfterNight.ledgerToken)
        assertFalse(reminderDelivery(token, token, showing = false, quiet = true).post)
    }

    @Test
    fun daytimeRemindersForANewRenewalAlertNormally() {
        assertEquals(
            ReminderDelivery(post = true, ledgerToken = token),
            reminderDelivery("2026-08-16|1", token, showing = false, quiet = false),
        )
    }
}
