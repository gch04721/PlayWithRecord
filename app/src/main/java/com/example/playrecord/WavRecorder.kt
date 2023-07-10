package com.example.playrecord

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.EditText
import androidx.annotation.RequiresApi
import java.io.*

class WavRecorder(private val output: String, var context: Context) {

    val RECORDER_BPP = 16;
    val AUDIO_RECORDER_FOLDER = "AudioRecorder";
    val AUDIO_RECORDER_TEMP_FILE = "record_temp.raw"
    val RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO
    val RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT

    var RECORDER_SAMPLING_RATE = 192000
    var isRecording: Boolean = true

    var recorder: AudioRecord? = null

    var recordingThread: Thread? = null
    var writingThread: Thread? = null

    var bufferSize: Int = 0
    var writeBuffer: fQueue? = null

    var isBottom = true


    init{

    }

    private fun getFilename(): String {
        val name = (context as Activity).findViewById<View>(R.id.recordFileName) as EditText
        var nameOfTheFile = name.text.toString()
        nameOfTheFile += ".wav"
        return "$output/$nameOfTheFile"
    }

    private fun getTempFilename(): String {
        val filepath = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        val file = File(filepath, AUDIO_RECORDER_FOLDER)
        if (!file.exists()) {
            file.mkdirs()
        }
        val tempFile = File(filepath, AUDIO_RECORDER_TEMP_FILE)
        if (tempFile.exists()) tempFile.delete()
        return file.absolutePath + "/" + AUDIO_RECORDER_TEMP_FILE
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("MissingPermission")
    fun startRecording(){
        val sampleRate = (context as Activity).findViewById<EditText>(R.id.samplingRateValue)
        RECORDER_SAMPLING_RATE = sampleRate.text.toString().toInt()

        bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLING_RATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING) * 3;
        writeBuffer = fQueue(1000)
        Log.d("TEST", "startRecording: $writeBuffer")

        if(isBottom)
            recorder = AudioRecord(MediaRecorder.AudioSource.UNPROCESSED, RECORDER_SAMPLING_RATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING, bufferSize)
        else
            recorder = AudioRecord(MediaRecorder.AudioSource.CAMCORDER, RECORDER_SAMPLING_RATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING, bufferSize)

        val i = recorder!!.state
        if(i == 1)
            recorder!!.startRecording()

        isRecording = true;

        recordingThread = Thread({ writeAudioDataToFile() }, "AudioRecorder Thread")
        writingThread = Thread({ threadWriting() }, "Writing Thread")

        recordingThread!!.start()
        writingThread!!.start()
    }

    fun stopRecording(){
        if (null != recorder) {
            val i = recorder!!.state
            if (i == 1) recorder!!.stop()
            recorder!!.release()
            while (writingThread != null) {
                if (writeBuffer!!.getSize() == 0) {
//                    Log.d(tag,"Buffer Size 0"+this.writeBuffer.getSize().toString());
                    writingThread!!.interrupt()
                    recordingThread!!.interrupt()
                    writingThread = null
                    recordingThread = null
                }
                try {
                    Thread.sleep(900)
                } catch (e: InterruptedException) {
                    e.printStackTrace()

                }
            }
            isRecording = false
            writingThread = null
            recordingThread = null
            writeBuffer = null
        }

        copyWaveFile(getTempFilename(), getFilename())

        deleteTempFile()
    }

    private fun deleteTempFile() {
        val file: File = File(getTempFilename())
        file.delete()
    }

    private fun copyWaveFile(inFilename: String, outFilename: String) {
        var `in`: FileInputStream? = null
        var out: FileOutputStream? = null
        var totalAudioLen: Long = 0
        var totalDataLen = totalAudioLen + 36
        val longSampleRate: Long = RECORDER_SAMPLING_RATE.toLong()
        val channels = if (RECORDER_CHANNELS == AudioFormat.CHANNEL_IN_MONO) 1 else 2
        val byteRate: Long =
            (RECORDER_BPP * RECORDER_SAMPLING_RATE * channels / 8).toLong()
        val data = ByteArray(bufferSize)
        try {
            `in` = FileInputStream(inFilename)
            out = FileOutputStream(outFilename)
            totalAudioLen = `in`.channel.size()
            totalDataLen = totalAudioLen + 36
            WriteWaveFileHeader(
                out, totalAudioLen, totalDataLen,
                longSampleRate, channels, byteRate
            )
            while (`in`.read(data) != -1) {
                out.write(data)
            }
            `in`.close()
            out.close()
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun threadWriting(){
        val filename: String = getTempFilename()
        var os1: FileOutputStream? = null
        var temp: ByteArray?
        try {
            os1 = FileOutputStream(filename)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        }
        try {
            while (isRecording || writeBuffer!!.getSize() != 0) {
                temp = writeBuffer?.remove()
                if (temp == null) {
                    Thread.sleep(1)
                } else {
                    os1!!.write(temp)
                    //                    Thread.sleep(10);
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            e.printStackTrace()
            Log.d("TEST", "stopRecording: thread stop")
        }

        try {
            Log.d("TEST", "threadWriting: check")
            os1!!.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun writeAudioDataToFile(){
        val data = ByteArray(bufferSize)

        var read = 0

        while (isRecording && recorder != null) {
            read = recorder!!.read(data, 0, bufferSize)
            if (AudioRecord.ERROR_INVALID_OPERATION != read) {
                try {
                    writeBuffer!!.add(data.clone())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun WriteWaveFileHeader(
        out: FileOutputStream, totalAudioLen: Long,
        totalDataLen: Long, longSampleRate: Long, channels: Int, byteRate: Long
    ) {
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte() // RIFF/WAVE header
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte() // 'fmt ' chunk
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // 4 bytes: size of 'fmt ' chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // format = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (longSampleRate and 0xff).toByte()
        header[25] = (longSampleRate shr 8 and 0xff).toByte()
        header[26] = (longSampleRate shr 16 and 0xff).toByte()
        header[27] = (longSampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] =
            ((if (RECORDER_CHANNELS == AudioFormat.CHANNEL_IN_MONO) 1 else 2) * 16 / 8).toByte() // block align
        header[33] = 0
        header[34] = RECORDER_BPP.toByte() // bits per sample
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = (totalAudioLen shr 8 and 0xff).toByte()
        header[42] = (totalAudioLen shr 16 and 0xff).toByte()
        header[43] = (totalAudioLen shr 24 and 0xff).toByte()
        out.write(header, 0, 44)
    }
}