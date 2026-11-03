package com.example.myapplication

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var landmarks: List<PointF> = emptyList()
    private var imageSourceWidth: Int = 0
    private var imageSourceHeight: Int = 0

    private val paintPoint = Paint().apply {
        color = Color.parseColor("#FF9800") // 橙色点
        style = Paint.Style.FILL
        strokeWidth = 15f
    }

    private val paintLine = Paint().apply {
        color = Color.WHITE // 白色线
        strokeWidth = 6f
    }

    // 骨架连线定义, 使用ML Kit索引
    private val connections = listOf(
        Pair(11, 12), // Shoulder
        Pair(11, 13), Pair(13, 15), // Left arm
        Pair(12, 14), Pair(14, 16), // Right arm
        Pair(11, 23), // Left body
        Pair(12, 24), // Right body
        Pair(23, 24), // Hip
        Pair(23, 25), Pair(25, 27), // Left leg
        Pair(24, 26), Pair(26, 28)  // Right leg
    )

    fun setLandmarks(landmarks: List<PointF>, imageOriginalHeight: Int, imageOriginalWidth: Int) {
        this.landmarks = landmarks
        // 因为摄像头传感器的旋转，ML Kit处理的图像是旋转过的。
        // ML Kit图像的宽度 = 原始摄像头图像的高度。
        this.imageSourceWidth = imageOriginalHeight
        this.imageSourceHeight = imageOriginalWidth
        postInvalidate() // 使用postInvalidate，因为这个方法可能从后台线程调用
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (landmarks.isEmpty() || imageSourceWidth == 0 || imageSourceHeight == 0) return

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        // 计算缩放比例，将图像源适配到视图中
        val scaleX = viewWidth / imageSourceWidth.toFloat()
        val scaleY = viewHeight / imageSourceHeight.toFloat()
        
        // 使用较小的缩放比例以保持纵横比（letterboxing）
        val scale = min(scaleX, scaleY)

        // 计算偏移量以使缩放后的图像居中
        val offsetX = (viewWidth - imageSourceWidth.toFloat() * scale) / 2
        val offsetY = (viewHeight - imageSourceHeight.toFloat() * scale) / 2

        // 转换并绘制点和线
        val transformedPoints = landmarks.map {
            PointF(it.x * scale + offsetX, it.y * scale + offsetY)
        }

        // 绘制连线
        for ((start, end) in connections) {
            if (start < transformedPoints.size && end < transformedPoints.size) {
                val p1 = transformedPoints[start]
                val p2 = transformedPoints[end]
                // 只在点有效时绘制（有时地标点未被检测到）
                if(p1.x > 0 && p1.y > 0 && p2.x > 0 && p2.y > 0) {
                     canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paintLine)
                }
            }
        }

        // 绘制关节点
        for (point in transformedPoints) {
            if(point.x > 0 && point.y > 0) {
                canvas.drawCircle(point.x, point.y, 10f, paintPoint)
            }
        }
    }
}