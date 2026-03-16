package com.smokless.smokeless.ui.main

/**
 * Enhanced data class for chart information
 * Provides comprehensive data for visualizing smoking patterns and trends
 * 
 * Improvements:
 * - Adaptive moving average window based on time period
 * - Weighted trend calculation for better accuracy
 * - Consistent data structure across all time periods
 * - Optimized label generation to prevent chart overcrowding
 */
data class ChartData(
    val dailyCounts: List<Int>,          // Cigarettes per day/hour (includes 0 counts)
    val labels: List<String>,            // Date/time labels for x-axis
    val movingAverage: List<Double>,     // Adaptive moving average for trend line
    val avgDailyCount: Double,           // Overall average for the period
    val isImproving: Boolean,            // Whether trend shows improvement (fewer cigarettes)
    val bestDay: Int,                    // Lowest non-zero count in period
    val worstDay: Int,                   // Highest count in period
    val cleanDays: Int,                  // Days/hours with 0 cigarettes
    val trendPercentage: Double          // Trend change as percentage (positive = improving)
)





