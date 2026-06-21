package com.smokless.smokeless

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
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
import com.smokless.smokeless.databinding.ActivityStatsBinding
import com.smokless.smokeless.ui.main.ChartData
import com.smokless.smokeless.ui.main.MainViewModel
import com.smokless.smokeless.util.ScoreCalculator
import com.smokless.smokeless.ui.main.ScoreAdapter
import com.smokless.smokeless.ui.main.ScoreData
import com.smokless.smokeless.util.SubstanceCopy
import com.smokless.smokeless.util.TimeFormatter
import java.text.DecimalFormat
import kotlin.math.min

/**
 * Dedicated stats surface. Mirrors the Bios pattern of a Scaffold-style
 * dashboard (toolbar with back arrow + scrollable card stack) instead of
 * burying the same content in a bottom sheet on the main screen.
 */
class StatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatsBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var statsAdapter: ScoreAdapter

    private val SELF_EVAL_DIGEST_LABELS: List<Pair<String, String>> = listOf(
        com.smokless.smokeless.bios.BiosClient.METRIC_SMELL_SELF_RATING to "Smell",
        com.smokless.smokeless.bios.BiosClient.METRIC_TASTE_SELF_RATING to "Taste",
        com.smokless.smokeless.bios.BiosClient.METRIC_COUGH_FREQUENCY_SELF_RATING to "Cough",
        com.smokless.smokeless.bios.BiosClient.METRIC_SPUTUM_SELF_RATING to "Sputum",
        com.smokless.smokeless.bios.BiosClient.METRIC_BREATH_EASE_SELF_RATING to "Breath",
        com.smokless.smokeless.bios.BiosClient.METRIC_SMOKER_IDENTITY_SELF_RATING to "Identity",
    )

    private var currentPeriod = "month"
    private var currentChartRange = "month"
    private var copy: SubstanceCopy = SubstanceCopy.TOBACCO

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        setupStatsRecycler()
        setupChipGroup()
        setupChartRangeChips()
        setupCharts()
        setupCollapsibleSections()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshData()
        updateSelfEvalStats()
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
                // The chart has its own granularity selector now; period
                // chip only drives the stats list / period highlights.
                updateStatsForPeriod()

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

    private fun setupChartRangeChips() {
        binding.sectionCharts.chipGroupChartRange.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            currentChartRange = when (checkedIds[0]) {
                R.id.chipRangeWeek -> "week"
                R.id.chipRangeYear -> "year"
                else -> "month"
            }
            updateChartLabels()
            viewModel.setChartRange(currentChartRange)
        }
        binding.sectionCharts.chipRangeMonth.isChecked = true
        updateChartLabels()
    }

    private fun updateChartLabels() {
        binding.sectionCharts.textTrendChartTitle.text = when (currentChartRange) {
            "week" -> "Trend — last week"
            "year" -> "Trend — last year"
            else -> "Trend — last month"
        }
        binding.sectionCharts.textBarChartTitle.text = when (currentChartRange) {
            "week" -> "Last 7 days"
            "year" -> "Last 12 months"
            else -> "Last 30 days"
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
        barChart.legend.apply {
            isEnabled = true
            textColor = ContextCompat.getColor(this@StatsActivity, R.color.text_secondary)
            textSize = 10f
            form = com.github.mikephil.charting.components.Legend.LegendForm.SQUARE
            formSize = 8f
            xEntrySpace = 12f
        }
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

        val stackBySubstance = getSharedPreferences("SmokelessPrefs", MODE_PRIVATE)
            .getBoolean(
                com.smokless.smokeless.ui.settings.SettingsViewModel.KEY_STACK_CHART_BY_SUBSTANCE,
                true,
            )

        val barEntries = if (stackBySubstance) {
            // Stacked bar: cannabis (dark green) sits at the base, tobacco
            // (orange) on top — tobacco is the headline substance, so it
            // reads first.
            data.dailyCounts.mapIndexed { index, _ ->
                val tobacco = data.tobaccoCounts.getOrNull(index)?.toFloat() ?: 0f
                val cannabis = data.cannabisCounts.getOrNull(index)?.toFloat() ?: 0f
                BarEntry(index.toFloat(), floatArrayOf(cannabis, tobacco))
            }
        } else {
            data.dailyCounts.mapIndexed { index, total ->
                BarEntry(index.toFloat(), total.toFloat())
            }
        }
        if (barEntries.isNotEmpty()) {
            binding.sectionCharts.barChart.visibility = View.VISIBLE
            binding.sectionCharts.emptyStateBar.visibility = View.GONE
            binding.sectionCharts.barChart.legend.isEnabled = stackBySubstance
            val barDataSet = BarDataSet(barEntries, "Sessions").apply {
                if (stackBySubstance) {
                    setColors(
                        ContextCompat.getColor(this@StatsActivity, R.color.chart_substance_cannabis),
                        ContextCompat.getColor(this@StatsActivity, R.color.chart_substance_tobacco),
                    )
                    stackLabels = arrayOf("Cannabis", "Tobacco")
                } else {
                    color = ContextCompat.getColor(this@StatsActivity, R.color.accent_primary)
                }
                setDrawValues(true)
                valueTextColor = ContextCompat.getColor(this@StatsActivity, R.color.text_secondary)
                valueTextSize = 9f
                // Only label the bar's total. For stacked bars the renderer
                // calls getBarStackedLabel once per segment in array order;
                // emit the total only on the topmost non-zero segment so the
                // bar carries a single number, never a per-slice breakdown.
                // Aggregated bars fall back to the plain getBarLabel path.
                valueFormatter = object : ValueFormatter() {
                    private var lastEntry: BarEntry? = null
                    private var segmentsSeen = 0

                    override fun getBarLabel(barEntry: BarEntry?): String {
                        val entry = barEntry ?: return ""
                        return if (entry.y > 0f) entry.y.toInt().toString() else ""
                    }

                    override fun getBarStackedLabel(value: Float, stackedEntry: BarEntry?): String {
                        val entry = stackedEntry ?: return ""
                        val vals = entry.yVals
                            ?: return if (entry.y > 0f) entry.y.toInt().toString() else ""

                        if (entry !== lastEntry) {
                            lastEntry = entry
                            segmentsSeen = 0
                        }
                        val idx = segmentsSeen
                        segmentsSeen++

                        val topIdx = vals.indices.lastOrNull { vals[it] > 0f } ?: return ""
                        if (idx != topIdx) return ""
                        val total = entry.y.toInt()
                        return if (total > 0) total.toString() else ""
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

        val avgUnit = when (currentChartRange) {
            "year" -> "/mo"
            else -> "/day"
        }
        binding.sectionCharts.textBarChartAvg.text =
            String.format("Avg: %.1f%s", data.avgDailyCount, avgUnit)

        val lineEntries = data.movingAverage.mapIndexed { index, avg ->
            Entry(index.toFloat(), avg.toFloat())
        }
        if (lineEntries.isNotEmpty()) {
            binding.sectionCharts.lineChart.visibility = View.VISIBLE
            binding.sectionCharts.emptyStateTrend.visibility = View.GONE
            val lineDataSet = LineDataSet(lineEntries, "Trend").apply {
                color = ContextCompat.getColor(this@StatsActivity, R.color.accent_primary)
                setCircleColor(ContextCompat.getColor(this@StatsActivity, R.color.accent_primary))
                lineWidth = 2.5f
                circleRadius = 3f
                setDrawCircleHole(true)
                circleHoleRadius = 1.5f
                circleHoleColor = ContextCompat.getColor(this@StatsActivity, R.color.surface_card)
                setDrawValues(false)
                mode = LineDataSet.Mode.LINEAR
                cubicIntensity = 0.1f
                setDrawFilled(true)
                fillColor = ContextCompat.getColor(this@StatsActivity, R.color.accent_primary)
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

    private fun applySubstanceCopy() {
        binding.sectionReductionTrend.textReductionUnit.text = copy.perDay
    }

    private fun observeViewModel() {
        viewModel.primarySubstance.observe(this) { substance ->
            copy = SubstanceCopy.forSubstance(substance)
            applySubstanceCopy()
        }

        viewModel.bankedSmokeFreeMs.observe(this) { ms ->
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

        viewModel.reductionStats.observe(this) { stats -> updateReductionTrend(stats) }

        viewModel.scopedBaselines.observe(this) { baselines -> updateBaselines(baselines) }
    }

    private fun updateBaselines(baselines: List<com.smokless.smokeless.util.ScoreCalculator.ScopedBaseline>) {
        val rowsGroup = binding.sectionBaselines.groupBaselineRows
        val empty = binding.sectionBaselines.textBaselinesEmpty
        rowsGroup.removeAllViews()

        if (baselines.isEmpty()) {
            empty.visibility = View.VISIBLE
            return
        }
        empty.visibility = View.GONE

        val numFmt = DecimalFormat("0.#")
        val showLabel = baselines.size > 1
        baselines.forEachIndexed { index, b ->
            val row = layoutInflater.inflate(R.layout.item_baseline_stat_row, rowsGroup, false)
            val labelView = row.findViewById<android.widget.TextView>(R.id.textBaselineRowLabel)

            if (showLabel) {
                labelView.text = substanceLabel(b.substance)
            } else {
                labelView.visibility = View.GONE
            }

            bindBaselineScope(
                row, R.id.textBaselineDayValue, R.id.textBaselineDayBaseline, b.day, numFmt
            )
            bindBaselineScope(
                row, R.id.textBaselineWeekValue, R.id.textBaselineWeekBaseline, b.week, numFmt
            )
            bindBaselineScope(
                row, R.id.textBaselineMonthValue, R.id.textBaselineMonthBaseline, b.month, numFmt
            )

            if (index > 0) {
                val lp = row.layoutParams as android.widget.LinearLayout.LayoutParams
                lp.topMargin = (4 * resources.displayMetrics.density).toInt()
                row.layoutParams = lp
            }
            rowsGroup.addView(row)
        }
    }

    /** Render one scope cell: current dose (coloured by verdict) over its baseline. */
    private fun bindBaselineScope(
        row: View,
        valueId: Int,
        baselineId: Int,
        scope: com.smokless.smokeless.util.ScoreCalculator.ScopeComparison,
        numFmt: DecimalFormat,
    ) {
        val valueView = row.findViewById<android.widget.TextView>(valueId)
        val baselineView = row.findViewById<android.widget.TextView>(baselineId)
        valueView.text = numFmt.format(scope.current)
        valueView.setTextColor(ContextCompat.getColor(this, paceColor(scope.state)))
        baselineView.text = if (scope.state == ScoreCalculator.PaceState.CALIBRATING) {
            "building…"
        } else {
            "vs ${numFmt.format(scope.baselinePerPeriod)}"
        }
    }

    /** Verdict → colour, matching the hero pace chip's palette. */
    private fun paceColor(state: ScoreCalculator.PaceState): Int = when (state) {
        ScoreCalculator.PaceState.AHEAD,
        ScoreCalculator.PaceState.CLEAN_TODAY -> R.color.status_champion
        ScoreCalculator.PaceState.ON_PACE,
        ScoreCalculator.PaceState.CLEAN_BREAK -> R.color.accent_amber
        ScoreCalculator.PaceState.BEHIND -> R.color.status_reset
        ScoreCalculator.PaceState.CALIBRATING -> R.color.text_secondary
    }

    private fun substanceLabel(substance: com.smokless.smokeless.data.entity.Substance): String =
        when (substance) {
            com.smokless.smokeless.data.entity.Substance.TOBACCO -> "🚬 Tobacco"
            com.smokless.smokeless.data.entity.Substance.CANNABIS -> "🌿 Cannabis"
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
     * Renders the "Subjective Scales" stats card — latest weekly value per
     * metric plus the delta against the prior entry. Period-agnostic (the
     * scales are captured weekly, so the period-chip framing doesn't apply).
     * Tap the CTA → opens the carousel to update this week's check-in.
     */
    private fun updateSelfEvalStats() {
        val sectionRoot = binding.sectionSelfEvalStats.root
        val rowsGroup = sectionRoot.findViewById<android.widget.LinearLayout>(R.id.groupSelfEvalStatRows)
        val empty = sectionRoot.findViewById<android.widget.TextView>(R.id.textSelfEvalStatsEmpty)
        val openBtn = sectionRoot.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSelfEvalStatsOpen)
        val store = com.smokless.smokeless.util.WeeklySelfEvalStore(applicationContext)
        val inflater = layoutInflater
        val numFmt = DecimalFormat("0.#")

        rowsGroup.removeAllViews()
        var anyRendered = false
        for ((key, label) in SELF_EVAL_DIGEST_LABELS) {
            val recent = store.recent(key, limit = 4)
            if (recent.isEmpty()) continue
            anyRendered = true

            val row = inflater.inflate(R.layout.item_self_eval_stat_row, rowsGroup, false)
            val labelView = row.findViewById<android.widget.TextView>(R.id.textStatRowLabel)
            val sparklineView = row.findViewById<android.widget.TextView>(R.id.textStatRowSparkline)
            val valueView = row.findViewById<android.widget.TextView>(R.id.textStatRowValue)
            val deltaView = row.findViewById<android.widget.TextView>(R.id.textStatRowDelta)

            labelView.text = label
            val latest = recent.first()
            valueView.text = numFmt.format(latest.value)

            if (recent.size >= 2) {
                val prior = recent[1]
                val delta = latest.value - prior.value
                val absStr = numFmt.format(kotlin.math.abs(delta))
                // Direction-only badge — "no judgment" per the cessation
                // manifesto. Polarities differ across these scales (↓ cough
                // is desirable, ↓ smell isn't), so the owner reads the
                // direction; the app doesn't moralise.
                val badge = when {
                    kotlin.math.abs(delta) < 0.5 -> "→ steady"
                    delta > 0 -> "↑ +$absStr"
                    else -> "↓ −$absStr"
                }
                deltaView.text = badge
                deltaView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                deltaView.visibility = View.VISIBLE
            } else {
                deltaView.visibility = View.GONE
            }

            // Oldest → newest sparkline so the trend reads left-to-right.
            val ordered = recent.reversed()
            sparklineView.text = ordered.joinToString(" → ") { numFmt.format(it.value) }

            rowsGroup.addView(row)
        }
        empty.visibility = if (anyRendered) View.GONE else View.VISIBLE
        openBtn.setOnClickListener {
            startActivity(Intent(this, WeeklySelfEvalActivity::class.java))
        }
    }
}
