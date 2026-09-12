package com.vabxsen.budgie.data

import com.vabxsen.budgie.domain.*
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

object CollectionCodec {
    const val MAX_BACKUP_BYTES = 5 * 1024 * 1024

    fun encode(collection: BudgieCollection): String =
        JSONObject()
            .apply {
                put("format", "budgie-android")
                put("version", 1)
                put(
                    "preferences",
                    JSONObject().apply {
                        put("budgetMinor", collection.preferences.budgetMinor)
                        put("reminderDays", collection.preferences.reminderDays)
                        put("notifications", collection.preferences.notifications)
                        put("appearance", collection.preferences.appearance.name)
                        put("onboarded", collection.preferences.onboarded)
                    },
                )
                put(
                    "subscriptions",
                    JSONArray().apply {
                        collection.subscriptions.forEach { s ->
                            put(
                                JSONObject().apply {
                                    put("id", s.id)
                                    put("name", s.name)
                                    put("plan", s.plan)
                                    put("priceMinor", s.priceMinor)
                                    put("cycle", s.cycle.name)
                                    put("anchorDate", s.anchorDate.toString())
                                    put("category", s.category.name)
                                    put("brand", s.brand)
                                    put("status", s.status.name)
                                    put("reminderDays", s.reminderDays)
                                    put("notes", s.notes)
                                    put("createdDate", s.createdDate.toString())
                                }
                            )
                        }
                    },
                )
            }
            .toString(2)

    fun decode(text: String): BudgieCollection {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) {
            "Backup exceeds the 5 MB limit."
        }
        val json = JSONObject(text)
        require(json.getString("format") == "budgie-android" && json.getInt("version") == 1) {
            "This is not a supported Budgie Android backup."
        }
        val settings = json.getJSONObject("preferences")
        val prefs =
            Preferences(
                settings.getLong("budgetMinor"),
                settings.getInt("reminderDays"),
                settings.getBoolean("notifications"),
                Appearance.valueOf(settings.getString("appearance")),
                settings.getBoolean("onboarded"),
            )
        require(prefs.budgetMinor in 100..1_000_000_000L && prefs.reminderDays in 0..30)
        val array = json.getJSONArray("subscriptions")
        require(array.length() <= 5000) { "This backup contains too many subscriptions." }
        val subscriptions =
            (0 until array.length()).map { i ->
                array.getJSONObject(i).let { s ->
                    Subscription(
                        id = s.getString("id"),
                        name = s.getString("name"),
                        plan = s.optString("plan"),
                        priceMinor = s.getLong("priceMinor"),
                        cycle = BillingCycle.valueOf(s.getString("cycle")),
                        anchorDate = LocalDate.parse(s.getString("anchorDate")),
                        category = Category.valueOf(s.getString("category")),
                        brand = s.optString("brand", "custom"),
                        status = SubscriptionStatus.valueOf(s.getString("status")),
                        reminderDays = s.getInt("reminderDays"),
                        notes = s.optString("notes"),
                        createdDate = LocalDate.parse(s.getString("createdDate")),
                    )
                }
            }
        require(subscriptions.map { it.id }.distinct().size == subscriptions.size) {
            "Duplicate subscription identifiers."
        }
        require(
            subscriptions.all {
                it.id.isNotBlank() &&
                    it.id.length <= 100 &&
                    it.anchorDate.year in 1900..2200 &&
                    it.createdDate.year in 1900..2200
            }
        )
        return BudgieCollection(subscriptions, prefs)
    }

    fun csv(collection: BudgieCollection): String {
        fun cell(value: String): String {
            // Prevent spreadsheet formula execution when a user-controlled name is exported.
            val safe =
                if (value.firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r')) "'$value"
                else value
            return "\"${safe.replace("\"","\"\"")}\""
        }
        return "Service,Plan,Amount,Currency,Billing cycle,Billing anchor,Next renewal,Category,Status,Notes\r\n" +
            collection.subscriptions.joinToString("\r\n") { s ->
                listOf(
                        s.name,
                        s.plan,
                        s.priceMinor.toBigDecimal().movePointLeft(2).toPlainString(),
                        "INR",
                        s.cycle.label,
                        s.anchorDate.toString(),
                        s.nextRenewal().toString(),
                        s.category.label,
                        s.status.label,
                        s.notes,
                    )
                    .joinToString(",") { cell(it) }
            }
    }
}
