package com.example.myapplication

import android.graphics.Color
import android.graphics.PointF
import java.util.LinkedList
import kotlin.math.abs
import kotlin.math.atan2

// 1. 动作配置
data class ExerciseConfig(
    val downAngle: Double, // 复位/松弛 阈值
    val upAngle: Double,   // 发力/顶峰 阈值
    val leftIndices: IntArray,
    val rightIndices: IntArray,
    val isLegExercise: Boolean = false,
    val perfectAngle: Double = 90.0,        // 完美角度目标
    val isMinimizingGoal: Boolean = true,   // true=角度越小越好, false=越大越好
    val name: String = ""                   // 动作名称
)

// 2. 结果封装
data class AnalysisResult(
    val currentAngle: Double,      // 当前角度
    val count: Int,                // 累计次数
    val isRepCompleted: Boolean,   // 是否有效完成(用于播放音效)
    val score: Int,                // 分数
    val scoreColor: Int,           // 颜色
    val feedbackText: String       // 提示
)

class ExerciseCounter {
    var counter = 0
    var stage: String? = null

    private var leftStage: String? = null
    private var rightStage: String? = null

    private var lastCountTime = 0L
    private val minRepTimeMs = 500L

    private val smoothingWindow = 5
    private val angleHistory = LinkedList<Double>()

    // 评分状态变量
    private var currentRepExtremum: Double = 0.0 // 记录极值
    private var lastScore = 0
    private var lastColor = Color.GRAY
    private var lastFeedback = "准备开始"

    /**
     * 动作配置表 (使用你提供的最新标准)
     */
    private val configs = mapOf(
        // 深蹲
        "Squat" to ExerciseConfig(110.0, 160.0, intArrayOf(23, 25, 27), intArrayOf(24, 26, 28),
            perfectAngle = 70.0, isMinimizingGoal = true, name = "深蹲"),
        "深蹲" to ExerciseConfig(110.0, 160.0, intArrayOf(23, 25, 27), intArrayOf(24, 26, 28),
            perfectAngle = 70.0, isMinimizingGoal = true, name = "深蹲"),

        // 俯卧撑
        "Push Up" to ExerciseConfig(110.0, 160.0, intArrayOf(11, 13, 15), intArrayOf(12, 14, 16),
            perfectAngle = 70.0, isMinimizingGoal = true, name = "俯卧撑"),
        "俯卧撑" to ExerciseConfig(110.0, 160.0, intArrayOf(11, 13, 15), intArrayOf(12, 14, 16),
            perfectAngle = 70.0, isMinimizingGoal = true, name = "俯卧撑"),

        // 仰卧起坐 (Down=145躺下, Up=80坐起)
        "Sit Up" to ExerciseConfig(140.0, 80.0, intArrayOf(11, 23, 25), intArrayOf(12, 24, 26),
            perfectAngle = 50.0, isMinimizingGoal = true, name = "仰卧起坐"),
        "仰卧起坐" to ExerciseConfig(140.0, 80.0, intArrayOf(11, 23, 25), intArrayOf(12, 24, 26),
            perfectAngle = 50.0, isMinimizingGoal = true, name = "仰卧起坐"),

        // 二头弯举
        "Bicep Curl" to ExerciseConfig(160.0, 60.0, intArrayOf(11, 13, 15), intArrayOf(12, 14, 16),
            perfectAngle = 40.0, isMinimizingGoal = true, name = "二头弯举"),
        "二头弯举" to ExerciseConfig(160.0, 60.0, intArrayOf(11, 13, 15), intArrayOf(12, 14, 16),
            perfectAngle = 40.0, isMinimizingGoal = true, name = "二头弯举"),

        // 侧平举
        "Lateral Raise" to ExerciseConfig(30.0, 80.0, intArrayOf(23, 11, 13), intArrayOf(24, 12, 14),
            perfectAngle = 90.0, isMinimizingGoal = false, name = "侧平举"),
        "侧平举" to ExerciseConfig(30.0, 80.0, intArrayOf(23, 11, 13), intArrayOf(24, 12, 14),
            perfectAngle = 90.0, isMinimizingGoal = false, name = "侧平举"),

        // 推举
        "Overhead Press" to ExerciseConfig(90.0, 150.0, intArrayOf(11, 13, 15), intArrayOf(12, 14, 16),
            perfectAngle = 170.0, isMinimizingGoal = false, name = "站姿推举"),

        "站姿推举" to ExerciseConfig(90.0, 150.0, intArrayOf(11, 13, 15), intArrayOf(12, 14, 16),
            perfectAngle = 170.0, isMinimizingGoal = false, name = "站姿推举"),

        // 腿部动作
        "Leg Raise" to ExerciseConfig(160.0, 110.0, intArrayOf(11, 23, 25), intArrayOf(12, 24, 26), true,
            perfectAngle = 90.0, isMinimizingGoal = true, name = "抬腿"),
        "抬腿" to ExerciseConfig(160.0, 110.0, intArrayOf(11, 23, 25), intArrayOf(12, 24, 26), true,
            perfectAngle = 90.0, isMinimizingGoal = true, name = "抬腿"),

        "Knee Raise" to ExerciseConfig(160.0, 90.0, intArrayOf(11, 23, 25), intArrayOf(12, 24, 26), true,
            perfectAngle = 60.0, isMinimizingGoal = true, name = "提膝"),
        "提膝" to ExerciseConfig(160.0, 90.0, intArrayOf(11, 23, 25), intArrayOf(12, 24, 26), true,
            perfectAngle = 60.0, isMinimizingGoal = true, name = "提膝"),

        "Knee Press" to ExerciseConfig(100.0, 160.0, intArrayOf(23, 25, 27), intArrayOf(24, 26, 28), true,
            perfectAngle = 70.0, isMinimizingGoal = true, name = "腿举"),
        "腿举" to ExerciseConfig(100.0, 160.0, intArrayOf(23, 25, 27), intArrayOf(24, 26, 28), true,
            perfectAngle = 70.0, isMinimizingGoal = true, name = "腿举")
    )

