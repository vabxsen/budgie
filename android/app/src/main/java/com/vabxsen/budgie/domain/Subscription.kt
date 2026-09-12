package com.vabxsen.budgie.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.Locale
import java.util.UUID

enum class BillingCycle(val label: String, val suffix: String) {
    MONTHLY("Monthly", "month"),
    YEARLY("Yearly", "year"),
    WEEKLY("Weekly", "week"),
}

enum class SubscriptionStatus(val label: String) {
    ACTIVE("Active"),
    TRIAL("Free trial"),
    ARCHIVED("Archived"),
}

enum class Category(val label: String) {
    ENTERTAINMENT("Entertainment"),
    PRODUCTIVITY("Productivity"),
    MUSIC("Music"),
    STORAGE("Storage"),
    OTHER("Other"),
}

enum class Appearance(val label: String) {
    SYSTEM("Use device setting"),
    LIGHT("Daylight"),
    DARK("After hours"),
}

/** Prices are integer minor units; the billing anchor never drifts at month ends. */
data class Subscription(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val plan: String = "",
    val priceMinor: Long,
    val cycle: BillingCycle = BillingCycle.MONTHLY,
    val anchorDate: LocalDate,
    val category: Category = Category.OTHER,
    val brand: String = "custom",
    val status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
    val reminderDays: Int = 3,
    val notes: String = "",
    val createdDate: LocalDate = LocalDate.now(),
) {
    init {
        require(name.isNotBlank() && name.length <= 80)
        require(priceMinor in 1..1_000_000_000L)
        require(reminderDays in 0..30)
        require(plan.length <= 120 && notes.length <= 2000)
    }

    fun monthlyMinor(): BigDecimal =
        when (cycle) {
            BillingCycle.MONTHLY -> priceMinor.toBigDecimal()
            BillingCycle.YEARLY ->
                priceMinor.toBigDecimal().divide(12.toBigDecimal(), 8, RoundingMode.HALF_UP)
            BillingCycle.WEEKLY ->
                priceMinor
                    .toBigDecimal()
                    .multiply(52.toBigDecimal())
                    .divide(12.toBigDecimal(), 8, RoundingMode.HALF_UP)
        }

    fun occurrence(index: Long): LocalDate =
        when (cycle) {
            BillingCycle.MONTHLY -> anchorDate.plusMonths(index)
            BillingCycle.YEARLY -> anchorDate.plusYears(index)
            BillingCycle.WEEKLY -> anchorDate.plusWeeks(index)
        }

    fun nextRenewal(onOrAfter: LocalDate = LocalDate.now()): LocalDate {
        if (onOrAfter <= anchorDate) return anchorDate
        var index =
            when (cycle) {
                BillingCycle.MONTHLY ->
                    ChronoUnit.MONTHS.between(YearMonth.from(anchorDate), YearMonth.from(onOrAfter))
                BillingCycle.YEARLY -> (onOrAfter.year - anchorDate.year).toLong()
                BillingCycle.WEEKLY -> ChronoUnit.DAYS.between(anchorDate, onOrAfter) / 7
            }.coerceAtLeast(0)
        while (occurrence(index) < onOrAfter) index++
        return occurrence(index)
    }

    fun renewalsIn(month: YearMonth): List<LocalDate> {
        if (status == SubscriptionStatus.ARCHIVED) return emptyList()
        val result = mutableListOf<LocalDate>()
        var date = nextRenewal(month.atDay(1))
        while (date <= month.atEndOfMonth()) {
            result += date
            date = nextRenewal(date.plusDays(1))
        }
        return result
    }
}

data class Preferences(
    val budgetMinor: Long = 0,
    val reminderDays: Int = 3,
    val notifications: Boolean = false,
    val appearance: Appearance = Appearance.SYSTEM,
    val onboarded: Boolean = false,
)

data class BudgieCollection(
    val subscriptions: List<Subscription> = emptyList(),
    val preferences: Preferences = Preferences(),
)

/** Trial expiry is a forecast of active billing, not confirmation of a charge. */
fun BudgieCollection.onDate(today: LocalDate): BudgieCollection =
    copy(
        subscriptions =
            subscriptions.map {
                if (it.status == SubscriptionStatus.TRIAL && it.anchorDate < today)
                    it.copy(status = SubscriptionStatus.ACTIVE)
                else it
            }
    )

fun List<Subscription>.monthlyTotal(): BigDecimal = filter {
    it.status == SubscriptionStatus.ACTIVE
}
    .fold(BigDecimal.ZERO) { total, s -> total + s.monthlyMinor() }

fun money(minor: Long): String = money(minor.toBigDecimal())

fun money(minor: BigDecimal): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"))
        .apply {
            currency = Currency.getInstance("INR")
            minimumFractionDigits =
                if (minor.remainder(100.toBigDecimal()).compareTo(BigDecimal.ZERO) == 0) 0 else 2
            maximumFractionDigits = 2
        }
        .format(minor.divide(100.toBigDecimal(), 2, RoundingMode.HALF_UP))

fun parseAmount(text: String): Long? =
    try {
        text.trim().toBigDecimal().multiply(100.toBigDecimal()).longValueExact().takeIf {
            it in 1..1_000_000_000L
        }
    } catch (_: Exception) {
        null
    }

object ServiceCatalog {
    data class Service(val name: String, val brand: String, val category: Category)

    val services =
        listOf(
            Service("Netflix", "netflix", Category.ENTERTAINMENT),
            Service("Spotify", "spotify", Category.MUSIC),
            Service("YouTube", "youtube", Category.ENTERTAINMENT),
            Service("Notion", "notion", Category.PRODUCTIVITY),
            Service("Figma", "figma", Category.PRODUCTIVITY),
            Service("Google One", "google", Category.STORAGE),
            Service("Prime Video", "prime", Category.ENTERTAINMENT),
            Service("iCloud+", "icloud", Category.STORAGE),
        )

}
