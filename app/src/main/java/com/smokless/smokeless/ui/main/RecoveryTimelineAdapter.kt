package com.smokless.smokeless.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.smokless.smokeless.R
import com.smokless.smokeless.util.HealthMilestone

class RecoveryTimelineAdapter(
    private var milestones: List<HealthMilestone> = emptyList(),
) : RecyclerView.Adapter<RecoveryTimelineAdapter.ViewHolder>() {

    private val expanded = mutableSetOf<Int>()

    fun submit(updated: List<HealthMilestone>) {
        milestones = updated
        notifyDataSetChanged()
    }

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
        val ctx = holder.itemView.context

        holder.icon.text = milestone.icon
        holder.title.text = milestone.title
        holder.bodySystem.text = milestone.bodySystem.label
        holder.description.text = milestone.description

        val firstUpcomingIdx = milestones.indexOfFirst { !it.isAchieved }
        val isCurrent = !milestone.isAchieved && firstUpcomingIdx == position

        holder.status.text = when {
            milestone.isAchieved -> "✅"
            isCurrent -> "⏳"
            else -> "🔒"
        }

        // Three visual states: achieved (faded), current (full), upcoming (dimmed).
        holder.itemView.alpha = when {
            isCurrent -> 1.0f
            milestone.isAchieved -> 0.65f
            else -> 0.45f
        }

        // Highlight the current milestone with a bright border so the eye lands on it.
        if (isCurrent) {
            holder.card.strokeWidth = 2
            holder.card.strokeColor = ContextCompat.getColor(ctx, R.color.accent_primary)
        } else {
            holder.card.strokeWidth = 1
            holder.card.strokeColor = ContextCompat.getColor(ctx, R.color.card_border_dim)
        }

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

    fun currentMilestoneIndex(): Int {
        val firstUpcoming = milestones.indexOfFirst { !it.isAchieved }
        return if (firstUpcoming == -1) milestones.lastIndex else firstUpcoming
    }
}
