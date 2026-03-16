package com.smokless.smokeless.ui.main

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.smokless.smokeless.R
import com.smokless.smokeless.util.TimeFormatter
import java.text.DecimalFormat
import kotlin.math.min

class ScoreAdapter : RecyclerView.Adapter<ScoreAdapter.ScoreViewHolder>() {
    
    private var scores: List<ScoreData> = emptyList()
    private val percentFormat = DecimalFormat("0")
    private val frequencyFormat = DecimalFormat("0.0")
    
    fun setScores(newScores: List<ScoreData>?) {
        this.scores = newScores ?: emptyList()
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScoreViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_score, parent, false)
        return ScoreViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ScoreViewHolder, position: Int) {
        val score = scores[position]
        holder.bind(score, percentFormat, frequencyFormat, position)
    }
    
    override fun getItemCount(): Int = scores.size
    
    class ScoreViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        
        private val progressRing: CircularProgressIndicator = itemView.findViewById(R.id.progressRing)
        private val textPercent: TextView = itemView.findViewById(R.id.textPercent)
        private val textLabel: TextView = itemView.findViewById(R.id.textLabel)
        private val textStatus: TextView = itemView.findViewById(R.id.textStatus)
        private val textValue: TextView = itemView.findViewById(R.id.textValue)
        
        fun bind(score: ScoreData, format: DecimalFormat, freqFormat: DecimalFormat, @Suppress("UNUSED_PARAMETER") position: Int) {
            val context = itemView.context
            
            // Set the label
            textLabel.text = score.label
            
            // Set progress ring and percentage
            val progressValue = min(score.percentage.coerceIn(0.0, 100.0), 100.0).toInt()
            progressRing.progress = progressValue
            
            // Display value based on type
            when (score.type) {
                ScoreData.StatType.COUNT -> {
                    textValue.text = "${score.value} ${score.unit}"
                    
                    // For raw counts (like "Total Smoked"), don't show percentage
                    if (score.percentage == 0.0 || score.label.contains("Total", ignoreCase = true)) {
                        textPercent.text = ""
                        progressRing.progress = 0
                        val neutralColor = ContextCompat.getColor(context, R.color.text_secondary)
                        textValue.setTextColor(neutralColor)
                        progressRing.setIndicatorColor(neutralColor)
                    } else {
                        textPercent.text = "${format.format(score.percentage)}%"
                        val color = getCountStatusColor(context, score.percentage)
                        textPercent.setTextColor(color)
                        textValue.setTextColor(color)
                        progressRing.setIndicatorColor(color)
                    }
                }
                ScoreData.StatType.AVERAGE, ScoreData.StatType.FREQUENCY -> {
                    textValue.text = "${freqFormat.format(score.decimalValue)} ${score.unit}"
                    textPercent.text = "${format.format(score.percentage)}%"
                    
                    val color = getAverageStatusColor(context, score.decimalValue)
                    textPercent.setTextColor(color)
                    textValue.setTextColor(color)
                    progressRing.setIndicatorColor(color)
                }
                ScoreData.StatType.DAYS -> {
                    // For success rate, show the percentage as the main metric
                    if (score.label.contains("Success", ignoreCase = true)) {
                        textValue.text = score.unit  // Shows "X/Y days"
                        textPercent.text = "${format.format(score.percentage)}%"
                    } else {
                        textValue.text = "${score.value} ${score.unit}"
                        textPercent.text = "${format.format(score.percentage)}%"
                    }
                    
                    val color = getStatusColors(context, score.percentage)[0]
                    textPercent.setTextColor(color)
                    textValue.setTextColor(color)
                    progressRing.setIndicatorColor(color)
                }
                ScoreData.StatType.STREAK -> {
                    textValue.text = "${score.value} ${score.unit}"
                    textPercent.text = "${format.format(score.percentage)}%"
                    
                    val color = getStatusColors(context, score.percentage)[0]
                    textPercent.setTextColor(color)
                    textValue.setTextColor(color)
                    progressRing.setIndicatorColor(color)
                }
                ScoreData.StatType.TREND -> {
                    // For trend, show the trend indicator emoji and description
                    textValue.text = score.unit
                    textPercent.text = score.statusEmoji
                    
                    val color = when {
                        score.percentage >= 20 -> ContextCompat.getColor(context, R.color.status_champion)
                        score.percentage >= -20 -> ContextCompat.getColor(context, R.color.status_steady)
                        else -> ContextCompat.getColor(context, R.color.status_reset)
                    }
                    textPercent.setTextColor(color)
                    textValue.setTextColor(color)
                    progressRing.setIndicatorColor(color)
                    progressRing.progress = 50  // Always 50% for trend
                }
            }
            
            // Show status badge for exceptional performance
            val statusText = when (score.type) {
                ScoreData.StatType.COUNT -> 
                    if (score.percentage == 0.0) null else getCountStatusText(score.percentage)
                ScoreData.StatType.AVERAGE, ScoreData.StatType.FREQUENCY -> 
                    getAverageStatusText(score.decimalValue)
                ScoreData.StatType.DAYS -> 
                    getSuccessRateStatusText(score.percentage)
                ScoreData.StatType.STREAK -> 
                    getStreakStatusText(score.value.toInt())
                ScoreData.StatType.TREND -> 
                    getTrendStatusText(score.percentage)
            }
            
            if (statusText != null) {
                textStatus.text = statusText
                val statusColor = when (score.type) {
                    ScoreData.StatType.COUNT -> 
                        getCountStatusColor(context, score.percentage)
                    ScoreData.StatType.AVERAGE, ScoreData.StatType.FREQUENCY ->
                        getAverageStatusColor(context, score.decimalValue)
                    ScoreData.StatType.DAYS ->
                        getStatusColors(context, score.percentage)[0]
                    ScoreData.StatType.STREAK ->
                        getStatusColors(context, score.percentage)[0]
                    ScoreData.StatType.TREND -> {
                        when {
                            score.percentage >= 20 -> ContextCompat.getColor(context, R.color.status_champion)
                            score.percentage >= -20 -> ContextCompat.getColor(context, R.color.status_steady)
                            else -> ContextCompat.getColor(context, R.color.status_reset)
                        }
                    }
                }
                textStatus.setTextColor(statusColor)
                textStatus.visibility = View.VISIBLE
            } else {
                textStatus.visibility = View.GONE
            }
        }
        
        private fun getStatusText(percentage: Double): String? {
            return when {
                percentage >= 100 -> "EXCELLENT"
                percentage >= 80 -> "GREAT"
                percentage >= 60 -> "GOOD"
                else -> null
            }
        }
        
        private fun getCountStatusText(percentage: Double): String? {
            // For cigarette count, show meaningful status (lower is better)
            return when {
                percentage == 100.0 -> "PERFECT"
                percentage >= 80 -> "GREAT"
                percentage >= 60 -> "GOOD"
                percentage >= 40 -> "FAIR"
                else -> null
            }
        }
        
        private fun getAverageStatusText(average: Double): String? {
            return when {
                average == 0.0 -> "SMOKE-FREE"
                average <= 2.0 -> "EXCELLENT"
                average <= 5.0 -> "VERY GOOD"
                average <= 10.0 -> "GOOD"
                average <= 15.0 -> "IMPROVING"
                else -> null
            }
        }
        
        private fun getSuccessRateStatusText(percentage: Double): String? {
            return when {
                percentage == 100.0 -> "PERFECT"
                percentage >= 80 -> "AMAZING"
                percentage >= 60 -> "GREAT"
                percentage >= 40 -> "GOOD"
                percentage >= 20 -> "KEEP GOING"
                else -> null
            }
        }
        
        private fun getStreakStatusText(days: Int): String? {
            return when {
                days >= 30 -> "CHAMPION"
                days >= 14 -> "AMAZING"
                days >= 7 -> "STRONG"
                days >= 3 -> "BUILDING"
                days >= 1 -> "STARTED"
                else -> null
            }
        }
        
        private fun getTrendStatusText(trendPercentage: Double): String? {
            return when {
                trendPercentage >= 30 -> "EXCELLENT"
                trendPercentage >= 10 -> "IMPROVING"
                trendPercentage >= -10 -> "STABLE"
                trendPercentage >= -30 -> "DECLINING"
                else -> "NEEDS FOCUS"
            }
        }
        
        private fun getStatusColors(context: Context, percentage: Double): IntArray {
            return when {
                percentage >= 100 -> intArrayOf(
                    ContextCompat.getColor(context, R.color.status_champion),
                    ContextCompat.getColor(context, R.color.status_champion_dim)
                )
                percentage >= 80 -> intArrayOf(
                    ContextCompat.getColor(context, R.color.status_strong),
                    ContextCompat.getColor(context, R.color.status_strong_dim)
                )
                percentage >= 60 -> intArrayOf(
                    ContextCompat.getColor(context, R.color.status_steady),
                    ContextCompat.getColor(context, R.color.status_steady_dim)
                )
                percentage >= 40 -> intArrayOf(
                    ContextCompat.getColor(context, R.color.status_building),
                    ContextCompat.getColor(context, R.color.status_building_dim)
                )
                percentage >= 20 -> intArrayOf(
                    ContextCompat.getColor(context, R.color.status_starting),
                    ContextCompat.getColor(context, R.color.status_starting_dim)
                )
                else -> intArrayOf(
                    ContextCompat.getColor(context, R.color.status_reset),
                    ContextCompat.getColor(context, R.color.status_reset_dim)
                )
            }
        }
        
        private fun getCountStatusColor(context: Context, percentage: Double): Int {
            // For cigarette count, use reverse colors (fewer is better)
            return when {
                percentage == 100.0 -> ContextCompat.getColor(context, R.color.status_champion)  // Perfect - green
                percentage >= 80 -> ContextCompat.getColor(context, R.color.status_strong)       // 1-4 cigarettes - light green
                percentage >= 60 -> ContextCompat.getColor(context, R.color.accent_teal)         // 5-8 cigarettes - teal
                percentage >= 40 -> ContextCompat.getColor(context, R.color.accent_amber)        // 9-12 cigarettes - amber
                percentage >= 20 -> ContextCompat.getColor(context, R.color.status_starting)     // 13-16 cigarettes - orange
                else -> ContextCompat.getColor(context, R.color.status_reset)                     // 17+ cigarettes - red
            }
        }
        
        private fun getAverageStatusColor(context: Context, average: Double): Int {
            // Color based on cigarettes per day average
            return when {
                average == 0.0 -> ContextCompat.getColor(context, R.color.status_champion)      // Smoke-free
                average <= 2.0 -> ContextCompat.getColor(context, R.color.status_strong)        // Excellent
                average <= 5.0 -> ContextCompat.getColor(context, R.color.accent_teal)          // Very good
                average <= 10.0 -> ContextCompat.getColor(context, R.color.status_steady)       // Good
                average <= 15.0 -> ContextCompat.getColor(context, R.color.accent_amber)        // Improving
                else -> ContextCompat.getColor(context, R.color.status_reset)                    // Needs work
            }
        }
    }
}

