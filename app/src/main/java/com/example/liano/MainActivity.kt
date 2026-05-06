package com.example.liano

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.io.android.AudioDispatcherFactory
import be.tarsos.dsp.pitch.PitchProcessor
import kotlin.math.log2
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private data class SheetSong(
        val title: String,
        val notes: List<String>
    )

    private val songs = listOf(
        SheetSong("First Steps", listOf("C4", "D4", "C4", "D4", "C4", "D4")),
        SheetSong("Warm Up", listOf("C4", "C4", "D4", "D4", "C4", "D4")),
        SheetSong("Little Melody", listOf("D4", "C4", "D4", "C4", "D4", "C4"))
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

        setupSheetButtons()
        requestMicPermission()
    }

    private fun setupSheetButtons() {
        songs.forEach { song ->
            val button = Button(this).apply {
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

            sheetButtonContainer.addView(button, params)
        }
    }

    private fun openSong(song: SheetSong) {
        exercise = SimpleNoteExercise(song.notes)

        taskText.text = "Play: ${song.notes.joinToString(" ") { it.replace("4", "") }}"

        val visualNotes = song.notes.mapIndexed { index, note ->
            NoteBandView.SongNote(
                name = note,
                startBeat = index * 1.4f,
                lengthBeats = 1f,
                lane = if (note.startsWith("C")) 0 else 1
            )
        }

        noteBand.setSongNotes(visualNotes)

        sheetSelectionContainer.visibility = View.GONE
        playContainer.visibility = View.VISIBLE
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
        ) { pitchDetectionResult, _ ->
            val pitchInHz = pitchDetectionResult.pitch

            runOnUiThread {
                if (pitchInHz > 0) {
                    Log.d("Pitch", "Detected pitch: $pitchInHz Hz")

                    val noteName = frequencyToNoteName(pitchInHz)
                    pitchText.text = "$noteName (${pitchInHz.toInt()} Hz)"
                    noteBand.setDetectedNote(noteName)

                    if (::exercise.isInitialized &&
                        exercise.onNoteDetected(noteName, SystemClock.elapsedRealtime())
                    ) {
                        showGreatWork()
                    }
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
}
