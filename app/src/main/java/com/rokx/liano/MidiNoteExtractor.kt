package com.rokx.liano

import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import kotlin.math.max

object MidiNoteExtractor {
    data class ExtractedNote(
        val name: String,
        val startTick: Long,
        val durationTicks: Long,
        val velocity: Int,
        val channel: Int
    )

    data class MidiSong(
        val ticksPerQuarterNote: Int,
        val notes: List<ExtractedNote>
    )

    private data class ActiveNoteKey(val channel: Int, val noteNumber: Int)
    private data class ActiveNote(val startTick: Long, val velocity: Int)

    fun extractNotes(midiBytes: ByteArray): MidiSong {
        val reader = MidiReader(midiBytes)
        reader.expectChunkId("MThd")
        val headerLength = reader.readInt()
        require(headerLength >= STANDARD_HEADER_LENGTH) { "Invalid MIDI header length." }

        val format = reader.readUnsignedShort()
        require(format in 0..2) { "Unsupported MIDI format: $format." }

        val trackCount = reader.readUnsignedShort()
        val division = reader.readUnsignedShort()
        require(division and SMPTE_DIVISION_MASK == 0) { "SMPTE time division MIDI files are not supported." }

        if (headerLength > STANDARD_HEADER_LENGTH) {
            reader.skip(headerLength - STANDARD_HEADER_LENGTH)
        }

        val allNotes = mutableListOf<ExtractedNote>()
        repeat(trackCount) {
            if (reader.isAtEnd()) return@repeat
            reader.expectChunkId("MTrk")
            val trackLength = reader.readInt()
            val trackBytes = reader.readBytes(trackLength)
            allNotes += parseTrack(trackBytes)
        }

        return MidiSong(
            ticksPerQuarterNote = division,
            notes = allNotes.sortedWith(compareBy<ExtractedNote> { it.startTick }.thenBy { it.name })
        )
    }

    fun noteName(noteNumber: Int): String {
        require(noteNumber in MIDI_NOTE_MIN..MIDI_NOTE_MAX) { "MIDI note out of range: $noteNumber." }
        val octave = noteNumber / NOTES_PER_OCTAVE - 1
        return "${NOTE_NAMES[noteNumber % NOTES_PER_OCTAVE]}$octave"
    }

    private fun parseTrack(trackBytes: ByteArray): List<ExtractedNote> {
        val reader = MidiReader(trackBytes)
        val activeNotes = mutableMapOf<ActiveNoteKey, ArrayDeque<ActiveNote>>()
        val extractedNotes = mutableListOf<ExtractedNote>()
        var tick = 0L
        var runningStatus: Int? = null

        while (!reader.isAtEnd()) {
            tick += reader.readVariableLengthQuantity()
            var status = reader.readUnsignedByte()

            if (status < STATUS_BYTE_MIN) {
                status = runningStatus
                    ?: throw IllegalArgumentException("Missing MIDI running status.")
                reader.rewind(1)
            } else if (status < SYSTEM_MESSAGE_MIN) {
                runningStatus = status
            }

            when {
                status == META_EVENT_STATUS -> {
                    runningStatus = null
                    val metaType = reader.readUnsignedByte()
                    val length = reader.readVariableLengthQuantity().toInt()
                    reader.skip(length)
                    if (metaType == END_OF_TRACK_META_TYPE) break
                }

                status == SYSEX_STATUS || status == ESCAPED_SYSEX_STATUS -> {
                    runningStatus = null
                    val length = reader.readVariableLengthQuantity().toInt()
                    reader.skip(length)
                }

                status in STATUS_BYTE_MIN until SYSTEM_MESSAGE_MIN -> {
                    val command = status and COMMAND_MASK
                    val channel = status and CHANNEL_MASK
                    val firstDataByte = reader.readUnsignedByte()
                    val secondDataByte = if (command == PROGRAM_CHANGE || command == CHANNEL_PRESSURE) {
                        null
                    } else {
                        reader.readUnsignedByte()
                    }

                    when (command) {
                        NOTE_ON -> handleNoteOn(
                            channel = channel,
                            noteNumber = firstDataByte,
                            velocity = secondDataByte ?: 0,
                            tick = tick,
                            activeNotes = activeNotes,
                            extractedNotes = extractedNotes
                        )

                        NOTE_OFF -> handleNoteOff(
                            channel = channel,
                            noteNumber = firstDataByte,
                            tick = tick,
                            activeNotes = activeNotes,
                            extractedNotes = extractedNotes
                        )
                    }
                }

                else -> throw IllegalArgumentException("Unsupported MIDI status byte: $status.")
            }
        }

        return extractedNotes
    }

