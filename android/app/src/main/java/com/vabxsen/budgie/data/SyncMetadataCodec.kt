package com.vabxsen.budgie.data

import org.json.JSONObject

internal data class SyncMetadata(
    val subscriptionTimes: MutableMap<String, Long> = mutableMapOf(),
    val deletedTimes: MutableMap<String, Long> = mutableMapOf(),
    var preferencesTime: Long = 0,
)

internal object SyncMetadataCodec {
    fun encode(value: SyncMetadata): String =
        JSONObject().apply {
            put("version", 1)
            put("preferencesTime", value.preferencesTime)
            put("subscriptionTimes", JSONObject(value.subscriptionTimes as Map<*, *>))
            put("deletedTimes", JSONObject(value.deletedTimes as Map<*, *>))
        }.toString()

    fun decode(text: String): SyncMetadata {
        val json = JSONObject(text)
        require(json.getInt("version") == 1)
        return SyncMetadata(
            json.getJSONObject("subscriptionTimes").toLongMap(),
            json.getJSONObject("deletedTimes").toLongMap(),
            json.getLong("preferencesTime"),
        )
    }

    private fun JSONObject.toLongMap(): MutableMap<String, Long> =
        keys().asSequence().associateWith { getLong(it) }.toMutableMap()
}
