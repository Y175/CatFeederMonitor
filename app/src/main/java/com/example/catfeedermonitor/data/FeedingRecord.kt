package com.example.catfeedermonitor.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feeding_records")
data class FeedingRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val catName: String,
    val timestamp: Long,
    val imagePath: String,
    val duration: Long = 0L // NEW: 进食时长，单位毫秒
)