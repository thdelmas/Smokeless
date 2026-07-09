package com.smokless.smokeless

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.smokless.smokeless.data.entity.Substance
import com.smokless.smokeless.databinding.ActivityMainBinding
import com.smokless.smokeless.ui.main.MainViewModel
import com.smokless.smokeless.ui.main.RecoveryTimelineAdapter
import com.smokless.smokeless.util.HealthBenefits
import com.smokless.smokeless.util.ScoreCalculator
import com.smokless.smokeless.util.SubstanceCopy
import com.smokless.smokeless.util.TimeFormatter
import java.text.DecimalFormat
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private val timelineAdapter = RecoveryTimelineAdapter()

    private val refreshHandler = Handler(Looper.getMainLooper())

    private val SELF_EVAL_DIGEST_LABELS: List<Pair<String, String>> = listOf(
        com.smokless.smokeless.bios.BiosClient.METRIC_SMELL_SELF_RATING to "Smell",
        com.smokless.smokeless.bios.BiosClient.METRIC_TASTE_SELF_RATING to "Taste",
        com.smokless.smokeless.bios.BiosClient.METRIC_COUGH_FREQUENCY_SELF_RATING to "Cough",
        com.smokless.smokeless.bios.BiosClient.METRIC_SPUTUM_SELF_RATING to "Sputum",
        com.smokless.smokeless.bios.BiosClient.METRIC_BREATH_EASE_SELF_RATING to "Breath",
        com.smokless.smokeless.bios.BiosClient.METRIC_SMOKER_IDENTITY_SELF_RATING to "Identity",
    )

    private var copy: SubstanceCopy = SubstanceCopy.TOBACCO

    // Banked recovery is "paused" during the substance exposure window after a
    // slip. Tobacco is 10min, cannabis is 30min — using the upper bound as a
    // single threshold keeps the badge logic simple and substance-agnostic.
    private val pausedThresholdMs = 30L * 60 * 1000

    // Only re-submit milestones when the achieved count actually changes —
    // avoids churning the adapter every tick.
    private var lastAchievedCount = -1

    private val refreshRunnable = object : Runnable {
        override fun run() {
            viewModel.updateTimer()
            refreshHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!OnboardingActivity.isOnboardingDone(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupTimeline()
        setupFab()
        setupSwipeRefresh()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_entries -> {
                    startActivity(Intent(this, HistoryActivity::class.java))
                    true
                }
                R.id.action_stats -> {
                    startActivity(Intent(this, StatsActivity::class.java))
                    true
                }
                R.id.action_achievements -> {
                    startActivity(Intent(this, AchievementsActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun setupTimeline() {
        binding.recyclerTimeline.layoutManager = LinearLayoutManager(this)
        binding.recyclerTimeline.adapter = timelineAdapter
    }

    private fun setupFab() {
        binding.fabSmoke.setOnClickListener { view ->
            view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            showSmokePicker()
        }
        binding.fabSmoke.setOnLongClickListener { view ->
            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            showSizedSmokeDialog()
            true
        }
    }

    /**
     * Sized log path — substance + dose-bucket chips in one dialog. Single-tap
     * users land in [showSmokePicker] which defaults quantity=1.0; this dialog
     * is reserved for moments when the count alone would hide the actual dose.
     */
    private fun showSizedSmokeDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_sized_smoke, null)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Log") { _, _ ->
                val substanceChips = view.findViewById<
                    com.google.android.material.chip.ChipGroup
                >(R.id.chipGroupSubstance)
                val sizeChips = view.findViewById<
                    com.google.android.material.chip.ChipGroup
                >(R.id.chipGroupSize)

                val substance = when (substanceChips.checkedChipId) {
                    R.id.chipSubstanceCannabis ->
                        com.smokless.smokeless.data.entity.Substance.CANNABIS
                    else ->
                        com.smokless.smokeless.data.entity.Substance.TOBACCO
                }
                val quantity = when (sizeChips.checkedChipId) {
                    R.id.chipSizeDrag -> 0.25
                    R.id.chipSizeHalf -> 0.5
                    R.id.chipSizeMore -> 1.5
                    else -> 1.0
                }
                recordSmokeAction(
                    exposureOffsetMs = substance.exposureMs,
                    substance = substance,
                    quantity = quantity,
                )
            }
            .show()
    }

    private fun recordSmokeAction(
        exposureOffsetMs: Long,
        substance: com.smokless.smokeless.data.entity.Substance,
        quantity: Double = 1.0,
    ) {
        viewModel.recordSmokeWithId(exposureOffsetMs, substance, quantity) { sessionId ->
            updateWidgets()
            val bankedMs = viewModel.bankedSmokeFreeMs.value ?: 0L
            // Lead with the size when it's not a default full smoke so the
            // user sees their choice reflected in the confirmation.
            val sizeNote = formatQuantityLabel(quantity)?.let { "$it · " }.orEmpty()
            val message = if (bankedMs > 0L) {
                "${sizeNote}Logged. ${TimeFormatter.formatShort(bankedMs)} smoke-free banked — still yours."
            } else {
                "${sizeNote}Smoke recorded"
            }
            com.google.android.material.snackbar.Snackbar
                .make(binding.root, message, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                .setAction("UNDO") {
                    viewModel.undoSmoke(sessionId)
                    updateWidgets()
                }
                .setBackgroundTint(ContextCompat.getColor(this, R.color.surface_elevated))
                .setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                .setActionTextColor(ContextCompat.getColor(this, R.color.accent_amber))
                .show()
        }
    }

    private fun formatQuantityLabel(quantity: Double): String? = when {
        kotlin.math.abs(quantity - 0.25) < 0.01 -> "Drag"
        kotlin.math.abs(quantity - 0.5) < 0.01 -> "Half"
        kotlin.math.abs(quantity - 1.5) < 0.01 -> "More"
        else -> null // full / non-bucket values — no badge
    }

    /**
     * Render a dose-weighted count: integers stay clean ("5"), fractional
     * sums show one decimal ("3.5") so the bucket math is visible without
     * cluttering whole-number totals.
     */
    private fun formatDoseCount(value: Double): String {
        val rounded = kotlin.math.round(value)
        return if (kotlin.math.abs(value - rounded) < 0.05) {
            rounded.toInt().toString()
        } else {
            String.format(java.util.Locale.getDefault(), "%.1f", value)
        }
    }

    private fun showSmokePicker() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("What did you smoke?")
            .setMessage("Your banked smoke-free time is preserved. The exposure window pauses recovery — it doesn't erase it.")
            .setPositiveButton("Cigarette (~10 min)") { _, _ ->
                recordSmokeAction(
                    com.smokless.smokeless.data.entity.Substance.TOBACCO.exposureMs,
                    com.smokless.smokeless.data.entity.Substance.TOBACCO,
                )
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Weed (~30 min)") { _, _ ->
                recordSmokeAction(
                    com.smokless.smokeless.data.entity.Substance.CANNABIS.exposureMs,
                    com.smokless.smokeless.data.entity.Substance.CANNABIS,
                )
            }
            .show()
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.accent_primary)
        binding.swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.surface_card)
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshData()
            binding.swipeRefresh.postDelayed({ binding.swipeRefresh.isRefreshing = false }, 800)
        }
    }

    private var primarySubstance: Substance = Substance.DEFAULT

    /**
     * Body's recovery milestones (heart rate, CO, circulation, etc.) reset
     * after each cigarette — that's biology, not a UX choice. So the
     * milestone list is driven by the current clean streak (time since last
     * smoke). Lifetime banked time is no longer surfaced here; the
     * post-log snackbar reframes a slip with banked time when it's
     * actually motivating.
     */
    private fun updateRecoveryHero(timeSinceLastSmokeMs: Long) {
        val cleanHours = timeSinceLastSmokeMs / 3_600_000L
        val substance = primarySubstance
        val milestones = HealthBenefits.getMilestones(cleanHours, substance)
        val achievedCount = milestones.count { it.isAchieved }
        val total = milestones.size

        binding.sectionRecoveryHero.textMilestoneCount.text = "$achievedCount / $total"
        binding.sectionRecoveryHero.progressMilestones.max = total
        binding.sectionRecoveryHero.progressMilestones.progress = achievedCount

        val current = HealthBenefits.getCurrentMilestone(cleanHours, substance)
        if (current != null) {
            binding.sectionRecoveryHero.textCurrentMilestone.text =
                "You are at: ${current.icon} ${current.title}"
        } else {
            binding.sectionRecoveryHero.textCurrentMilestone.text =
                "You are at: 🌱 Starting"
        }

        val next = HealthBenefits.getNextMilestone(cleanHours, substance)
        if (next != null) {
            val remaining = (next.hours - cleanHours).coerceAtLeast(0L)
            binding.sectionRecoveryHero.textNextMilestone.text =
                "Next: ${next.icon} ${next.title} — in ${formatDuration(remaining)}"
        } else {
            binding.sectionRecoveryHero.textNextMilestone.text =
                "You've reached every milestone."
        }

        if (achievedCount != lastAchievedCount) {
            timelineAdapter.submit(milestones)
            lastAchievedCount = achievedCount
        }
    }

    private fun formatDuration(hours: Long): String = when {
        hours <= 0 -> "now"
        hours < 24 -> "${hours}h"
        hours < 24 * 30 -> "${hours / 24}d"
        hours < 24 * 365 -> "${hours / (24 * 30)}mo"
        else -> "${hours / (24 * 365)}y"
    }

    private fun updatePausedBadge(timeSinceLastSmokeMs: Long) {
        binding.sectionRecoveryHero.textPausedBadge.visibility =
            if (timeSinceLastSmokeMs in 1 until pausedThresholdMs) View.VISIBLE else View.GONE
    }

    private fun observeViewModel() {
        viewModel.primarySubstance.observe(this) { substance ->
            primarySubstance = substance
            copy = SubstanceCopy.forSubstance(substance)
            // Substance change re-anchors the milestone list. Force a refresh
            // even when the achieved-count number is unchanged.
            lastAchievedCount = -1
            updateRecoveryHero(viewModel.currentScore.value ?: 0L)
        }

        viewModel.currentScore.observe(this) { score ->
            updatePausedBadge(score)
            // Milestones reflect the current clean streak, so they update each tick.
            updateRecoveryHero(score)
        }

        // bankedSmokeFreeMs is still observed so the post-log snackbar can
        // report the user's smoke-free balance, even though the records card
        // (which used to render it on the home screen) now lives in
        // StatsActivity.
        viewModel.bankedSmokeFreeMs.observe(this) { /* snackbar reads .value */ }

        // perSubstancePace drives the hero verdict block: one chip per
        // substance the user logs (just one for single-substance users,
        // stacked rows for tobacco + cannabis dogfooders). The combined
        // todayPace LiveData stays computed in the ViewModel but no longer
        // surfaces — pooling unlike substances into one verdict was
        // misleading for multi-substance users.
        viewModel.perSubstancePace.observe(this) { entries -> updateHeroPace(entries) }

        viewModel.firstSmokeOfDay.observe(this) { fs -> updateFirstSmokeOfDay(fs) }
        viewModel.substanceLevels.observe(this) { levels -> updateSubstanceLevels(levels) }
        viewModel.triggerWindows.observe(this) { windows -> updateTriggerWindows(windows) }
        viewModel.weeklyDigest.observe(this) { digest -> updateWeeklyDigest(digest) }
    }

    private fun updateWeeklyDigest(digest: ScoreCalculator.WeeklyDigest) {
        val sectionRoot = binding.sectionWeeklyDigest.root
        val smokeCount = sectionRoot.findViewById<android.widget.TextView>(R.id.textDigestSmokeCount)
        val smokeLabel = sectionRoot.findViewById<android.widget.TextView>(R.id.textDigestSmokeLabel)
        val smokeDelta = sectionRoot.findViewById<android.widget.TextView>(R.id.textDigestSmokeDelta)
        val cleanDays = sectionRoot.findViewById<android.widget.TextView>(R.id.textDigestCleanDays)
        val longest = sectionRoot.findViewById<android.widget.TextView>(R.id.textDigestLongestStretch)
        val milestonesGroup = sectionRoot.findViewById<android.widget.LinearLayout>(R.id.groupDigestMilestones)
        val milestonesText = sectionRoot.findViewById<android.widget.TextView>(R.id.textDigestMilestones)

        smokeCount.text = formatDoseCount(digest.smokesThisWeek)
        val unit = if (kotlin.math.abs(digest.smokesThisWeek - 1.0) < 0.01) copy.unit else copy.units
        smokeLabel.text = "$unit this week"

        val change = digest.smokeChangePercent
        if (change == null) {
            smokeDelta.visibility = View.GONE
        } else {
            val rounded = change.roundToInt()
            val (txt, colorRes) = when {
                rounded >= 10 -> "↓ ${rounded}% vs last week" to R.color.status_champion
                rounded >= 5 -> "↓ ${rounded}% vs last week" to R.color.status_strong
                rounded <= -10 -> "↑ ${-rounded}% vs last week" to R.color.status_reset
                rounded <= -5 -> "↑ ${-rounded}% vs last week" to R.color.accent_amber
                else -> "→ steady vs last week" to R.color.text_secondary
            }
            smokeDelta.text = txt
            smokeDelta.setTextColor(ContextCompat.getColor(this, colorRes))
            smokeDelta.visibility = View.VISIBLE
        }

        cleanDays.text = "${digest.cleanDaysThisWeek}/7"

        val longestMs = digest.longestStretchMs
        val longestHours = longestMs / 3_600_000L
        longest.text = when {
            longestHours >= 24 -> "${longestHours / 24}d"
            longestHours >= 1 -> "${longestHours}h"
            else -> "${longestMs / 60_000}m"
        }

        val crossed = digest.milestonesReachedThisWeek
        if (crossed.isEmpty()) {
            milestonesGroup.visibility = View.GONE
        } else {
            milestonesText.text = crossed.joinToString(" · ") { "${it.icon} ${it.title}" }
            milestonesGroup.visibility = View.VISIBLE
        }

        renderSelfEvalTrend(sectionRoot)
    }

    /**
     * Prior 4 weekly self-eval ratings per metric — trend only, no judgment.
     * Renders straight from the local mirror ([WeeklySelfEvalStore]) so the
     * trend is visible even when Bios isn't installed / approved.
     */
    private fun renderSelfEvalTrend(sectionRoot: View) {
        val rowsGroup = sectionRoot.findViewById<android.widget.LinearLayout>(R.id.groupDigestSelfEvalRows)
        val empty = sectionRoot.findViewById<android.widget.TextView>(R.id.textDigestSelfEvalEmpty)
        val openBtn = sectionRoot.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDigestOpenSelfEval)
        val store = com.smokless.smokeless.util.WeeklySelfEvalStore(applicationContext)

        rowsGroup.removeAllViews()
        var anyRendered = false
        val numFmt = DecimalFormat("0.#")
        for ((key, label) in SELF_EVAL_DIGEST_LABELS) {
            val recent = store.recent(key, limit = 4)
            if (recent.isEmpty()) continue
            anyRendered = true
            val row = android.widget.TextView(this).apply {
                val ordered = recent.reversed() // oldest → newest
                val nums = ordered.joinToString(" → ") { numFmt.format(it.value) }
                text = "$label  $nums"
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
                textSize = 13f
                setPadding(0, (4 * resources.displayMetrics.density).toInt(), 0, 0)
            }
            rowsGroup.addView(row)
        }
        empty.visibility = if (anyRendered) View.GONE else View.VISIBLE

        openBtn.setOnClickListener {
            startActivity(Intent(this, WeeklySelfEvalActivity::class.java))
        }
    }

    private fun formatHourLabel(hour: Int): String = String.format("%02d:00", hour)

    private fun updateTriggerWindows(windows: List<ScoreCalculator.TriggerWindow>) {
        val sectionRoot = binding.sectionTriggerMap.root
        val group = sectionRoot.findViewById<android.widget.LinearLayout>(R.id.groupTriggerWindows)
        val empty = sectionRoot.findViewById<android.widget.TextView>(R.id.textTriggerEmpty)
        val headsUp = sectionRoot.findViewById<android.widget.TextView>(R.id.textTriggerHeadsUp)

        group.removeAllViews()
        if (windows.isEmpty() || windows.all { it.peakHours.isEmpty() }) {
            empty.visibility = View.VISIBLE
            headsUp.visibility = View.GONE
            return
        }
        empty.visibility = View.GONE

        val nearPeakSubs = windows.filter { it.nearPeakNow }
        if (nearPeakSubs.isNotEmpty()) {
            val joined = nearPeakSubs.joinToString(" + ") { substanceLabel(it.substance) }
            headsUp.text = "⏰ Heads up — typically a smoke window for you ($joined)"
            headsUp.visibility = View.VISIBLE
        } else {
            headsUp.visibility = View.GONE
        }

        val inflater = layoutInflater
        val nowHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        for (tw in windows) {
            val row = inflater.inflate(R.layout.item_trigger_window, group, false)
            val labelView = row.findViewById<android.widget.TextView>(R.id.textTriggerSubstance)
            val peaksView = row.findViewById<android.widget.TextView>(R.id.textTriggerPeaks)
            val strip = row.findViewById<android.widget.LinearLayout>(R.id.hourStrip)

            labelView.text = substanceLabel(tw.substance)
            peaksView.text = if (tw.peakHours.isEmpty()) {
                "—"
            } else {
                tw.peakHours.joinToString(" · ") { formatHourLabel(it) }
            }
            renderHourStrip(strip, tw, nowHour)
            group.addView(row)
        }
    }

    private fun renderHourStrip(
        container: android.widget.LinearLayout,
        tw: ScoreCalculator.TriggerWindow,
        nowHour: Int,
    ) {
        container.removeAllViews()
        val max = tw.hourCounts.maxOrNull()?.coerceAtLeast(1) ?: 1
        val peakSet = tw.peakHours.toSet()
        val baseColor = ContextCompat.getColor(this, R.color.accent_primary)
        val peakColor = ContextCompat.getColor(this, R.color.accent_amber)
        val dimColor = ContextCompat.getColor(this, R.color.progress_track)
        val nowOutline = ContextCompat.getColor(this, R.color.text_primary)

        for (h in 0..23) {
            val cell = View(this).apply {
                val lp = android.widget.LinearLayout.LayoutParams(0, 0).apply {
                    weight = 1f
                    height = android.widget.LinearLayout.LayoutParams.MATCH_PARENT
                    marginEnd = if (h < 23) (resources.displayMetrics.density * 1).toInt() else 0
                }
                layoutParams = lp
            }
            val count = tw.hourCounts[h]
            val ratio = count.toDouble() / max
            val isPeak = h in peakSet
            val baseFill = when {
                count == 0 -> dimColor
                isPeak -> peakColor
                else -> baseColor
            }
            val alpha = if (count == 0) 0.35f else (0.45f + 0.55f * ratio).toFloat()
            val bg = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = resources.displayMetrics.density * 3
                setColor(baseFill)
                if (h == nowHour) setStroke((resources.displayMetrics.density * 1.5f).toInt(), nowOutline)
            }
            cell.background = bg
            cell.alpha = alpha
            container.addView(cell)
        }
    }

    private fun substanceLabel(substance: Substance): String = when (substance) {
        Substance.TOBACCO -> "🚬 Tobacco"
        Substance.CANNABIS -> "🌿 Cannabis"
    }

    private fun substanceUnit(substance: Substance, dose: Double): String = when (substance) {
        Substance.TOBACCO -> if (dose == 1.0) "cig" else "cigs"
        Substance.CANNABIS -> if (dose == 1.0) "session" else "sessions"
    }

    /**
     * Hero pace block: one chip per substance the user logs (just one row
     * for single-substance users; tobacco + cannabis users see them
     * stacked, each with its own delta badge so the headline doesn't pool
     * unlike substances into a misleading single verdict).
     */
    private fun updateHeroPace(entries: List<ScoreCalculator.SubstancePace>) {
        val container = binding.containerTodayPace
        container.removeAllViews()
        if (entries.isEmpty()) {
            val chip = layoutInflater.inflate(
                R.layout.item_hero_pace_chip, container, false
            )
            val copy = chip.findViewById<android.widget.TextView>(R.id.textHeroPaceCopy)
            copy.text = "Keep logging — your pace verdict shows up after 3 days"
            container.addView(chip)
            return
        }
        val showSubstanceLabel = entries.size > 1
        val doseFormat = DecimalFormat("0.##")
        val rowGap = (8 * resources.displayMetrics.density).toInt()
        entries.forEachIndexed { index, entry ->
            val chip = layoutInflater.inflate(
                R.layout.item_hero_pace_chip, container, false
            )
            val label = chip.findViewById<android.widget.TextView>(R.id.textHeroPaceSubstanceLabel)
            val deltaView = chip.findViewById<android.widget.TextView>(R.id.textHeroPaceDelta)
            val copy = chip.findViewById<android.widget.TextView>(R.id.textHeroPaceCopy)

            if (showSubstanceLabel) {
                label.text = substanceLabel(entry.substance).uppercase()
                label.visibility = View.VISIBLE
            }

            val pace = entry.pace
            val unit = substanceUnit(entry.substance, pace.actualToday)
            val actualStr = doseFormat.format(pace.actualToday)
            val typicalStr = doseFormat.format(pace.typicalByNow)
            val (text, colorRes) = when (pace.state) {
                ScoreCalculator.PaceState.CALIBRATING ->
                    "Keep logging — verdict shows up after 3 days" to R.color.text_secondary
                ScoreCalculator.PaceState.AHEAD ->
                    "Ahead of pace — $actualStr $unit today, usually $typicalStr by now" to R.color.status_champion
                ScoreCalculator.PaceState.SLIGHTLY_AHEAD ->
                    "Slightly ahead — $actualStr $unit today, usually $typicalStr by now" to R.color.accent_blue
                ScoreCalculator.PaceState.ON_PACE ->
                    "On pace — $actualStr $unit today, usually $typicalStr by now" to R.color.accent_amber
                ScoreCalculator.PaceState.BEHIND ->
                    "Behind pace — $actualStr $unit today, usually $typicalStr by now" to R.color.status_reset
                ScoreCalculator.PaceState.CLEAN_TODAY ->
                    "Matching your clean baseline — 0 today" to R.color.status_champion
                ScoreCalculator.PaceState.CLEAN_BREAK ->
                    "$actualStr $unit today — you've been clean lately, gentle reset" to R.color.accent_amber
            }
            copy.text = text
            copy.setTextColor(ContextCompat.getColor(this, colorRes))

            val showBadge = when (pace.state) {
                ScoreCalculator.PaceState.CALIBRATING,
                ScoreCalculator.PaceState.CLEAN_TODAY,
                ScoreCalculator.PaceState.CLEAN_BREAK -> false
                else -> true
            }
            if (showBadge) {
                val delta = pace.actualToday - pace.typicalByNow
                val absStr = doseFormat.format(kotlin.math.abs(delta))
                // ¼-cig deadband on the displayed delta — avoids "+0.1"
                // jitter while the verdict reads ON_PACE.
                val badge = when {
                    kotlin.math.abs(delta) < 0.25 -> "±0"
                    delta > 0 -> "+$absStr"
                    else -> "−$absStr"
                }
                deltaView.text = badge
                deltaView.setTextColor(ContextCompat.getColor(this, colorRes))
                deltaView.visibility = View.VISIBLE
            }

            if (index > 0) {
                val lp = chip.layoutParams as android.widget.LinearLayout.LayoutParams
                lp.topMargin = rowGap
                chip.layoutParams = lp
            }
            container.addView(chip)
        }
    }

    private fun formatHourOfDay(hourFloat: Double): String {
        val h = hourFloat.toInt().coerceIn(0, 23)
        val m = ((hourFloat - h) * 60).toInt().coerceIn(0, 59)
        return String.format("%02d:%02d", h, m)
    }

    private fun formatSignedMinutes(deltaMin: Long): String {
        val abs = kotlin.math.abs(deltaMin)
        val sign = if (deltaMin >= 0) "+" else "−"
        return if (abs >= 60) {
            val h = abs / 60
            val m = abs % 60
            if (m == 0L) "$sign${h}h" else "$sign${h}h ${m}m"
        } else {
            "$sign${abs}m"
        }
    }

    private fun updateFirstSmokeOfDay(fs: ScoreCalculator.FirstSmokeOfDay) {
        val text = binding.sectionProgression.root.findViewById<android.widget.TextView>(
            R.id.textFirstSmoke
        )
        if (fs.typicalFirstHour == null && fs.todayFirstClockHour == null) {
            text.text = "Log a few mornings to compare today against your usual."
            text.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            return
        }
        val typicalText = fs.typicalFirstHour?.let { "typically ${formatHourOfDay(it)}" }

        if (fs.todayFirstClockHour == null) {
            val typ = typicalText ?: "no typical time yet"
            text.text = "Haven't smoked yet today — $typ."
            text.setTextColor(ContextCompat.getColor(this, R.color.status_champion))
            return
        }
        val todayText = "First smoke at ${formatHourOfDay(fs.todayFirstClockHour)}"
        val delta = fs.deltaMinutes
        val combined = when {
            delta == null -> "$todayText."
            else -> "$todayText — $typicalText (${formatSignedMinutes(delta)} ${if (delta >= 0) "later" else "earlier"})."
        }
        text.text = combined
        val color = when {
            delta == null -> R.color.text_secondary
            delta >= 30 -> R.color.status_champion
            delta >= -15 -> R.color.accent_amber
            else -> R.color.status_reset
        }
        text.setTextColor(ContextCompat.getColor(this, color))
    }

    private fun updateSubstanceLevels(levels: List<ScoreCalculator.SubstanceLevel>) {
        val group = binding.sectionProgression.root.findViewById<android.widget.LinearLayout>(
            R.id.groupSubstanceLevels
        )
        val empty = binding.sectionProgression.root.findViewById<android.widget.TextView>(
            R.id.textSubstanceLevelsEmpty
        )
        group.removeAllViews()
        if (levels.isEmpty()) {
            empty.visibility = View.VISIBLE
            return
        }
        empty.visibility = View.GONE
        val inflater = layoutInflater
        for (level in levels) {
            val row = inflater.inflate(R.layout.item_substance_level, group, false)
            val label = row.findViewById<android.widget.TextView>(R.id.textLevelLabel)
            val value = row.findViewById<android.widget.TextView>(R.id.textLevelValue)
            val bar = row.findViewById<
                com.google.android.material.progressindicator.LinearProgressIndicator
            >(R.id.progressLevel)
            val details = row.findViewById<android.widget.TextView>(R.id.textLevelDetails)

            val (compoundName, halfLifeText) = when (level.substance) {
                Substance.TOBACCO -> "Nicotine" to "half-life ~2h"
                Substance.CANNABIS -> "THC" to "half-life ~25h"
            }
            label.text = "${substanceLabel(level.substance)} · $compoundName"
            value.text = "${level.percentRemaining.roundToInt()}%"
            bar.progress = level.percentRemaining.roundToInt().coerceIn(0, 100)

            val hoursSince = level.hoursSinceLast
            val sinceText = when {
                hoursSince < 1.0 -> "${(hoursSince * 60).roundToInt()} min ago"
                hoursSince < 48.0 -> "${hoursSince.roundToInt()}h ago"
                else -> "${(hoursSince / 24).roundToInt()}d ago"
            }
            details.text = "Last log $sinceText · $halfLifeText"

            val colorRes = when {
                level.percentRemaining >= 50 -> R.color.status_reset
                level.percentRemaining >= 20 -> R.color.accent_amber
                else -> R.color.status_champion
            }
            bar.setIndicatorColor(ContextCompat.getColor(this, colorRes))
            value.setTextColor(ContextCompat.getColor(this, colorRes))

            group.addView(row)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshData()
        refreshHandler.postDelayed(refreshRunnable, 1000)
        // Idempotent — schedules only when the alarm hasn't been set yet.
        com.smokless.smokeless.util.TriggerWindowReceiver.schedule(this)
        com.smokless.smokeless.util.WeeklyDigestReceiver.schedule(this)
    }

    override fun onPause() {
        super.onPause()
        refreshHandler.removeCallbacks(refreshRunnable)
    }

    private fun updateWidgets() {
        val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(this)
        val widgetComponent = android.content.ComponentName(this, com.smokless.smokeless.widget.SmokelessWidget::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(widgetComponent)
        for (id in widgetIds) {
            com.smokless.smokeless.widget.SmokelessWidget.updateWidget(this, appWidgetManager, id)
        }
    }
}
