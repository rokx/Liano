package com.example.liano

class SimpleNoteExercise(
    private val notesToPlay: List<String> = listOf("C4", "D4", "C4", "D4", "C4", "D4")
) {
    private var currentIndex = 0
    private var lastAcceptedTime = 0L
    private val debounceTimeMs = 700L

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
}
