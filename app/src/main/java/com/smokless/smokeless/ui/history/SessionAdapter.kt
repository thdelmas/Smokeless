package com.smokless.smokeless.ui.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.smokless.smokeless.R
import com.smokless.smokeless.data.entity.SmokingSession

/**
 * Flat, tappable list of logged sessions. Tapping a row opens the edit/delete
 * dialog; the host activity owns that flow via [onClick].
 */
class SessionAdapter(
    private val onClick: (SmokingSession) -> Unit,
) : RecyclerView.Adapter<SessionAdapter.SessionViewHolder>() {

    private var sessions: List<SmokingSession> = emptyList()

    fun submit(newSessions: List<SmokingSession>) {
        sessions = newSessions
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_session, parent, false)
        return SessionViewHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        holder.bind(sessions[position])
    }

    override fun getItemCount(): Int = sessions.size

    class SessionViewHolder(
        itemView: View,
        private val onClick: (SmokingSession) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {

        private val emoji: TextView = itemView.findViewById(R.id.textSessionEmoji)
        private val title: TextView = itemView.findViewById(R.id.textSessionTitle)
        private val subtitle: TextView = itemView.findViewById(R.id.textSessionSubtitle)
        private val dose: TextView = itemView.findViewById(R.id.textSessionDose)

        fun bind(session: SmokingSession) {
            emoji.text = SessionFormat.substanceEmoji(session.substance)
            title.text = "${SessionFormat.formatDate(session.timestamp)} · ${SessionFormat.formatTime(session.timestamp)}"
            subtitle.text = SessionFormat.substanceLabel(session.substance)
            dose.text = SessionFormat.quantityLabel(session.quantity)
            itemView.setOnClickListener { onClick(session) }
        }
    }
}
