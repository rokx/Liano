package com.rokx.liano

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.io.android.AudioDispatcherFactory
import be.tarsos.dsp.pitch.PitchProcessor
import org.json.JSONObject
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private data class SheetSong(
        val title: String,
        val notes: List<String>,
        val visualNotes: List<NoteBandView.SongNote> = notes.mapIndexed { index, note ->
            NoteBandView.SongNote(
                name = note,
                startBeat = index * DEFAULT_NOTE_SPACING_BEATS,
                lengthBeats = DEFAULT_NOTE_LENGTH_BEATS,
                lane = notes.distinct().indexOf(note)
            )
        }
    )

    private val RECORD_AUDIO_PERMISSION = Manifest.permission.RECORD_AUDIO
    private val REQUEST_MIC = 100

    private lateinit var exercise: SimpleNoteExercise
    private var dispatcher: AudioDispatcher? = null

    private lateinit var pitchText: TextView

    private lateinit var greatWorkText: TextView
    private lateinit var taskText: TextView
    private lateinit var noteBand: NoteBandView
    private lateinit var playContainer: View
    private lateinit var sheetSelectionContainer: View
    private lateinit var sheetButtonContainer: LinearLayout
    private lateinit var backToMenuButton: Button
    private lateinit var pausePlaybackButton: Button
    private lateinit var uploadMidiButton: Button

    private val midiPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::importMidiSong)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pitchText = findViewById(R.id.pitchText)

        greatWorkText = findViewById(R.id.greatWorkText)
        taskText = findViewById(R.id.taskText)
        noteBand = findViewById(R.id.noteBand)
        playContainer = findViewById(R.id.playContainer)
        sheetSelectionContainer = findViewById(R.id.sheetSelectionContainer)
        sheetButtonContainer = findViewById(R.id.sheetButtonContainer)
        backToMenuButton = findViewById(R.id.backToMenuButton)
        pausePlaybackButton = findViewById(R.id.pausePlaybackButton)
        uploadMidiButton = findViewById(R.id.uploadMidiButton)

        backToMenuButton.setOnClickListener {
            showSheetSelection()
        }
        pausePlaybackButton.setOnClickListener {
            toggleSheetPlayback()
        }
        uploadMidiButton.setOnClickListener {
            midiPicker.launch(arrayOf(MIDI_MIME_TYPE, LEGACY_MIDI_MIME_TYPE, OCTET_STREAM_MIME_TYPE, ANY_FILE_MIME_TYPE))
        }
        noteBand.onPlaybackFinished = {
            pausePlaybackButton.text = "Finished"
            pausePlaybackButton.isEnabled = false
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (playContainer.visibility == View.VISIBLE) {
                    showSheetSelection()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        setupSheetButtons()
        requestMicPermission()
    }

    private fun setupSheetButtons() {
        loadSongsFromAssets().forEach(::addSongButton)
    }

    private fun addSongButton(song: SheetSong, addToTop: Boolean = false) {
        val button = Button(this).apply {
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_song_button)
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.black))
            text = song.title
            textSize = 22f
            setPadding(32, 24, 32, 24)
            setOnClickListener {
                openSong(song)
            }
        }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 0, 24)
        }

        if (addToTop) {
            sheetButtonContainer.addView(button, 0, params)
        } else {
            sheetButtonContainer.addView(button, params)
        }
    }

    private fun loadSongsFromAssets(): List<SheetSong> {
        val songFiles = assets.list("songs")
            ?.filter { it.endsWith(".json") }
            ?.sorted()
            .orEmpty()

        return songFiles.map { fileName ->
            assets.open("songs/$fileName").bufferedReader().use { reader ->
                val json = JSONObject(reader.readText())
                val notesJson = json.getJSONArray("notes")
                val notes = List(notesJson.length()) { index -> notesJson.getString(index) }

                SheetSong(
                    title = json.getString("title"),
                    notes = notes
                )
            }
        }
    }

    private fun openSong(song: SheetSong) {
        exercise = SimpleNoteExercise(song.notes)

        taskText.text = "Play: ${song.notes.joinToString(" ") { it.replace("4", "") }}"

        noteBand.setSongNotes(song.visualNotes)
        pausePlaybackButton.text = "Pause"
        pausePlaybackButton.isEnabled = true

        sheetSelectionContainer.visibility = View.GONE
        playContainer.visibility = View.VISIBLE
    }

    private fun importMidiSong(uri: Uri) {
        val song = runCatching {
            val bytes = contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.readBytes()
            } ?: error("Unable to read MIDI file.")

            createSongFromMidi(
                title = displayNameForUri(uri),
                midiSong = MidiNoteExtractor.extractNotes(bytes)
            )
        }.getOrElse { error ->
            Toast.makeText(this, "Could not import MIDI: ${error.message}", Toast.LENGTH_LONG).show()
            return
        }

        addSongButton(song, addToTop = true)
        Toast.makeText(this, "Imported ${song.notes.size} notes", Toast.LENGTH_SHORT).show()
        openSong(song)
    }

    private fun createSongFromMidi(title: String, midiSong: MidiNoteExtractor.MidiSong): SheetSong {
        require(midiSong.notes.isNotEmpty()) { "No notes were found in this MIDI file." }

        val notesToImport = midiSong.notes.take(MAX_IMPORTED_MIDI_NOTES)
        val firstTick = notesToImport.first().startTick
        val ticksPerBeat = midiSong.ticksPerQuarterNote.toFloat()
        val lanes = notesToImport.map { it.name }.distinct()
        val visualNotes = notesToImport.map { note ->
            NoteBandView.SongNote(
                name = note.name,
                startBeat = max(0f, (note.startTick - firstTick) / ticksPerBeat),
                lengthBeats = max(MIN_IMPORTED_NOTE_LENGTH_BEATS, note.durationTicks / ticksPerBeat),
                lane = lanes.indexOf(note.name)
            )
        }

        return SheetSong(
            title = "Imported: $title",
            notes = notesToImport.map { it.name },
            visualNotes = visualNotes
        )
    }

    private fun displayNameForUri(uri: Uri): String {
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor: Cursor ->
                if (cursor.moveToFirst()) {
                    val displayNameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (displayNameIndex >= 0) {
                        return cursor.getString(displayNameIndex)
                    }
                }
            }

        return uri.lastPathSegment ?: "MIDI song"
    }

    private fun showSheetSelection() {
        noteBand.pausePlayback()
        playContainer.visibility = View.GONE
        sheetSelectionContainer.visibility = View.VISIBLE
    }

    private fun toggleSheetPlayback() {
        if (noteBand.hasPlaybackFinished()) {
            pausePlaybackButton.text = "Finished"
            pausePlaybackButton.isEnabled = false
            return
        }

        if (noteBand.isPlaybackPaused()) {
            noteBand.resumePlayback()
            pausePlaybackButton.text = "Pause"
        } else {
            noteBand.pausePlayback()
            pausePlaybackButton.text = "Resume"
        }
    }

    private fun requestMicPermission() {
        if (ContextCompat.checkSelfPermission(this, RECORD_AUDIO_PERMISSION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(RECORD_AUDIO_PERMISSION),
                REQUEST_MIC
            )
        } else {
            startPitchDetection()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_MIC &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startPitchDetection()
        }
    }

    private fun startPitchDetection() {
        dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(22050, 1024, 0)

        val pitchDetector = PitchProcessor(
            PitchProcessor.PitchEstimationAlgorithm.YIN,
            22050f,
            1024
        ) { pitchDetectionResult, audioEvent ->
            val pitchInHz = pitchDetectionResult.pitch
            val isVoiceLevel = audioEvent.rms >= MIN_VOICE_RMS
            val isConfidentPitch = pitchDetectionResult.isPitched &&
                pitchDetectionResult.probability >= MIN_PITCH_PROBABILITY

            runOnUiThread {
                if (pitchInHz > 0 && isVoiceLevel && isConfidentPitch) {
                    Log.d("Pitch", "Detected pitch: $pitchInHz Hz")

                    val noteName = frequencyToNoteName(pitchInHz)
                    pitchText.text = "$noteName (${pitchInHz.toInt()} Hz)"
                    noteBand.setDetectedNote(noteName)

                    if (playContainer.visibility == View.VISIBLE &&
                        ::exercise.isInitialized &&
                        exercise.onNoteDetected(noteName, SystemClock.elapsedRealtime())
                    ) {
                        showGreatWork()
                    }
                } else {
                    pitchText.text = "Waiting for voice..."
                    noteBand.clearDetectedNote()
                }
            }
        }

        dispatcher?.addAudioProcessor(pitchDetector)

        Thread {
            dispatcher?.run()
        }.start()
    }

    private fun showGreatWork() {
        greatWorkText.animate().cancel()
        greatWorkText.alpha = 0f

        greatWorkText.animate()
            .alpha(1f)
            .setDuration(500L)
            .withEndAction {
                greatWorkText.animate()
                    .alpha(0f)
                    .setStartDelay(900L)
                    .setDuration(700L)
                    .start()
            }
            .start()
    }

    private fun frequencyToNoteName(freq: Float): String {
        val noteNames = arrayOf(
            "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
        )

        val a4 = 440.0
        val noteNumber = (12 * (log2(freq / a4)) + 69).roundToInt()
        val name = noteNames[noteNumber % 12]
        val octave = noteNumber / 12 - 1

        return "$name$octave"
    }

    companion object {
        private const val MIN_VOICE_RMS = 0.015
        private const val MIN_PITCH_PROBABILITY = 0.75f
        private const val DEFAULT_NOTE_SPACING_BEATS = 1.4f
        private const val DEFAULT_NOTE_LENGTH_BEATS = 1f
        private const val MIN_IMPORTED_NOTE_LENGTH_BEATS = 0.25f
        private const val MAX_IMPORTED_MIDI_NOTES = 256
        private const val MIDI_MIME_TYPE = "audio/midi"
        private const val LEGACY_MIDI_MIME_TYPE = "audio/x-midi"
        private const val OCTET_STREAM_MIME_TYPE = "application/octet-stream"
        private const val ANY_FILE_MIME_TYPE = "*/*"
    }
}
