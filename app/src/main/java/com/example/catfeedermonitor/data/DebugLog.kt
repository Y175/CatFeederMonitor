package com.example.catfeedermonitor.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "debug_logs")
data class DebugLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val level: String, // INFO, WARN, ERROR
    val tag: String,
    val message: String
)
