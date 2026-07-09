package com.smokless.smokeless

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.smokless.smokeless.data.entity.SmokingSession
import com.smokless.smokeless.data.entity.Substance
import com.smokless.smokeless.databinding.ActivityHistoryBinding
import com.smokless.smokeless.ui.history.HistoryViewModel
import com.smokless.smokeless.ui.history.SessionAdapter
import com.smokless.smokeless.ui.history.SessionFormat
import java.util.Calendar

/**
 * Data-entries screen — the raw, editable log. Lists every [SmokingSession]
 * newest-first and lets the user add, edit, or remove any of them. The
 * home/stats surfaces stay read-only summaries; this is the one place the
 * underlying rows are directly editable.
 */
class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private val viewModel: HistoryViewModel by viewModels()
    private lateinit var adapter: SessionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = SessionAdapter { session -> showEditor(session) }
        binding.recyclerSessions.layoutManager = LinearLayoutManager(this)
        binding.recyclerSessions.adapter = adapter

        binding.fabAdd.setOnClickListener { showEditor(null) }

        viewModel.sessions.observe(this) { sessions ->
            adapter.submit(sessions)
            binding.textEmpty.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    /**
     * Add/edit dialog. [existing] null → add a new entry (defaults to now);
     * non-null → edit that row, with a Delete action.
     */
    private fun showEditor(existing: SmokingSession?) {
        val view = layoutInflater.inflate(R.layout.dialog_edit_session, null)

        val titleView = view.findViewById<android.widget.TextView>(R.id.textDialogTitle)
        val btnDate = view.findViewById<MaterialButton>(R.id.btnPickDate)
        val btnTime = view.findViewById<MaterialButton>(R.id.btnPickTime)
        val substanceChips = view.findViewById<ChipGroup>(R.id.chipGroupSubstance)
        val sizeChips = view.findViewById<ChipGroup>(R.id.chipGroupSize)

        titleView.text = if (existing == null) "Add entry" else "Edit entry"

        val cal = Calendar.getInstance().apply {
            timeInMillis = existing?.timestamp ?: System.currentTimeMillis()
        }

        fun refreshWhenLabels() {
            btnDate.text = SessionFormat.formatDate(cal.timeInMillis)
            btnTime.text = SessionFormat.formatTime(cal.timeInMillis)
        }
        refreshWhenLabels()

        btnDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    cal.set(Calendar.YEAR, year)
                    cal.set(Calendar.MONTH, month)
                    cal.set(Calendar.DAY_OF_MONTH, day)
                    refreshWhenLabels()
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH),
            ).apply {
                // A session can't end in the future.
                datePicker.maxDate = System.currentTimeMillis()
            }.show()
        }

        btnTime.setOnClickListener {
            TimePickerDialog(
                this,
                { _, hour, minute ->
                    cal.set(Calendar.HOUR_OF_DAY, hour)
                    cal.set(Calendar.MINUTE, minute)
                    cal.set(Calendar.SECOND, 0)
                    refreshWhenLabels()
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                true,
            ).show()
        }

        // Prefill substance + size from the existing row.
        existing?.let {
            when (it.substance) {
                Substance.CANNABIS -> substanceChips.check(R.id.chipSubstanceCannabis)
                Substance.TOBACCO -> substanceChips.check(R.id.chipSubstanceTobacco)
            }
            sizeChips.check(nearestSizeChipId(it.quantity))
        }

        val builder = MaterialAlertDialogBuilder(this)
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(if (existing == null) "Add" else "Save") { _, _ ->
                val substance = when (substanceChips.checkedChipId) {
                    R.id.chipSubstanceCannabis -> Substance.CANNABIS
                    else -> Substance.TOBACCO
                }
                val quantity = when (sizeChips.checkedChipId) {
                    R.id.chipSizeDrag -> 0.25
                    R.id.chipSizeHalf -> 0.5
                    R.id.chipSizeMore -> 1.5
                    else -> 1.0
                }
                // Guard against a future timestamp if the clock was edited.
                val timestamp = cal.timeInMillis.coerceAtMost(System.currentTimeMillis())
                if (existing == null) {
                    viewModel.add(timestamp, substance, quantity)
                } else {
                    viewModel.update(existing.id, timestamp, substance, quantity)
                }
            }

        if (existing != null) {
            builder.setNeutralButton("Delete") { _, _ -> confirmDelete(existing) }
        }

        builder.show()
    }

    private fun confirmDelete(session: SmokingSession) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete this entry?")
            .setMessage(
                "${SessionFormat.formatDate(session.timestamp)} · " +
                    "${SessionFormat.formatTime(session.timestamp)} — " +
                    SessionFormat.substanceLabel(session.substance)
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ -> viewModel.delete(session.id) }
            .show()
    }

    /** Closest bucket chip to an arbitrary stored quantity. */
    private fun nearestSizeChipId(quantity: Double): Int {
        val buckets = listOf(
            0.25 to R.id.chipSizeDrag,
            0.5 to R.id.chipSizeHalf,
            1.0 to R.id.chipSizeFull,
            1.5 to R.id.chipSizeMore,
        )
        return buckets.minByOrNull { kotlin.math.abs(it.first - quantity) }!!.second
    }
}
