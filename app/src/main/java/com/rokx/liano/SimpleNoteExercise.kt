package com.rokx.liano

class SimpleNoteExercise private constructor(
    private val stepsToPlay: List<Set<String>>,
    @Suppress("UNUSED_PARAMETER") unused: Unit
) {
    constructor(
        notesToPlay: List<String> = listOf("C4", "D4", "C4", "D4", "C4", "D4")
    ) : this(notesToPlay.map(::setOf), Unit)

    private var currentIndex = 0
    private var acceptedNotesForStep = emptySet<String>()
    private var currentStepStartedAt = 0L
    private var lastAcceptedTime = 0L
    private val debounceTimeMs = 700L
    private val chordWindowMs = 1200L

    fun currentIndex(): Int = currentIndex

    fun currentNote(): String? = stepsToPlay.getOrNull(currentIndex)?.singleOrNull()

    fun isFinished(): Boolean = currentIndex >= stepsToPlay.size

    fun onNoteDetected(noteName: String, nowMs: Long): Boolean {
        val advanced = advanceCurrentStep(noteName, nowMs)
        return advanced && isFinished()
    }

    fun matchesCurrentNote(noteName: String, nowMs: Long): Boolean = advanceCurrentStep(noteName, nowMs)

    private fun advanceCurrentStep(noteName: String, nowMs: Long): Boolean {
        if (currentIndex >= stepsToPlay.size) return false

        val currentStep = stepsToPlay[currentIndex]
        if (noteName !in currentStep) return false

        if (currentStep.size == 1 && nowMs - lastAcceptedTime < debounceTimeMs) {
            return false
        }

        if (acceptedNotesForStep.isEmpty() || nowMs - currentStepStartedAt > chordWindowMs) {
            acceptedNotesForStep = emptySet()
            currentStepStartedAt = nowMs
        }

        acceptedNotesForStep += noteName
        if (acceptedNotesForStep.containsAll(currentStep)) {
            currentIndex++
            acceptedNotesForStep = emptySet()
            currentStepStartedAt = 0L
            lastAcceptedTime = nowMs
            return true
        }

        return false
    }

    companion object {
        fun fromSteps(stepsToPlay: List<Set<String>>): SimpleNoteExercise =
            SimpleNoteExercise(stepsToPlay, Unit)
    }
}
