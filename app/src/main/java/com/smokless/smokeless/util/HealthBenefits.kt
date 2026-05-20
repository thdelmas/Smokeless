package com.smokless.smokeless.util

import com.smokless.smokeless.data.entity.Substance

enum class BodySystem(val label: String, val emoji: String) {
    IMMEDIATE("Immediate", "🌱"),
    HEART("Heart", "❤️"),
    LUNGS("Lungs", "🫁"),
    BLOOD("Blood & oxygen", "💨"),
    SENSES("Taste & smell", "👃"),
    CIRCULATION("Circulation", "🚶"),
    CANCER_RISK("Cancer risk", "🛡️"),
    LIFE("Life expectancy", "✨"),
    SLEEP("Sleep", "😴"),
    COGNITION("Memory & focus", "🧠"),
    MOOD("Mood", "🌤️"),
}

data class HealthMilestone(
    val hours: Int,
    val title: String,
    val description: String,
    val icon: String,
    val isAchieved: Boolean = false,
    val bodySystem: BodySystem = BodySystem.IMMEDIATE,
    val details: String = "",
    val source: String = "",
    /**
     * Evidence-backed habits that *accelerate* recovery at this stage —
     * humid/sea air for cilia clearance, aerobic exercise for vascular
     * recovery, breathing exercises for vagal tone, etc. Empty when there's
     * no high-quality actionable lever at this specific timepoint.
     */
    val actions: String = "",
)

object HealthBenefits {

