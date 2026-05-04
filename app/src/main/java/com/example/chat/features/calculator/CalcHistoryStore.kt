package com.example.chat.features.calculator

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

data class CalcHistoryEntry(
    val expression: String,
    val result: String,
    val timestampMs: Long,
)

private val Context.calcDataStore: DataStore<Preferences> by preferencesDataStore(name = "calc")

private val historyKey = stringPreferencesKey("history_json")

object CalcHistoryStore {
    fun historyFlow(context: Context): Flow<List<CalcHistoryEntry>> {
        return context.calcDataStore.data.map { prefs ->
            parseHistory(prefs[historyKey].orEmpty())
        }
    }

    suspend fun addEntry(
        context: Context,
        entry: CalcHistoryEntry,
        maxEntries: Int = 50,
    ) {
        context.calcDataStore.edit { prefs ->
            val existing = parseHistory(prefs[historyKey].orEmpty()).toMutableList()
            existing.add(0, entry)
            val trimmed = existing.take(maxEntries)
            prefs[historyKey] = serializeHistory(trimmed)
        }
    }

    suspend fun clear(context: Context) {
        context.calcDataStore.edit { prefs ->
            prefs.remove(historyKey)
        }
    }

    private fun parseHistory(raw: String): List<CalcHistoryEntry> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        CalcHistoryEntry(
                            expression = obj.optString("expression"),
                            result = obj.optString("result"),
                            timestampMs = obj.optLong("timestampMs"),
                        )
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun serializeHistory(entries: List<CalcHistoryEntry>): String {
        val arr = JSONArray()
        for (e in entries) {
            val obj = JSONObject()
                .put("expression", e.expression)
                .put("result", e.result)
                .put("timestampMs", e.timestampMs)
            arr.put(obj)
        }
        return arr.toString()
    }
}
