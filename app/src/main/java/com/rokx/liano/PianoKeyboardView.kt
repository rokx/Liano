package com.rokx.liano

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

class PianoKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(248, 248, 244)
        style = Paint.Style.FILL
    }
    private val whitePressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(117, 188, 255)
        style = Paint.Style.FILL
    }
    private val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(24, 24, 28)
        style = Paint.Style.FILL
    }
    private val blackPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(29, 119, 255)
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(70, 70, 76)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(40, 40, 46)
        textAlign = Paint.Align.CENTER
        textSize = 24f
    }

    private val pressedNotes = mutableSetOf<Int>()

    fun setPressedNotes(notes: Set<Int>) {
        pressedNotes.clear()
        pressedNotes += notes
        invalidate()
    }

    fun pressNote(noteNumber: Int) {
        pressedNotes += noteNumber
        invalidate()
    }

    fun releaseNote(noteNumber: Int) {
        pressedNotes -= noteNumber
        invalidate()
    }

    fun clearPressedNotes() {
        pressedNotes.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val whiteNotes = (FIRST_NOTE..LAST_NOTE).filterNot(::isBlackKey)
        val contentWidth = width - paddingLeft - paddingRight
        val contentHeight = height - paddingTop - paddingBottom
        if (contentWidth <= 0 || contentHeight <= 0) return

        val whiteKeyWidth = contentWidth / whiteNotes.size.toFloat()
        val whiteKeyHeight = contentHeight.toFloat()
        val top = paddingTop.toFloat()
        val bottom = top + whiteKeyHeight

        val whiteKeyRects = mutableMapOf<Int, RectF>()
        whiteNotes.forEachIndexed { index, noteNumber ->
            val left = paddingLeft + index * whiteKeyWidth
            val rect = RectF(left, top, left + whiteKeyWidth, bottom)
            whiteKeyRects[noteNumber] = rect
            canvas.drawRect(rect, if (noteNumber in pressedNotes) whitePressedPaint else whitePaint)
            canvas.drawRect(rect, strokePaint)

            if (noteNumber % NOTES_PER_OCTAVE == 0) {
                canvas.drawText(MidiNoteExtractor.noteName(noteNumber), rect.centerX(), bottom - 18f, labelPaint)
            }
        }

        val blackKeyWidth = max(18f, whiteKeyWidth * 0.58f)
        val blackKeyHeight = whiteKeyHeight * 0.62f
        for (noteNumber in FIRST_NOTE..LAST_NOTE) {
            if (!isBlackKey(noteNumber)) continue
            val previousWhite = (noteNumber - 1 downTo FIRST_NOTE).firstOrNull { !isBlackKey(it) } ?: continue
            val previousRect = whiteKeyRects[previousWhite] ?: continue
            val centerX = previousRect.right
            val rect = RectF(
                centerX - blackKeyWidth / 2f,
                top,
                centerX + blackKeyWidth / 2f,
                top + blackKeyHeight
            )
            canvas.drawRoundRect(
                rect,
                8f,
                8f,
                if (noteNumber in pressedNotes) blackPressedPaint else blackPaint
            )
        }
    }

    private fun isBlackKey(noteNumber: Int): Boolean =
        noteNumber % NOTES_PER_OCTAVE in BLACK_KEY_OFFSETS

    private companion object {
        private const val FIRST_NOTE = 48
        private const val LAST_NOTE = 83
        private const val NOTES_PER_OCTAVE = 12
        private val BLACK_KEY_OFFSETS = setOf(1, 3, 6, 8, 10)
    }
}
