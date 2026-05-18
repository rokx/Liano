package com.rokx.liano

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

class NoteBandView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class SongNote(
        val name: String,
        val startBeat: Float,
        val lengthBeats: Float,
        val lane: Int
    )

    private var songNotes = listOf(
        SongNote("C4", 0f, 1f, 0),
        SongNote("D4", 1.4f, 1f, 1)
    )

    private data class InputMark(
        val noteName: String,
        val beat: Float
    )

    private val staffPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(70, 70, 70)
        strokeWidth = 3f
    }

    private val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(30, 30, 30)
        style = Paint.Style.FILL
    }

    private val activeNotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(49, 120, 255)
        style = Paint.Style.FILL
    }

    private val inputPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(70, 180, 110)
        style = Paint.Style.FILL
        alpha = 180
    }

    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(66, 133, 244)
        strokeWidth = 5f
    }

    private val clefPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(45, 58, 80)
        textAlign = Paint.Align.LEFT
    }

    private var scrollBeat = 0f
    private var currentNoteName: String? = null
    private var inputMarks = emptyList<InputMark>()
    private var lastInputMarkBeat = Float.NEGATIVE_INFINITY
    private var songEndBeat = songNotes.maxOf { it.startBeat + it.lengthBeats }
    private var playbackWasCancelled = false
    private var pendingScrollFinished: (() -> Unit)? = null
    private var lastDragX = 0f
    private var hasDragged = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    var onPlaybackFinished: (() -> Unit)? = null

    private val animator = ValueAnimator().apply {
        repeatCount = 0
        interpolator = LinearInterpolator()
        addUpdateListener {
            scrollBeat = it.animatedValue as Float
            invalidate()
        }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                playbackWasCancelled = false
            }

            override fun onAnimationCancel(animation: Animator) {
                playbackWasCancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (!playbackWasCancelled && scrollBeat >= songEndBeat) {
                    scrollBeat = songEndBeat
                    invalidate()
                    onPlaybackFinished?.invoke()
                } else if (!playbackWasCancelled) {
                    pendingScrollFinished?.invoke()
                }
                pendingScrollFinished = null
            }
        })
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        configurePlaybackAnimator()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    fun setSongNotes(notes: List<SongNote>, autoplay: Boolean = true) {
        songNotes = notes
        inputMarks = emptyList()
        lastInputMarkBeat = Float.NEGATIVE_INFINITY
        currentNoteName = null
        songEndBeat = notes.maxOfOrNull { it.startBeat + it.lengthBeats } ?: 1f
        scrollBeat = 0f
        configurePlaybackAnimator()
        if (autoplay) {
            animator.start()
        }
        invalidate()
    }

    fun pausePlayback() {
        if (animator.isRunning && !animator.isPaused) {
            animator.pause()
        }
    }

    fun resumePlayback() {
        when {
            animator.isPaused -> animator.resume()
            !animator.isStarted && scrollBeat < songEndBeat -> animator.start()
        }
    }

    fun isPlaybackPaused(): Boolean = animator.isPaused

    fun hasPlaybackFinished(): Boolean = scrollBeat >= songEndBeat && !animator.isRunning

    fun scrollToNote(index: Int, onFinished: (() -> Unit)? = null) {
        val targetBeat = songNotes.getOrNull(index)?.startBeat ?: songEndBeat
        animateScrollToBeat(targetBeat.coerceIn(0f, songEndBeat), onFinished)
    }

    fun jumpToNote(index: Int) {
        val targetBeat = songNotes.getOrNull(index)?.startBeat ?: songEndBeat
        animator.cancel()
        scrollBeat = targetBeat.coerceIn(0f, songEndBeat)
        configurePlaybackAnimator()
        invalidate()
    }

    fun setDetectedNote(noteName: String) {
        currentNoteName = noteName
        addInputMark(noteName)
        invalidate()
    }

    fun clearDetectedNote() {
        currentNoteName = null
        invalidate()
    }

    private fun addInputMark(noteName: String) {
        if (scrollBeat - lastInputMarkBeat < MIN_INPUT_MARK_BEAT_SPACING) return

        inputMarks = (inputMarks + InputMark(noteName, scrollBeat))
            .takeLast(MAX_INPUT_MARKS)

        lastInputMarkBeat = scrollBeat
    }

    private fun isManualScrollEnabled(): Boolean = animator.isPaused && !hasPlaybackFinished()

    private fun scrollByPixels(deltaX: Float) {
        val beatWidth = currentBeatWidth()
        if (beatWidth <= 0f) return

        scrollBeat = (scrollBeat - deltaX / beatWidth).coerceIn(0f, songEndBeat)
        animator.currentPlayTime = (scrollBeat * MS_PER_BEAT).roundToLong()
        invalidate()
    }

    private fun animateScrollToBeat(targetBeat: Float, onFinished: (() -> Unit)? = null) {
        animator.cancel()
        pendingScrollFinished = onFinished

        val distance = abs(targetBeat - scrollBeat)
        if (distance < MIN_SCROLL_DISTANCE_BEATS) {
            scrollBeat = targetBeat
            invalidate()
            pendingScrollFinished?.invoke()
            pendingScrollFinished = null
            configurePlaybackAnimator()
            return
        }

        animator.setFloatValues(scrollBeat, targetBeat)
        animator.duration = max(MIN_SCROLL_ANIMATION_MS, (distance * MS_PER_BEAT).roundToLong())
        animator.repeatCount = 0
        animator.start()
    }

    private fun currentBeatWidth(): Float {
        val contentWidth = width - paddingLeft - paddingRight
        return if (contentWidth > 0) max(MIN_BEAT_WIDTH_PX, contentWidth / VISIBLE_BEATS) else 0f
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isManualScrollEnabled()) {
            return super.onTouchEvent(event)
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastDragX = event.x
                hasDragged = false
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.x - lastDragX
                if (abs(deltaX) >= touchSlop || hasDragged) {
                    hasDragged = true
                    scrollByPixels(deltaX)
                    lastDragX = event.x
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (!hasDragged) performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }

        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val contentWidth = width - paddingLeft - paddingRight
        val contentHeight = height - paddingTop - paddingBottom
        if (contentWidth <= 0 || contentHeight <= 0) return

        val targetX = paddingLeft + contentWidth * 0.42f
        val beatWidth = currentBeatWidth()

        val staffLeft = paddingLeft + 86f
        val staffRight = width - paddingRight - 20f
        val lineSpacing = max(15f, contentHeight / 15f)
        val trebleStaffTop = paddingTop + contentHeight * 0.08f
        val bassStaffTop = trebleStaffTop + lineSpacing * 7.1f

        drawStaff(canvas, staffLeft, staffRight.toFloat(), trebleStaffTop, lineSpacing)
        drawStaff(canvas, staffLeft, staffRight.toFloat(), bassStaffTop, lineSpacing)
        drawClefs(canvas, paddingLeft + 18f, trebleStaffTop, bassStaffTop, lineSpacing)

        canvas.drawLine(
            targetX,
            trebleStaffTop - lineSpacing * 1.5f,
            targetX,
            bassStaffTop + lineSpacing * 4.75f,
            cursorPaint
        )

        inputMarks.forEach { mark ->
            val x = targetX + (mark.beat - scrollBeat) * beatWidth
            if (x < paddingLeft || x > width - paddingRight) return@forEach

            val y = noteY(mark.noteName, trebleStaffTop, bassStaffTop, lineSpacing)
            canvas.drawCircle(x, y, 9f, inputPaint)
        }

        songNotes.forEach { note ->
            val x = targetX + (note.startBeat - scrollBeat) * beatWidth
            if (x < paddingLeft - 40 || x > width - paddingRight + 40) return@forEach

            val y = noteY(note.name, trebleStaffTop, bassStaffTop, lineSpacing)
            val isActive = currentNoteName == note.name && kotlin.math.abs(note.startBeat - scrollBeat) < 0.55f
            val paint = if (isActive) activeNotePaint else notePaint

            canvas.save()
            canvas.rotate(-18f, x, y)
            canvas.drawOval(x - 14f, y - 10f, x + 14f, y + 10f, paint)
            canvas.restore()

            canvas.drawLine(x + 12f, y, x + 12f, y - 68f, paint)

            if (note.name == "C4") {
                canvas.drawLine(x - 24f, y, x + 24f, y, staffPaint)
            }
        }
    }

    private fun drawStaff(canvas: Canvas, left: Float, right: Float, staffTop: Float, spacing: Float) {
        for (i in 0 until 5) {
            val y = staffTop + i * spacing
            canvas.drawLine(left, y, right, y, staffPaint)
        }
    }

    private fun drawClefs(canvas: Canvas, x: Float, trebleStaffTop: Float, bassStaffTop: Float, spacing: Float) {
        clefPaint.textSize = spacing * 3.9f
        canvas.drawText(TREBLE_CLEF, x, trebleStaffTop + spacing * 3.45f, clefPaint)

        clefPaint.textSize = spacing * 2.9f
        canvas.drawText(BASS_CLEF, x + spacing * 0.2f, bassStaffTop + spacing * 3.15f, clefPaint)
    }

    private fun noteY(noteName: String, trebleStaffTop: Float, bassStaffTop: Float, spacing: Float): Float {
        val parsedNote = NOTE_NAME_PATTERN.matchEntire(noteName) ?: return trebleStaffTop + spacing * 4f
        val letter = parsedNote.groupValues[1]
        val octave = parsedNote.groupValues[2].toIntOrNull() ?: return trebleStaffTop + spacing * 4f
        val scaleIndex = NOTE_SCALE_INDEX[letter] ?: return trebleStaffTop + spacing * 4f
        val diatonicStepsFromC4 = (octave - 4) * NATURAL_NOTES_PER_OCTAVE + scaleIndex
        val staffTop = if (octave < 4) bassStaffTop else trebleStaffTop
        val c4Position = if (octave < 4) BASS_C4_STAFF_POSITION else TREBLE_C4_STAFF_POSITION

        return staffTop + spacing * (c4Position - diatonicStepsFromC4 * STAFF_POSITION_PER_STEP)
    }

    companion object {
        private const val MAX_INPUT_MARKS = 260
        private const val MIN_INPUT_MARK_BEAT_SPACING = 0.06f
        private const val MIN_SCROLL_DISTANCE_BEATS = 0.01f
        private const val MIN_SCROLL_ANIMATION_MS = 220L
        private const val MS_PER_BEAT = 1250L
        private const val NATURAL_NOTES_PER_OCTAVE = 7
        private const val TREBLE_C4_STAFF_POSITION = 5f
        private const val BASS_C4_STAFF_POSITION = -1f
        private const val STAFF_POSITION_PER_STEP = 0.5f
        private const val MIN_BEAT_WIDTH_PX = 100f
        private const val VISIBLE_BEATS = 5f
        private const val TREBLE_CLEF = "\uD834\uDD1E"
        private const val BASS_CLEF = "\uD834\uDD22"
        private val NOTE_NAME_PATTERN = Regex("^([A-G])#?(-?\\d+)$")
        private val NOTE_SCALE_INDEX = mapOf(
            "C" to 0,
            "D" to 1,
            "E" to 2,
            "F" to 3,
            "G" to 4,
            "A" to 5,
            "B" to 6
        )
    }

    private fun configurePlaybackAnimator() {
        animator.cancel()
        pendingScrollFinished = null
        animator.setFloatValues(scrollBeat, songEndBeat)
        animator.duration = max(1L, ((songEndBeat - scrollBeat) * MS_PER_BEAT).roundToLong())
        animator.repeatCount = 0
    }
}
