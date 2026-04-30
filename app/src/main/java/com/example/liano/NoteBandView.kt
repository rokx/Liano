package com.example.liano

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.max

class NoteBandView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class SongNote(
        val name: String,
        val startBeat: Float,
        val lengthBeats: Float,
        val lane: Int
    )

    private val songNotes = listOf(
        SongNote("C4", 0f, 1f, 0),
        SongNote("D4", 1.2f, 1f, 1),
        SongNote("E4", 2.4f, 1f, 2),
        SongNote("C4", 3.6f, 1.2f, 0),
        SongNote("E4", 5.1f, 1f, 2),
        SongNote("F4", 6.3f, 1f, 3),
        SongNote("G4", 7.5f, 1.6f, 4),
        SongNote("G4", 9.5f, 1f, 4),
        SongNote("F4", 10.7f, 1f, 3),
        SongNote("E4", 11.9f, 1f, 2),
        SongNote("D4", 13.1f, 1f, 1),
        SongNote("C4", 14.3f, 1.6f, 0)
    )

    private val lanes = listOf("C4", "D4", "E4", "F4", "G4")
    private val lanePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(238, 242, 247)
        style = Paint.Style.FILL
    }
    private val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(88, 166, 255)
        style = Paint.Style.FILL
    }
    private val matchedNotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(55, 190, 120)
        style = Paint.Style.FILL
    }
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 193, 7)
        strokeWidth = 6f
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(34, 45, 60)
        textAlign = Paint.Align.CENTER
        textSize = 36f
        isFakeBoldText = true
    }
    private val smallTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(87, 96, 111)
        textAlign = Paint.Align.CENTER
        textSize = 24f
    }

    private var scrollBeat = 0f
    private var currentNoteName: String? = null
    private val animator = ValueAnimator.ofFloat(0f, 16f).apply {
        duration = 24000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            scrollBeat = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    fun setDetectedNote(noteName: String) {
        currentNoteName = noteName
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val contentWidth = width - paddingLeft - paddingRight
        val contentHeight = height - paddingTop - paddingBottom
        if (contentWidth <= 0 || contentHeight <= 0) return

        val laneHeight = contentHeight / lanes.size.toFloat()
        val beatWidth = max(90f, contentWidth / 4f)
        val targetX = paddingLeft + contentWidth * 0.25f

        lanes.forEachIndexed { index, laneName ->
            val top = paddingTop + index * laneHeight
            val bottom = top + laneHeight - 8f
            canvas.drawRoundRect(
                RectF(paddingLeft.toFloat(), top, (width - paddingRight).toFloat(), bottom),
                18f,
                18f,
                lanePaint
            )
            canvas.drawText(laneName, paddingLeft + 36f, top + laneHeight * 0.62f, smallTextPaint)
        }

        canvas.drawLine(targetX, paddingTop.toFloat(), targetX, (height - paddingBottom).toFloat(), targetPaint)

        songNotes.forEach { note ->
            val left = targetX + (note.startBeat - scrollBeat) * beatWidth
            val right = left + note.lengthBeats * beatWidth
            if (right < paddingLeft || left > width - paddingRight) return@forEach

            val top = paddingTop + note.lane * laneHeight + 10f
            val bottom = top + laneHeight - 28f
            val isInTarget = targetX in left..right
            val isMatched = isInTarget && currentNoteName == note.name
            val paint = if (isMatched) matchedNotePaint else notePaint

            canvas.drawRoundRect(RectF(left, top, right, bottom), 24f, 24f, paint)
            canvas.drawText(note.name, (left + right) / 2f, top + (bottom - top) * 0.62f, textPaint)
        }
    }
}
