package com.smokless.smokeless

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.slider.Slider
import com.smokless.smokeless.bios.BiosClient
import com.smokless.smokeless.data.AppDatabase
import com.smokless.smokeless.databinding.ActivityOnboardingBinding
import com.smokless.smokeless.util.WeeklySelfEvalStore

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    companion object {
        private const val PREF_NAME = "SmokelessPrefs"
        private const val KEY_ONBOARDING_DONE = "onboardingDone"
        private const val KEY_USER_GOAL = "userGoal"
        private const val KEY_BASELINE_CIGS = "baselineCigs"
        private const val KEY_DIFFICULTY = "difficultyLevel"

        // Welcome → Goal → Baseline cig count → Baseline self-eval → Ready
        private const val PAGE_COUNT = 5

        fun isOnboardingDone(context: Context): Boolean {
            return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ONBOARDING_DONE, false)
        }
    }

    private var selectedGoal = "reduce"
    private var baselineCigs = 10

    /**
     * Baseline self-eval ratings the user touches during onboarding. Only
     * touched sliders end up here — matches the "no zero-fill" rule, so
     * untouched scales stay empty and the user can rate them later from
     * the weekly carousel.
     */
    private val selfEvalBaseline: MutableMap<String, Double> = mutableMapOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPager()
        setupIndicators()
        setupButtons()
    }

    private fun setupViewPager() {
        binding.viewPager.adapter = OnboardingAdapter()
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateIndicators(position)
                updateButton(position)
            }
        })
    }

    private fun setupIndicators() {
        val count = PAGE_COUNT
        for (i in 0 until count) {
            val dot = View(this).apply {
                layoutParams = ViewGroup.MarginLayoutParams(8.dp, 8.dp).apply {
                    marginStart = 4.dp
                    marginEnd = 4.dp
                }
                background = ContextCompat.getDrawable(this@OnboardingActivity, android.R.drawable.presence_invisible)
                backgroundTintList = ContextCompat.getColorStateList(
                    this@OnboardingActivity,
                    if (i == 0) R.color.accent_primary else R.color.progress_track
                )
            }
            binding.indicatorContainer.addView(dot)
        }
    }

    private fun updateIndicators(position: Int) {
        for (i in 0 until binding.indicatorContainer.childCount) {
            val dot = binding.indicatorContainer.getChildAt(i)
            dot.backgroundTintList = ContextCompat.getColorStateList(
                this,
                if (i == position) R.color.accent_primary else R.color.progress_track
            )
        }
    }

    private fun updateButton(position: Int) {
        if (position == PAGE_COUNT - 1) {
            binding.btnNext.text = "Get Started"
        } else {
            binding.btnNext.text = "Next"
        }
    }

    private fun setupButtons() {
        binding.btnNext.setOnClickListener {
            val current = binding.viewPager.currentItem
            if (current < PAGE_COUNT - 1) {
                binding.viewPager.currentItem = current + 1
            } else {
                finishOnboarding()
            }
        }

        binding.btnSkip.setOnClickListener {
            finishOnboarding()
        }
    }

    private fun finishOnboarding() {
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_ONBOARDING_DONE, true)
            .putString(KEY_USER_GOAL, selectedGoal)
            .putInt(KEY_BASELINE_CIGS, baselineCigs)
            .putInt(KEY_DIFFICULTY, when (selectedGoal) {
                "quit" -> 4
                "reduce" -> 2
                else -> 0
            })
            .apply()

        persistSelfEvalBaseline()

        com.smokless.smokeless.util.ReminderReceiver.schedule(this)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    /**
     * Persist whichever baseline self-eval sliders the user touched. Untouched
     * scales stay empty — they can be filled in from the weekly check-in
     * carousel later. Mirrors the live runtime path: local store + Bios push,
     * silent-fail semantics from BiosClient.
     */
    private fun persistSelfEvalBaseline() {
        if (selfEvalBaseline.isEmpty()) return
        val snapshot = selfEvalBaseline.toMap()
        val timestamp = System.currentTimeMillis()
        val store = WeeklySelfEvalStore(applicationContext)
        val bios = BiosClient(applicationContext)
        AppDatabase.databaseExecutor.execute {
            for ((key, value) in snapshot) {
                store.save(key, value, timestamp)
                bios.pushSelfRating(key, value, timestamp)
            }
        }
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    inner class OnboardingAdapter : RecyclerView.Adapter<OnboardingAdapter.PageViewHolder>() {

        override fun getItemCount() = PAGE_COUNT

        override fun getItemViewType(position: Int) = position

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val layoutId = when (viewType) {
                0 -> R.layout.fragment_onboarding_welcome
                1 -> R.layout.fragment_onboarding_goal
                2 -> R.layout.fragment_onboarding_baseline
                3 -> R.layout.fragment_onboarding_self_eval_baseline
                else -> R.layout.fragment_onboarding_ready
            }
            val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
            return PageViewHolder(view)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            when (position) {
                1 -> bindGoalPage(holder.itemView)
                2 -> bindBaselinePage(holder.itemView)
                3 -> bindSelfEvalBaselinePage(holder.itemView)
            }
        }

        private fun bindGoalPage(view: View) {
            val radioGroup = view.findViewById<RadioGroup>(R.id.radioGoal)
            radioGroup.setOnCheckedChangeListener { _, checkedId ->
                selectedGoal = when (checkedId) {
                    R.id.radioQuit -> "quit"
                    R.id.radioReduce -> "reduce"
                    R.id.radioTrack -> "track"
                    else -> "reduce"
                }
            }
        }

        private fun bindBaselinePage(view: View) {
            val slider = view.findViewById<Slider>(R.id.sliderBaseline)
            val valueText = view.findViewById<TextView>(R.id.textBaselineValue)
            slider.addOnChangeListener { _, value, _ ->
                baselineCigs = value.toInt()
                valueText.text = baselineCigs.toString()
            }
        }

        /**
         * Renders one compact row per cessation-recovery scale. Only sliders
         * the user actually releases (onStopTrackingTouch) get added to
         * [selfEvalBaseline] — untouched scales stay out of the map and the
         * user can rate them later from the weekly carousel.
         */
        private fun bindSelfEvalBaselinePage(view: View) {
            val container = view.findViewById<LinearLayout>(R.id.groupBaselineSelfEvalRows)
            if (container.childCount > 0) return // already bound
            val inflater = LayoutInflater.from(view.context)
            for (scale in BASELINE_SCALES) {
                val row = inflater.inflate(R.layout.item_onboarding_self_eval_row, container, false)
                val title = row.findViewById<TextView>(R.id.textOnboardingRowTitle)
                val hint = row.findViewById<TextView>(R.id.textOnboardingRowHint)
                val value = row.findViewById<TextView>(R.id.textOnboardingRowValue)
                val slider = row.findViewById<Slider>(R.id.sliderOnboardingRow)
                title.text = scale.title
                hint.text = scale.hint
                slider.addOnChangeListener { _, v, _ ->
                    value.text = v.toInt().toString()
                    value.setTextColor(
                        ContextCompat.getColor(this@OnboardingActivity, R.color.accent_primary)
                    )
                }
                slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                    override fun onStartTrackingTouch(slider: Slider) {}
                    override fun onStopTrackingTouch(slider: Slider) {
                        selfEvalBaseline[scale.metricKey] = slider.value.toDouble()
                    }
                })
                container.addView(row)
            }
        }

        inner class PageViewHolder(view: View) : RecyclerView.ViewHolder(view)
    }

    private data class Scale(val metricKey: String, val title: String, val hint: String)

    private val BASELINE_SCALES: List<Scale> = listOf(
        Scale(BiosClient.METRIC_SMELL_SELF_RATING, "Smell", "0 = none · 10 = vivid"),
        Scale(BiosClient.METRIC_TASTE_SELF_RATING, "Taste", "0 = flat · 10 = vivid"),
        Scale(BiosClient.METRIC_COUGH_FREQUENCY_SELF_RATING, "Cough frequency", "0 = none · 10 = constant"),
        Scale(BiosClient.METRIC_SPUTUM_SELF_RATING, "Sputum / morning clearing", "0 = none · 10 = heavy"),
        Scale(BiosClient.METRIC_BREATH_EASE_SELF_RATING, "Breath ease at exertion", "0 = struggling · 10 = effortless"),
        Scale(BiosClient.METRIC_SMOKER_IDENTITY_SELF_RATING, "\"I am a smoker\"", "0 = doesn't fit · 10 = fits exactly"),
    )
}