    private fun calculateAngle(a: PointF, b: PointF, c: PointF): Double {
        val abX = a.x - b.x
        val abY = a.y - b.y
        val cbX = c.x - b.x
        val cbY = c.y - b.y

        val dot = (abX * cbX + abY * cbY)
        val cross = (abX * cbY - abY * cbX)
        val alpha = atan2(cross.toDouble(), dot.toDouble())
        var angle = Math.toDegrees(abs(alpha))
        if (angle > 180.0) angle = 360.0 - angle
        return angle
    }

    private fun smoothAngle(angle: Double): Double {
        angleHistory.add(angle)
        if (angleHistory.size > smoothingWindow) angleHistory.removeFirst()
        return angleHistory.average()
    }

    fun process(landmarks: List<PointF>, type: String): AnalysisResult? {
        val config = configs[type] ?: configs[type.replace(" ", "")] ?: return null
        if (landmarks.size <= 28) return null

        val leftAngleRaw = calculateAngle(
            landmarks[config.leftIndices[0]],
            landmarks[config.leftIndices[1]],
            landmarks[config.leftIndices[2]]
        )
        val rightAngleRaw = calculateAngle(
            landmarks[config.rightIndices[0]],
            landmarks[config.rightIndices[1]],
            landmarks[config.rightIndices[2]]
        )

        return if (config.isLegExercise) {
            processLegExercise(leftAngleRaw, rightAngleRaw, config)
        } else {
            processStandardExercise(leftAngleRaw, rightAngleRaw, config)
        }
    }

    private fun processStandardExercise(left: Double, right: Double, config: ExerciseConfig): AnalysisResult? {
        val avgAngle = (left + right) / 2.0
        val smoothedAngle = smoothAngle(avgAngle)
        val currentTime = System.currentTimeMillis()
        var isRepCompleted = false

        // 1. 追踪极值
        trackExtremum(smoothedAngle, config)

        val isExtension = config.upAngle > config.downAngle

        if (isExtension) {
            // 深蹲/俯卧撑等
            if (smoothedAngle > config.upAngle) {
                stage = "up"
            } else if (smoothedAngle < config.downAngle && stage == "up") {
                if (checkTiming(currentTime)) {
                    stage = "down"

                    // 【关键逻辑】先评分
                    evaluateScore(config)

                    // 只有分数颜色不是红色，才计数
                    if (lastColor != Color.RED) {
                        counter++
                        isRepCompleted = true
                    } else {
                        isRepCompleted = false // 红色不播放音效，不计数
                    }

                    lastCountTime = currentTime
                    resetExtremum(config)
                }
            }
        } else {
            // 仰卧起坐/弯举等
            if (smoothedAngle < config.upAngle) {
                stage = "up"
            } else if (smoothedAngle > config.downAngle && stage == "up") {
                if (checkTiming(currentTime)) {
                    stage = "down"

                    // 【关键逻辑】先评分
                    evaluateScore(config)

                    if (lastColor != Color.RED) {
                        counter++
                        isRepCompleted = true
                    } else {
                        isRepCompleted = false
                    }

                    lastCountTime = currentTime
                    resetExtremum(config)
                }
            }
        }

        return AnalysisResult(smoothedAngle, counter, isRepCompleted, lastScore, lastColor, lastFeedback)
    }