    private fun handleNoteOn(
        channel: Int,
        noteNumber: Int,
        velocity: Int,
        tick: Long,
        activeNotes: MutableMap<ActiveNoteKey, ArrayDeque<ActiveNote>>,
        extractedNotes: MutableList<ExtractedNote>
    ) {
        if (velocity == 0) {
            handleNoteOff(channel, noteNumber, tick, activeNotes, extractedNotes)
            return
        }

        val key = ActiveNoteKey(channel, noteNumber)
        val notes = activeNotes.getOrPut(key) { ArrayDeque() }
        notes.addLast(ActiveNote(tick, velocity))
    }

    private fun handleNoteOff(
        channel: Int,
        noteNumber: Int,
        tick: Long,
        activeNotes: MutableMap<ActiveNoteKey, ArrayDeque<ActiveNote>>,
        extractedNotes: MutableList<ExtractedNote>
    ) {
        val key = ActiveNoteKey(channel, noteNumber)
        val startedNotes = activeNotes[key] ?: return
        val activeNote = startedNotes.pollFirst() ?: return
        if (startedNotes.isEmpty()) activeNotes.remove(key)

        extractedNotes += ExtractedNote(
            name = noteName(noteNumber),
            startTick = activeNote.startTick,
            durationTicks = max(1L, tick - activeNote.startTick),
            velocity = activeNote.velocity,
            channel = channel
        )
    }

    private class MidiReader(private val bytes: ByteArray) {
        private var position = 0

        fun isAtEnd(): Boolean = position >= bytes.size

        fun expectChunkId(expected: String) {
            val actual = String(readBytes(CHUNK_ID_LENGTH), StandardCharsets.US_ASCII)
            require(actual == expected) { "Expected $expected chunk but found $actual." }
        }

        fun readBytes(length: Int): ByteArray {
            require(length >= 0) { "Negative read length." }
            require(position + length <= bytes.size) { "Unexpected end of MIDI data." }
            val output = bytes.copyOfRange(position, position + length)
            position += length
            return output
        }

        fun readInt(): Int {
            require(position + 4 <= bytes.size) { "Unexpected end of MIDI data." }
            return (readUnsignedByte() shl 24) or
                (readUnsignedByte() shl 16) or
                (readUnsignedByte() shl 8) or
                readUnsignedByte()
        }

        fun readUnsignedShort(): Int {
            require(position + 2 <= bytes.size) { "Unexpected end of MIDI data." }
            return (readUnsignedByte() shl 8) or readUnsignedByte()
        }

        fun readUnsignedByte(): Int {
            require(position < bytes.size) { "Unexpected end of MIDI data." }
            return bytes[position++].toInt() and BYTE_MASK
        }

        fun readVariableLengthQuantity(): Long {
            var value = 0L
            var count = 0
            do {
                val nextByte = readUnsignedByte()
                value = (value shl 7) or (nextByte and VARIABLE_LENGTH_VALUE_MASK).toLong()
                count++
                require(count <= MAX_VARIABLE_LENGTH_BYTES) { "Invalid MIDI variable length quantity." }
            } while (nextByte and VARIABLE_LENGTH_CONTINUATION != 0)
            return value
        }

        fun rewind(byteCount: Int) {
            position = max(0, position - byteCount)
        }

        fun skip(length: Int) {
            readBytes(length)
        }
    }

    private const val STANDARD_HEADER_LENGTH = 6
    private const val CHUNK_ID_LENGTH = 4
    private const val BYTE_MASK = 0xFF
    private const val STATUS_BYTE_MIN = 0x80
    private const val SYSTEM_MESSAGE_MIN = 0xF0
    private const val META_EVENT_STATUS = 0xFF
    private const val SYSEX_STATUS = 0xF0
    private const val ESCAPED_SYSEX_STATUS = 0xF7
    private const val END_OF_TRACK_META_TYPE = 0x2F
    private const val SMPTE_DIVISION_MASK = 0x8000
    private const val COMMAND_MASK = 0xF0
    private const val CHANNEL_MASK = 0x0F
    private const val NOTE_OFF = 0x80
    private const val NOTE_ON = 0x90
    private const val PROGRAM_CHANGE = 0xC0
    private const val CHANNEL_PRESSURE = 0xD0
    private const val VARIABLE_LENGTH_VALUE_MASK = 0x7F
    private const val VARIABLE_LENGTH_CONTINUATION = 0x80
    private const val MAX_VARIABLE_LENGTH_BYTES = 4
    private const val MIDI_NOTE_MIN = 0
    private const val MIDI_NOTE_MAX = 127
    private const val NOTES_PER_OCTAVE = 12
    private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
}
