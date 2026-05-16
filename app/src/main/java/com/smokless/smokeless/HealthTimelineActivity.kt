package com.smokless.smokeless

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.smokless.smokeless.data.AppDatabase
import com.smokless.smokeless.util.HealthBenefits
import com.smokless.smokeless.util.HealthMilestone
import com.smokless.smokeless.util.SubstanceCopy

class HealthTimelineActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_health_timeline)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { finish() }

        val recycler = findViewById<RecyclerView>(R.id.recyclerTimeline)
        recycler.layoutManager = LinearLayoutManager(this)

        loadTimeline()
    }

    private fun loadTimeline() {
        AppDatabase.databaseExecutor.execute {
            val db = AppDatabase.getInstance(application)
            val sessions = db.smokingSessionDao().getAllSessions()
            val lastTimestamp = db.smokingSessionDao().getLastTimestamp() ?: 0L
            val hoursSmokeFree = if (lastTimestamp > 0)
                (System.currentTimeMillis() - lastTimestamp) / 3_600_000L
            else 0L

            val milestones = HealthBenefits.getMilestones(hoursSmokeFree)
            val next = HealthBenefits.getNextMilestone(hoursSmokeFree)
            val achieved = milestones.count { it.isAchieved }
            val copy = SubstanceCopy.forSubstance(SubstanceCopy.primarySubstance(sessions))

            runOnUiThread {
                findViewById<TextView>(R.id.textSmokeFreeTime).text =
                    formatSmokeFreeLabel(hoursSmokeFree, copy)

                findViewById<TextView>(R.id.textMilestoneProgress).text =
                    "$achieved / ${milestones.size} milestones reached"

                val progress = findViewById<LinearProgressIndicator>(R.id.progressMilestones)
                progress.max = milestones.size
                progress.progress = achieved

                val nextLabel = findViewById<TextView>(R.id.textNextMilestone)
                if (next != null) {
                    val remainingHours = (next.hours - hoursSmokeFree).coerceAtLeast(0L)
                    nextLabel.text =
                        "Next: ${next.icon} ${next.title} — in ${formatDuration(remainingHours)}"
                } else {
                    nextLabel.text = "You've reached every milestone in the timeline."
                }

                findViewById<RecyclerView>(R.id.recyclerTimeline).adapter =
                    TimelineAdapter(milestones, hoursSmokeFree)
            }
        }
    }

    private fun formatSmokeFreeLabel(hours: Long, copy: SubstanceCopy): String {
        if (hours <= 0) return "Tracking from your last log"
        val days = hours / 24
        val suffix = copy.cleanSuffix
        return when {
            days >= 365 -> "${days / 365}y ${days % 365}d $suffix"
            days >= 1 -> "${days}d ${hours % 24}h $suffix"
            else -> "${hours}h $suffix"
        }
    }

    private fun formatDuration(hours: Long): String {
        return when {
            hours <= 0 -> "now"
            hours < 24 -> "${hours}h"
            hours < 24 * 30 -> "${hours / 24}d"
            hours < 24 * 365 -> "${hours / (24 * 30)}mo"
            else -> "${hours / (24 * 365)}y"
        }
    }

    private class TimelineAdapter(
        private val milestones: List<HealthMilestone>,
        private val hoursSmokeFree: Long
    ) : RecyclerView.Adapter<TimelineAdapter.ViewHolder>() {

        private val expanded = mutableSetOf<Int>()

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val card: MaterialCardView = view.findViewById(R.id.cardRoot)
            val icon: TextView = view.findViewById(R.id.textIcon)
            val title: TextView = view.findViewById(R.id.textTitle)
            val bodySystem: TextView = view.findViewById(R.id.textBodySystem)
            val status: TextView = view.findViewById(R.id.textStatus)
            val description: TextView = view.findViewById(R.id.textDescription)
            val details: LinearLayout = view.findViewById(R.id.groupDetails)
            val detailsText: TextView = view.findViewById(R.id.textDetails)
            val sourceText: TextView = view.findViewById(R.id.textSource)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_health_milestone, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val milestone = milestones[position]
            holder.icon.text = milestone.icon
            holder.title.text = milestone.title
            holder.bodySystem.text = milestone.bodySystem.label
            holder.description.text = milestone.description

            val isNext = !milestone.isAchieved &&
                milestones.indexOfFirst { !it.isAchieved } == position
            holder.status.text = when {
                milestone.isAchieved -> "✅"
                isNext -> "⏳"
                else -> "🔒"
            }
            holder.itemView.alpha = if (milestone.isAchieved || isNext) 1.0f else 0.55f

            val isOpen = expanded.contains(position)
            holder.details.visibility = if (isOpen) View.VISIBLE else View.GONE
            holder.detailsText.text = milestone.details
            holder.sourceText.text = if (milestone.source.isNotEmpty())
                "Source: ${milestone.source}"
            else ""
            holder.sourceText.visibility =
                if (milestone.source.isNotEmpty()) View.VISIBLE else View.GONE

            holder.card.setOnClickListener {
                if (isOpen) expanded.remove(position) else expanded.add(position)
                notifyItemChanged(position)
            }
        }

        override fun getItemCount() = milestones.size
    }
}
