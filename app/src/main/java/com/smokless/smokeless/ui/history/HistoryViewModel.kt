package com.smokless.smokeless.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.smokless.smokeless.data.entity.SmokingSession
import com.smokless.smokeless.data.entity.Substance
import com.smokless.smokeless.data.repository.SmokingRepository

/**
 * Backs the data-entries screen: a plain, editable list of every logged
 * [SmokingSession]. Kept deliberately separate from [com.smokless.smokeless.ui.main.MainViewModel]
 * (which does heavy per-second stat recomputation) — this surface just needs
 * CRUD over the raw rows.
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SmokingRepository(application)

    /** Newest entry first. */
    val sessions: LiveData<List<SmokingSession>> = repository.getAllSessionsDesc()

    fun add(timestamp: Long, substance: Substance, quantity: Double) {
        repository.addManualSession(timestamp, substance, quantity)
    }

    fun update(id: Long, timestamp: Long, substance: Substance, quantity: Double) {
        val session = SmokingSession(timestamp, substance, quantity).apply { this.id = id }
        repository.updateSession(session)
    }

    fun delete(id: Long) {
        repository.deleteSessionAsync(id)
    }
}
