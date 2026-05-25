package com.smokless.smokeless

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
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
 * One-question-per-slide carousel for the Sunday-evening self-evaluation.
 * Six pages, one per cessation-recovery scale (Bios #323): the user swipes
 * between them and each rating is persisted the moment they lift their
 * finger off the slider — no separate Save step.
 *
 * Local mirror via [WeeklySelfEvalStore] backs the prior-trend strip in the
 * digest UI even when Bios isn't installed / approved; [BiosClient.pushSelfRating]
 * forwards to Bios with the same silent-fail semantics as smoking events.
 *
 * Skip closes without writing further rows; Remind-me-later snoozes the
 * digest receiver 24h forward.
 */
class WeeklySelfEvalActivity : AppCompatActivity() {

    private lateinit var bios: BiosClient
    private lateinit var store: WeeklySelfEvalStore
    private lateinit var pager: ViewPager2
    private lateinit var counter: TextView
    private lateinit var nextBtn: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weekly_self_eval)

        bios = BiosClient(applicationContext)
        store = WeeklySelfEvalStore(applicationContext)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        counter = findViewById(R.id.textSelfEvalPageCounter)
        nextBtn = findViewById(R.id.btnSelfEvalNext)
        pager = findViewById(R.id.pagerSelfEval)
        pager.adapter = PageAdapter(SCALES, ::onRatingChanged)

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                renderPageCounter(position)
            }
        })
        renderPageCounter(0)

        nextBtn.setOnClickListener {
            val current = pager.currentItem
            if (current < SCALES.size - 1) {
                pager.setCurrentItem(current + 1, true)
            } else {
                finish()
            }
        }

        findViewById<MaterialButton>(R.id.btnSelfEvalSkip).setOnClickListener {
            // Skip on the first untouched page writes nothing. If the user has
            // already saved some ratings on earlier pages, those stay.
            finish()
        }
        findViewById<MaterialButton>(R.id.btnSelfEvalRemindLater).setOnClickListener {
            WeeklyDigestReceiver.snooze(this, SNOOZE_MS)
            Snackbar.make(it, "We'll nudge you again tomorrow.", Snackbar.LENGTH_SHORT).show()
            it.postDelayed({ finish() }, 600L)
        }
    }

    private fun renderPageCounter(position: Int) {
        counter.text = "${position + 1} of ${SCALES.size}"
        nextBtn.text = if (position == SCALES.size - 1) "Done" else "Next"
    }

    /**
     * Called by the page adapter when the user releases the slider — debounces
     * to "intentional input only" so the default-5 placeholder doesn't get
     * persisted just because the page rendered.
     */
    private fun onRatingChanged(scale: Scale, value: Double, savedIndicator: View) {
        val timestamp = System.currentTimeMillis()
        AppDatabase.databaseExecutor.execute {
            store.save(scale.metricKey, value, timestamp)
            // Best-effort push; outcomes (including PENDING_APPROVAL) are
            // persisted on BiosClient and surfaced from Settings.
            bios.pushSelfRating(scale.metricKey, value, timestamp)
            savedIndicator.post {
                savedIndicator.visibility = View.VISIBLE
                savedIndicator.alpha = 0f
                savedIndicator.animate().alpha(1f).setDuration(180L).start()
            }
        }
    }

    private class PageAdapter(
        private val scales: List<Scale>,
        private val onRatingChanged: (Scale, Double, View) -> Unit,
    ) : RecyclerView.Adapter<PageAdapter.PageViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.page_self_eval, parent, false)
            return PageViewHolder(view)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            holder.bind(scales[position], onRatingChanged)
        }

        override fun getItemCount(): Int = scales.size

        class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val title: TextView = view.findViewById(R.id.textPageTitle)
            private val hint: TextView = view.findViewById(R.id.textPageHint)
            private val body: TextView = view.findViewById(R.id.textPageBody)
            private val valueView: TextView = view.findViewById(R.id.textPageValue)
            private val slider: Slider = view.findViewById(R.id.sliderPage)
            private val priorView: TextView = view.findViewById(R.id.textPagePrior)
            private val savedIndicator: TextView = view.findViewById(R.id.textPageSavedIndicator)

            fun bind(scale: Scale, onRatingChanged: (Scale, Double, View) -> Unit) {
                title.text = scale.title
                hint.text = scale.hint
                body.text = scale.body

                val store = WeeklySelfEvalStore(itemView.context.applicationContext)
                val prior = store.recent(scale.metricKey, limit = 4)
                if (prior.isEmpty()) {
                    priorView.visibility = View.GONE
                    slider.value = SLIDER_DEFAULT.toFloat()
                    savedIndicator.visibility = View.INVISIBLE
                } else {
                    // Pre-fill with the most recent rating so the user nudges
                    // from their last week's mark instead of restarting at 5.
                    slider.value = prior.first().value.toFloat().coerceIn(0f, 10f)
                    val ordered = prior.reversed()
                    val numFmt = DecimalFormat("0.#")
                    val parts = ordered.map { entry ->
                        val dateLabel = DATE_FORMAT.format(Date(entry.timestampMs))
                        "$dateLabel: ${numFmt.format(entry.value)}"
                    }
                    priorView.text = "Prior · " + parts.joinToString(" · ")
                    priorView.visibility = View.VISIBLE
                    savedIndicator.visibility = View.INVISIBLE
                }
                valueView.text = INT_FORMAT.format(slider.value)

                slider.clearOnChangeListeners()
                slider.addOnChangeListener { _, v, _ ->
                    valueView.text = INT_FORMAT.format(v)
                }
                slider.clearOnSliderTouchListeners()
                slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                    override fun onStartTrackingTouch(slider: Slider) {}
                    override fun onStopTrackingTouch(slider: Slider) {
                        onRatingChanged(scale, slider.value.toDouble(), savedIndicator)
                    }
                })
            }

            companion object {
                private const val SLIDER_DEFAULT = 5
                private val INT_FORMAT = DecimalFormat("0")
                private val DATE_FORMAT = SimpleDateFormat("MMM d", Locale.getDefault())
            }
        }
    }

    data class Scale(
        val metricKey: String,
        val title: String,
        val hint: String,
        val body: String,
    )

    companion object {
        const val EXTRA_FROM_DIGEST = "fromDigest"

        private const val SNOOZE_MS = 24L * 60L * 60L * 1000L

        private val SCALES: List<Scale> = listOf(
            Scale(
                metricKey = BiosClient.METRIC_SMELL_SELF_RATING,
                title = "Smell",
                hint = "0 = none · 10 = vivid",
                body = "How sharp do everyday scents feel — coffee, soap, food cooking? Think about a smell you encounter most days and rate how clearly you picked it up this week.",
            ),
            Scale(
                metricKey = BiosClient.METRIC_TASTE_SELF_RATING,
                title = "Taste",
                hint = "0 = flat · 10 = vivid",
                body = "How fully do flavours come through — sweet, salty, sour, bitter? Anchor on a meal you eat regularly so you can compare week to week.",
            ),
            Scale(
                metricKey = BiosClient.METRIC_COUGH_FREQUENCY_SELF_RATING,
                title = "Cough frequency",
                hint = "0 = none · 10 = constant",
                body = "How often did you cough this week without trying — morning, after meals, during exercise? Rate the typical day, not the worst one.",
            ),
            Scale(
                metricKey = BiosClient.METRIC_SPUTUM_SELF_RATING,
                title = "Sputum / morning clearing",
                hint = "0 = none · 10 = heavy",
                body = "Mucus you bring up when you first wake or first cough. 0 = clear, no clearing needed; 10 = a heavy throat-clearing routine before you can speak.",
            ),
            Scale(
                metricKey = BiosClient.METRIC_BREATH_EASE_SELF_RATING,
                title = "Breath ease at exertion",
                hint = "0 = struggling · 10 = effortless",
                body = "Stairs, brisk walking, anything that raises your heart rate. 10 = barely noticed your breathing; 0 = winded fast and had to slow down.",
            ),
            Scale(
                metricKey = BiosClient.METRIC_SMOKER_IDENTITY_SELF_RATING,
                title = "\"I am a smoker\"",
                hint = "0 = doesn't fit · 10 = fits exactly",
                body = "How strongly does the label \"smoker\" describe you this week? There's no right direction — we just track how it shifts over time.",
            ),
        )
    }
}
