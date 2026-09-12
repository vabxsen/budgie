package com.vabxsen.budgie.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.vabxsen.budgie.domain.Appearance
import com.vabxsen.budgie.domain.BillingCycle
import com.vabxsen.budgie.domain.Category
import com.vabxsen.budgie.domain.Preferences
import com.vabxsen.budgie.domain.Subscription
import com.vabxsen.budgie.domain.SubscriptionStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date

internal data class RemoteSubscription(
    val subscription: Subscription?,
    val updatedAtMillis: Long,
)

internal fun Subscription.toFirestore(updatedAtMillis: Long): Map<String, Any> =
    mapOf(
        "schemaVersion" to 1L,
        "deleted" to false,
        "name" to name,
        "plan" to plan,
        "priceMinor" to priceMinor,
        "cycle" to cycle.name,
        "anchorDate" to anchorDate.toTimestamp(),
        "category" to category.name,
        "brand" to brand,
        "status" to status.name,
        "reminderDays" to reminderDays.toLong(),
        "notes" to notes,
        "createdDate" to createdDate.toTimestamp(),
        "updatedAtMillis" to updatedAtMillis,
        "updatedAt" to FieldValue.serverTimestamp(),
    )

internal fun deletedSubscription(updatedAtMillis: Long): Map<String, Any> =
    mapOf(
        "schemaVersion" to 1L,
        "deleted" to true,
        "updatedAtMillis" to updatedAtMillis,
        "updatedAt" to FieldValue.serverTimestamp(),
    )

internal fun remoteSubscription(id: String, data: Map<String, Any>): RemoteSubscription {
    val updated = data.long("updatedAtMillis")
    if (data["deleted"] == true) return RemoteSubscription(null, updated)
    return RemoteSubscription(
        Subscription(
            id = id,
            name = data.string("name"),
            plan = data.string("plan"),
            priceMinor = data.long("priceMinor"),
            cycle = BillingCycle.valueOf(data.string("cycle")),
            anchorDate = data.date("anchorDate"),
            category = Category.valueOf(data.string("category")),
            brand = data.string("brand"),
            status = SubscriptionStatus.valueOf(data.string("status")),
            reminderDays = data.long("reminderDays").toInt(),
            notes = data.string("notes"),
            createdDate = data.date("createdDate"),
        ),
        updated,
    )
}

internal fun Preferences.toFirestore(updatedAtMillis: Long): Map<String, Any> =
    mapOf(
        "schemaVersion" to 1L,
        "budgetMinor" to budgetMinor,
        "reminderDays" to reminderDays.toLong(),
        "notifications" to notifications,
        "appearance" to appearance.name,
        "onboarded" to onboarded,
        "updatedAtMillis" to updatedAtMillis,
        "updatedAt" to FieldValue.serverTimestamp(),
    )

internal fun remotePreferences(data: Map<String, Any>): Pair<Preferences, Long> =
    Preferences(
        budgetMinor = data.long("budgetMinor"),
        reminderDays = data.long("reminderDays").toInt(),
        notifications = data.bool("notifications"),
        appearance = Appearance.valueOf(data.string("appearance")),
        onboarded = data.bool("onboarded"),
    ) to data.long("updatedAtMillis")

private fun LocalDate.toTimestamp(): Timestamp =
    Timestamp(Date.from(atStartOfDay().toInstant(ZoneOffset.UTC)))

private fun Map<String, Any>.date(key: String): LocalDate {
    val timestamp = get(key) as? Timestamp ?: error("$key must be a timestamp")
    return Instant.ofEpochSecond(timestamp.seconds, timestamp.nanoseconds.toLong())
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
}

private fun Map<String, Any>.string(key: String) = get(key) as? String ?: error("$key must be text")
private fun Map<String, Any>.long(key: String) = (get(key) as? Number)?.toLong() ?: error("$key must be a number")
private fun Map<String, Any>.bool(key: String) = get(key) as? Boolean ?: error("$key must be true or false")
