package com.smokless.smokeless.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cravings")
data class Craving(
    val timestamp: Long
) {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0
}

