package com.rokx.liano

class SimpleNoteExercise(
    private val notesToPlay: List<String> = listOf("C4", "D4", "C4", "D4", "C4", "D4")
) {
    private var currentIndex = 0
    private var lastAcceptedTime = 0L
    private val debounceTimeMs = 700L

    fun currentIndex(): Int = currentIndex

    fun currentNote(): String? = notesToPlay.getOrNull(currentIndex)

    fun isFinished(): Boolean = currentIndex >= notesToPlay.size

    fun onNoteDetected(noteName: String, nowMs: Long): Boolean {
        if (currentIndex >= notesToPlay.size) return false
        if (nowMs - lastAcceptedTime < debounceTimeMs) return false

        if (noteName == notesToPlay[currentIndex]) {
            currentIndex++
            lastAcceptedTime = nowMs
            return currentIndex == notesToPlay.size
        }

        return false
    }

    fun matchesCurrentNote(noteName: String, nowMs: Long): Boolean {
        if (currentIndex >= notesToPlay.size) return false
        if (nowMs - lastAcceptedTime < debounceTimeMs) return false
        if (noteName != notesToPlay[currentIndex]) return false

        currentIndex++
        lastAcceptedTime = nowMs
        return true
    }
}
