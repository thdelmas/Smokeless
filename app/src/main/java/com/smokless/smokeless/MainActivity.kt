package com.smokless.smokeless

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.smokless.smokeless.databinding.ActivityMainBinding
import com.smokless.smokeless.ui.main.ChartData
import com.smokless.smokeless.ui.main.MainViewModel
import com.smokless.smokeless.ui.main.ScoreAdapter
import com.smokless.smokeless.ui.main.ScoreData
import com.smokless.smokeless.util.HealthBenefits
import com.smokless.smokeless.util.TimeFormatter
import java.text.DecimalFormat
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    
    private lateinit var statsAdapter: ScoreAdapter
    
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val percentFormat = DecimalFormat("0.0")
    
    private var currentPeriod = "month"
    
    private val refreshRunnable = object : Runnable {
        override fun run() {
            // Always update the countdown timer regardless of period
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
        setupRecyclerView()
        setupChipGroup()
        setupCharts()
        setupFab()
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
                    showAchievementsDialog()
                    true
                }
                else -> false
            }
        }
    }
    
    /**
     * Show achievements in a dialog
     */
    private fun showAchievementsDialog() {
        startActivity(Intent(this, AchievementsActivity::class.java))
    }
    
    private fun setupRecyclerView() {
        statsAdapter = ScoreAdapter()
        binding.sectionStatistics.recyclerStats.layoutManager = LinearLayoutManager(this)
        binding.sectionStatistics.recyclerStats.adapter = statsAdapter
    }
    
    private fun setupChipGroup() {
        binding.sectionStatistics.chipGroupPeriod.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            
            val checkedId = checkedIds[0]
            
            when (checkedId) {
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
            
            // Fade out stats, update, then fade back in
            val statsRecycler = binding.sectionStatistics.recyclerStats
            val quickStats = binding.sectionQuickStats.root
            statsRecycler.animate().alpha(0f).setDuration(150).withEndAction {
                // Update all period-specific components
                viewModel.setGoalPeriod(currentPeriod)
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
                viewModel.currentGoal.value?.let { updateGoalLabel(it) }

                statsRecycler.animate().alpha(1f).setDuration(200).start()
            }.start()
            quickStats.animate().alpha(0f).setDuration(150).withEndAction {
                quickStats.animate().alpha(1f).setDuration(200).setStartDelay(100).start()
            }.start()
        }
        
        // Set initial selection
        binding.sectionStatistics.chipMonth.isChecked = true
    }
    
    /**
     * Update chart section labels based on current period
     */
    private fun updateChartLabels() {
        // Update trend chart title based on period
        val trendTitle = when (currentPeriod) {
            "day" -> "Hourly Trend"
            "week" -> "Daily Trend"
            "month" -> "7-Day Average Trend"
            "year" -> "Monthly Trend"
            "all" -> "Long-term Trend"
            else -> "Trend"
        }
        binding.sectionCharts.textTrendChartTitle.text = trendTitle
        
        // Update bar chart title based on period
        val barTitle = when (currentPeriod) {
            "day" -> "Hourly Count"
            "week" -> "Daily Count"
            "month" -> "Daily Count"
            "year" -> "Daily Count"
            "all" -> "Daily Count"
            else -> "Cigarette Count"
        }
        binding.sectionCharts.textBarChartTitle.text = barTitle
    }
    
    private fun updatePeriodHeader(icon: String, title: String) {
        binding.sectionStatistics.textPeriodIcon.text = icon
        binding.sectionStatistics.textPeriodTitle.text = title
        
        // Update quick stats title and labels based on period
        val quickStatsTitle = when (currentPeriod) {
            "day" -> "Today's Highlights"
            "week" -> "This Week's Highlights"
            "month" -> "This Month's Highlights"
            "year" -> "This Year's Highlights"
            "all" -> "All-Time Highlights"
            else -> "Period Highlights"
        }
        binding.sectionQuickStats.textQuickStatsTitle.text = quickStatsTitle
        
        // Update card labels
        val streakLabel = when (currentPeriod) {
            "day" -> "Today's Streak"
            "week" -> "Week Streak"
            "month" -> "Month Streak"
            "year" -> "Year Streak"
            "all" -> "Best Streak"
            else -> "Clean Streak"
        }
        binding.sectionQuickStats.textBestTodayLabel.text = streakLabel
        
        val countLabel = when (currentPeriod) {
            "day" -> "Today"
            "week" -> "This Week"
            "month" -> "This Month"
            "year" -> "This Year"
            "all" -> "All Time"
            else -> "Total"
        }
        binding.sectionQuickStats.textCountTodayLabel.text = countLabel
    }
    
    /**
     * Update goal label to be contextual to the selected period
     */
    @Suppress("UNUSED_PARAMETER")
    private fun updateGoalLabel(goal: Double) {
        // Update progress label — framed around interval
        val progressLabel = when (currentPeriod) {
            "day" -> "Interval Progress"
            else -> "Interval Goal"
        }
        binding.sectionHero.textGoalProgressLabel.text = progressLabel

        // Show target interval duration
        val targetMs = viewModel.targetInterval.value ?: 0L
        if (targetMs > 0L) {
            val targetHours = targetMs / (1000.0 * 60.0 * 60.0)
            val labelText = if (targetHours >= 1.0) {
                val h = targetHours.toInt()
                val m = ((targetHours - h) * 60).toInt()
                "Target: wait ${h}h ${m}m between smokes"
            } else {
                val m = (targetHours * 60).toInt()
                "Target: wait ${m}m between smokes"
            }
            binding.sectionHero.textViewGoalLabel.text = labelText
        } else {
            binding.sectionHero.textViewGoalLabel.text = "Log smokes to set your interval goal"
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
        // Find total count from scores
        for (score in scores) {
            if (score.type == ScoreData.StatType.COUNT) {
                val count = score.value
                binding.sectionStatistics.textPeriodCount.text = "$count ${if (count == 1L) "cigarette" else "cigarettes"}"
                return
            }
        }
        binding.sectionStatistics.textPeriodCount.text = "0 cigarettes"
    }
    
    private fun setupCharts() {
        // Setup Bar Chart (Daily Cigarettes)
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
        
        // Setup Line Chart (Moving Average Trend)
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
            binding.sectionCharts.lineChart.visibility = android.view.View.INVISIBLE
            binding.sectionCharts.emptyStateTrend.visibility = android.view.View.VISIBLE
            binding.sectionCharts.barChart.clear()
            binding.sectionCharts.barChart.visibility = android.view.View.INVISIBLE
            binding.sectionCharts.emptyStateBar.visibility = android.view.View.VISIBLE
            binding.sectionCharts.textChartTrend.text = "No data yet"
            binding.sectionCharts.textBarChartAvg.text = "Avg: 0/day"
            return
        }

        // Hide empty states, show charts
        binding.sectionCharts.lineChart.visibility = android.view.View.VISIBLE
        binding.sectionCharts.emptyStateTrend.visibility = android.view.View.GONE
        binding.sectionCharts.barChart.visibility = android.view.View.VISIBLE
        binding.sectionCharts.emptyStateBar.visibility = android.view.View.GONE
        
        // Determine max value for consistent scaling
        val maxCount = data.dailyCounts.maxOrNull() ?: 0
        val maxAverage = data.movingAverage.maxOrNull() ?: 0.0
        val dataMaxValue = kotlin.math.max(maxCount.toFloat(), maxAverage.toFloat())
        
        // Set minimum chart height to avoid compressed charts with small values
        val chartMaxValue = kotlin.math.max(dataMaxValue * 1.2f, 5f) // At least 5 or 20% above max
        
        // Update Bar Chart (Daily Counts)
        val barEntries = data.dailyCounts.mapIndexed { index, count ->
            BarEntry(index.toFloat(), count.toFloat())
        }
        
        if (barEntries.isNotEmpty()) {
            binding.sectionCharts.barChart.visibility = android.view.View.VISIBLE
            binding.sectionCharts.emptyStateBar.visibility = android.view.View.GONE
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
            
            val barData = BarData(barDataSet).apply {
                barWidth = 0.6f
            }
            
            // Apply consistent Y-axis scaling
            binding.sectionCharts.barChart.axisLeft.axisMaximum = kotlin.math.max(chartMaxValue, 1f)
            binding.sectionCharts.barChart.xAxis.valueFormatter = IndexAxisValueFormatter(getLimitedLabels(data.labels))
            binding.sectionCharts.barChart.xAxis.setLabelCount(getLimitedLabelCount(data.labels.size), false)
            binding.sectionCharts.barChart.data = barData
            binding.sectionCharts.barChart.invalidate()
        } else {
            binding.sectionCharts.barChart.visibility = android.view.View.INVISIBLE
            binding.sectionCharts.emptyStateBar.visibility = android.view.View.VISIBLE
        }

        // Update average label based on period
        val avgLabel = when (currentPeriod) {
            "day" -> {
                val total = data.dailyCounts.sum()
                "Total: $total"
            }
            else -> String.format("Avg: %.1f/day", data.avgDailyCount)
        }
        binding.sectionCharts.textBarChartAvg.text = avgLabel
        
        // Update Line Chart (Moving Average)
        val lineEntries = data.movingAverage.mapIndexed { index, avg ->
            Entry(index.toFloat(), avg.toFloat())
        }
        
        if (lineEntries.isNotEmpty()) {
            binding.sectionCharts.lineChart.visibility = android.view.View.VISIBLE
            binding.sectionCharts.emptyStateTrend.visibility = android.view.View.GONE
            val lineDataSet = LineDataSet(lineEntries, "Trend").apply {
                color = ContextCompat.getColor(this@MainActivity, R.color.accent_primary)
                setCircleColor(ContextCompat.getColor(this@MainActivity, R.color.accent_primary))
                lineWidth = 2.5f
                circleRadius = 3f
                setDrawCircleHole(true)
                circleHoleRadius = 1.5f
                circleHoleColor = ContextCompat.getColor(this@MainActivity, R.color.surface_card)
                setDrawValues(false)
                // Use linear mode for more accurate representation
                mode = LineDataSet.Mode.LINEAR
                cubicIntensity = 0.1f
                setDrawFilled(true)
                fillColor = ContextCompat.getColor(this@MainActivity, R.color.accent_primary)
                fillAlpha = 30
            }
            
            val lineData = LineData(lineDataSet)
            
            // Apply consistent Y-axis scaling
            binding.sectionCharts.lineChart.axisLeft.axisMaximum = kotlin.math.max(chartMaxValue, 1f)
            binding.sectionCharts.lineChart.xAxis.valueFormatter = IndexAxisValueFormatter(getLimitedLabels(data.labels))
            binding.sectionCharts.lineChart.xAxis.setLabelCount(getLimitedLabelCount(data.labels.size), false)
            binding.sectionCharts.lineChart.data = lineData
            binding.sectionCharts.lineChart.invalidate()
        } else {
            binding.sectionCharts.lineChart.visibility = android.view.View.INVISIBLE
            binding.sectionCharts.emptyStateTrend.visibility = android.view.View.VISIBLE
        }

        // Update trend indicator with better logic
        updateTrendIndicator(data)
    }
    
    /**
     * Limit labels for better readability on charts
     */
    private fun getLimitedLabels(labels: List<String>): List<String> {
        // For large datasets, show every nth label
        if (labels.size <= 15) {
            return labels
        }
        
        val step = labels.size / 12
        return labels.mapIndexed { index, label ->
            if (index % step == 0 || index == labels.size - 1) label else ""
        }
    }
    
    /**
     * Get optimal label count for chart
     */
    private fun getLimitedLabelCount(totalLabels: Int): Int {
        return when {
            totalLabels <= 7 -> totalLabels
            totalLabels <= 15 -> 7
            totalLabels <= 30 -> 10
            else -> 12
        }
    }
    
    /**
     * Update trend indicator with improved logic
     */
    private fun updateTrendIndicator(data: ChartData) {
        val absChange = kotlin.math.abs(data.trendPercentage)
        
        when {
            // Significant improvement (reduction in cigarettes)
            data.isImproving && absChange >= 10 -> {
                binding.sectionCharts.textChartTrend.text = String.format("↓ Down %.0f%%", absChange)
                binding.sectionCharts.textChartTrend.setTextColor(ContextCompat.getColor(this, R.color.status_champion))
            }
            // Slight improvement
            data.isImproving && absChange >= 5 -> {
                binding.sectionCharts.textChartTrend.text = String.format("↓ Down %.0f%%", absChange)
                binding.sectionCharts.textChartTrend.setTextColor(ContextCompat.getColor(this, R.color.status_strong))
            }
            // Stable/minimal change
            absChange < 5 -> {
                binding.sectionCharts.textChartTrend.text = "→ Stable"
                binding.sectionCharts.textChartTrend.setTextColor(ContextCompat.getColor(this, R.color.status_steady))
            }
            // Slight worsening
            !data.isImproving && absChange < 20 -> {
                binding.sectionCharts.textChartTrend.text = String.format("↑ Up %.0f%%", absChange)
                binding.sectionCharts.textChartTrend.setTextColor(ContextCompat.getColor(this, R.color.accent_amber))
            }
            // Significant worsening
            else -> {
                binding.sectionCharts.textChartTrend.text = String.format("↑ Up %.0f%%", absChange)
                binding.sectionCharts.textChartTrend.setTextColor(ContextCompat.getColor(this, R.color.status_reset))
            }
        }
    }
    
    private fun setupFab() {
        binding.fabSmoke.setOnClickListener { view ->
            view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            // Show confirmation to prevent accidental taps
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Log a cigarette?")
                .setMessage("This will restart your countdown timer.")
                .setPositiveButton("Yes, I smoked") { _, _ ->
                    recordSmokeAction()
                }
                .setNegativeButton("Cancel", null)
                .setNeutralButton("I Resisted!") { _, _ ->
                    binding.fabResist.performClick()
                }
                .show()
        }

    }

    private fun recordSmokeAction() {
        // Animate timer reset
        binding.sectionHero.textViewCurrentScore.animate()
            .scaleX(0.8f)
            .scaleY(0.8f)
            .alpha(0.5f)
            .setDuration(150)
            .withEndAction {
                binding.sectionHero.textViewCurrentScore.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(200)
                    .start()
            }
            .start()
        viewModel.recordSmokeWithId { sessionId ->
            updateButtonState(0.0)
            updateWidgets()
            com.google.android.material.snackbar.Snackbar
                .make(binding.root, "Smoke recorded", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
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

    private fun setupResistFab() {
        binding.fabResist.setOnClickListener { view ->
            view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
            viewModel.recordCravingResisted()
            showResistConfirmation()
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
        setupCollapsible(
            binding.sectionInsights.headerInsights,
            binding.sectionInsights.contentInsights,
            binding.sectionInsights.chevronInsights
        )
    }

    private fun setupCollapsible(header: android.view.View, content: android.view.View, chevron: android.widget.TextView) {
        header.setOnClickListener {
            if (content.visibility == android.view.View.GONE) {
                content.visibility = android.view.View.VISIBLE
                chevron.text = "▲"
                content.alpha = 0f
                content.animate().alpha(1f).setDuration(200).start()
            } else {
                content.animate().alpha(0f).setDuration(150).withEndAction {
                    content.visibility = android.view.View.GONE
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

    /**
     * Show confirmation when craving is resisted
     */
    private fun showResistConfirmation() {
        // Create a simple snackbar/toast to encourage the user
        val messages = listOf(
            "💪 Great job resisting!",
            "🌟 You're stronger than you think!",
            "🔥 That's the spirit!",
            "💚 Your body thanks you!",
            "⭐ Keep up the great work!",
            "🎉 Victory over craving!",
            "💎 Every resistance makes you stronger!"
        )
        val message = messages.random()
        
        com.google.android.material.snackbar.Snackbar
            .make(binding.root, message, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
            .setBackgroundTint(ContextCompat.getColor(this, R.color.status_champion))
            .setTextColor(ContextCompat.getColor(this, R.color.white))
            .show()
    }
    
    /**
     * Update health benefits display based on smoke-free time
     */
    private fun updateHealthBenefits(score: Long) {
        val hours = score / 3600
        val milestones = HealthBenefits.getMilestones(hours)
        val achievedCount = milestones.count { it.isAchieved }
        val totalCount = milestones.size
        
        // Update progress text
        binding.sectionInsights.textHealthProgress.text = "$achievedCount/$totalCount milestones"
        
        // Update progress bar
        val progressPercentage = (achievedCount.toFloat() / totalCount) * 100
        binding.sectionInsights.progressHealth.progress = progressPercentage.toInt()
        
        // Update current milestone
        val current = HealthBenefits.getCurrentMilestone(hours)
        if (current != null) {
            binding.sectionInsights.textCurrentHealthMilestone.text = "${current.icon} ${current.title} - ${current.description}"
        }
        
        // Update next milestone
        val next = HealthBenefits.getNextMilestone(hours)
        if (next != null) {
            binding.sectionInsights.textNextHealthMilestone.text = "Next: ${next.icon} ${next.title} - ${next.description}"
        } else {
            binding.sectionInsights.textNextHealthMilestone.text = "🎊 You've achieved all health milestones!"
        }
    }
    
    /**
     * Update insight message based on user progress
     */
    private fun updateInsightMessage(score: Long, todayCount: Int) {
        val hours = score / 3600
        val days = hours / 24
        
        val insight = when {
            days >= 365 -> "🎊 One year smoke-free! You've completely transformed your life. Your risk of heart disease has dropped by 50%. You're an inspiration!"
            days >= 180 -> "🌟 Six months strong! Your lung function has significantly improved. You're breathing easier and your body is healing beautifully."
            days >= 90 -> "💎 Three months! Your circulation has improved and your lung function has increased by up to 30%. The physical improvements are remarkable!"
            days >= 30 -> "🏆 One month milestone! Your immune system is recovering, your coughing has decreased, and you have more energy. Amazing progress!"
            days >= 21 -> "🔥 Three weeks! You've broken the habit cycle. New neural pathways have formed, making it easier to stay smoke-free each day."
            days >= 14 -> "💪 Two weeks! Your sense of taste and smell are nearly back to normal. Food tastes better and you can smell things you missed before."
            days >= 7 -> "🎉 One week! Your body has eliminated most of the nicotine. The physical withdrawal is behind you. The mental game gets easier from here."
            days >= 3 -> "⭐ 72 hours! This is huge! Nicotine is out of your body. Breathing is easier and your energy levels are increasing. You've got this!"
            days >= 2 -> "💚 48 hours! Your nerve endings are starting to regrow. Your sense of taste and smell are improving. You're past the hardest part!"
            days >= 1 -> "🌟 24 hours! Your blood pressure and heart rate have returned to normal. The carbon monoxide in your blood has dropped significantly."
            hours >= 12 -> "💪 Half a day! Your body is already healing. Carbon monoxide levels are decreasing and oxygen levels are increasing. Keep going!"
            hours >= 8 -> "⭐ 8 hours in! Your oxygen levels are returning to normal. Your body is thanking you with every breath. This is real progress!"
            hours >= 2 -> "🌱 2+ hours! Your heart rate and blood pressure are already starting to drop. Every minute smoke-free is a win for your health!"
            todayCount == 0 -> "🎯 Clean day so far! Every smoke-free day reduces your health risks. You're building a healthier future, one day at a time."
            todayCount <= 2 -> "💚 You're showing great restraint today. Remember: cravings typically last only 3-5 minutes. You can wait them out!"
            todayCount <= 5 -> "🌿 Having a challenging day? Try the 4-7-8 breathing: inhale for 4, hold for 7, exhale for 8. This activates your relaxation response."
            else -> "💡 Each cigarette takes 11 minutes off your life. But the good news? Every day you don't smoke, your body heals a little more. Keep trying!"
        }
        
        binding.sectionInsights.textInsightMessage.text = insight
    }
    
    private fun observeViewModel() {
        viewModel.currentScore.observe(this) { score ->
            // Update motivational elements based on score
            updateMotivationalContent(score)

            // Update health benefits
            updateHealthBenefits(score)
        }

        viewModel.timeRemaining.observe(this) { remaining ->
            // Update hero countdown display whenever remaining time changes
            if (currentPeriod == "day") {
                updateCountdownDisplay(remaining)
            }
        }
        
        // Observe hero metrics (contextual to selected period)
        viewModel.heroValue.observe(this) { value ->
            updateHeroDisplay(value)
        }
        
        viewModel.heroLabel.observe(this) { label ->
            binding.sectionHero.labelCurrentStreak.text = label
        }
        
        viewModel.heroUnit.observe(this) { unit ->
            // Store unit for display formatting
        }
        
        viewModel.currentPercentage.observe(this) { percentage ->
            binding.sectionHero.textViewPercentage.text = "${percentFormat.format(percentage)}%"
            val target = min(percentage, 100.0).toInt()
            val current = binding.sectionHero.progressIndicator.progress
            android.animation.ObjectAnimator.ofInt(
                binding.sectionHero.progressIndicator, "progress", current, target
            ).apply {
                duration = 300
                interpolator = android.view.animation.DecelerateInterpolator()
                start()
            }
            updateButtonState(percentage)
            updateProgressColor(percentage)
        }
        
        viewModel.currentGoal.observe(this) { goal ->
            updateGoalLabel(goal)
        }
        
        // Observe all period scores and update when the selected period changes
        viewModel.allTimeScores.observe(this) { scores ->
            if (currentPeriod == "all") {
                statsAdapter.setScores(scores)
                updatePeriodCount(scores)
                updatePeriodCards(scores)
            }
            // Always update record cards from all-time data
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
            
            // Always update insight based on today's data
            val todayCount = scores.find { it.type == ScoreData.StatType.COUNT }?.value?.toInt() ?: 0
            val currentScore = viewModel.currentScore.value ?: 0L
            updateInsightMessage(currentScore, todayCount)
        }
        
        // Observe chart data
        viewModel.chartData.observe(this) { data ->
            updateCharts(data)
        }
        
        // Observe money saved
        viewModel.moneySavedFormatted.observe(this) { formatted ->
            binding.sectionRecords.textMoneySaved.text = formatted
        }
    }
    
    private fun updateProgressColor(percentage: Double) {
        val colorRes = when {
            percentage >= 100 -> R.color.status_champion
            percentage >= 80 -> R.color.status_strong
            percentage >= 60 -> R.color.status_steady
            percentage >= 40 -> R.color.status_building
            percentage >= 20 -> R.color.status_starting
            else -> R.color.status_reset
        }
        
        binding.sectionHero.progressIndicator.setIndicatorColor(ContextCompat.getColor(this, colorRes))
        binding.sectionHero.textViewCurrentScore.setTextColor(ContextCompat.getColor(this, colorRes))
        binding.sectionHero.textViewPercentage.setTextColor(ContextCompat.getColor(this, colorRes))
    }
    
    /**
     * Update hero display value based on period context
     */
    private fun updateHeroDisplay(value: Double) {
        when (currentPeriod) {
            "day" -> {
                // Show countdown timer: time remaining before next allowed smoke
                val remainingMs = viewModel.timeRemaining.value ?: 0L
                updateCountdownDisplay(remainingMs)
            }
            else -> {
                // For other periods: show average interval in hours
                if (value >= 1.0) {
                    val hours = value.toInt()
                    val minutes = ((value - hours) * 60).toInt()
                    binding.sectionHero.textViewCurrentScore.text = String.format("%dh %02dm", hours, minutes)
                } else {
                    val minutes = (value * 60).toInt()
                    binding.sectionHero.textViewCurrentScore.text = String.format("%dm", minutes)
                }
            }
        }
    }

    /**
     * Format and display the countdown timer
     */
    private fun updateCountdownDisplay(remainingMs: Long) {
        if (remainingMs > 0) {
            // Still counting down — show time to wait
            val totalSeconds = remainingMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60

            if (totalSeconds < 600) {
                binding.sectionHero.textViewCurrentScore.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                binding.sectionHero.textViewCurrentScore.text = String.format("%02d:%02d", hours, minutes)
            }
            binding.sectionHero.labelCurrentStreak.text = "WAIT BEFORE NEXT"
        } else {
            // Countdown finished — show bonus time the user waited beyond target
            val bonusMs = -remainingMs
            val totalSeconds = bonusMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60

            if (totalSeconds < 600) {
                binding.sectionHero.textViewCurrentScore.text = String.format("+%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                binding.sectionHero.textViewCurrentScore.text = String.format("+%02d:%02d", hours, minutes)
            }
            binding.sectionHero.labelCurrentStreak.text = "BONUS SMOKE-FREE TIME"
        }
    }
    
    /**
     * Update quick stats cards based on selected period
     */
    private fun updatePeriodCards(scores: List<ScoreData>?) {
        if (scores.isNullOrEmpty()) {
            // Show empty state with encouraging message
            binding.sectionQuickStats.textBestTodayValue.text = "0d"
            binding.sectionQuickStats.textCountTodayValue.text = "0"
            binding.sectionQuickStats.progressBestToday.progress = 0
            binding.sectionQuickStats.progressCountToday.progress = 0
            return
        }
        
        for (score in scores) {
            when (score.type) {
                ScoreData.StatType.STREAK -> {
                    if (score.label.contains("Current")) {
                        val displayValue = when (currentPeriod) {
                            "day" -> {
                                // For today, show hours if less than 1 day
                                if (score.value >= 1L) "${score.value}d"
                                else "${score.value}h"
                            }
                            "week" -> "${score.value}d"
                            "month" -> "${score.value}d"
                            "year" -> "${score.value}d"
                            "all" -> "${score.value}d"
                            else -> "${score.value}d"
                        }
                        binding.sectionQuickStats.textBestTodayValue.text = displayValue
                        val progress = min(score.percentage, 100.0).toInt()
                        binding.sectionQuickStats.progressBestToday.progress = progress
                        
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
                }
                ScoreData.StatType.COUNT -> {
                    val displayValue = when {
                        score.value == 0L -> "0 🎉"
                        score.value == 1L -> "1"
                        else -> score.value.toString()
                    }
                    binding.sectionQuickStats.textCountTodayValue.text = displayValue
                    
                    val progress = min(score.percentage, 100.0).toInt()
                    binding.sectionQuickStats.progressCountToday.progress = progress
                    
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
                ScoreData.StatType.STREAK -> {
                    if (score.label.contains("Best")) {
                        binding.sectionRecords.textAllTimeBest.text = "${score.value} days"
                    }
                }
                ScoreData.StatType.COUNT -> {
                    binding.sectionRecords.textTotalSessions.text = score.value.toString()
                }
                else -> {}
            }
        }
    }
    
    /**
     * Update motivational content based on current progress
     */
    private fun updateMotivationalContent(score: Long) {
        val remainingMs = viewModel.timeRemaining.value ?: 0L
        val hours = score / 3600
        val days = hours / 24
        val reachedTarget = remainingMs <= 0

        // Update top motivational message — countdown-aware
        val motivation = when {
            days >= 30 -> "You're a champion! 🏆"
            days >= 7 -> "Incredible discipline! 🎉"
            days >= 1 -> "Over a day! Keep going 💚"
            reachedTarget -> "Target reached! Every extra minute counts 💪"
            hours >= 1 -> "Hold on, you're getting there ⭐"
            else -> "Stretch the interval 🌿"
        }
        binding.sectionHero.textViewMotivation.text = motivation

        // Update status badge
        val statusBadge = when {
            days >= 30 -> "🏆 Champion"
            days >= 7 -> "🔥 On Fire"
            days >= 1 -> "🌟 Day Starter"
            reachedTarget -> "💪 Target Reached"
            else -> "🌱 Waiting"
        }
        binding.sectionHero.textStatusBadge.text = statusBadge

        // Update contextual message below timer
        val context = when {
            days >= 7 -> "Your intervals are massive. Your body is healing."
            days >= 1 -> "Over 24 hours! You're stretching your limits"
            reachedTarget -> "You've hit your target. Keep going for bonus time!"
            hours >= 3 -> "More than halfway there. Stay strong!"
            hours >= 1 -> "One hour down. The craving will pass."
            else -> "Every extra minute reduces your exposure."
        }
        binding.sectionHero.textStreakContext.text = context
    }
    
    private fun updateButtonState(percentage: Double) {
        // Keep button consistent - always amber color with "I Smoked" text
        val colorRes = R.color.accent_amber
        val text = "I Smoked"
        
        binding.fabSmoke.backgroundTintList = ContextCompat.getColorStateList(this, colorRes)
        binding.fabSmoke.text = text
        
        // Update text color for contrast
        val textColor = R.color.black
        binding.fabSmoke.setTextColor(ContextCompat.getColor(this, textColor))
        binding.fabSmoke.iconTint = ContextCompat.getColorStateList(this, textColor)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshData()
        refreshHandler.postDelayed(refreshRunnable, 1000)
    }

    override fun onPause() {
        super.onPause()
        refreshHandler.removeCallbacks(refreshRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
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
