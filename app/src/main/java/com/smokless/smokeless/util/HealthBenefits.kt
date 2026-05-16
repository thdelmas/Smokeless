package com.smokless.smokeless.util

enum class BodySystem(val label: String, val emoji: String) {
    IMMEDIATE("Immediate", "🌱"),
    HEART("Heart", "❤️"),
    LUNGS("Lungs", "🫁"),
    BLOOD("Blood & oxygen", "💨"),
    SENSES("Taste & smell", "👃"),
    CIRCULATION("Circulation", "🚶"),
    CANCER_RISK("Cancer risk", "🛡️"),
    LIFE("Life expectancy", "✨")
}

data class HealthMilestone(
    val hours: Int,
    val title: String,
    val description: String,
    val icon: String,
    val isAchieved: Boolean = false,
    val bodySystem: BodySystem = BodySystem.IMMEDIATE,
    val details: String = "",
    val source: String = ""
)

object HealthBenefits {

    private val milestones = listOf(
        HealthMilestone(
            hours = 0,
            title = "Immediate",
            description = "Your journey to better health begins now",
            icon = "🌱",
            bodySystem = BodySystem.IMMEDIATE,
            details = "Each cigarette delivers ~7,000 chemicals — nicotine, carbon monoxide, tar, and at least 70 known carcinogens. The moment you stop, your body begins clearing them. Recovery is not all-or-nothing: every cigarette skipped is real exposure avoided.",
            source = "CDC, U.S. Surgeon General"
        ),
        HealthMilestone(
            hours = 1,
            title = "20 minutes",
            description = "Heart rate and blood pressure drop to normal levels",
            icon = "❤️",
            bodySystem = BodySystem.HEART,
            details = "Nicotine is a stimulant: it constricts blood vessels and elevates heart rate by 10–20 bpm. Within 20 minutes of the last cigarette, your pulse and blood pressure return toward baseline. Peripheral circulation in your hands and feet starts to recover.",
            source = "American Heart Association"
        ),
        HealthMilestone(
            hours = 8,
            title = "8 hours",
            description = "Carbon monoxide level drops, oxygen level rises to normal",
            icon = "💨",
            bodySystem = BodySystem.BLOOD,
            details = "Carbon monoxide from smoke binds to hemoglobin 200× more tightly than oxygen, starving your tissues. After 8 hours without smoke, CO levels in your blood halve and oxygen-carrying capacity returns. You may notice less fatigue and clearer thinking.",
            source = "NHS"
        ),
        HealthMilestone(
            hours = 12,
            title = "12 hours",
            description = "Carbon monoxide in your blood normalizes",
            icon = "🫁",
            bodySystem = BodySystem.BLOOD,
            details = "CO has cleared to the level of a non-smoker. Your red blood cells can again carry their full payload of oxygen. Organs that were running on a reduced oxygen budget — heart, brain, muscles — are back to full supply.",
            source = "CDC"
        ),
        HealthMilestone(
            hours = 24,
            title = "1 day",
            description = "Heart attack risk begins to decrease",
            icon = "💪",
            bodySystem = BodySystem.HEART,
            details = "After 24 hours, the acute risk of heart attack starts dropping. Smoking causes platelets to clump and arteries to constrict — both of which begin to reverse. Carbon monoxide and nicotine are essentially gone from your bloodstream.",
            source = "American Heart Association"
        ),
        HealthMilestone(
            hours = 48,
            title = "2 days",
            description = "Nerve endings start regrowing, senses of taste and smell improve",
            icon = "👃",
            bodySystem = BodySystem.SENSES,
            details = "Smoke damages and dulls the nerve endings in your nose and the taste buds on your tongue. By 48 hours, those nerves begin to regenerate. Food starts to taste sharper. Smells you'd been missing — coffee, rain, skin — come back online.",
            source = "U.S. Surgeon General"
        ),
        HealthMilestone(
            hours = 72,
            title = "3 days",
            description = "Breathing becomes easier, bronchial tubes relax",
            icon = "🌬️",
            bodySystem = BodySystem.LUNGS,
            details = "The bronchial tubes that carry air into your lungs relax and open up. Lung capacity measurably increases. This is also the peak of physical nicotine withdrawal — irritability and cravings tend to be strongest now, then steadily ease.",
            source = "NHS"
        ),
        HealthMilestone(
            hours = 168,
            title = "1 week",
            description = "Most nicotine is out of your body, sense of taste and smell improve significantly",
            icon = "🌟",
            bodySystem = BodySystem.SENSES,
            details = "Nicotine and its main metabolite cotinine are fully cleared. Physical withdrawal symptoms — headache, restlessness, sleep disturbance — are mostly behind you. From here, the work is mostly behavioral, not chemical.",
            source = "CDC"
        ),
        HealthMilestone(
            hours = 336,
            title = "2 weeks",
            description = "Circulation improves, walking becomes easier",
            icon = "🚶",
            bodySystem = BodySystem.CIRCULATION,
            details = "Blood flow to your gums, fingers, toes, and skin improves measurably. Wound healing speeds up. Exercise feels easier as your cardiovascular system delivers more oxygen with less work.",
            source = "American Heart Association"
        ),
        HealthMilestone(
            hours = 720,
            title = "1 month",
            description = "Coughing and shortness of breath decrease, lung function begins to improve",
            icon = "🏃",
            bodySystem = BodySystem.LUNGS,
            details = "Cilia — the tiny hair-like structures lining your airways — start regrowing. They sweep mucus and trapped particles out of your lungs. Many people notice an increase in productive cough during this period; it's the lungs actively clearing accumulated tar.",
            source = "U.S. Surgeon General"
        ),
        HealthMilestone(
            hours = 2160,
            title = "3 months",
            description = "Circulation and lung function improve significantly",
            icon = "💚",
            bodySystem = BodySystem.LUNGS,
            details = "Lung function can improve by up to 30%. Cilia are largely restored, so the lungs are dramatically better at clearing infection. Cardiovascular fitness improves enough that aerobic activity feels noticeably easier.",
            source = "NHS"
        ),
        HealthMilestone(
            hours = 4320,
            title = "6 months",
            description = "Coughing, congestion, and shortness of breath continue to decrease",
            icon = "✨",
            bodySystem = BodySystem.LUNGS,
            details = "Chronic smoker's cough fades. Sinus congestion, fatigue, and shortness of breath continue to ease. Stress resilience improves — paradoxically, smoking was raising your baseline stress, not lowering it.",
            source = "CDC"
        ),
        HealthMilestone(
            hours = 8760,
            title = "1 year",
            description = "Risk of coronary heart disease is half that of a smoker",
            icon = "🎉",
            bodySystem = BodySystem.HEART,
            details = "Your excess risk of coronary heart disease has dropped to roughly half that of a continuing smoker. This is the single largest year-over-year health gain in the entire timeline.",
            source = "U.S. Surgeon General"
        ),
        HealthMilestone(
            hours = 26280,
            title = "3 years",
            description = "Risk of heart attack falls to that of a non-smoker",
            icon = "🏆",
            bodySystem = BodySystem.HEART,
            details = "Heart attack risk has fallen close to that of someone who never smoked. Vascular inflammation has subsided. Your endothelium — the lining of your blood vessels — has had time to heal.",
            source = "American Heart Association"
        ),
        HealthMilestone(
            hours = 43800,
            title = "5 years",
            description = "Stroke risk reduced to that of a non-smoker",
            icon = "🌈",
            bodySystem = BodySystem.CIRCULATION,
            details = "Stroke risk has dropped to that of a never-smoker. Risk of cancers of the mouth, throat, esophagus, and bladder has halved. Cervical cancer risk has returned to baseline.",
            source = "CDC"
        ),
        HealthMilestone(
            hours = 87600,
            title = "10 years",
            description = "Risk of lung cancer falls to half that of a smoker",
            icon = "👑",
            bodySystem = BodySystem.CANCER_RISK,
            details = "Lung cancer mortality risk is approximately half that of a continuing smoker. Risk of cancers of the larynx and pancreas also decreases substantially. Damage that took decades to accumulate continues to reverse.",
            source = "U.S. Surgeon General"
        ),
        HealthMilestone(
            hours = 131400,
            title = "15 years",
            description = "Risk of coronary heart disease is the same as a non-smoker",
            icon = "🎊",
            bodySystem = BodySystem.LIFE,
            details = "Your risk of coronary heart disease is now indistinguishable from someone who never smoked. Life expectancy gains are at their fullest: stopping before 40 recovers up to 9 years of lost life.",
            source = "New England Journal of Medicine"
        )
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
