package com.vabxsen.budgie.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class SubscriptionTest {
    private fun sub(
        date: String = "2026-01-31",
        cycle: BillingCycle = BillingCycle.MONTHLY,
        minor: Long = 120000,
    ) =
        Subscription(
            name = "Test",
            priceMinor = minor,
            anchorDate = LocalDate.parse(date),
            cycle = cycle,
        )

    @Test
    fun monthlyRenewalKeepsTheAnchorAfterFebruary() {
        val sub = sub()
        assertEquals(LocalDate.of(2026, 2, 28), sub.nextRenewal(LocalDate.of(2026, 2, 1)))
        assertEquals(LocalDate.of(2026, 3, 31), sub.nextRenewal(LocalDate.of(2026, 3, 1)))
        assertEquals(LocalDate.of(2026, 3, 31), sub.nextRenewal(LocalDate.of(2026, 3, 31)))
        assertEquals(LocalDate.of(2026, 4, 30), sub.nextRenewal(LocalDate.of(2026, 4, 1)))
    }

    @Test
    fun yearlyLeapDayRecoversInLeapYear() {
        val sub = sub("2024-02-29", BillingCycle.YEARLY)
        assertEquals(LocalDate.of(2026, 2, 28), sub.nextRenewal(LocalDate.of(2026, 1, 1)))
        assertEquals(LocalDate.of(2028, 2, 29), sub.nextRenewal(LocalDate.of(2028, 1, 1)))
        assertTrue(sub.renewalsIn(YearMonth.of(2026, 3)).isEmpty())
    }

    @Test
    fun weeklyIncludesEveryChargeWithinMonth() {
        val sub = sub("2026-08-31", BillingCycle.WEEKLY)
        assertEquals(
            listOf(7, 14, 21, 28),
            sub.renewalsIn(YearMonth.of(2026, 9)).map { it.dayOfMonth },
        )
        assertTrue(sub.renewalsIn(YearMonth.of(2026, 7)).isEmpty())
    }

    @Test
    fun archivedPlansHaveNoChargesOrSpend() {
        val sub = sub().copy(status = SubscriptionStatus.ARCHIVED)
        assertTrue(sub.renewalsIn(YearMonth.of(2026, 3)).isEmpty())
        assertEquals(0, listOf(sub).monthlyTotal().compareTo(BigDecimal.ZERO))
    }

    @Test
    fun monthlyTotalsNormalizeCyclesWithoutFloatingPointError() {
        val monthly = sub(minor = 10000)
        val yearly = sub(cycle = BillingCycle.YEARLY, minor = 120000)
        val weekly = sub(cycle = BillingCycle.WEEKLY, minor = 1200)
        assertEquals(
            0,
            listOf(monthly, yearly, weekly).monthlyTotal().compareTo(BigDecimal("25200")),
        )

    }

    @Test
    fun parseAmountsRejectsOverprecisionZeroAndInvalidInput() {
        assertEquals(12345L, parseAmount("123.45"))
        assertEquals(10L, parseAmount("0.10"))
        listOf("", "0", "-1", "2.001", "NaN", "1000000000000").forEach {
            assertNull(parseAmount(it))
        }
    }

    @Test
    fun longDormantSubscriptionResolvesDirectlyToCurrentCycle() {
        val sub = sub("2000-12-31")
        assertEquals(LocalDate.of(2026, 9, 30), sub.nextRenewal(LocalDate.of(2026, 9, 12)))
    }

    @Test
    fun trialExpiryBecomesForecastActiveAndArchivedStaysArchived() {
        val trial = sub("2026-09-12").copy(status = SubscriptionStatus.TRIAL)
        val data =
            BudgieCollection(
                listOf(trial, trial.copy(id = "archived", status = SubscriptionStatus.ARCHIVED))
            )
        assertEquals(
            SubscriptionStatus.TRIAL,
            data.onDate(LocalDate.of(2026, 9, 12)).subscriptions.first().status,
        )
        val after = data.onDate(LocalDate.of(2026, 9, 13))
        assertEquals(SubscriptionStatus.ACTIVE, after.subscriptions.first().status)
        assertEquals(SubscriptionStatus.ARCHIVED, after.subscriptions.last().status)
        assertEquals(0, after.subscriptions.monthlyTotal().compareTo(BigDecimal("120000")))
    }
}
