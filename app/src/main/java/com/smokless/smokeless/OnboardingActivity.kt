package com.smokless.smokeless

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.slider.Slider
import com.smokless.smokeless.databinding.ActivityOnboardingBinding

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    companion object {
        private const val PREF_NAME = "SmokelessPrefs"
        private const val KEY_ONBOARDING_DONE = "onboardingDone"
        private const val KEY_USER_GOAL = "userGoal"
        private const val KEY_BASELINE_CIGS = "baselineCigs"
        private const val KEY_DIFFICULTY = "difficultyLevel"

        fun isOnboardingDone(context: Context): Boolean {
            return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ONBOARDING_DONE, false)
        }
    }

    private var selectedGoal = "reduce"
    private var baselineCigs = 10

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
        val count = 4
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
        if (position == 3) {
            binding.btnNext.text = "Get Started"
        } else {
            binding.btnNext.text = "Next"
        }
    }

    private fun setupButtons() {
        binding.btnNext.setOnClickListener {
            val current = binding.viewPager.currentItem
            if (current < 3) {
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

        com.smokless.smokeless.util.ReminderReceiver.schedule(this)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    inner class OnboardingAdapter : RecyclerView.Adapter<OnboardingAdapter.PageViewHolder>() {

        override fun getItemCount() = 4

        override fun getItemViewType(position: Int) = position

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val layoutId = when (viewType) {
                0 -> R.layout.fragment_onboarding_welcome
                1 -> R.layout.fragment_onboarding_goal
                2 -> R.layout.fragment_onboarding_baseline
                else -> R.layout.fragment_onboarding_ready
            }
            val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
            return PageViewHolder(view)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            when (position) {
                1 -> bindGoalPage(holder.itemView)
                2 -> bindBaselinePage(holder.itemView)
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

        inner class PageViewHolder(view: View) : RecyclerView.ViewHolder(view)
    }
}
