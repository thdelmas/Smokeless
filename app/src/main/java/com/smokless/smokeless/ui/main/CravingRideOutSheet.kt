package com.smokless.smokeless.ui.main

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.smokless.smokeless.R
import com.smokless.smokeless.util.CravingTactic
import com.smokless.smokeless.util.CravingTactics
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Just-in-time intervention surface for an active craving. Closes the loop
 * between the timeline's pedagogy (the milestone "actions" field) and actual
 * behavior in the craving moment — runs a 5-minute ride-it-out timer, offers
 * a rotating evidence-based tactic, and gives the user a clean exit either
 * way (made it through, or smoked anyway).
 */
class CravingRideOutSheet(
    private val context: Context,
    private val onMadeIt: () -> Unit,
    private val onSmokedAnyway: () -> Unit,
) {
    companion object {
        private const val TOTAL_MS = 5L * 60 * 1000
        private const val TICK_MS = 250L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var dialog: BottomSheetDialog? = null
    private var currentTactic: CravingTactic = CravingTactics.random()
    private var startMs: Long = 0L
    private var finishedNaturally = false

    private val tickRunnable = object : Runnable {
        override fun run() {
            updateTimerViews()
            val elapsed = System.currentTimeMillis() - startMs
            if (elapsed < TOTAL_MS) {
                handler.postDelayed(this, TICK_MS)
            } else {
                onTimerComplete()
            }
        }
    }

    fun show() {
        val view = android.view.LayoutInflater.from(context)
            .inflate(R.layout.sheet_craving_ride_out, null)
        val dlg = BottomSheetDialog(context).apply { setContentView(view) }
        dialog = dlg

        bindTactic(view)
        view.findViewById<MaterialButton>(R.id.btnAnotherTactic).setOnClickListener {
            currentTactic = CravingTactics.nextDistinctFrom(currentTactic)
            bindTactic(view)
        }
        view.findViewById<MaterialButton>(R.id.btnMadeIt).setOnClickListener {
            dlg.dismiss()
            onMadeIt()
        }
        view.findViewById<MaterialButton>(R.id.btnSmokedAnyway).setOnClickListener {
            dlg.dismiss()
            onSmokedAnyway()
        }

        dlg.setOnDismissListener {
            handler.removeCallbacks(tickRunnable)
        }

        startMs = System.currentTimeMillis()
        updateTimerViews()
        handler.postDelayed(tickRunnable, TICK_MS)

        dlg.show()
    }

    private fun bindTactic(view: android.view.View) {
        view.findViewById<android.widget.TextView>(R.id.textTacticIcon).text = currentTactic.icon
        view.findViewById<android.widget.TextView>(R.id.textTacticTitle).text = currentTactic.title
        view.findViewById<android.widget.TextView>(R.id.textTacticBody).text = currentTactic.body
    }

    private fun updateTimerViews() {
        val dlg = dialog ?: return
        val timerView = dlg.findViewById<android.widget.TextView>(R.id.textRideOutTimer) ?: return
        val progress = dlg.findViewById<LinearProgressIndicator>(R.id.progressRideOut) ?: return
        val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(0L)
        val remaining = (TOTAL_MS - elapsed).coerceAtLeast(0L)
        val mins = TimeUnit.MILLISECONDS.toMinutes(remaining)
        val secs = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60
        timerView.text = String.format("%d:%02d", mins, secs)
        val pct = ((elapsed.toDouble() / TOTAL_MS) * 100.0).roundToInt().coerceIn(0, 100)
        progress.progress = pct
    }

    private fun onTimerComplete() {
        if (finishedNaturally) return
        finishedNaturally = true
        val dlg = dialog ?: return
        val timerView = dlg.findViewById<android.widget.TextView>(R.id.textRideOutTimer) ?: return
        val madeItBtn = dlg.findViewById<MaterialButton>(R.id.btnMadeIt) ?: return
        timerView.text = "✓"
        timerView.setTextColor(ContextCompat.getColor(context, R.color.status_champion))
        madeItBtn.text = "You made it"
        // Auto-dismiss after a short celebration delay; user can also tap.
        handler.postDelayed({
            if (dlg.isShowing) {
                dlg.dismiss()
                onMadeIt()
            }
        }, 2500L)
    }
}
