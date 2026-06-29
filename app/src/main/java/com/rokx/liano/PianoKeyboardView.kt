package com.rokx.liano

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
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
    private val whiteSuggestedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 216, 97)
        style = Paint.Style.FILL
    }
    private val blackSuggestedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 174, 57)
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
    private val suggestedNotes = mutableSetOf<Int>()
    private val pointerNotes = mutableMapOf<Int, Int>()
    private var firstNote = DEFAULT_FIRST_NOTE
    private var lastNote = DEFAULT_LAST_NOTE

    var onNotePressed: ((Int) -> Unit)? = null
    var onNoteReleased: ((Int) -> Unit)? = null

    fun setNoteRange(firstNote: Int, lastNote: Int) {
        require(firstNote in 0..127 && lastNote in firstNote..127)
        this.firstNote = firstNote
        this.lastNote = lastNote
        invalidate()
    }

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

    fun setSuggestedNotes(notes: Set<Int>) {
        suggestedNotes.clear()
        suggestedNotes += notes
        invalidate()
    }

    fun clearSuggestedNotes() {
        suggestedNotes.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val whiteNotes = (firstNote..lastNote).filterNot(::isBlackKey)
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
            val paint = when (noteNumber) {
                in pressedNotes -> whitePressedPaint
                in suggestedNotes -> whiteSuggestedPaint
                else -> whitePaint
            }
            canvas.drawRect(rect, paint)
            canvas.drawRect(rect, strokePaint)

            if (noteNumber % NOTES_PER_OCTAVE == 0) {
                canvas.drawText(MidiNoteExtractor.noteName(noteNumber), rect.centerX(), bottom - 18f, labelPaint)
            }
        }

        val blackKeyWidth = max(18f, whiteKeyWidth * 0.58f)
        val blackKeyHeight = whiteKeyHeight * 0.62f
        for (noteNumber in firstNote..lastNote) {
            if (!isBlackKey(noteNumber)) continue
            val previousWhite = (noteNumber - 1 downTo firstNote).firstOrNull { !isBlackKey(it) } ?: continue
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
                when (noteNumber) {
                    in pressedNotes -> blackPressedPaint
                    in suggestedNotes -> blackSuggestedPaint
                    else -> blackPaint
                }
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (onNotePressed == null && onNoteReleased == null) return super.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                updatePointerNote(event.getPointerId(index), noteAt(event.getX(index), event.getY(index)))
            }
            MotionEvent.ACTION_MOVE -> {
                repeat(event.pointerCount) { index ->
                    updatePointerNote(event.getPointerId(index), noteAt(event.getX(index), event.getY(index)))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                releasePointer(event.getPointerId(event.actionIndex))
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
            }
            MotionEvent.ACTION_CANCEL -> pointerNotes.keys.toList().forEach(::releasePointer)
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updatePointerNote(pointerId: Int, noteNumber: Int?) {
        val previous = pointerNotes[pointerId]
        if (previous == noteNumber) return
        if (previous != null) onNoteReleased?.invoke(previous)
        if (noteNumber == null) {
            pointerNotes.remove(pointerId)
        } else {
            pointerNotes[pointerId] = noteNumber
            onNotePressed?.invoke(noteNumber)
        }
    }

    private fun releasePointer(pointerId: Int) {
        pointerNotes.remove(pointerId)?.let { onNoteReleased?.invoke(it) }
    }

    private fun noteAt(x: Float, y: Float): Int? {
        val whiteNotes = (firstNote..lastNote).filterNot(::isBlackKey)
        val contentWidth = width - paddingLeft - paddingRight
        val contentHeight = height - paddingTop - paddingBottom
        if (contentWidth <= 0 || contentHeight <= 0 || x !in paddingLeft.toFloat()..(width - paddingRight).toFloat()) return null

        val keyWidth = contentWidth / whiteNotes.size.toFloat()
        val localX = x - paddingLeft
        if (y <= paddingTop + contentHeight * 0.62f) {
            for (note in firstNote..lastNote) {
                if (!isBlackKey(note)) continue
                val precedingWhiteCount = (firstNote until note).count { !isBlackKey(it) }
                val center = precedingWhiteCount * keyWidth
                if (localX in (center - keyWidth * 0.29f)..(center + keyWidth * 0.29f)) return note
            }
        }
        return whiteNotes.getOrNull((localX / keyWidth).toInt().coerceAtMost(whiteNotes.lastIndex))
    }

    private fun isBlackKey(noteNumber: Int): Boolean =
        noteNumber % NOTES_PER_OCTAVE in BLACK_KEY_OFFSETS

    private companion object {
        private const val DEFAULT_FIRST_NOTE = 48
        private const val DEFAULT_LAST_NOTE = 83
        private const val NOTES_PER_OCTAVE = 12
        private val BLACK_KEY_OFFSETS = setOf(1, 3, 6, 8, 10)
    }
}