    private fun processLegExercise(left: Double, right: Double, config: ExerciseConfig): AnalysisResult? {
        val currentTime = System.currentTimeMillis()
        var counted = false
        val isExtension = config.upAngle > config.downAngle

        val activeLegAngle = if (abs(left - config.perfectAngle) < abs(right - config.perfectAngle)) left else right
        trackExtremum(activeLegAngle, config)

        // 腿部检测逻辑
        if (isExtension) {
            if (left > config.upAngle) leftStage = "up"
            else if (left < config.downAngle && leftStage == "up") {
                if (checkTiming(currentTime)) { leftStage = "down"; counted = true; lastCountTime = currentTime }
            }
        } else {
            if (left < config.upAngle) leftStage = "up"
            else if (left > config.downAngle && leftStage == "up") {
                if (checkTiming(currentTime)) { leftStage = "down"; counted = true; lastCountTime = currentTime }
            }
        }

        if (!counted) {
            if (isExtension) {
                if (right > config.upAngle) rightStage = "up"
                else if (right < config.downAngle && rightStage == "up") {
                    if (checkTiming(currentTime)) { rightStage = "down"; counted = true; lastCountTime = currentTime }
                }
            } else {
                if (right < config.upAngle) rightStage = "up"
                else if (right > config.downAngle && rightStage == "up") {
                    if (checkTiming(currentTime)) { rightStage = "down"; counted = true; lastCountTime = currentTime }
                }
            }
        }

        var actualCountIncremented = false
        if (counted) {
            // 【关键逻辑】先评分
            evaluateScore(config)

            if (lastColor != Color.RED) {
                counter++
                actualCountIncremented = true
            }

            resetExtremum(config)
        }

        stage = if (leftStage == "up" || rightStage == "up") "up" else "down"
        return AnalysisResult((left + right) / 2, counter, actualCountIncremented, lastScore, lastColor, lastFeedback)
    }

    private fun trackExtremum(angle: Double, config: ExerciseConfig) {
        if (config.isMinimizingGoal) {
            if (currentRepExtremum == 0.0 || currentRepExtremum == -1.0) currentRepExtremum = angle
            if (angle < currentRepExtremum) currentRepExtremum = angle
        } else {
            if (angle > currentRepExtremum) currentRepExtremum = angle
        }
    }

    private fun resetExtremum(config: ExerciseConfig) {
        currentRepExtremum = if (config.isMinimizingGoal) 360.0 else 0.0
    }

    private fun evaluateScore(config: ExerciseConfig) {
        val achieved = currentRepExtremum
        val target = config.perfectAngle
        val diff = abs(achieved - target)

        // 评分算法
        var rawScore = 100
        if (diff > 10) {
            rawScore = (100 - (diff - 10) * 2).toInt()
        }
        if (rawScore < 0) rawScore = 0
        lastScore = rawScore

        if (rawScore >= 85) {
            lastColor = Color.GREEN
            lastFeedback = "完美！保持节奏"
        } else if (rawScore >= 60) {
            lastColor = Color.YELLOW
            lastFeedback = generateCorrectionHint(config, achieved, target)
        } else {
            lastColor = Color.RED
            lastFeedback = generateCorrectionHint(config, achieved, target)
        }
    }

    // 使用你提供的详细提示标准
    private fun generateCorrectionHint(config: ExerciseConfig, achieved: Double, target: Double): String {
        val diff = achieved - target

        return when {
            config.name.contains("深蹲") -> {
                if (diff > 15) "再蹲低一点！目标大腿平行地面"
                else if (diff > 5) "可以蹲得更低一些"
                else "保持背部挺直，膝盖不要内扣"
            }
            config.name.contains("俯卧撑") -> {
                if (diff > 15) "胸部再贴近地面一点"
                else if (diff > 5) "身体可以再低一些"
                else "保持身体成直线，不要塌腰"
            }
            config.name.contains("仰卧起坐") -> {
                if (diff > 20) "再坐起来一点，感受腹部发力"
                else if (diff > 10) "可以坐得更高一些"
                else "注意用腹部发力，不要用手拉脖子"
            }
            config.name.contains("弯举") -> {
                if (diff > 20) "手臂再弯曲一点，顶峰收缩"
                else if (diff > 10) "可以弯举更高"
                else "保持肘部固定，不要前后晃动"
            }
            config.name.contains("侧平举") -> {
                if (diff < -20) "再举高一点，与肩平行"
                else if (diff < -10) "手臂可以再抬高"
                else "保持肘部微屈，不要耸肩"
            }
            config.name.contains("推举") -> {
                if (diff < -30) "手臂没有完全伸直"
                else if (diff < -15) "向上推得更直一些"
                else "保持核心收紧，不要过度后仰"
            }
            config.name.contains("抬腿") || config.name.contains("提膝") -> {
                if (diff > 20) "腿可以抬得更高"
                else if (diff > 10) "尝试抬得更高一些"
                else "保持身体稳定，不要晃动"
            }
            config.name.contains("腿举") -> {
                if (diff > 25) "可以蹲得更深"
                else if (diff > 10) "幅度可以更大"
                else "保持膝盖与脚尖方向一致"
            }
            else -> {
                if (config.isMinimizingGoal) {
                    if (diff > 10) "动作幅度不够，再深入一些"
                    else "注意动作规范和质量"
                } else {
                    if (diff < -10) "动作幅度不够，再伸展一些"
                    else "注意动作规范和质量"
                }
            }
        }
    }

    private fun checkTiming(currentTime: Long): Boolean {
        return (currentTime - lastCountTime) > minRepTimeMs
    }

    fun reset() {
        counter = 0
        stage = null
        leftStage = null
        rightStage = null
        angleHistory.clear()

        // 重置所有评分相关数据
        lastScore = 0
        lastFeedback = "准备开始"
        lastColor = Color.GRAY
        currentRepExtremum = 0.0
    }
}