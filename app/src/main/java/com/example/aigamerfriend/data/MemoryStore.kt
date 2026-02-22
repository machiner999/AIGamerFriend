package com.example.aigamerfriend.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

class MemoryStore(private val dataStore: DataStore<Preferences>) {
    companion object {
        private const val TAG = "MemoryStore"
        private const val MAX_SUMMARIES = 10
        private const val MAX_SUMMARY_LENGTH = 200
        private val KEY_SUMMARIES = stringPreferencesKey("session_summaries")
    }

    data class Entry(val t: Long, val s: String)

    suspend fun addSummary(summary: String) {
        try {
            val truncated = if (summary.length > MAX_SUMMARY_LENGTH) {
                summary.take(MAX_SUMMARY_LENGTH) + "..."
            } else {
                summary
            }
            val entry = Entry(t = System.currentTimeMillis(), s = truncated)
            dataStore.edit { prefs ->
                val existing = deserialize(prefs[KEY_SUMMARIES])
                val updated = (existing + entry).takeLast(MAX_SUMMARIES)
                prefs[KEY_SUMMARIES] = serialize(updated)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save summary", e)
        }
    }

    suspend fun getFormattedSummaries(): String? {
        return try {
            dataStore.data.map { prefs ->
                val entries = deserialize(prefs[KEY_SUMMARIES])
                if (entries.isEmpty()) {
                    null
                } else {
                    entries.joinToString("\n") { entry ->
                        val date = java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.JAPAN)
                            .format(java.util.Date(entry.t))
                        "- [$date] ${entry.s}"
                    }
                }
            }.first()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read summaries", e)
            null
        }
    }

    suspend fun clear() {
        try {
            dataStore.edit { prefs ->
                prefs.remove(KEY_SUMMARIES)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear summaries", e)
        }
    }

    private fun serialize(entries: List<Entry>): String {
        val array = JsonArray(
            entries.map { entry ->
                JsonObject(
                    mapOf(
                        "t" to JsonPrimitive(entry.t),
                        "s" to JsonPrimitive(entry.s),
                    ),
                )
            },
        )
        return array.toString()
    }

    private fun deserialize(raw: String?): List<Entry> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            Json.parseToJsonElement(raw).jsonArray.map { element ->
                val obj = element.jsonObject
                Entry(
                    t = obj["t"]!!.jsonPrimitive.long,
                    s = obj["s"]!!.jsonPrimitive.content,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to deserialize summaries", e)
            emptyList()
        }
    }
}
