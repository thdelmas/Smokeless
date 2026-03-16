package com.smokless.smokeless.util

data class HealthMilestone(
    val hours: Int,
    val title: String,
    val description: String,
    val icon: String,
    val isAchieved: Boolean = false
)

object HealthBenefits {
    
    private val milestones = listOf(
        HealthMilestone(0, "Immediate", "Your journey to better health begins now", "🌱"),
        HealthMilestone(1, "20 Minutes", "Heart rate and blood pressure drop to normal levels", "❤️"),
        HealthMilestone(8, "8 Hours", "Carbon monoxide level drops, oxygen level rises to normal", "💨"),
        HealthMilestone(12, "12 Hours", "Carbon monoxide in your blood normalizes", "🫁"),
        HealthMilestone(24, "1 Day", "Heart attack risk begins to decrease", "💪"),
        HealthMilestone(48, "2 Days", "Nerve endings start regrowing, senses of taste and smell improve", "👃"),
        HealthMilestone(72, "3 Days", "Breathing becomes easier, bronchial tubes relax", "🌬️"),
        HealthMilestone(168, "1 Week", "Most nicotine is out of your body, sense of taste and smell improve significantly", "🌟"),
        HealthMilestone(336, "2 Weeks", "Circulation improves, walking becomes easier", "🚶"),
        HealthMilestone(720, "1 Month", "Coughing and shortness of breath decrease, lung function begins to improve", "🏃"),
        HealthMilestone(2160, "3 Months", "Circulation and lung function improve significantly", "💚"),
        HealthMilestone(4320, "6 Months", "Coughing, congestion, and shortness of breath continue to decrease", "✨"),
        HealthMilestone(8760, "1 Year", "Risk of coronary heart disease is half that of a smoker", "🎉"),
        HealthMilestone(26280, "3 Years", "Risk of heart attack falls to that of a non-smoker", "🏆"),
        HealthMilestone(43800, "5 Years", "Stroke risk reduced to that of a non-smoker", "🌈"),
        HealthMilestone(87600, "10 Years", "Risk of lung cancer falls to half that of a smoker", "👑"),
        HealthMilestone(131400, "15 Years", "Risk of coronary heart disease is the same as a non-smoker", "🎊")
    )
    
    fun getMilestones(hoursSmokesFree: Long): List<HealthMilestone> {
        return milestones.map { milestone ->
            milestone.copy(isAchieved = hoursSmokesFree >= milestone.hours)
        }
    }
    
    fun getNextMilestone(hoursSmokesFree: Long): HealthMilestone? {
        return milestones.firstOrNull { it.hours > hoursSmokesFree }
    }
    
    fun getCurrentMilestone(hoursSmokesFree: Long): HealthMilestone? {
        return milestones.lastOrNull { it.hours <= hoursSmokesFree }
    }
    
    fun getProgressToNextMilestone(hoursSmokesFree: Long): Float {
        val current = getCurrentMilestone(hoursSmokesFree)
        val next = getNextMilestone(hoursSmokesFree)
        
        if (current == null || next == null) return 100f
        
        val progress = (hoursSmokesFree - current.hours).toFloat()
        val total = (next.hours - current.hours).toFloat()
        
        return (progress / total) * 100f
    }
    
    fun getMotivationalMessage(hoursSmokesFree: Long): String {
        return when {
            hoursSmokesFree < 1 -> "Every moment smoke-free is a win for your health!"
            hoursSmokesFree < 12 -> "Your body is already starting to heal. Keep going!"
            hoursSmokesFree < 24 -> "Half a day! Your oxygen levels are improving."
            hoursSmokesFree < 72 -> "Great progress! Your body is working hard to recover."
            hoursSmokesFree < 168 -> "Almost a week! The worst of the withdrawal is behind you."
            hoursSmokesFree < 720 -> "You're building lasting change. Your body is thanking you!"
            hoursSmokesFree < 2160 -> "A full month! You've proven you can do this."
            hoursSmokesFree < 8760 -> "Months of progress! Your health improvements are significant."
            hoursSmokesFree < 43800 -> "Years of health gains! You're a success story."
            else -> "You've achieved incredible long-term health benefits!"
        }
    }
}

