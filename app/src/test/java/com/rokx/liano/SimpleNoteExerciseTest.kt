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
}
