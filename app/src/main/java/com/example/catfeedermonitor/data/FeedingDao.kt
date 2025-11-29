package com.example.catfeedermonitor.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedingDao {
    @Insert
    suspend fun insert(record: FeedingRecord)

    @Query("SELECT * FROM feeding_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<FeedingRecord>>

    @Query("SELECT COUNT(*) FROM feeding_records WHERE catName = :catName AND timestamp BETWEEN :startTime AND :endTime")
    fun getCountByDate(catName: String, startTime: Long, endTime: Long): Flow<Int>
    
    @Query("SELECT * FROM feeding_records WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getRecordsByDate(startTime: Long, endTime: Long): Flow<List<FeedingRecord>>

    @androidx.room.Delete
    suspend fun delete(record: FeedingRecord)
}
