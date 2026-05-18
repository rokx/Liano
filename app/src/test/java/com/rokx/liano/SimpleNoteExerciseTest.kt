package com.rokx.liano

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimpleNoteExerciseTest {
    @Test
    fun `matchesCurrentNote advances only for expected notes`() {
        val exercise = SimpleNoteExercise(listOf("C4", "D4"))

        assertEquals(0, exercise.currentIndex())
        assertEquals("C4", exercise.currentNote())
        assertFalse(exercise.matchesCurrentNote("D4", 1_000L))
        assertEquals("C4", exercise.currentNote())

        assertTrue(exercise.matchesCurrentNote("C4", 1_000L))
        assertEquals(1, exercise.currentIndex())
        assertEquals("D4", exercise.currentNote())

        assertFalse(exercise.matchesCurrentNote("D4", 1_200L))
        assertEquals("D4", exercise.currentNote())

        assertTrue(exercise.matchesCurrentNote("D4", 1_800L))
        assertTrue(exercise.isFinished())
        assertEquals(null, exercise.currentNote())
    }

    @Test
    fun `simultaneous notes complete one chord step`() {
        val exercise = SimpleNoteExercise.fromSteps(listOf(setOf("C3", "E4")))

        assertFalse(exercise.onNoteDetected("C3", 1_000L))
        assertTrue(exercise.onNoteDetected("E4", 1_250L))
    }

    @Test
    fun `chord notes must arrive within chord window`() {
        val exercise = SimpleNoteExercise.fromSteps(listOf(setOf("C3", "E4")))

        assertFalse(exercise.onNoteDetected("C3", 1_000L))
        assertFalse(exercise.onNoteDetected("E4", 2_500L))
    }
}
