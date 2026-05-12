package com.rokx.liano

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.M)
class UsbMidiPianoInput(
    context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onMidiStatusChanged(status: String)
        fun onMidiNoteOn(noteNumber: Int, noteName: String)
        fun onMidiNoteOff(noteNumber: Int, noteName: String)
    }

    private val midiManager = context.getSystemService(MidiManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var device: MidiDevice? = null
    private var outputPort: MidiOutputPort? = null
    private var runningStatus: Int? = null
    private val pendingDataBytes = mutableListOf<Int>()

    private val receiver = object : MidiReceiver() {
        override fun onSend(data: ByteArray, offset: Int, count: Int, timestamp: Long) {
            for (index in offset until offset + count) {
                parseMidiByte(data[index].toInt() and BYTE_MASK)
            }
        }
    }

    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(info: MidiDeviceInfo) {
            if (device == null) {
                openFirstUsableDevice()
            }
        }

        override fun onDeviceRemoved(info: MidiDeviceInfo) {
            if (device?.info?.id == info.id) {
                close()
                listener.onMidiStatusChanged("USB piano disconnected")
                start()
            }
        }
    }

    fun start() {
        midiManager.registerDeviceCallback(deviceCallback, mainHandler)
        openFirstUsableDevice()
    }

    fun stop() {
        midiManager.unregisterDeviceCallback(deviceCallback)
        close()
    }

    private fun openFirstUsableDevice() {
        val info = midiManager.devices.firstOrNull(::hasOutputPort)
        if (info == null) {
            listener.onMidiStatusChanged("Connect a USB MIDI piano")
            return
        }

        listener.onMidiStatusChanged("Opening ${deviceName(info)}...")
        midiManager.openDevice(info, { openedDevice ->
            if (openedDevice == null) {
                listener.onMidiStatusChanged("Could not open USB piano")
                return@openDevice
            }

            val portNumber = openedDevice.info.ports
                .firstOrNull { it.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT }
                ?.portNumber

            if (portNumber == null) {
                openedDevice.close()
                listener.onMidiStatusChanged("USB piano has no MIDI output port")
                return@openDevice
            }

            val openedPort = openedDevice.openOutputPort(portNumber)
            if (openedPort == null) {
                openedDevice.close()
                listener.onMidiStatusChanged("Could not read from USB piano")
                return@openDevice
            }

            device = openedDevice
            outputPort = openedPort
            openedPort.connect(receiver)
            listener.onMidiStatusChanged("USB piano ready: ${deviceName(openedDevice.info)}")
        }, mainHandler)
    }

    private fun close() {
        outputPort?.disconnect(receiver)
        outputPort?.close()
        device?.close()
        outputPort = null
        device = null
        runningStatus = null
        pendingDataBytes.clear()
    }

    private fun parseMidiByte(value: Int) {
        if (value >= STATUS_BYTE_MIN) {
            if (value < SYSTEM_MESSAGE_MIN) {
                runningStatus = value
                pendingDataBytes.clear()
            }
            return
        }

        val status = runningStatus ?: return
        pendingDataBytes += value
        val command = status and COMMAND_MASK
        val neededBytes = when (command) {
            NOTE_OFF, NOTE_ON -> 2
            PROGRAM_CHANGE, CHANNEL_PRESSURE -> 1
            else -> 2
        }
        if (pendingDataBytes.size < neededBytes) return

        val noteNumber = pendingDataBytes[0]
        val velocity = pendingDataBytes.getOrElse(1) { 0 }
        pendingDataBytes.clear()

        when {
            command == NOTE_ON && velocity > 0 -> {
                listener.onMidiNoteOn(noteNumber, MidiNoteExtractor.noteName(noteNumber))
            }

            command == NOTE_OFF || command == NOTE_ON -> {
                listener.onMidiNoteOff(noteNumber, MidiNoteExtractor.noteName(noteNumber))
            }
        }
    }

    private fun hasOutputPort(info: MidiDeviceInfo): Boolean =
        info.ports.any { it.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT }

    private fun deviceName(info: MidiDeviceInfo): String {
        val name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME)
        val manufacturer = info.properties.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER)
        return listOfNotNull(manufacturer, name).joinToString(" ").ifBlank { "MIDI device ${info.id}" }
    }

    private companion object {
        private const val BYTE_MASK = 0xFF
        private const val STATUS_BYTE_MIN = 0x80
        private const val SYSTEM_MESSAGE_MIN = 0xF0
        private const val COMMAND_MASK = 0xF0
        private const val NOTE_OFF = 0x80
        private const val NOTE_ON = 0x90
        private const val PROGRAM_CHANGE = 0xC0
        private const val CHANNEL_PRESSURE = 0xD0
    }
}
