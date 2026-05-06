package com.rokx.liano.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.rokx.liano.R
import kotlin.math.max
import kotlin.math.min

class PitchLevelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFECECEC.toInt()
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFBBBBBB.toInt()
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.purple_500)
        style = Paint.Style.FILL
    }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66AAAAAA
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density
    }

    private var minPitchHz = 80f
    private var maxPitchHz = 300f
    private var level = 0f

    fun setRange(minHz: Float, maxHz: Float) {
        minPitchHz = minHz
        maxPitchHz = maxHz
        invalidate()
    }

    fun setPitch(pitchHz: Float) {
        val clamped = when {
            pitchHz <= 0f -> 0f
            pitchHz <= minPitchHz -> 0f
            pitchHz >= maxPitchHz -> 1f
            else -> (pitchHz - minPitchHz) / (maxPitchHz - minPitchHz)
        }
        val alpha = 0.2f
        level = alpha * clamped + (1 - alpha) * level
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        canvas.drawRect(0f, 0f, w, h, bgPaint)
        canvas.drawRect(0f, 0f, w, h, borderPaint)

        val filledHeight = h * max(0f, min(1f, level))
        canvas.drawRect(0f, h - filledHeight, w, h, fillPaint)

        val mid = 0.5f
        val yMid = h * (1f - mid)
        canvas.drawLine(0f, yMid, w, yMid, guidePaint)
    }
}
