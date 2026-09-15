package com.vabxsen.budgie.data

enum class SyncStatus { SIGNED_OUT, CONNECTING, SYNCING, SYNCED, OFFLINE, ERROR }

data class SyncState(
    val status: SyncStatus = SyncStatus.SIGNED_OUT,
    val lastSyncedAtMillis: Long? = null,
    val message: String? = null,
)
