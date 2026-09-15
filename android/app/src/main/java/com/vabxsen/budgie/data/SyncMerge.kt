package com.vabxsen.budgie.data

import com.vabxsen.budgie.domain.Preferences
import com.vabxsen.budgie.domain.Subscription

/** A document to write to the cloud: a live subscription, or a deletion when [subscription] is null. */
internal data class SyncUpload(val id: String, val subscription: Subscription?, val updatedAtMillis: Long)

internal data class SubscriptionMerge(val subscriptions: List<Subscription>, val uploads: List<SyncUpload>)

/** [preferences] replaces the local value when set; [uploadAtMillis] asks to upload the local value. */
internal data class PreferencesMerge(val preferences: Preferences?, val uploadAtMillis: Long?)

/** Reminder consent belongs to each device, so it never takes part in sync decisions. */
internal fun Preferences.syncedFields(): Preferences = copy(notifications = false)

/**
 * Last-write-wins reconciliation between this device and a cloud snapshot; updates [SyncMetadata]
 * in place. Uploads wait for a server snapshot, because a cache snapshot can be missing or behind
 * documents that already exist in the cloud.
 */
internal object SyncMerge {
    fun subscriptions(
        local: List<Subscription>,
        remote: Map<String, RemoteSubscription>,
        metadata: SyncMetadata,
        fromCache: Boolean,
        nextTimestamp: () -> Long,
    ): SubscriptionMerge? {
        if (metadata.pendingClaim && fromCache) return null
        val merged = LinkedHashMap<String, Subscription>()
        local.forEach { merged[it.id] = it }
        if (metadata.pendingClaim) {
            // A signed-out entry the account already has is the same subscription entered twice.
            val cloud = remote.values.mapNotNull { it.subscription }
            local
                .filter { it.id !in remote && cloud.any { saved -> saved.isSameServiceAs(it) } }
                .forEach {
                    merged.remove(it.id)
                    metadata.subscriptionTimes.remove(it.id)
                }
            metadata.pendingClaim = false
        }
        val uploads = LinkedHashMap<String, SyncUpload>()
        remote.forEach { (id, cloud) ->
            val localTime = maxOf(metadata.subscriptionTimes[id] ?: 0, metadata.deletedTimes[id] ?: 0)
            if (cloud.updatedAtMillis > localTime) {
                if (cloud.subscription == null) {
                    merged.remove(id)
                    metadata.subscriptionTimes.remove(id)
                    metadata.deletedTimes[id] = cloud.updatedAtMillis
                } else {
                    merged[id] = cloud.subscription
                    metadata.deletedTimes.remove(id)
                    metadata.subscriptionTimes[id] = cloud.updatedAtMillis
                }
            } else if (localTime > cloud.updatedAtMillis && !fromCache) {
                uploads[id] = SyncUpload(id, merged[id], localTime)
            }
        }
        if (!fromCache) {
            merged.values.filter { it.id !in remote }.forEach {
                uploads[it.id] = SyncUpload(it.id, it, metadata.subscriptionTimes.getOrPut(it.id, nextTimestamp))
            }
            metadata.deletedTimes.filterKeys { it !in remote }.forEach { (id, time) ->
                uploads[id] = SyncUpload(id, null, time)
            }
        }
        return SubscriptionMerge(merged.values.toList(), uploads.values.toList())
    }

    fun preferences(
        local: Preferences,
        remote: Pair<Preferences, Long>?,
        metadata: SyncMetadata,
        fromCache: Boolean,
        nextTimestamp: () -> Long,
    ): PreferencesMerge {
        if (fromCache && (remote == null || metadata.preferencesTime > remote.second)) {
            return PreferencesMerge(null, null)
        }
        if (remote == null) {
            if (metadata.preferencesTime == 0L) metadata.preferencesTime = nextTimestamp()
            return PreferencesMerge(null, metadata.preferencesTime)
        }
        val (cloud, cloudTime) = remote
        return when {
            cloudTime > metadata.preferencesTime -> {
                metadata.preferencesTime = cloudTime
                PreferencesMerge(cloud.copy(notifications = local.notifications), null)
            }
            metadata.preferencesTime > cloudTime -> PreferencesMerge(null, metadata.preferencesTime)
            else -> PreferencesMerge(null, null)
        }
    }

    private fun Subscription.isSameServiceAs(other: Subscription) =
        name.trim().equals(other.name.trim(), ignoreCase = true) &&
            cycle == other.cycle &&
            priceMinor == other.priceMinor
}
