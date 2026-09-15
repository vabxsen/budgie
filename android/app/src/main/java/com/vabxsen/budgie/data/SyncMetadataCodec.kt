package com.vabxsen.budgie.data

import org.json.JSONObject

internal data class SyncMetadata(
    val subscriptionTimes: MutableMap<String, Long> = mutableMapOf(),
    val deletedTimes: MutableMap<String, Long> = mutableMapOf(),
    var preferencesTime: Long = 0,
    /** Signed-out data was copied into this account and waits for the cloud copy before it syncs. */
    var pendingClaim: Boolean = false,
)

internal object SyncMetadataCodec {
    fun encode(value: SyncMetadata): String =
        JSONObject().apply {
            put("version", 1)
            put("preferencesTime", value.preferencesTime)
            put("subscriptionTimes", JSONObject(value.subscriptionTimes as Map<*, *>))
            put("deletedTimes", JSONObject(value.deletedTimes as Map<*, *>))
            put("pendingClaim", value.pendingClaim)
        }.toString()

    fun decode(text: String): SyncMetadata {
        val json = JSONObject(text)
        require(json.getInt("version") == 1)
        return SyncMetadata(
            json.getJSONObject("subscriptionTimes").toLongMap(),
            json.getJSONObject("deletedTimes").toLongMap(),
            json.getLong("preferencesTime"),
            json.optBoolean("pendingClaim", false),
        )
    }

    private fun JSONObject.toLongMap(): MutableMap<String, Long> =
        keys().asSequence().associateWith { getLong(it) }.toMutableMap()
}
