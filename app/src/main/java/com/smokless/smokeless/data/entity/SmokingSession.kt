package com.smokless.smokeless.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "smoking_sessions")
data class SmokingSession(
    val timestamp: Long
) {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0
}







