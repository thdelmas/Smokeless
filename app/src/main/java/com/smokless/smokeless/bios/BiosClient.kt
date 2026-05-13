package com.smokless.smokeless.bios

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log

/**
 * Companion writer that forwards substance-use events from Smokeless to Bios
 * via the BiosHealthProvider companion URI:
 *
 *   content://com.bios.app.health/companion/{metric_type}
 *
 * Best-effort and fire-and-forget. Three failure modes are all silent:
 *  - Bios not installed (resolver returns null type)
 *  - User has not opted in (settings flag off)
 *  - Bios rejects the metric type (SecurityException — paired Bios update lands later)
 *
 * Smokeless keeps working identically whether or not any of those succeed.
 */
class BiosClient(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val isAvailable: Boolean
        get() = try {
            context.contentResolver.getType(BASE_URI) != null
        } catch (_: Exception) {
            false
        }

    val isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun status(): Status = when {
        !isAvailable -> Status.NOT_INSTALLED
        !isEnabled -> Status.NOT_ENABLED
        else -> Status.CONNECTED
    }

    fun pushSmokingEvent(timestamp: Long) = push(METRIC_TOBACCO_USE, timestamp)

    fun pushCravingEvent(timestamp: Long) = push(METRIC_TOBACCO_CRAVING, timestamp)

    /**
     * Replays existing history into Bios. Called from the "Sync history" button —
     * idempotency is Bios's concern (per Smokeless docs/ROADMAP §1.3); if the user
     * runs it twice they may end up with duplicate events.
     *
     * Short-circuits if disabled or Bios is absent so the caller can avoid
     * walking the DB unnecessarily.
     */
    fun backfill(smokingTimestamps: List<Long>, cravingTimestamps: List<Long>): BackfillResult {
        val total = smokingTimestamps.size + cravingTimestamps.size
        if (!isEnabled || !isAvailable) {
            return BackfillResult(pushed = 0, failed = 0, total = total)
        }
        var pushed = 0
        var failed = 0
        for (ts in smokingTimestamps) {
            if (push(METRIC_TOBACCO_USE, ts)) pushed++ else failed++
        }
        for (ts in cravingTimestamps) {
            if (push(METRIC_TOBACCO_CRAVING, ts)) pushed++ else failed++
        }
        return BackfillResult(pushed = pushed, failed = failed, total = total)
    }

    private fun push(metricType: String, timestamp: Long): Boolean {
        if (!isEnabled) return false
        if (!isAvailable) return false
        val uri = COMPANION_URI.buildUpon().appendPath(metricType).build()
        val values = ContentValues().apply {
            put("value", 1.0)
            put("timestamp", timestamp)
        }
        return try {
            context.contentResolver.insert(uri, values)
            true
        } catch (e: SecurityException) {
            Log.d(TAG, "Bios rejected $metricType: ${e.message}")
            false
        } catch (e: Exception) {
            Log.d(TAG, "Bios push failed for $metricType: ${e.message}")
            false
        }
    }

    enum class Status { NOT_INSTALLED, NOT_ENABLED, CONNECTED }

    data class BackfillResult(val pushed: Int, val failed: Int, val total: Int)

    companion object {
        private const val TAG = "BiosClient"
        private const val PREFS_NAME = "SmokelessPrefs"
        private const val KEY_ENABLED = "biosIntegrationEnabled"

        const val METRIC_TOBACCO_USE = "tobacco_use"
        const val METRIC_TOBACCO_CRAVING = "tobacco_craving"

        private val BASE_URI: Uri = Uri.parse("content://com.bios.app.health")
        private val COMPANION_URI: Uri = BASE_URI.buildUpon().appendPath("companion").build()
    }
}
