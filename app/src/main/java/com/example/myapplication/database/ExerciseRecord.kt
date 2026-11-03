package com.example.myapplication.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercise_records")
data class ExerciseRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,        // 动作类型 (如 "深蹲")
    val count: Int,          // 个数
    val score: Int,          // 评分 (新增)
    val timestamp: Long      // 时间戳
)