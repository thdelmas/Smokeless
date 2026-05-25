package com.smokless.smokeless.util

import android.content.Context
import android.content.SharedPreferences
import com.smokless.smokeless.bios.BiosClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local mirror of the weekly self-evaluation ratings the user submits.
 *
 * Bios is the authoritative store, but the digest needs to render the prior
 * trend even when Bios isn't installed / enabled / approved — and Bios reads
 * require permission we don't always have. So Smokeless persists its own
 * rolling history of the values the user submitted from this device.
 *
 * Scope: last 12 entries per metric key. The digest renders the most recent
 * 4; the extra headroom keeps a tiny safety margin and is still trivially
 * small in SharedPreferences.
 */
class WeeklySelfEvalStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class Entry(val timestampMs: Long, val value: Double)

    /** Append the rating, trim oldest entries past [MAX_PER_KEY]. */
    fun save(metricKey: String, value: Double, timestampMs: Long) {
        val arr = readArray(metricKey)
        arr.put(JSONObject().apply {
            put(FIELD_TS, timestampMs)
            put(FIELD_VALUE, value)
        })
        val trimmed = trimToMax(arr)
        prefs.edit().putString(historyKey(metricKey), trimmed.toString()).apply()
        prefs.edit().putLong(KEY_LAST_SUBMITTED_MS, timestampMs).apply()
    }

    /** Most recent entries, newest first, up to [limit]. */
    fun recent(metricKey: String, limit: Int = 4): List<Entry> {
        val arr = readArray(metricKey)
        val out = ArrayList<Entry>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val ts = obj.optLong(FIELD_TS, 0L)
            val v = obj.optDouble(FIELD_VALUE, Double.NaN)
            if (ts > 0 && !v.isNaN()) out += Entry(ts, v)
        }
        return out.sortedByDescending { it.timestampMs }.take(limit)
    }

    /** Most recent submission timestamp across all keys, or null. */
    fun lastSubmittedMs(): Long? {
        val v = prefs.getLong(KEY_LAST_SUBMITTED_MS, 0L)
        return if (v <= 0L) null else v
    }

    private fun readArray(metricKey: String): JSONArray {
        val raw = prefs.getString(historyKey(metricKey), null) ?: return JSONArray()
        return try {
            JSONArray(raw)
        } catch (_: Exception) {
            JSONArray()
        }
    }

    private fun trimToMax(arr: JSONArray): JSONArray {
        if (arr.length() <= MAX_PER_KEY) return arr
        val sorted = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            .sortedByDescending { it.optLong(FIELD_TS, 0L) }
            .take(MAX_PER_KEY)
        val out = JSONArray()
        for (obj in sorted) out.put(obj)
        return out
    }

    private fun historyKey(metricKey: String): String = "selfEval_${metricKey}_history"

    companion object {
        private const val PREFS_NAME = "SmokelessPrefs"
        private const val KEY_LAST_SUBMITTED_MS = "weeklySelfEvalLastSubmittedMs"
        private const val FIELD_TS = "ts"
        private const val FIELD_VALUE = "v"
        private const val MAX_PER_KEY = 12

        /** Convenience exposing the 6 keys the prompt sheet writes. */
        val KEYS: List<String> = BiosClient.SELF_RATING_KEYS
    }
}
