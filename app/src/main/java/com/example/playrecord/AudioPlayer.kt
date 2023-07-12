package com.example.playrecord

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import java.io.FileNotFoundException
import java.io.IOException
import java.io.RandomAccessFile

class AudioPlayer(filePath : String, samplingRate: Int, isStereo: Boolean) : Thread() {
    var audioSessionID = 0
    var filePath = ""
    private lateinit var audioTrack : AudioTrack
    lateinit var audioBuffer: ByteArray
    private var bufSize = 0

    var bufCnt = 0
    var isStarted = false
    var isPlaying = false

    var isNew = false
    var volume = 0.1f


    lateinit var fd : RandomAccessFile


    init{
        buildPlayer(samplingRate, isStereo)
    }

    override fun run(){
        while(filePath=="init")
            continue

        if(this.isStarted){
            this.audioTrack.play()
            var playOffset = 0

            while((!isInterrupted) && isStarted){
                for(i in audioBuffer.indices){
                    audioBuffer[i] = 0
                }

                try{
                    if(isNew && ((MainActivity.isRecord && MainActivity.isRecordStart) || !MainActivity.isRecord)){
                        this.audioTrack.setVolume(this.volume)
                        isPlaying = true
                        MainActivity.isStopStreamRequested = false
                        fd = RandomAccessFile(this.filePath, "r")
                        fd.seek(44)
                        playOffset = 0
                        isNew= false
                        isPlaying = true
                    }
                    if(isPlaying){
                        if(fd.read(audioBuffer, playOffset, bufSize) == -1 || MainActivity.isStopStreamRequested){
                            fd.close()
                            isNew = (MainActivity.isLoop && !MainActivity.isStopStreamRequested)
                            isStarted= (MainActivity.isLoop && !MainActivity.isStopStreamRequested)
                            isPlaying = false
                            MainActivity.isStopStreamRequested = false
                            continue
                        }
                    }
                } catch(e: FileNotFoundException){
                    e.printStackTrace()
                    isNew = false
                    isPlaying = false
                } catch(e : IOException){
                    e.printStackTrace()
                    isNew = false
                    isPlaying = false
                }

                audioTrack.write(audioBuffer, 0, bufSize)
                bufCnt++
            }
            Log.d("TEST", "run: interrupted, $isInterrupted")
            bufCnt = 0
            isStarted = false
            isPlaying = false
            audioTrack.stop()
        }
    }

    private fun buildPlayer(samplingRate: Int, isStereo: Boolean){
        this.filePath = filePath

        if (isStereo){
            bufSize = AudioTrack.getMinBufferSize(samplingRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT)
            this.audioBuffer = ByteArray(bufSize)

            val audioAttr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(samplingRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build()

            audioSessionID= AudioManager.AUDIO_SESSION_ID_GENERATE

            this.audioTrack = AudioTrack(audioAttr, audioFormat, bufSize, AudioTrack.MODE_STREAM, audioSessionID)

            // 소리 크기 조절
            this.audioTrack.setVolume(this.volume)

            this.bufCnt = 0
            this.isStarted = true
            this.isPlaying = false
        }else{
            bufSize = AudioTrack.getMinBufferSize(samplingRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT)
            this.audioBuffer = ByteArray(bufSize)

            val audioAttr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(samplingRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            audioSessionID= AudioManager.AUDIO_SESSION_ID_GENERATE

            this.audioTrack = AudioTrack(audioAttr, audioFormat, bufSize, AudioTrack.MODE_STREAM, audioSessionID)

            // 소리 크기 조절
            this.audioTrack.setVolume(this.volume)

            this.bufCnt = 0
            this.isStarted = true
            this.isPlaying = false
        }
    }

    fun requestStop(){
        MainActivity.isStopStreamRequested=true
    }
}