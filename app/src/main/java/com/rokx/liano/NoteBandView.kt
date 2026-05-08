package com.rokx.liano

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
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

    private var scrollBeat = 0f
    private var currentNoteName: String? = null
    private var inputMarks = emptyList<InputMark>()
    private var lastInputMarkBeat = Float.NEGATIVE_INFINITY
    private var songEndBeat = songNotes.maxOf { it.startBeat + it.lengthBeats }
    private var playbackWasCancelled = false

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
                }
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

    fun setSongNotes(notes: List<SongNote>) {
        songNotes = notes
        inputMarks = emptyList()
        lastInputMarkBeat = Float.NEGATIVE_INFINITY
        currentNoteName = null
        songEndBeat = notes.maxOfOrNull { it.startBeat + it.lengthBeats } ?: 1f
        scrollBeat = 0f
        configurePlaybackAnimator()
        animator.start()
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val contentWidth = width - paddingLeft - paddingRight
        val contentHeight = height - paddingTop - paddingBottom
        if (contentWidth <= 0 || contentHeight <= 0) return

        val targetX = paddingLeft + contentWidth * 0.42f
        val beatWidth = max(100f, contentWidth / 5f)

        val staffLeft = paddingLeft + 40f
        val staffRight = width - paddingRight - 20f
        val lineSpacing = 28f
        val staffTop = paddingTop + 70f

        for (i in 0 until 5) {
            val y = staffTop + i * lineSpacing
            canvas.drawLine(staffLeft, y, staffRight.toFloat(), y, staffPaint)
        }

        canvas.drawLine(
            targetX,
            staffTop - 55f,
            targetX,
            staffTop + lineSpacing * 5 + 40f,
            cursorPaint
        )

        inputMarks.forEach { mark ->
            val x = targetX + (mark.beat - scrollBeat) * beatWidth
            if (x < paddingLeft || x > width - paddingRight) return@forEach

            val y = noteY(mark.noteName, staffTop, lineSpacing)
            canvas.drawCircle(x, y, 9f, inputPaint)
        }

        songNotes.forEach { note ->
            val x = targetX + (note.startBeat - scrollBeat) * beatWidth
            if (x < paddingLeft - 40 || x > width - paddingRight + 40) return@forEach

            val y = noteY(note.name, staffTop, lineSpacing)
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

    private fun noteY(noteName: String, staffTop: Float, spacing: Float): Float {
        return when (noteName) {
            "C4" -> staffTop + spacing * 5f
            "D4" -> staffTop + spacing * 4.5f
            "E4" -> staffTop + spacing * 4f
            "F4" -> staffTop + spacing * 3.5f
            "G4" -> staffTop + spacing * 3f
            "A4" -> staffTop + spacing * 2.5f
            "B4" -> staffTop + spacing * 2f
            "C5" -> staffTop + spacing * 1.5f
            else -> staffTop + spacing * 4f
        }
    }

    companion object {
        private const val MAX_INPUT_MARKS = 260
        private const val MIN_INPUT_MARK_BEAT_SPACING = 0.06f
        private const val MS_PER_BEAT = 1250L
    }

    private fun configurePlaybackAnimator() {
        animator.cancel()
        animator.setFloatValues(scrollBeat, songEndBeat)
        animator.duration = max(1L, ((songEndBeat - scrollBeat) * MS_PER_BEAT).roundToLong())
        animator.repeatCount = 0
    }
}
