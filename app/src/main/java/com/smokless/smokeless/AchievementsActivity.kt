package com.smokless.smokeless

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.smokless.smokeless.data.AppDatabase
import com.smokless.smokeless.databinding.ActivityAchievementsBinding
import com.smokless.smokeless.util.Achievement
import com.smokless.smokeless.util.AchievementManager

class AchievementsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAchievementsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAchievementsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.recyclerAchievements.layoutManager = LinearLayoutManager(this)

        loadAchievements()
    }

    private fun loadAchievements() {
        AppDatabase.databaseExecutor.execute {
            val db = AppDatabase.getInstance(application)
            val sessions = db.smokingSessionDao().getAllSessions()
            val cravings = db.cravingDao().getAllCravings()

            val bestStreak = if (sessions.isEmpty()) 0
            else com.smokless.smokeless.util.ScoreCalculator.calculatePeriodStats(sessions, "all").bestStreak

            val cleanDays = if (sessions.isEmpty()) 0
            else com.smokless.smokeless.util.ScoreCalculator.calculatePeriodStats(sessions, "all").cleanDays

            val lastTimestamp = db.smokingSessionDao().getLastTimestamp() ?: 0L
            val hoursSinceLast = if (lastTimestamp > 0) (System.currentTimeMillis() - lastTimestamp) / 3600000 else 0L
            val daysSinceLast = (hoursSinceLast / 24).toInt()

            val achievements = AchievementManager.getAllAchievements(
                daysSinceLast, bestStreak, cravings.size, cleanDays
            )

            val unlockedCount = achievements.count { it.isUnlocked }

            runOnUiThread {
                binding.textAchievementProgress.text = "$unlockedCount of ${achievements.size} achievements unlocked"
                binding.progressAchievements.max = achievements.size
                binding.progressAchievements.progress = unlockedCount
                binding.recyclerAchievements.adapter = AchievementAdapter(achievements)
            }
        }
    }

    class AchievementAdapter(private val achievements: List<Achievement>) :
        RecyclerView.Adapter<AchievementAdapter.ViewHolder>() {

        class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
            val icon: TextView = view.findViewById(R.id.textIcon)
            val title: TextView = view.findViewById(R.id.textTitle)
            val description: TextView = view.findViewById(R.id.textDescription)
            val status: TextView = view.findViewById(R.id.textStatus)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_achievement, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val achievement = achievements[position]
            holder.icon.text = achievement.icon
            holder.title.text = achievement.title
            holder.description.text = achievement.description
            holder.status.text = if (achievement.isUnlocked) "✅" else "🔒"
            holder.itemView.alpha = if (achievement.isUnlocked) 1.0f else 0.5f
        }

        override fun getItemCount() = achievements.size
    }
}
