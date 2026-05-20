package com.smokless.smokeless.bios

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.smokless.smokeless.data.entity.Substance

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

    /**
     * Outcome of the most recent push attempt. Persisted so the settings UI
     * can surface actionable guidance — particularly the
     * [LastPushOutcome.PENDING_APPROVAL] state, which is the first-write
     * response from Bios's CompanionGate (the owner has to approve
     * Smokeless inside Bios → Settings → Companion Apps before any data
     * lands). Without surfacing this, the user logs an event, sees nothing
     * in Bios, and has no signal that the integration is one approval tap
     * away from working.
     */
    val lastPushOutcome: LastPushOutcome
        get() = prefs.getString(KEY_LAST_PUSH_OUTCOME, null)
            ?.let { runCatching { LastPushOutcome.valueOf(it) }.getOrNull() }
            ?: LastPushOutcome.NEVER_TRIED

    private fun recordOutcome(outcome: LastPushOutcome) {
        prefs.edit().putString(KEY_LAST_PUSH_OUTCOME, outcome.name).apply()
    }

    fun pushSmokingEvent(timestamp: Long, substance: Substance) =
        push(useMetricFor(substance), timestamp)

    fun pushCravingEvent(timestamp: Long, substance: Substance = Substance.TOBACCO) =
        push(cravingMetricFor(substance), timestamp)

    /**
     * Replays existing history into Bios. Called from the "Sync history" button —
     * idempotency is Bios's concern (per Smokeless docs/ROADMAP §1.3); if the user
     * runs it twice they may end up with duplicate events.
     *
     * Short-circuits if disabled or Bios is absent so the caller can avoid
     * walking the DB unnecessarily.
     */
    fun backfill(
        smokingEvents: List<Pair<Long, Substance>>,
        cravingTimestamps: List<Long>,
    ): BackfillResult {
        val total = smokingEvents.size + cravingTimestamps.size
        if (!isEnabled || !isAvailable) {
            return BackfillResult(pushed = 0, failed = 0, total = total)
        }
        var pushed = 0
        var failed = 0
        for ((ts, substance) in smokingEvents) {
            if (push(useMetricFor(substance), ts)) pushed++ else failed++
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
            recordOutcome(LastPushOutcome.OK)
            true
        } catch (e: SecurityException) {
            val msg = e.message.orEmpty()
            // Bios's CompanionGate throws this exact message on first
            // contact from a new package. Distinguish it from the
            // post-revoke / cert-pin failures so the UI can guide the
            // user to the approval tap instead of a generic error.
            val outcome = if (msg.contains("not approved", ignoreCase = true)) {
                LastPushOutcome.PENDING_APPROVAL
            } else {
                LastPushOutcome.OTHER_FAILURE
            }
            recordOutcome(outcome)
            Log.d(TAG, "Bios rejected $metricType: $msg (outcome=$outcome)")
            false
        } catch (e: Exception) {
            recordOutcome(LastPushOutcome.OTHER_FAILURE)
            Log.d(TAG, "Bios push failed for $metricType: ${e.message}")
            false
        }
    }

    enum class Status { NOT_INSTALLED, NOT_ENABLED, CONNECTED }

    /**
     * What happened the last time Smokeless tried to write to Bios.
     * Persisted across restarts so the settings screen can render a
     * meaningful state on cold open.
     */
    enum class LastPushOutcome {
        /** No push attempted yet (fresh install or integration just toggled on). */
        NEVER_TRIED,

        /** Last push landed. The integration is fully wired. */
        OK,

        /** Bios's CompanionGate rejected the write because the owner has
         *  not yet approved Smokeless in Bios → Settings → Companion Apps. */
        PENDING_APPROVAL,

        /** Bios rejected the write for some other reason — most often a
         *  paired-release mismatch (Bios doesn't yet whitelist a key
         *  Smokeless writes). */
        OTHER_FAILURE,
    }

    data class BackfillResult(val pushed: Int, val failed: Int, val total: Int)

    companion object {
        private const val TAG = "BiosClient"
        private const val PREFS_NAME = "SmokelessPrefs"
        private const val KEY_ENABLED = "biosIntegrationEnabled"
        private const val KEY_LAST_PUSH_OUTCOME = "biosLastPushOutcome"

        /** Bios's `MainActivity` reads this extra to deep-link directly
         *  into Settings → Companion Apps. Mirrors
         *  `CompanionAccessNotifier.EXTRA_NAVIGATE_TO_COMPANIONS` in
         *  Bios; pinned by string here because Smokeless doesn't depend
         *  on Bios's source. */
        const val BIOS_PACKAGE = "com.bios.app"
        const val BIOS_EXTRA_NAVIGATE_TO_COMPANIONS = "navigate_to_companions"

        const val METRIC_TOBACCO_USE = "tobacco_use"
        const val METRIC_TOBACCO_CRAVING = "tobacco_craving"
        const val METRIC_CANNABIS_USE = "cannabis_use"
        const val METRIC_CANNABIS_CRAVING = "cannabis_craving"

        private val BASE_URI: Uri = Uri.parse("content://com.bios.app.health")
        private val COMPANION_URI: Uri = BASE_URI.buildUpon().appendPath("companion").build()

        private fun useMetricFor(substance: Substance): String = when (substance) {
            Substance.TOBACCO -> METRIC_TOBACCO_USE
            Substance.CANNABIS -> METRIC_CANNABIS_USE
        }

        private fun cravingMetricFor(substance: Substance): String = when (substance) {
            Substance.TOBACCO -> METRIC_TOBACCO_CRAVING
            Substance.CANNABIS -> METRIC_CANNABIS_CRAVING
        }
    }
}
