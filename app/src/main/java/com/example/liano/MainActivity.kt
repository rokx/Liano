package com.example.liano

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.io.android.AudioDispatcherFactory
import be.tarsos.dsp.pitch.PitchProcessor
import com.example.liano.ui.theme.LianoTheme
import kotlin.math.log2
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private val RECORD_AUDIO_PERMISSION = Manifest.permission.RECORD_AUDIO
    private val REQUEST_MIC = 100
    private val exercise = SimpleNoteExercise()
    private var dispatcher: AudioDispatcher? = null

    private lateinit var pitchText: TextView
    private lateinit var greatWorkText: TextView
    private lateinit var noteBand: NoteBandView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pitchText = findViewById(R.id.pitchText)
        greatWorkText = findViewById(R.id.greatWorkText)
        noteBand = findViewById(R.id.noteBand)

        requestMicPermission()
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
                    if (exercise.onNoteDetected(noteName, SystemClock.elapsedRealtime())) {
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
        val A4 = 440.0
        val noteNumber = (12 * (log2(freq / A4)) + 69).roundToInt()
        val name = noteNames[noteNumber % 12]
        val octave = noteNumber / 12 - 1
        return "$name$octave"
    }

}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    LianoTheme {
        Greeting("Android")
    }
}
