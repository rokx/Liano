package com.rokx.liano

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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

    private enum class InputMode {
        MICROPHONE,
        USB_PIANO
    }

    private enum class SheetPlaybackMode {
        FLOW,
        WAIT_FOR_NOTE
    }

    private lateinit var exercise: SimpleNoteExercise
    private var dispatcher: AudioDispatcher? = null
    private var usbMidiInput: UsbMidiPianoInput? = null
    private var inputMode = InputMode.MICROPHONE
    private var sheetPlaybackMode = SheetPlaybackMode.FLOW
    private val pressedMidiNotes = mutableSetOf<Int>()
    private var currentSong: SheetSong? = null
    private var hintTargetNote: String? = null
    private val showHintRunnable = Runnable {
        showPracticeHintIfStillWaiting()
    }

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
    private lateinit var inputModeButton: Button
    private lateinit var playInputModeButton: Button
    private lateinit var noteGateModeButton: Button
    private lateinit var pianoTestButton: Button
    private lateinit var pianoTestContainer: View
    private lateinit var backFromPianoTestButton: Button
    private lateinit var testInputModeButton: Button
    private lateinit var pianoTestStatusText: TextView
    private lateinit var pianoKeyboardView: PianoKeyboardView
    private lateinit var practiceHintKeyboardView: PianoKeyboardView
    private lateinit var pressedNotesText: TextView

    private val midiPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::importMidiSong)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterFullscreen()
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
        inputModeButton = findViewById(R.id.inputModeButton)
        playInputModeButton = findViewById(R.id.playInputModeButton)
        noteGateModeButton = findViewById(R.id.noteGateModeButton)
        pianoTestButton = findViewById(R.id.pianoTestButton)
        pianoTestContainer = findViewById(R.id.pianoTestContainer)
        backFromPianoTestButton = findViewById(R.id.backFromPianoTestButton)
        testInputModeButton = findViewById(R.id.testInputModeButton)
        pianoTestStatusText = findViewById(R.id.pianoTestStatusText)
        pianoKeyboardView = findViewById(R.id.pianoKeyboardView)
        practiceHintKeyboardView = findViewById(R.id.practiceHintKeyboardView)
        pressedNotesText = findViewById(R.id.pressedNotesText)

        backToMenuButton.setOnClickListener {
            showSheetSelection()
        }
        pausePlaybackButton.setOnClickListener {
            toggleSheetPlayback()
        }
        uploadMidiButton.setOnClickListener {
            midiPicker.launch(arrayOf(MIDI_MIME_TYPE, LEGACY_MIDI_MIME_TYPE, OCTET_STREAM_MIME_TYPE, ANY_FILE_MIME_TYPE))
        }
        inputModeButton.setOnClickListener {
            toggleInputMode()
        }
        playInputModeButton.setOnClickListener {
            toggleInputMode()
        }
        noteGateModeButton.setOnClickListener {
            toggleSheetPlaybackMode()
        }
        testInputModeButton.setOnClickListener {
            toggleInputMode()
        }
        pianoTestButton.setOnClickListener {
            showPianoTest()
        }
        backFromPianoTestButton.setOnClickListener {
            showSheetSelection()
        }
        noteBand.onPlaybackFinished = {
            pausePlaybackButton.text = "Finished"
            pausePlaybackButton.isEnabled = false
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (playContainer.visibility == View.VISIBLE || pianoTestContainer.visibility == View.VISIBLE) {
                    showSheetSelection()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        setupSheetButtons()
        updateInputModeButtons()
        updateSheetPlaybackModeButton()
        startSelectedInput()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enterFullscreen()
        }
    }

    private fun enterFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun setupSheetButtons() {
        loadSongsFromAssets().forEach(::addSongButton)
    }

    private fun addSongButton(song: SheetSong, addToTop: Boolean = false) {
        val button = Button(this).apply {
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_song_button)
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.black))
            text = song.title
            textSize = 18f
            setPadding(24, 14, 24, 14)
            setOnClickListener {
                openSong(song)
            }
        }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 0, 12)
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
        currentSong = song
        exercise = SimpleNoteExercise(song.notes)

        taskText.text = "Play: ${song.notes.joinToString(" ") { it.replace("4", "") }}"

        noteBand.setSongNotes(song.visualNotes, autoplay = sheetPlaybackMode == SheetPlaybackMode.FLOW)
        pausePlaybackButton.text = "Pause"
        pausePlaybackButton.isEnabled = true
        hidePracticeHint()
        if (sheetPlaybackMode == SheetPlaybackMode.WAIT_FOR_NOTE) {
            startWaitForNotePlayback()
        }

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
        hidePracticeHint()
        playContainer.visibility = View.GONE
        pianoTestContainer.visibility = View.GONE
        sheetSelectionContainer.visibility = View.VISIBLE
    }

    private fun showPianoTest() {
        noteBand.pausePlayback()
        hidePracticeHint()
        sheetSelectionContainer.visibility = View.GONE
        playContainer.visibility = View.GONE
        pianoTestContainer.visibility = View.VISIBLE
        setInputMode(InputMode.USB_PIANO)
    }

    private fun toggleSheetPlayback() {
        if (sheetPlaybackMode == SheetPlaybackMode.WAIT_FOR_NOTE) {
            startWaitForNotePlayback()
            return
        }

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

    private fun toggleSheetPlaybackMode() {
        sheetPlaybackMode = when (sheetPlaybackMode) {
            SheetPlaybackMode.FLOW -> SheetPlaybackMode.WAIT_FOR_NOTE
            SheetPlaybackMode.WAIT_FOR_NOTE -> SheetPlaybackMode.FLOW
        }
        updateSheetPlaybackModeButton()

        val song = currentSong ?: return
        exercise = SimpleNoteExercise(song.notes)
        noteBand.setSongNotes(song.visualNotes, autoplay = sheetPlaybackMode == SheetPlaybackMode.FLOW)
        pausePlaybackButton.isEnabled = true
        pausePlaybackButton.text = "Pause"
        hidePracticeHint()

        if (sheetPlaybackMode == SheetPlaybackMode.WAIT_FOR_NOTE) {
            startWaitForNotePlayback()
        }
    }

    private fun updateSheetPlaybackModeButton() {
        noteGateModeButton.text = when (sheetPlaybackMode) {
            SheetPlaybackMode.FLOW -> "Mode: Flow"
            SheetPlaybackMode.WAIT_FOR_NOTE -> "Mode: Wait"
        }
    }

    private fun startWaitForNotePlayback() {
        if (!::exercise.isInitialized) return
        if (exercise.isFinished()) {
            finishWaitForNotePlayback()
            return
        }

        hidePracticeHint()
        pausePlaybackButton.text = "Waiting"
        pausePlaybackButton.isEnabled = true
        noteBand.scrollToNote(exercise.currentIndex()) {
            schedulePracticeHint()
        }
    }

    private fun finishWaitForNotePlayback() {
        hidePracticeHint()
        noteBand.scrollToNote(Int.MAX_VALUE) {
            pausePlaybackButton.text = "Finished"
            pausePlaybackButton.isEnabled = false
        }
    }

    private fun requestMicPermission() {
        stopUsbPianoInput()
        if (ContextCompat.checkSelfPermission(this, RECORD_AUDIO_PERMISSION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pitchText.text = "Microphone permission needed"
            pianoTestStatusText.text = "Microphone permission needed"
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
            if (inputMode == InputMode.MICROPHONE) {
                startPitchDetection()
            }
        } else if (requestCode == REQUEST_MIC) {
            pitchText.text = "Microphone permission denied"
            pianoTestStatusText.text = "Microphone permission denied"
        }
    }

    private fun startPitchDetection() {
        stopUsbPianoInput()
        dispatcher?.stop()
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
                    val noteNumber = midiNoteNumberForName(noteName)
                    pitchText.text = "Microphone: $noteName (${pitchInHz.toInt()} Hz)"
                    pianoTestStatusText.text = "Microphone: $noteName (${pitchInHz.toInt()} Hz)"
                    setPressedMidiNotes(noteNumber?.let(::setOf).orEmpty())
                    handleDetectedNote(noteName)
                } else {
                    pitchText.text = "Waiting for microphone..."
                    pianoTestStatusText.text = "Waiting for microphone..."
                    noteBand.clearDetectedNote()
                    setPressedMidiNotes(emptySet())
                }
            }
        }

        dispatcher?.addAudioProcessor(pitchDetector)

        Thread {
            dispatcher?.run()
        }.start()
    }

    private fun toggleInputMode() {
        setInputMode(
            when (inputMode) {
                InputMode.MICROPHONE -> InputMode.USB_PIANO
                InputMode.USB_PIANO -> InputMode.MICROPHONE
            }
        )
    }

    private fun setInputMode(mode: InputMode) {
        if (inputMode == mode && (dispatcher != null || usbMidiInput != null)) return
        inputMode = mode
        updateInputModeButtons()
        setPressedMidiNotes(emptySet())
        noteBand.clearDetectedNote()
        startSelectedInput()
    }

    private fun updateInputModeButtons() {
        val text = when (inputMode) {
            InputMode.MICROPHONE -> "Input: Microphone"
            InputMode.USB_PIANO -> "Input: USB piano"
        }
        inputModeButton.text = text
        playInputModeButton.text = text
        testInputModeButton.text = text
    }

    private fun startSelectedInput() {
        when (inputMode) {
            InputMode.MICROPHONE -> requestMicPermission()
            InputMode.USB_PIANO -> startUsbPianoInput()
        }
    }

    private fun startUsbPianoInput() {
        dispatcher?.stop()
        dispatcher = null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            pitchText.text = "USB piano needs Android 6.0 or newer"
            pianoTestStatusText.text = "USB piano needs Android 6.0 or newer"
            return
        }

        stopUsbPianoInput()
        usbMidiInput = UsbMidiPianoInput(this, object : UsbMidiPianoInput.Listener {
            override fun onMidiStatusChanged(status: String) {
                runOnUiThread {
                    pitchText.text = status
                    pianoTestStatusText.text = status
                }
            }

            override fun onMidiNoteOn(noteNumber: Int, noteName: String) {
                runOnUiThread {
                    pressedMidiNotes += noteNumber
                    updatePressedNotes()
                    pitchText.text = "USB piano: $noteName"
                    pianoTestStatusText.text = "USB piano: $noteName"
                    handleDetectedNote(noteName)
                }
            }

            override fun onMidiNoteOff(noteNumber: Int, noteName: String) {
                runOnUiThread {
                    pressedMidiNotes -= noteNumber
                    updatePressedNotes()
                    if (pressedMidiNotes.isEmpty()) {
                        noteBand.clearDetectedNote()
                    }
                }
            }
        }).also { it.start() }
    }

    private fun stopUsbPianoInput() {
        usbMidiInput?.stop()
        usbMidiInput = null
    }

    private fun handleDetectedNote(noteName: String) {
        noteBand.setDetectedNote(noteName)

        if (playContainer.visibility != View.VISIBLE || !::exercise.isInitialized) return

        val nowMs = SystemClock.elapsedRealtime()
        if (sheetPlaybackMode == SheetPlaybackMode.WAIT_FOR_NOTE) {
            if (exercise.matchesCurrentNote(noteName, nowMs)) {
                showGreatWork()
                hidePracticeHint()
                if (exercise.isFinished()) {
                    finishWaitForNotePlayback()
                } else {
                    startWaitForNotePlayback()
                }
            }
        } else if (exercise.onNoteDetected(noteName, nowMs)) {
            showGreatWork()
        }
    }

    private fun setPressedMidiNotes(notes: Set<Int>) {
        pressedMidiNotes.clear()
        pressedMidiNotes += notes
        updatePressedNotes()
    }

    private fun updatePressedNotes() {
        pianoKeyboardView.setPressedNotes(pressedMidiNotes)
        practiceHintKeyboardView.setPressedNotes(pressedMidiNotes)
        val pressedText = pressedMidiNotes
            .sorted()
            .joinToString(" ") { MidiNoteExtractor.noteName(it) }
            .ifBlank { "none" }
        pressedNotesText.text = "Pressed: $pressedText"
    }

    private fun schedulePracticeHint() {
        hintTargetNote = exercise.currentNote()
        practiceHintKeyboardView.removeCallbacks(showHintRunnable)
        practiceHintKeyboardView.postDelayed(showHintRunnable, NOTE_HINT_DELAY_MS)
    }

    private fun showPracticeHintIfStillWaiting() {
        val targetNote = hintTargetNote ?: return
        if (playContainer.visibility != View.VISIBLE) return
        if (sheetPlaybackMode != SheetPlaybackMode.WAIT_FOR_NOTE) return
        if (targetNote != exercise.currentNote()) return

        val targetNoteNumber = midiNoteNumberForName(targetNote) ?: return
        practiceHintKeyboardView.setSuggestedNotes(setOf(targetNoteNumber))
        practiceHintKeyboardView.visibility = View.VISIBLE
    }

    private fun hidePracticeHint() {
        practiceHintKeyboardView.removeCallbacks(showHintRunnable)
        hintTargetNote = null
        practiceHintKeyboardView.clearSuggestedNotes()
        practiceHintKeyboardView.visibility = View.GONE
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

    private fun midiNoteNumberForName(noteName: String): Int? {
        val match = NOTE_NAME_PATTERN.matchEntire(noteName) ?: return null
        val pitchClass = NOTE_NAME_TO_OFFSET[match.groupValues[1]] ?: return null
        val octave = match.groupValues[2].toIntOrNull() ?: return null
        return (octave + 1) * NOTES_PER_OCTAVE + pitchClass
    }

    override fun onDestroy() {
        dispatcher?.stop()
        stopUsbPianoInput()
        super.onDestroy()
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
        private const val NOTES_PER_OCTAVE = 12
        private const val NOTE_HINT_DELAY_MS = 5_000L
        private val NOTE_NAME_PATTERN = Regex("^([A-G]#?)(-?\\d+)$")
        private val NOTE_NAME_TO_OFFSET = mapOf(
            "C" to 0,
            "C#" to 1,
            "D" to 2,
            "D#" to 3,
            "E" to 4,
            "F" to 5,
            "F#" to 6,
            "G" to 7,
            "G#" to 8,
            "A" to 9,
            "A#" to 10,
            "B" to 11
        )
    }
}
