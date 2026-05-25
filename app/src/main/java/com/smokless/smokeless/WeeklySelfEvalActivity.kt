package com.smokless.smokeless

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import com.smokless.smokeless.bios.BiosClient
import com.smokless.smokeless.data.AppDatabase
import com.smokless.smokeless.util.WeeklyDigestReceiver
import com.smokless.smokeless.util.WeeklySelfEvalStore
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sunday-evening self-evaluation prompt sheet — companion-side capture for the
 * cessation-recovery scales added in Bios #323. Six owner-rated 0–10 sliders
 * (sensory + respiratory + smoker identity); writes pass through to Bios via
 * [BiosClient.pushSelfRating] and are mirrored locally so the digest can
 * render the prior 4-week trend even when Bios isn't installed / approved.
 *
 * Skip → no row written. Remind me later → reschedules the digest receiver
 * 24h forward (and clears the last-fired marker so it can fire that day).
 */
class WeeklySelfEvalActivity : AppCompatActivity() {

    private data class Row(
        val metricKey: String,
        val title: String,
        val hint: String,
        val slider: Slider,
        val valueView: TextView,
        val priorView: TextView,
    )

    private val rows = ArrayList<Row>(6)
    private lateinit var bios: BiosClient
    private lateinit var store: WeeklySelfEvalStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weekly_self_eval)

        bios = BiosClient(applicationContext)
        store = WeeklySelfEvalStore(applicationContext)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        val container = findViewById<LinearLayout>(R.id.groupSelfEvalRows)
        val inflater = LayoutInflater.from(this)
        SCALES.forEach { scale ->
            val view = inflater.inflate(R.layout.item_self_eval_row, container, false)
            val title = view.findViewById<TextView>(R.id.textSelfEvalRowTitle)
            val hint = view.findViewById<TextView>(R.id.textSelfEvalRowHint)
            val valueView = view.findViewById<TextView>(R.id.textSelfEvalRowValue)
            val priorView = view.findViewById<TextView>(R.id.textSelfEvalRowPrior)
            val slider = view.findViewById<Slider>(R.id.sliderSelfEvalRow)

            title.text = scale.title
            hint.text = scale.hint
            valueView.text = INT_FORMAT.format(slider.value)
            slider.addOnChangeListener { _, v, _ ->
                valueView.text = INT_FORMAT.format(v)
            }
            renderPrior(scale.metricKey, priorView)

            rows += Row(scale.metricKey, scale.title, scale.hint, slider, valueView, priorView)
            container.addView(view)
        }

        findViewById<MaterialButton>(R.id.btnSelfEvalSkip).setOnClickListener {
            // Skip leaves no row written — neither locally nor in Bios.
            finish()
        }
        findViewById<MaterialButton>(R.id.btnSelfEvalRemindLater).setOnClickListener {
            WeeklyDigestReceiver.snooze(this, SNOOZE_MS)
            Snackbar.make(it, "We'll nudge you again tomorrow.", Snackbar.LENGTH_SHORT).show()
            it.postDelayed({ finish() }, 600L)
        }
        findViewById<MaterialButton>(R.id.btnSelfEvalSave).setOnClickListener { saveBtn ->
            saveBtn.isEnabled = false
            val timestamp = System.currentTimeMillis()
            val snapshot = rows.map { it.metricKey to it.slider.value.toDouble() }
            AppDatabase.databaseExecutor.execute {
                snapshot.forEach { (key, value) ->
                    store.save(key, value, timestamp)
                    // pushSelfRating is best-effort; outcomes (incl. PENDING_APPROVAL)
                    // are persisted on BiosClient and surfaced from Settings.
                    bios.pushSelfRating(key, value, timestamp)
                }
                runOnUiThread {
                    Snackbar.make(saveBtn, "Saved — see the digest for the trend.", Snackbar.LENGTH_SHORT).show()
                    saveBtn.postDelayed({ finish() }, 600L)
                }
            }
        }
    }

    private fun renderPrior(metricKey: String, priorView: TextView) {
        val recent = store.recent(metricKey, limit = 4)
        if (recent.isEmpty()) {
            priorView.visibility = View.GONE
            return
        }
        // Newest first → reverse for left-to-right oldest→newest trend.
        val ordered = recent.reversed()
        val numFmt = DecimalFormat("0.#")
        val parts = ordered.map { entry ->
            val dateLabel = DATE_FORMAT.format(Date(entry.timestampMs))
            "$dateLabel: ${numFmt.format(entry.value)}"
        }
        priorView.text = "Prior · " + parts.joinToString(" · ")
        priorView.visibility = View.VISIBLE
    }

    private data class Scale(
        val metricKey: String,
        val title: String,
        val hint: String,
    )

    companion object {
        const val EXTRA_FROM_DIGEST = "fromDigest"

        private const val SNOOZE_MS = 24L * 60L * 60L * 1000L

        private val INT_FORMAT = DecimalFormat("0")
        private val DATE_FORMAT = SimpleDateFormat("MMM d", Locale.getDefault())

        private val SCALES: List<Scale> = listOf(
            Scale(
                metricKey = BiosClient.METRIC_SMELL_SELF_RATING,
                title = "Smell",
                hint = "0 = none · 10 = vivid",
            ),
            Scale(
                metricKey = BiosClient.METRIC_TASTE_SELF_RATING,
                title = "Taste",
                hint = "0 = flat · 10 = vivid",
            ),
            Scale(
                metricKey = BiosClient.METRIC_COUGH_FREQUENCY_SELF_RATING,
                title = "Cough frequency",
                hint = "0 = none · 10 = constant",
            ),
            Scale(
                metricKey = BiosClient.METRIC_SPUTUM_SELF_RATING,
                title = "Sputum / morning clearing",
                hint = "0 = none · 10 = heavy",
            ),
            Scale(
                metricKey = BiosClient.METRIC_BREATH_EASE_SELF_RATING,
                title = "Breath ease at exertion",
                hint = "0 = struggling · 10 = effortless",
            ),
            Scale(
                metricKey = BiosClient.METRIC_SMOKER_IDENTITY_SELF_RATING,
                title = "\"I am a smoker\"",
                hint = "0 = no · 10 = yes (just track, no direction)",
            ),
        )
    }
}
