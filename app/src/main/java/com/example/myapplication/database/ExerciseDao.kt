package com.example.myapplication.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    // 插入一条记录
    @Insert
    suspend fun insert(record: ExerciseRecord)

    // 查询所有记录，按时间倒序排列 (最新的在前面)
    @Query("SELECT * FROM exercise_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<ExerciseRecord>>

    // 删除某条记录
    @Delete
    suspend fun delete(record: ExerciseRecord)

    // 清空所有
    @Query("DELETE FROM exercise_records")
    suspend fun clearAll()
}