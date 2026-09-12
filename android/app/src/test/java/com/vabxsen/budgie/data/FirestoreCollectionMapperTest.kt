package com.vabxsen.budgie.data

import com.google.firebase.Timestamp
import com.vabxsen.budgie.domain.Appearance
import com.vabxsen.budgie.domain.BillingCycle
import com.vabxsen.budgie.domain.Category
import com.vabxsen.budgie.domain.Preferences
import com.vabxsen.budgie.domain.SubscriptionStatus
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FirestoreCollectionMapperTest {
    @Test
    fun remoteSubscriptionRestoresEveryUserField() {
        val anchor = LocalDate.of(2026, 9, 24)
        val created = LocalDate.of(2026, 3, 2)
        val remote =
            remoteSubscription(
                "subscription-1",
                mapOf(
                    "schemaVersion" to 1L,
                    "deleted" to false,
                    "name" to "Figma",
                    "plan" to "Professional",
                    "priceMinor" to 125000L,
                    "cycle" to "YEARLY",
                    "anchorDate" to anchor.timestamp(),
                    "category" to "PRODUCTIVITY",
                    "brand" to "figma",
                    "status" to "ACTIVE",
                    "reminderDays" to 5L,
                    "notes" to "Design renewal",
                    "createdDate" to created.timestamp(),
                    "updatedAtMillis" to 1234L,
                ),
            )

        with(remote.subscription!!) {
            assertEquals("subscription-1", id)
            assertEquals("Figma", name)
            assertEquals("Professional", plan)
            assertEquals(125000L, priceMinor)
            assertEquals(BillingCycle.YEARLY, cycle)
            assertEquals(anchor, anchorDate)
            assertEquals(Category.PRODUCTIVITY, category)
            assertEquals("figma", brand)
            assertEquals(SubscriptionStatus.ACTIVE, status)
            assertEquals(5, reminderDays)
            assertEquals("Design renewal", notes)
            assertEquals(created, createdDate)
        }
        assertEquals(1234L, remote.updatedAtMillis)
    }

    @Test
    fun tombstoneDoesNotRecreateDeletedSubscription() {
        val remote =
            remoteSubscription(
                "deleted-id",
                mapOf("schemaVersion" to 1L, "deleted" to true, "updatedAtMillis" to 88L),
            )
        assertNull(remote.subscription)
        assertEquals(88L, remote.updatedAtMillis)
    }

    @Test
    fun preferencesRoundTripCloudValues() {
        val expected =
            Preferences(
                budgetMinor = 500000,
                reminderDays = 7,
                notifications = true,
                appearance = Appearance.DARK,
                onboarded = true,
            )
        val (actual, timestamp) =
            remotePreferences(
                mapOf(
                    "schemaVersion" to 1L,
                    "budgetMinor" to 500000L,
                    "reminderDays" to 7L,
                    "notifications" to true,
                    "appearance" to "DARK",
                    "onboarded" to true,
                    "updatedAtMillis" to 991L,
                )
            )
        assertEquals(expected, actual)
        assertEquals(991L, timestamp)
    }

    @Test
    fun syncMetadataRoundTripsConflictAndDeletionTimes() {
        val expected =
            SyncMetadata(
                subscriptionTimes = mutableMapOf("live" to 10L),
                deletedTimes = mutableMapOf("gone" to 20L),
                preferencesTime = 30L,
            )
        assertEquals(expected, SyncMetadataCodec.decode(SyncMetadataCodec.encode(expected)))
    }

    private fun LocalDate.timestamp() =
        Timestamp(Date.from(atStartOfDay().toInstant(ZoneOffset.UTC)))
}
