package com.smokless.smokeless.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "smoking_sessions")
data class SmokingSession(
    val timestamp: Long,
    @ColumnInfo(defaultValue = "TOBACCO")
    val substance: Substance = Substance.DEFAULT,
    /**
     * Dose this session delivered, expressed as a fraction of a "full"
     * cigarette/session. Buckets the UI exposes:
     *   drag  = 0.25
     *   half  = 0.5
     *   full  = 1.0  (legacy default — every row predating quantity tracking
     *                 maps here, and short-tap logging stays single-tap by
     *                 defaulting to this value)
     *   more  = 1.5  (a long pull, a back-to-back, a fat joint)
     * Stats that sum exposure (reduction trend, money saved, half-life decay)
     * weight by this value; event-count stats (trigger windows) ignore it.
     */
    @ColumnInfo(defaultValue = "1.0")
    val quantity: Double = 1.0,
) {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0
}
