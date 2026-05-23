package com.smokless.smokeless

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.smokless.smokeless.data.entity.Substance
import com.smokless.smokeless.databinding.ActivityMainBinding
import com.smokless.smokeless.ui.main.ChartData
import com.smokless.smokeless.ui.main.CravingRideOutSheet
import com.smokless.smokeless.ui.main.MainViewModel
import com.smokless.smokeless.ui.main.RecoveryTimelineAdapter
import com.smokless.smokeless.ui.main.ScoreAdapter
import com.smokless.smokeless.ui.main.ScoreData
import com.smokless.smokeless.util.HealthBenefits
import com.smokless.smokeless.util.ScoreCalculator
import com.smokless.smokeless.util.SubstanceCopy
import com.smokless.smokeless.util.TimeFormatter
import java.text.DecimalFormat
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private lateinit var statsAdapter: ScoreAdapter
    private val timelineAdapter = RecoveryTimelineAdapter()
    private lateinit var statsSheet: BottomSheetBehavior<View>

    private val refreshHandler = Handler(Looper.getMainLooper())

    private var currentPeriod = "month"
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
        setupStatsSheet()
        setupStatsRecycler()
        setupChipGroup()
        setupCharts()
        setupFab()
        setupResistFab()
        setupCollapsibleSections()
        setupSwipeRefresh()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
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

    private fun setupStatsSheet() {
        statsSheet = BottomSheetBehavior.from(binding.statsSheet)
        statsSheet.state = BottomSheetBehavior.STATE_COLLAPSED
        binding.statsSheetHandle.setOnClickListener {
            statsSheet.state =
                if (statsSheet.state == BottomSheetBehavior.STATE_EXPANDED)
                    BottomSheetBehavior.STATE_COLLAPSED
                else
                    BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun setupStatsRecycler() {
        statsAdapter = ScoreAdapter()
        binding.sectionStatistics.recyclerStats.layoutManager = LinearLayoutManager(this)
        binding.sectionStatistics.recyclerStats.adapter = statsAdapter
    }

    private fun setupChipGroup() {
        binding.sectionStatistics.chipGroupPeriod.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener

            when (checkedIds[0]) {
                R.id.chipToday -> {
                    currentPeriod = "day"
                    updatePeriodHeader("☀️", "Today")
                }
                R.id.chipWeek -> {
                    currentPeriod = "week"
                    updatePeriodHeader("📋", "This Week")
                }
                R.id.chipMonth -> {
                    currentPeriod = "month"
                    updatePeriodHeader("📆", "This Month")
                }
                R.id.chipYear -> {
                    currentPeriod = "year"
                    updatePeriodHeader("📅", "This Year")
                }
                R.id.chipAllTime -> {
                    currentPeriod = "all"
                    updatePeriodHeader("📊", "All Time")
                }
            }

            val statsRecycler = binding.sectionStatistics.recyclerStats
            val quickStats = binding.sectionQuickStats.root
            statsRecycler.animate().alpha(0f).setDuration(150).withEndAction {
                viewModel.setChartPeriod(currentPeriod)
                updateStatsForPeriod()
                updateChartLabels()

                val scores = when (currentPeriod) {
                    "day" -> viewModel.dayScores.value
                    "week" -> viewModel.weekScores.value
                    "month" -> viewModel.monthScores.value
                    "year" -> viewModel.yearScores.value
                    "all" -> viewModel.allTimeScores.value
                    else -> null
                }
                scores?.let { updatePeriodCards(it) }

                statsRecycler.animate().alpha(1f).setDuration(200).start()
            }.start()
            quickStats.animate().alpha(0f).setDuration(150).withEndAction {
                quickStats.animate().alpha(1f).setDuration(200).setStartDelay(100).start()
            }.start()
        }

        binding.sectionStatistics.chipMonth.isChecked = true
    }

    private fun updateChartLabels() {
        binding.sectionCharts.textTrendChartTitle.text = when (currentPeriod) {
            "day" -> "Hourly Trend"
            "week" -> "Daily Trend"
            "month" -> "7-Day Average Trend"
            "year" -> "Monthly Trend"
            "all" -> "Long-term Trend"
            else -> "Trend"
        }
        binding.sectionCharts.textBarChartTitle.text = when (currentPeriod) {
            "day" -> "Hourly Count"
            else -> "Daily Count"
        }
    }

    private fun updatePeriodHeader(icon: String, title: String) {
        binding.sectionStatistics.textPeriodIcon.text = icon
        binding.sectionStatistics.textPeriodTitle.text = title

        binding.sectionQuickStats.textQuickStatsTitle.text = when (currentPeriod) {
            "day" -> "Today's Highlights"
            "week" -> "This Week's Highlights"
            "month" -> "This Month's Highlights"
            "year" -> "This Year's Highlights"
            "all" -> "All-Time Highlights"
            else -> "Period Highlights"
        }
        binding.sectionQuickStats.textBestTodayLabel.text = when (currentPeriod) {
            "day" -> "Today's Streak"
            "week" -> "Week Streak"
            "month" -> "Month Streak"
            "year" -> "Year Streak"
            "all" -> "Best Streak"
            else -> "Clean Streak"
        }
        binding.sectionQuickStats.textCountTodayLabel.text = when (currentPeriod) {
            "day" -> "Today"
            "week" -> "This Week"
            "month" -> "This Month"
            "year" -> "This Year"
            "all" -> "All Time"
            else -> "Total"
        }
    }

    private fun updateStatsForPeriod() {
        val scores: List<ScoreData>? = when (currentPeriod) {
            "day" -> viewModel.dayScores.value
            "week" -> viewModel.weekScores.value
            "month" -> viewModel.monthScores.value
            "year" -> viewModel.yearScores.value
            "all" -> viewModel.allTimeScores.value
            else -> null
        }
        scores?.let {
            statsAdapter.setScores(it)
            updatePeriodCount(it)
        }
    }

    private fun updatePeriodCount(scores: List<ScoreData>) {
        for (score in scores) {
            if (score.type == ScoreData.StatType.COUNT) {
                val count = score.value
                binding.sectionStatistics.textPeriodCount.text = "$count ${copy.unitFor(count)}"
                return
            }
        }
        binding.sectionStatistics.textPeriodCount.text = "0 ${copy.units}"
    }

    private fun setupCharts() {
        val barChart = binding.sectionCharts.barChart
        barChart.setBackgroundColor(Color.TRANSPARENT)
        barChart.description.isEnabled = false
        barChart.setTouchEnabled(true)
        barChart.isDragEnabled = true
        barChart.setScaleEnabled(false)
        barChart.setPinchZoom(false)
        barChart.setDrawGridBackground(false)
        barChart.legend.isEnabled = false
        barChart.extraBottomOffset = 8f
        barChart.setFitBars(true)
        barChart.setNoDataText("Start tracking to see your patterns here")
        barChart.setNoDataTextColor(ContextCompat.getColor(this, R.color.text_secondary))

        val barXAxis = barChart.xAxis
        barXAxis.position = XAxis.XAxisPosition.BOTTOM
        barXAxis.setDrawGridLines(false)
        barXAxis.textColor = ContextCompat.getColor(this, R.color.text_tertiary)
        barXAxis.textSize = 10f
        barXAxis.granularity = 1f
        barXAxis.setAvoidFirstLastClipping(true)

        val barLeftAxis = barChart.axisLeft
        barLeftAxis.setDrawGridLines(true)
        barLeftAxis.gridColor = ContextCompat.getColor(this, R.color.divider)
        barLeftAxis.textColor = ContextCompat.getColor(this, R.color.text_tertiary)
        barLeftAxis.textSize = 10f
        barLeftAxis.axisMinimum = 0f
        barLeftAxis.granularity = 1f
        barLeftAxis.setDrawZeroLine(true)
        barLeftAxis.zeroLineColor = ContextCompat.getColor(this, R.color.divider)
        barChart.axisRight.isEnabled = false

        val lineChart = binding.sectionCharts.lineChart
        lineChart.setBackgroundColor(Color.TRANSPARENT)
        lineChart.description.isEnabled = false
        lineChart.setTouchEnabled(true)
        lineChart.isDragEnabled = true
        lineChart.setScaleEnabled(false)
        lineChart.setPinchZoom(false)
        lineChart.setDrawGridBackground(false)
        lineChart.legend.isEnabled = false
        lineChart.extraBottomOffset = 8f
        lineChart.setNoDataText("Your progress trends will appear here")
        lineChart.setNoDataTextColor(ContextCompat.getColor(this, R.color.text_secondary))

        val lineXAxis = lineChart.xAxis
        lineXAxis.position = XAxis.XAxisPosition.BOTTOM
        lineXAxis.setDrawGridLines(false)
        lineXAxis.textColor = ContextCompat.getColor(this, R.color.text_tertiary)
        lineXAxis.textSize = 10f
        lineXAxis.granularity = 1f
        lineXAxis.setAvoidFirstLastClipping(true)

        val lineLeftAxis = lineChart.axisLeft
        lineLeftAxis.setDrawGridLines(true)
        lineLeftAxis.gridColor = ContextCompat.getColor(this, R.color.divider)
        lineLeftAxis.textColor = ContextCompat.getColor(this, R.color.text_tertiary)
        lineLeftAxis.textSize = 10f
        lineLeftAxis.axisMinimum = 0f
        lineLeftAxis.granularity = 1f
        lineLeftAxis.setDrawZeroLine(true)
        lineLeftAxis.zeroLineColor = ContextCompat.getColor(this, R.color.divider)
        lineChart.axisRight.isEnabled = false
    }

    private fun updateCharts(data: ChartData?) {
        if (data == null) {
            binding.sectionCharts.lineChart.clear()
            binding.sectionCharts.lineChart.visibility = View.INVISIBLE
            binding.sectionCharts.emptyStateTrend.visibility = View.VISIBLE
            binding.sectionCharts.barChart.clear()
            binding.sectionCharts.barChart.visibility = View.INVISIBLE
            binding.sectionCharts.emptyStateBar.visibility = View.VISIBLE
            binding.sectionCharts.textChartTrend.text = "No data yet"
            binding.sectionCharts.textBarChartAvg.text = "Avg: 0/day"
            return
        }

        binding.sectionCharts.lineChart.visibility = View.VISIBLE
        binding.sectionCharts.emptyStateTrend.visibility = View.GONE
        binding.sectionCharts.barChart.visibility = View.VISIBLE
        binding.sectionCharts.emptyStateBar.visibility = View.GONE

        val maxCount = data.dailyCounts.maxOrNull() ?: 0
        val maxAverage = data.movingAverage.maxOrNull() ?: 0.0
        val dataMaxValue = kotlin.math.max(maxCount.toFloat(), maxAverage.toFloat())
        val chartMaxValue = kotlin.math.max(dataMaxValue * 1.2f, 5f)

        val barEntries = data.dailyCounts.mapIndexed { index, count ->
            BarEntry(index.toFloat(), count.toFloat())
        }
        if (barEntries.isNotEmpty()) {
            binding.sectionCharts.barChart.visibility = View.VISIBLE
            binding.sectionCharts.emptyStateBar.visibility = View.GONE
            val barDataSet = BarDataSet(barEntries, "Cigarettes").apply {
                color = ContextCompat.getColor(this@MainActivity, R.color.accent_amber)
                setDrawValues(true)
                valueTextColor = ContextCompat.getColor(this@MainActivity, R.color.text_secondary)
                valueTextSize = 9f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return if (value > 0) value.toInt().toString() else ""
                    }
                }
            }
            val barData = BarData(barDataSet).apply { barWidth = 0.6f }
            binding.sectionCharts.barChart.axisLeft.axisMaximum = kotlin.math.max(chartMaxValue, 1f)
            binding.sectionCharts.barChart.xAxis.valueFormatter = IndexAxisValueFormatter(getLimitedLabels(data.labels))
            binding.sectionCharts.barChart.xAxis.setLabelCount(getLimitedLabelCount(data.labels.size), false)
            binding.sectionCharts.barChart.data = barData
            binding.sectionCharts.barChart.invalidate()
        } else {
            binding.sectionCharts.barChart.visibility = View.INVISIBLE
            binding.sectionCharts.emptyStateBar.visibility = View.VISIBLE
        }

        val avgLabel = when (currentPeriod) {
            "day" -> "Total: ${data.dailyCounts.sum()}"
            else -> String.format("Avg: %.1f/day", data.avgDailyCount)
        }
        binding.sectionCharts.textBarChartAvg.text = avgLabel

        val lineEntries = data.movingAverage.mapIndexed { index, avg ->
            Entry(index.toFloat(), avg.toFloat())
        }
        if (lineEntries.isNotEmpty()) {
            binding.sectionCharts.lineChart.visibility = View.VISIBLE
            binding.sectionCharts.emptyStateTrend.visibility = View.GONE
            val lineDataSet = LineDataSet(lineEntries, "Trend").apply {
                color = ContextCompat.getColor(this@MainActivity, R.color.accent_primary)
                setCircleColor(ContextCompat.getColor(this@MainActivity, R.color.accent_primary))
                lineWidth = 2.5f
                circleRadius = 3f
                setDrawCircleHole(true)
                circleHoleRadius = 1.5f
                circleHoleColor = ContextCompat.getColor(this@MainActivity, R.color.surface_card)
                setDrawValues(false)
                mode = LineDataSet.Mode.LINEAR
                cubicIntensity = 0.1f
                setDrawFilled(true)
                fillColor = ContextCompat.getColor(this@MainActivity, R.color.accent_primary)
                fillAlpha = 30
            }
            val lineData = LineData(lineDataSet)
            binding.sectionCharts.lineChart.axisLeft.axisMaximum = kotlin.math.max(chartMaxValue, 1f)
            binding.sectionCharts.lineChart.xAxis.valueFormatter = IndexAxisValueFormatter(getLimitedLabels(data.labels))
            binding.sectionCharts.lineChart.xAxis.setLabelCount(getLimitedLabelCount(data.labels.size), false)
            binding.sectionCharts.lineChart.data = lineData
            binding.sectionCharts.lineChart.invalidate()
        } else {
            binding.sectionCharts.lineChart.visibility = View.INVISIBLE
            binding.sectionCharts.emptyStateTrend.visibility = View.VISIBLE
        }

        updateTrendIndicator(data)
    }

    private fun getLimitedLabels(labels: List<String>): List<String> {
        if (labels.size <= 15) return labels
        val step = labels.size / 12
        return labels.mapIndexed { index, label ->
            if (index % step == 0 || index == labels.size - 1) label else ""
        }
    }

    private fun getLimitedLabelCount(totalLabels: Int): Int = when {
        totalLabels <= 7 -> totalLabels
        totalLabels <= 15 -> 7
        totalLabels <= 30 -> 10
        else -> 12
    }

    private fun updateTrendIndicator(data: ChartData) {
        val absChange = kotlin.math.abs(data.trendPercentage)
        when {
            data.isImproving && absChange >= 10 -> {
                binding.sectionCharts.textChartTrend.text = String.format("↓ Down %.0f%%", absChange)
                binding.sectionCharts.textChartTrend.setTextColor(ContextCompat.getColor(this, R.color.status_champion))
            }
            data.isImproving && absChange >= 5 -> {
                binding.sectionCharts.textChartTrend.text = String.format("↓ Down %.0f%%", absChange)
                binding.sectionCharts.textChartTrend.setTextColor(ContextCompat.getColor(this, R.color.status_strong))
            }
            absChange < 5 -> {
                binding.sectionCharts.textChartTrend.text = "→ Stable"
                binding.sectionCharts.textChartTrend.setTextColor(ContextCompat.getColor(this, R.color.status_steady))
            }
            !data.isImproving && absChange < 20 -> {
                binding.sectionCharts.textChartTrend.text = String.format("↑ Up %.0f%%", absChange)
                binding.sectionCharts.textChartTrend.setTextColor(ContextCompat.getColor(this, R.color.accent_amber))
            }
            else -> {
                binding.sectionCharts.textChartTrend.text = String.format("↑ Up %.0f%%", absChange)
                binding.sectionCharts.textChartTrend.setTextColor(ContextCompat.getColor(this, R.color.status_reset))
            }
        }
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
        binding.sectionRecoveryHero.textBankedTimer.animate()
            .scaleX(0.92f).scaleY(0.92f).alpha(0.6f)
            .setDuration(150)
            .withEndAction {
                binding.sectionRecoveryHero.textBankedTimer.animate()
                    .scaleX(1f).scaleY(1f).alpha(1f)
                    .setDuration(200).start()
            }.start()

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

    private fun setupResistFab() {
        binding.fabResist.setOnClickListener { view ->
            view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
            viewModel.recordCravingResisted()
            val hours = (viewModel.currentScore.value ?: 0L) / 3_600_000L
            CravingRideOutSheet(
                context = this,
                onMadeIt = { showResistConfirmation() },
                onSmokedAnyway = { showSmokePicker() },
                substance = primarySubstance,
                hoursSinceLast = hours,
            ).show()
        }
    }

    /**
     * Opens the same substance picker the "I Smoked" FAB uses. Lifted to its
     * own method so the ride-out sheet can route to it cleanly when the user
     * taps "I smoked anyway."
     */
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

    private fun setupCollapsibleSections() {
        setupCollapsible(
            binding.sectionCharts.headerCharts,
            binding.sectionCharts.contentCharts,
            binding.sectionCharts.chevronCharts
        )
        setupCollapsible(
            binding.sectionRecords.headerRecords,
            binding.sectionRecords.contentRecords,
            binding.sectionRecords.chevronRecords
        )
    }

    private fun setupCollapsible(header: View, content: View, chevron: android.widget.TextView) {
        header.setOnClickListener {
            if (content.visibility == View.GONE) {
                content.visibility = View.VISIBLE
                chevron.text = "▲"
                content.alpha = 0f
                content.animate().alpha(1f).setDuration(200).start()
            } else {
                content.animate().alpha(0f).setDuration(150).withEndAction {
                    content.visibility = View.GONE
                }.start()
                chevron.text = "▼"
            }
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.accent_primary)
        binding.swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.surface_card)
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshData()
            binding.swipeRefresh.postDelayed({ binding.swipeRefresh.isRefreshing = false }, 800)
        }
    }

    private fun showResistConfirmation() {
        val messages = listOf(
            "💚 Logged. Cravings usually pass in 3–5 minutes.",
            "🌊 Riding it out. Breathe.",
            "🌱 You named it. That's the first move.",
            "✊ One thought at a time.",
            "⏳ Let's see if it holds.",
        )
        com.google.android.material.snackbar.Snackbar
            .make(binding.root, messages.random(), com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
            .setBackgroundTint(ContextCompat.getColor(this, R.color.surface_elevated))
            .setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            .show()
    }

    private fun showVictorySnackbar(count: Int) {
        val message = if (count == 1) {
            "🏅 1 craving held — verified smoke-free 30 min later."
        } else {
            "🏅 $count cravings held — verified smoke-free 30 min later."
        }
        com.google.android.material.snackbar.Snackbar
            .make(binding.root, message, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
            .setBackgroundTint(ContextCompat.getColor(this, R.color.status_champion))
            .setTextColor(ContextCompat.getColor(this, R.color.white))
            .show()
    }

    private var primarySubstance: Substance = Substance.DEFAULT

    /**
     * Banked time grows monotonically, but the body's recovery milestones
     * (heart rate, CO, circulation, etc.) reset after each cigarette — that's
     * biology, not a UX choice. So the milestone list is driven by the current
     * clean streak (time since last smoke). Banked stays as the lifetime
     * "never erased" metric in the timer text above.
     */
    private fun updateRecoveryHero(bankedMs: Long, timeSinceLastSmokeMs: Long) {
        binding.sectionRecoveryHero.textBankedTimer.text = TimeFormatter.formatShort(bankedMs)

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

    private fun applySubstanceCopy() {
        binding.sectionReductionTrend.textReductionUnit.text = copy.perDay
    }

    private fun observeViewModel() {
        viewModel.primarySubstance.observe(this) { substance ->
            primarySubstance = substance
            copy = SubstanceCopy.forSubstance(substance)
            applySubstanceCopy()
            // Substance change re-anchors the milestone list. Force a refresh
            // even when the achieved-count number is unchanged.
            lastAchievedCount = -1
            val banked = viewModel.bankedSmokeFreeMs.value ?: 0L
            val score = viewModel.currentScore.value ?: 0L
            updateRecoveryHero(banked, score)
        }

        viewModel.currentScore.observe(this) { score ->
            updatePausedBadge(score)
            // Milestones reflect the current clean streak, so they update each
            // tick. Banked timer is refreshed from its own LiveData below.
            val banked = viewModel.bankedSmokeFreeMs.value ?: 0L
            updateRecoveryHero(banked, score)
        }

        viewModel.bankedSmokeFreeMs.observe(this) { ms ->
            val score = viewModel.currentScore.value ?: 0L
            updateRecoveryHero(ms, score)
            binding.sectionRecords.textBankedHours.text = TimeFormatter.formatShort(ms)
        }

        viewModel.allTimeScores.observe(this) { scores ->
            if (currentPeriod == "all") {
                statsAdapter.setScores(scores)
                updatePeriodCount(scores)
                updatePeriodCards(scores)
            }
            updateRecordCards(scores)
        }
        viewModel.yearScores.observe(this) { scores ->
            if (currentPeriod == "year") {
                statsAdapter.setScores(scores)
                updatePeriodCount(scores)
                updatePeriodCards(scores)
            }
        }
        viewModel.monthScores.observe(this) { scores ->
            if (currentPeriod == "month") {
                statsAdapter.setScores(scores)
                updatePeriodCount(scores)
                updatePeriodCards(scores)
            }
        }
        viewModel.weekScores.observe(this) { scores ->
            if (currentPeriod == "week") {
                statsAdapter.setScores(scores)
                updatePeriodCount(scores)
                updatePeriodCards(scores)
            }
        }
        viewModel.dayScores.observe(this) { scores ->
            if (currentPeriod == "day") {
                statsAdapter.setScores(scores)
                updatePeriodCount(scores)
                updatePeriodCards(scores)
            }
        }

        viewModel.chartData.observe(this) { data -> updateCharts(data) }

        viewModel.moneySavedFormatted.observe(this) { formatted ->
            binding.sectionRecords.textMoneySaved.text = formatted
        }

        // perSubstancePace drives the hero verdict block: one chip per
        // substance the user logs (just one for single-substance users,
        // stacked rows for tobacco + cannabis dogfooders). The combined
        // todayPace LiveData stays computed in the ViewModel but no longer
        // surfaces — pooling unlike substances into one verdict was
        // misleading for multi-substance users.
        viewModel.perSubstancePace.observe(this) { entries -> updateHeroPace(entries) }

        viewModel.newCravingVictories.observe(this) { count ->
            if (count > 0) {
                showVictorySnackbar(count)
                viewModel.dismissNewVictories()
            }
        }

        viewModel.reductionStats.observe(this) { stats -> updateReductionTrend(stats) }
        viewModel.firstSmokeOfDay.observe(this) { fs -> updateFirstSmokeOfDay(fs) }
        viewModel.substanceLevels.observe(this) { levels -> updateSubstanceLevels(levels) }
        viewModel.triggerWindows.observe(this) { windows -> updateTriggerWindows(windows) }
        viewModel.resistanceStats.observe(this) { stats -> updateResistanceCard(stats) }
        viewModel.weeklyDigest.observe(this) { digest -> updateWeeklyDigest(digest) }
    }

    private fun updateWeeklyDigest(digest: ScoreCalculator.WeeklyDigest) {
        val sectionRoot = binding.sectionWeeklyDigest.root
        val smokeCount = sectionRoot.findViewById<android.widget.TextView>(R.id.textDigestSmokeCount)
        val smokeLabel = sectionRoot.findViewById<android.widget.TextView>(R.id.textDigestSmokeLabel)
        val smokeDelta = sectionRoot.findViewById<android.widget.TextView>(R.id.textDigestSmokeDelta)
        val resisted = sectionRoot.findViewById<android.widget.TextView>(R.id.textDigestResistance)
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

        resisted.text = digest.resistance.resistedCount.toString()
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
    }

    private fun updateResistanceCard(stats: ScoreCalculator.ResistanceStats) {
        val sectionRoot = binding.sectionResistance.root
        val percentView = sectionRoot.findViewById<android.widget.TextView>(R.id.textResistancePercent)
        val labelView = sectionRoot.findViewById<android.widget.TextView>(R.id.textResistanceLabel)
        val deltaView = sectionRoot.findViewById<android.widget.TextView>(R.id.textResistanceDelta)
        val breakdownView = sectionRoot.findViewById<android.widget.TextView>(R.id.textResistanceBreakdown)
        val progress = sectionRoot.findViewById<
            com.google.android.material.progressindicator.LinearProgressIndicator
        >(R.id.progressResistance)

        val total = stats.resistedCount + stats.smokedCount
        if (total == 0) {
            percentView.text = "—"
            percentView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            labelView.text = "no urges logged yet"
            breakdownView.text = "Tap \"I Resisted\" when an urge fires, even if you end up smoking. The ratio is what carries through."
            progress.progress = 0
            progress.setIndicatorColor(ContextCompat.getColor(this, R.color.progress_track))
            deltaView.visibility = View.GONE
            return
        }

        val pctInt = stats.resistancePercent.roundToInt().coerceIn(0, 100)
        percentView.text = "$pctInt%"
        labelView.text = "of urges held"

        val colorRes = when {
            pctInt >= 70 -> R.color.status_champion
            pctInt >= 40 -> R.color.accent_amber
            else -> R.color.status_reset
        }
        percentView.setTextColor(ContextCompat.getColor(this, colorRes))
        progress.progress = pctInt
        progress.setIndicatorColor(ContextCompat.getColor(this, colorRes))

        breakdownView.text =
            "${stats.resistedCount} resisted · ${stats.smokedCount} smoked  ·  $total moments this week"

        val delta = stats.vsPriorPercent
        if (delta == null) {
            deltaView.visibility = View.GONE
        } else {
            val rounded = delta.roundToInt()
            val (txt, deltaColor) = when {
                rounded >= 5 -> "↑ +${rounded}pp vs last week" to R.color.status_champion
                rounded <= -5 -> "↓ ${rounded}pp vs last week" to R.color.status_reset
                else -> "→ steady vs last week" to R.color.text_secondary
            }
            deltaView.text = txt
            deltaView.setTextColor(ContextCompat.getColor(this, deltaColor))
            deltaView.visibility = View.VISIBLE
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

    private fun updateReductionTrend(stats: com.smokless.smokeless.util.ScoreCalculator.ReductionStats) {
        val avgFormat = DecimalFormat("0.#")
        binding.sectionReductionTrend.textReductionAverage.text = avgFormat.format(stats.rollingAverage7d)

        val coverage = stats.loggedDaysLast7
        if (stats.hasEnoughData && coverage in 1..6) {
            binding.sectionReductionTrend.textReductionCoverage.text = "across $coverage of 7 days logged"
            binding.sectionReductionTrend.textReductionCoverage.visibility = View.VISIBLE
        } else {
            binding.sectionReductionTrend.textReductionCoverage.visibility = View.GONE
        }

        val velocityText = when {
            !stats.hasEnoughData -> "Logging — trend appears with more days of data"
            !stats.velocityComparable ->
                "Recent activity logged — comparison resumes after continuous tracking"
            stats.velocityPercent >= 5.0 ->
                "${DecimalFormat("0").format(stats.velocityPercent)}% less than 30 days ago"
            stats.velocityPercent <= -5.0 ->
                "${DecimalFormat("0").format(-stats.velocityPercent)}% more than 30 days ago"
            else -> "Steady vs. 30 days ago"
        }
        binding.sectionReductionTrend.textReductionVelocity.text = velocityText
    }

    private fun updatePeriodCards(scores: List<ScoreData>?) {
        if (scores.isNullOrEmpty()) {
            binding.sectionQuickStats.textBestTodayValue.text = "0d"
            binding.sectionQuickStats.textCountTodayValue.text = "0"
            binding.sectionQuickStats.progressBestToday.progress = 0
            binding.sectionQuickStats.progressCountToday.progress = 0
            return
        }
        for (score in scores) {
            when (score.type) {
                ScoreData.StatType.STREAK -> if (score.label.contains("Current")) {
                    val displayValue = when (currentPeriod) {
                        "day" -> if (score.value >= 1L) "${score.value}d" else "${score.value}h"
                        else -> "${score.value}d"
                    }
                    binding.sectionQuickStats.textBestTodayValue.text = displayValue
                    binding.sectionQuickStats.progressBestToday.progress =
                        min(score.percentage, 100.0).toInt()
                    val colorRes = when {
                        score.value >= 30L -> R.color.status_champion
                        score.value >= 7L -> R.color.status_strong
                        score.value >= 3L -> R.color.accent_teal
                        score.value >= 1L -> R.color.status_building
                        else -> R.color.status_reset
                    }
                    binding.sectionQuickStats.progressBestToday.setIndicatorColor(
                        ContextCompat.getColor(this, colorRes)
                    )
                }
                ScoreData.StatType.COUNT -> {
                    val displayValue = when {
                        score.value == 0L -> "0 🎉"
                        score.value == 1L -> "1"
                        else -> score.value.toString()
                    }
                    binding.sectionQuickStats.textCountTodayValue.text = displayValue
                    binding.sectionQuickStats.progressCountToday.progress =
                        min(score.percentage, 100.0).toInt()
                    val colorRes = when {
                        score.value == 0L -> R.color.status_champion
                        score.value <= 2L -> R.color.status_strong
                        score.value <= 5L -> R.color.accent_teal
                        score.value <= 10L -> R.color.status_building
                        score.value <= 15L -> R.color.accent_amber
                        else -> R.color.status_reset
                    }
                    binding.sectionQuickStats.progressCountToday.setIndicatorColor(
                        ContextCompat.getColor(this, colorRes)
                    )
                }
                else -> {}
            }
        }
    }

    private fun updateRecordCards(scores: List<ScoreData>?) {
        if (scores.isNullOrEmpty()) return
        for (score in scores) {
            when (score.type) {
                ScoreData.StatType.STREAK -> if (score.label.contains("Best")) {
                    binding.sectionRecords.textAllTimeBest.text = "${score.value} days"
                }
                ScoreData.StatType.COUNT -> {
                    binding.sectionRecords.textTotalSessions.text = score.value.toString()
                }
                else -> {}
            }
        }
    }

    /**
     * Breathing-guided animation on the orb behind the banked timer.
     * Asymmetric 4s inhale / 6s exhale activates the vagus nerve → parasympathetic
     * calming. Visual rhythm entrains the user's breath without instruction.
     */
    private var breathingAnimator: android.animation.ValueAnimator? = null

    private fun startBreathingAnimation() {
        if (breathingAnimator != null) return
        val orb = binding.sectionRecoveryHero.breathingOrb
        breathingAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 10_000L
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.RESTART
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                val breath = if (t < 0.4f) {
                    val p = t / 0.4f; p * p
                } else {
                    val p = (t - 0.4f) / 0.6f; 1f - p * p
                }
                orb.scaleX = 0.4f + breath * 0.6f
                orb.scaleY = 0.4f + breath * 0.6f
                orb.alpha = breath
            }
            start()
        }
    }

    private fun stopBreathingAnimation() {
        breathingAnimator?.cancel()
        breathingAnimator = null
        binding.sectionRecoveryHero.breathingOrb.apply {
            scaleX = 0.4f
            scaleY = 0.4f
            alpha = 0f
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshData()
        refreshHandler.postDelayed(refreshRunnable, 1000)
        startBreathingAnimation()
        // Idempotent — schedules only when the alarm hasn't been set yet.
        com.smokless.smokeless.util.TriggerWindowReceiver.schedule(this)
        com.smokless.smokeless.util.WeeklyDigestReceiver.schedule(this)
    }

    override fun onPause() {
        super.onPause()
        refreshHandler.removeCallbacks(refreshRunnable)
        stopBreathingAnimation()
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