    private val tobaccoMilestones = listOf(
        HealthMilestone(
            hours = 0,
            title = "Immediate",
            description = "Your journey to better health begins now",
            icon = "🌱",
            bodySystem = BodySystem.IMMEDIATE,
            details = "Each cigarette delivers ~7,000 chemicals — nicotine, carbon monoxide, tar, and at least 70 known carcinogens. The moment you stop, your body begins clearing them. Recovery is not all-or-nothing: every cigarette skipped is real exposure avoided.",
            source = "CDC, U.S. Surgeon General",
            actions = "Drink water — flushes nicotine metabolites faster. Try four slow nasal breaths (4s in, 6s out): this dampens the craving wave by activating the parasympathetic system."
        ),
        HealthMilestone(
            hours = 1,
            title = "20 minutes",
            description = "Heart rate and blood pressure drop to normal levels",
            icon = "❤️",
            bodySystem = BodySystem.HEART,
            details = "Nicotine is a stimulant: it constricts blood vessels and elevates heart rate by 10–20 bpm. Within 20 minutes of the last cigarette, your pulse and blood pressure return toward baseline. Peripheral circulation in your hands and feet starts to recover.",
            source = "American Heart Association",
            actions = "Slow diaphragmatic breathing speeds the heart-rate drop. A 5-minute walk warms peripheral circulation and pushes blood into the hands and feet."
        ),
        HealthMilestone(
            hours = 8,
            title = "8 hours",
            description = "Carbon monoxide level drops, oxygen level rises to normal",
            icon = "💨",
            bodySystem = BodySystem.BLOOD,
            details = "Carbon monoxide from smoke binds to hemoglobin 200× more tightly than oxygen, starving your tissues. After 8 hours without smoke, CO levels in your blood halve and oxygen-carrying capacity returns. You may notice less fatigue and clearer thinking.",
            source = "NHS",
            actions = "Step outside, ideally somewhere with fresh, humid air (a park, near water). Take ten slow deep breaths — your hemoglobin is finally free to pick up oxygen again."
        ),
        HealthMilestone(
            hours = 12,
            title = "12 hours",
            description = "Carbon monoxide in your blood normalizes",
            icon = "🫁",
            bodySystem = BodySystem.BLOOD,
            details = "CO has cleared to the level of a non-smoker. Your red blood cells can again carry their full payload of oxygen. Organs that were running on a reduced oxygen budget — heart, brain, muscles — are back to full supply.",
            source = "CDC",
            actions = "A 15–20 minute brisk walk capitalizes on the restored oxygen capacity. Light aerobic activity now feels noticeably less heavy than it did yesterday."
        ),
        HealthMilestone(
            hours = 24,
            title = "1 day",
            description = "Heart attack risk begins to decrease",
            icon = "💪",
            bodySystem = BodySystem.HEART,
            details = "After 24 hours, the acute risk of heart attack starts dropping. Smoking causes platelets to clump and arteries to constrict — both of which begin to reverse. Carbon monoxide and nicotine are essentially gone from your bloodstream.",
            source = "American Heart Association",
            actions = "20–30 minutes of moderate cardio (brisk walk, easy bike) compounds the cardiovascular gain. Sleep well: vascular healing happens overnight."
        ),
        HealthMilestone(
            hours = 48,
            title = "2 days",
            description = "Nerve endings start regrowing, senses of taste and smell improve",
            icon = "👃",
            bodySystem = BodySystem.SENSES,
            details = "Smoke damages and dulls the nerve endings in your nose and the taste buds on your tongue. By 48 hours, those nerves begin to regenerate. Food starts to taste sharper. Smells you'd been missing — coffee, rain, skin — come back online.",
            source = "U.S. Surgeon General",
            actions = "Eat something with sharp flavor or aroma — citrus, fresh herbs, a real cup of coffee. Anchoring the recovering senses to specific moments helps you notice the change."
        ),
        HealthMilestone(
            hours = 72,
            title = "3 days",
            description = "Breathing becomes easier, bronchial tubes relax",
            icon = "🌬️",
            bodySystem = BodySystem.LUNGS,
            details = "The bronchial tubes that carry air into your lungs relax and open up. Lung capacity measurably increases. This is also the peak of physical nicotine withdrawal — irritability and cravings tend to be strongest now, then steadily ease.",
            source = "NHS",
            actions = "Humid air helps the now-relaxed bronchial tubes clear: a hot shower with the door closed, a steaming bowl of water under a towel, or time outside near the coast all loosen mucus. Pursed-lip breathing (inhale 2s through nose, exhale 4s through pursed lips) opens the small airways."
        ),
        HealthMilestone(
            hours = 168,
            title = "1 week",
            description = "Most nicotine is out of your body, sense of taste and smell improve significantly",
            icon = "🌟",
            bodySystem = BodySystem.SENSES,
            details = "Nicotine and its main metabolite cotinine are fully cleared. Physical withdrawal symptoms — headache, restlessness, sleep disturbance — are mostly behind you. From here, the work is mostly behavioral, not chemical.",
            source = "CDC",
            actions = "Build a small daily aerobic habit — 20–30 min, three times this week. Exercise blunts craving intensity and accelerates cilia recovery. Keep hydration high (≥2L water) to support ongoing mucus clearance."
        ),
        HealthMilestone(
            hours = 336,
            title = "2 weeks",
            description = "Circulation improves, walking becomes easier",
            icon = "🚶",
            bodySystem = BodySystem.CIRCULATION,
            details = "Blood flow to your gums, fingers, toes, and skin improves measurably. Wound healing speeds up. Exercise feels easier as your cardiovascular system delivers more oxygen with less work.",
            source = "American Heart Association",
            actions = "Push the cardio a little: this is the window where Zone-2 work (40-min easy run or bike) returns disproportionate aerobic gains. Floss daily — gum healing is now on your side."
        ),
        HealthMilestone(
            hours = 720,
            title = "1 month",
            description = "Coughing and shortness of breath decrease, lung function begins to improve",
            icon = "🏃",
            bodySystem = BodySystem.LUNGS,
            details = "Cilia — the tiny hair-like structures lining your airways — start regrowing. They sweep mucus and trapped particles out of your lungs. Many people notice an increase in productive cough during this period; it's the lungs actively clearing accumulated tar.",
            source = "U.S. Surgeon General",
            actions = "This is the sea-air window. Salt-laden coastal or humid air thins airway mucus and gives the new cilia an easier sweep; a weekend by the sea, a walk in fog, or daily steam inhalation all support clearance. Aerobic exercise (≥30 min) physically mobilizes the deeper mucus. The cough is the system working — don't suppress it unless it disrupts sleep."
        ),
        HealthMilestone(
            hours = 2160,
            title = "3 months",
            description = "Circulation and lung function improve significantly",
            icon = "💚",
            bodySystem = BodySystem.LUNGS,
            details = "Lung function can improve by up to 30%. Cilia are largely restored, so the lungs are dramatically better at clearing infection. Cardiovascular fitness improves enough that aerobic activity feels noticeably easier.",
            source = "NHS",
            actions = "Add short intervals to your cardio — even 4 × 30 sec hard / 90 sec easy. The lung gain is now real enough that targeted training compounds it. Keep humid/coastal air in rotation if you can; mucociliary clearance still benefits."
        ),
        HealthMilestone(
            hours = 4320,
            title = "6 months",
            description = "Coughing, congestion, and shortness of breath continue to decrease",
            icon = "✨",
            bodySystem = BodySystem.LUNGS,
            details = "Chronic smoker's cough fades. Sinus congestion, fatigue, and shortness of breath continue to ease. Stress resilience improves — paradoxically, smoking was raising your baseline stress, not lowering it.",
            source = "CDC",
            actions = "Anchor a non-smoking stress habit: 5 minutes of slow breathing, a short walk, or cold-water splash on the wrists. Replacing the cigarette's stress-relief role is the main remaining defense against relapse."
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

    /**
     * Cannabis recovery timeline. THC is fat-soluble, so its physiological
     * profile is different from nicotine: shorter acute effects, but a
     * weeks-long clearance and sleep-architecture rebound that nicotine
     * doesn't have. Hours represent time since last cannabis exposure.
     * Sources: NIDA, Huestis 2007 (clinical pharmacokinetics of cannabinoids),
     * Bonn-Miller et al. on sleep architecture during cessation.
     */
    private val cannabisMilestones = listOf(
        HealthMilestone(
            hours = 0,
            title = "Immediate",
            description = "Acute exposure begins clearing from your bloodstream",
            icon = "🌱",
            bodySystem = BodySystem.IMMEDIATE,
            details = "THC plasma half-life in occasional users is roughly 24–30 hours. Even a single break of 24h takes your active level to about half. There's nothing all-or-nothing about cannabis recovery — every session skipped is real exposure avoided.",
            source = "Huestis, 2007 (Chem Biodivers)",
            actions = "Drink water and eat something with protein — both speed cannabinoid metabolism slightly. A short walk reduces the heaviness."
        ),
        HealthMilestone(
            hours = 4,
            title = "4 hours",
            description = "Acute high subsides, coordination and reaction time return",
            icon = "🎯",
            bodySystem = BodySystem.COGNITION,
            details = "Peak intoxication has passed. Motor coordination, reaction time, and short-term recall measurably recover within 3–4 hours of a single smoked dose. Edibles take longer — closer to 6–8 hours.",
            source = "NIDA",
            actions = "Fresh air and movement help shake off residual heaviness. Don't drive yet — coordination is back but reaction time can lag for several more hours."
        ),
        HealthMilestone(
            hours = 24,
            title = "1 day",
            description = "Plasma THC roughly halves; mental clarity sharpens",
            icon = "🧠",
            bodySystem = BodySystem.COGNITION,
            details = "Active THC in your bloodstream has dropped to about half. Working memory, attention, and verbal recall improve. Eye redness and dry-mouth side effects resolve.",
            source = "Huestis, 2007",
            actions = "Use the cognitive lift: do the work or conversation you'd been putting off. Mild cardio accelerates THC clearance via fat-cell turnover."
        ),
        HealthMilestone(
            hours = 72,
            title = "3 days",
            description = "REM rebound begins — sleep architecture starts normalizing",
            icon = "😴",
            bodySystem = BodySystem.SLEEP,
            details = "Cannabis suppresses REM sleep. By day 3, REM is bouncing back — this is when vivid dreams, sometimes unsettling ones, often appear. That's your brain catching up on dream-stage sleep it had been skipping.",
            source = "Bonn-Miller et al., 2014 (J Sleep Res)",
            actions = "Protect sleep: dim screens an hour before bed, cool room, no late caffeine. Vivid dreams will fade in a week or two as REM rebalances — don't fight them, they're a sign the brain is recovering."
        ),
        HealthMilestone(
            hours = 168,
            title = "1 week",
            description = "Withdrawal symptoms peak then ease — appetite and mood stabilize",
            icon = "🌤️",
            bodySystem = BodySystem.MOOD,
            details = "Cannabis withdrawal — irritability, anxiety, appetite loss, sleep disturbance — usually peaks days 2–6 and recedes by the end of week 1. The worst is behind you. Cravings continue but the physical edge softens.",
            source = "DSM-5; NIDA",
            actions = "Daily cardio is the single best lever now — 20–30 min meaningfully blunts irritability and aids sleep. Eat small frequent meals if appetite is off, and lean on social contact: this is where isolation drives relapse."
        ),
        HealthMilestone(
            hours = 336,
            title = "2 weeks",
            description = "Cognitive performance and mood baselines re-establish",
            icon = "🧠",
            bodySystem = BodySystem.COGNITION,
            details = "Studies of regular users show working memory, processing speed, and attention measurably improving over the first 2–3 weeks of cessation. Anxiety and irritability return toward baseline.",
            source = "Schuster et al., 2018 (J Clin Psychiatry)"
        ),
        HealthMilestone(
            hours = 720,
            title = "1 month",
            description = "Most occasional users test fully clear; airway irritation drops",
            icon = "🫁",
            bodySystem = BodySystem.LUNGS,
            details = "For occasional users, urine THC metabolites typically clear within 30 days. Cough and airway inflammation from smoke inhalation reduce sharply once exposure stops.",
            source = "Smith-Kielland et al., 1999",
            actions = "Humid or coastal air helps clear residual airway irritation — a walk by the sea, in fog, or daily steam inhalation thins mucus so the airways can finish recovering. Pursed-lip breathing exercises tone the deeper airways."
        ),
        HealthMilestone(
            hours = 2160,
            title = "3 months",
            description = "Lung inflammation continues to subside; deep sleep stabilizes",
            icon = "🌬️",
            bodySystem = BodySystem.LUNGS,
            details = "Chronic respiratory irritation from smoked cannabis continues to subside. Slow-wave (deep) sleep — disrupted by long-term THC use — has stabilized. Cognitive recovery continues in heavy former users.",
            source = "Tashkin, 2013 (Ann Am Thorac Soc)",
            actions = "Regular aerobic exercise (3–5×/week) compounds the lung gain. If a productive cough is still showing up, keep humid air around — coastal walks, steam, a humidifier indoors. Avoid secondhand smoke; the recovering airways are more reactive."
        ),
        HealthMilestone(
            hours = 8760,
            title = "1 year",
            description = "Cognitive baseline largely restored; cravings rare",
            icon = "🎉",
            bodySystem = BodySystem.COGNITION,
            details = "Most measurable cognitive and emotional differences from chronic use have resolved. Cravings still surface around triggers, but the physiological pull is essentially gone.",
            source = "Meier et al., 2018 (Addiction)"
        ),
    )

    private fun milestonesFor(substance: Substance): List<HealthMilestone> = when (substance) {
        Substance.TOBACCO -> tobaccoMilestones
        Substance.CANNABIS -> cannabisMilestones
    }

    fun getMilestones(
        hoursSmokesFree: Long,
        substance: Substance = Substance.DEFAULT,
    ): List<HealthMilestone> {
        return milestonesFor(substance).map { milestone ->
            milestone.copy(isAchieved = hoursSmokesFree >= milestone.hours)
        }
    }

    fun getNextMilestone(
        hoursSmokesFree: Long,
        substance: Substance = Substance.DEFAULT,
    ): HealthMilestone? {
        return milestonesFor(substance).firstOrNull { it.hours > hoursSmokesFree }
    }

    fun getCurrentMilestone(
        hoursSmokesFree: Long,
        substance: Substance = Substance.DEFAULT,
    ): HealthMilestone? {
        return milestonesFor(substance).lastOrNull { it.hours <= hoursSmokesFree }
    }

    fun getProgressToNextMilestone(
        hoursSmokesFree: Long,
        substance: Substance = Substance.DEFAULT,
    ): Float {
        val current = getCurrentMilestone(hoursSmokesFree, substance)
        val next = getNextMilestone(hoursSmokesFree, substance)

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
