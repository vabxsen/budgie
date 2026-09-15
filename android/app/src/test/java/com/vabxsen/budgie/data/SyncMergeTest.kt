package com.vabxsen.budgie.data

import com.vabxsen.budgie.domain.Appearance
import com.vabxsen.budgie.domain.Preferences
import com.vabxsen.budgie.domain.Subscription
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {
    private var clock = 1_000L
    private val next = { ++clock }

    private fun sub(id: String, name: String, priceMinor: Long = 64900) =
        Subscription(
            id = id,
            name = name,
            priceMinor = priceMinor,
            anchorDate = LocalDate.of(2026, 9, 15),
            createdDate = LocalDate.of(2026, 9, 1),
        )

    @Test
    fun claimedSignedOutSettingsNeverReplaceTheCloudCopy() {
        val metadata = SyncMetadata(pendingClaim = true)
        val cloud = Preferences(budgetMinor = 500000, appearance = Appearance.DARK, onboarded = true)
        val local = Preferences(onboarded = true, notifications = true)

        val result = SyncMerge.preferences(local, cloud to 90L, metadata, false, next)

        assertEquals(cloud.copy(notifications = true), result.preferences)
        assertNull(result.uploadAtMillis)
        assertEquals(90L, metadata.preferencesTime)
    }

    @Test
    fun claimedSubscriptionsSkipOnesTheAccountAlreadyHas() {
        val metadata = SyncMetadata(pendingClaim = true)
        val remote = mapOf("cloud" to RemoteSubscription(sub("cloud", "Netflix"), 50L))
        val local = listOf(sub("guest-copy", " netflix "), sub("guest-new", "Gym"))

        val result = SyncMerge.subscriptions(local, remote, metadata, false, next)!!

        assertEquals(listOf("guest-new", "cloud"), result.subscriptions.map { it.id })
        assertEquals(listOf("guest-new"), result.uploads.map { it.id })
        assertFalse(metadata.pendingClaim)
    }

    @Test
    fun claimWaitsForTheServerInsteadOfTrustingTheCache() {
        val metadata = SyncMetadata(pendingClaim = true)
        assertNull(SyncMerge.subscriptions(listOf(sub("guest", "Gym")), emptyMap(), metadata, true, next))
        assertTrue(metadata.pendingClaim)
    }

    @Test
    fun cacheSnapshotsApplyNewerCloudChangesButNeverUpload() {
        val metadata = SyncMetadata(subscriptionTimes = mutableMapOf("a" to 10L, "b" to 30L))
        val remote =
            mapOf(
                "a" to RemoteSubscription(sub("a", "Renamed"), 20L),
                "b" to RemoteSubscription(sub("b", "Old"), 5L),
            )
        val local = listOf(sub("a", "Mine"), sub("b", "Newer"), sub("c", "Local only"))

        val result = SyncMerge.subscriptions(local, remote, metadata, true, next)!!

        assertEquals(listOf("Renamed", "Newer", "Local only"), result.subscriptions.map { it.name })
        assertTrue(result.uploads.isEmpty())
    }

    @Test
    fun serverSnapshotsUploadNewerLocalEditsAndDeletions() {
        val newer = sub("b", "Newer")
        val metadata =
            SyncMetadata(
                subscriptionTimes = mutableMapOf("b" to 30L),
                deletedTimes = mutableMapOf("gone" to 40L),
            )
        val remote =
            mapOf(
                "b" to RemoteSubscription(sub("b", "Old"), 5L),
                "gone" to RemoteSubscription(sub("gone", "Deleted here"), 35L),
            )

        val result = SyncMerge.subscriptions(listOf(newer), remote, metadata, false, next)!!

        assertEquals(listOf(newer), result.subscriptions)
        assertEquals(listOf(SyncUpload("b", newer, 30L), SyncUpload("gone", null, 40L)), result.uploads)
    }

    @Test
    fun cloudDeletionRemovesTheLocalCopy() {
        val metadata = SyncMetadata(subscriptionTimes = mutableMapOf("a" to 10L))
        val remote = mapOf("a" to RemoteSubscription(null, 20L))

        val result = SyncMerge.subscriptions(listOf(sub("a", "Mine")), remote, metadata, false, next)!!

        assertTrue(result.subscriptions.isEmpty())
        assertEquals(20L, metadata.deletedTimes["a"])
    }

    @Test
    fun reminderConsentStaysOnThisDevice() {
        val metadata = SyncMetadata(preferencesTime = 10L)
        val cloud = Preferences(notifications = true, reminderDays = 7)

        val result = SyncMerge.preferences(Preferences(notifications = false), cloud to 20L, metadata, false, next)

        assertEquals(Preferences(notifications = false, reminderDays = 7), result.preferences)
        assertEquals(Preferences(notifications = true).syncedFields(), Preferences().syncedFields())
    }

    @Test
    fun missingCloudSettingsAreCreatedOnlyAfterTheServerConfirms() {
        val metadata = SyncMetadata()
        assertEquals(PreferencesMerge(null, null), SyncMerge.preferences(Preferences(), null, metadata, true, next))

        val created = SyncMerge.preferences(Preferences(), null, metadata, false, next)

        assertEquals(1001L, created.uploadAtMillis)
        assertEquals(1001L, metadata.preferencesTime)
    }
}
