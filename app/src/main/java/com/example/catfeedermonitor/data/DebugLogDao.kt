package com.example.catfeedermonitor.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DebugLogDao {
    @Insert
    suspend fun insert(log: DebugLog)

    @Query("SELECT * FROM debug_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<DebugLog>>

    @Query("DELETE FROM debug_logs")
    suspend fun clearAll()
}
