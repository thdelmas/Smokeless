package com.smokless.smokeless.ui.history

import com.smokless.smokeless.data.entity.Substance
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Formatting shared by the entries list and its add/edit dialog. */
object SessionFormat {

    private val dateFmt = SimpleDateFormat("EEE, MMM d yyyy", Locale.getDefault())
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    /** The four dose buckets the UI exposes, matching the sized-log dialog. */
    val QUANTITY_BUCKETS = listOf(0.25 to "Drag", 0.5 to "Half", 1.0 to "Full", 1.5 to "More")

    fun formatDate(timestamp: Long): String = dateFmt.format(Date(timestamp))

    fun formatTime(timestamp: Long): String = timeFmt.format(Date(timestamp))

    fun substanceLabel(substance: Substance): String = when (substance) {
        Substance.TOBACCO -> "🚬 Tobacco"
        Substance.CANNABIS -> "🌿 Cannabis"
    }

    fun substanceEmoji(substance: Substance): String = when (substance) {
        Substance.TOBACCO -> "🚬"
        Substance.CANNABIS -> "🌿"
    }

    /** Nearest named bucket, or a bare "0.75×" for off-bucket values. */
    fun quantityLabel(quantity: Double): String {
        val bucket = QUANTITY_BUCKETS.firstOrNull { kotlin.math.abs(it.first - quantity) < 0.01 }
        return bucket?.let { "${it.second} · ${formatDose(quantity)}×" }
            ?: "${formatDose(quantity)}×"
    }

    private fun formatDose(value: Double): String {
        val rounded = kotlin.math.round(value)
        return if (kotlin.math.abs(value - rounded) < 0.01) {
            rounded.toInt().toString()
        } else {
            String.format(Locale.getDefault(), "%.2f", value).trimEnd('0').trimEnd('.')
        }
    }
}
