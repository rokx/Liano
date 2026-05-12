package com.rokx.liano

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class MidiNoteExtractorTest {
    @Test
    fun `extractNotes reads note events in chronological order`() {
        val midi = buildMidi(
            ticksPerQuarterNote = 480,
            events = listOf(
                bytes(0x00, 0x90, 60, 96),
                bytes(0x81, 0x70, 0x80, 60, 0),
                bytes(0x00, 0x90, 64, 88),
                bytes(0x78, 0x90, 64, 0),
                bytes(0x00, 0xFF, 0x2F, 0x00)
            )
        )

        val song = MidiNoteExtractor.extractNotes(midi)

        assertEquals(480, song.ticksPerQuarterNote)
        assertEquals(listOf("C4", "E4"), song.notes.map { it.name })
        assertEquals(listOf(0L, 240L), song.notes.map { it.startTick })
        assertEquals(listOf(240L, 120L), song.notes.map { it.durationTicks })
    }

    @Test
    fun `extractNotes supports running status note events`() {
        val midi = buildMidi(
            ticksPerQuarterNote = 96,
            events = listOf(
                bytes(0x00, 0x90, 67, 100),
                bytes(0x60, 67, 0),
                bytes(0x00, 0xFF, 0x2F, 0x00)
            )
        )

        val song = MidiNoteExtractor.extractNotes(midi)

        assertEquals(listOf("G4"), song.notes.map { it.name })
        assertEquals(96L, song.notes.single().durationTicks)
    }

    private fun buildMidi(ticksPerQuarterNote: Int, events: List<ByteArray>): ByteArray {
        val trackData = ByteArrayOutputStream()
        events.forEach { trackData.write(it) }
        val trackBytes = trackData.toByteArray()

        return ByteArrayOutputStream().apply {
            writeAscii("MThd")
            writeIntValue(6)
            writeShortValue(1)
            writeShortValue(1)
            writeShortValue(ticksPerQuarterNote)
            writeAscii("MTrk")
            writeIntValue(trackBytes.size)
            write(trackBytes)
        }.toByteArray()
    }

    private fun bytes(vararg values: Int): ByteArray = values.map { it.toByte() }.toByteArray()

    private fun ByteArrayOutputStream.writeAscii(value: String) {
        write(value.toByteArray(StandardCharsets.US_ASCII))
    }

    private fun ByteArrayOutputStream.writeIntValue(value: Int) {
        write((value shr 24) and 0xFF)
        write((value shr 16) and 0xFF)
        write((value shr 8) and 0xFF)
        write(value and 0xFF)
    }

    private fun ByteArrayOutputStream.writeShortValue(value: Int) {
        write((value shr 8) and 0xFF)
        write(value and 0xFF)
    }
}
