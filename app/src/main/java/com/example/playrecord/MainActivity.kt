package com.example.playrecord

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock.sleep
import android.provider.DocumentsContract
import android.util.Log
import android.widget.*
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import kotlinx.coroutines.delay
import kotlin.math.ceil

class MainActivity : AppCompatActivity() {
    // Permission req code
    val REQUEST_PERMISSION_CODE = 1
    val REQUEST_FILE_SELECT = 2
    val REQUEST_INTERNET = 3

    // button for start / stop
    lateinit var btnStart : Button
    lateinit var btnStop : Button

    // thread for recorder / player
    lateinit var recorder: WavRecorder
    lateinit var player : AudioPlayer

    // variable for player
    var filePath = "init"
    var audioName = ""
    var isPlayable = false
    var pastSR = 0
    var sampleRate = 48000
    var isStereo = true

    // variable for recorder
    var isBottom = true

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // check all permissions
        if(!checkPermission())
            requestPermission()

        //********* Sampling rate  *************//
        val sampleRateEdit = findViewById<EditText>(R.id.samplingRateValue)
        pastSR = sampleRate
        sampleRate = sampleRateEdit.text.toString().toInt()

        //********* Recorder setting ***********//
        val dirs = getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.absolutePath!!  // Set record file save directory
        recorder = WavRecorder(dirs.toString(), this)  // make wav file record thread

        val chkRecord = findViewById<CheckBox>(R.id.isRecord)
        isRecord = chkRecord.isChecked
        chkRecord.setOnClickListener {
            isRecord = chkRecord.isChecked
            findViewById<EditText>(R.id.recordFileName).isEnabled = isRecord

            setBtnCond()
        }

        val groupMic = findViewById<RadioGroup>(R.id.radioGroupMic)
        groupMic.setOnCheckedChangeListener { _, i ->
            if(i==R.id.radioBottom){
                isBottom=true
                recorder.isBottom = isBottom
            }
            if(i==R.id.radioBack) {
                isBottom=false
                recorder.isBottom = isBottom
            }
        }

        //********* Player setting *************//
        val chkLoop = findViewById<CheckBox>(R.id.isLoop)
        chkLoop.isEnabled=isPlayable
        chkLoop.setOnClickListener {
            isLoop = chkLoop.isChecked
            setBtnCond()
        } // set audio loop

        val btnSelectPlayFile = findViewById<Button>(R.id.btnFileSelect)
        val fileNameView = findViewById<TextView>(R.id.audioFileNameView)
        val audioFileSelect = registerForActivityResult(
            ActivityResultContracts.GetContent(),
            ActivityResultCallback {
                if (it != null) {
                    isPlayable = true
                    chkLoop.isEnabled=isPlayable
                    this.filePath = "/storage/emulated/0/${it?.lastPathSegment!!.split(":")[1]}"
                    this.audioName = it.lastPathSegment!!.split(":")[1]
                    fileNameView.text = this.audioName
                    findViewById<RadioButton>(R.id.radioMono).isEnabled = true
                    findViewById<RadioButton>(R.id.radioStereo).isEnabled = true
                    setBtnCond()
                }
            }
        ) // get audio file path

        btnSelectPlayFile.setOnClickListener {
            audioFileSelect.launch("audio/*")
        } // select audio file

        // player hardware volume setting
        val sliderHardware = findViewById<SeekBar>(R.id.mediaVolumeSlider)
        val audioManager: AudioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val mVolume = ceil((maxVolume/2).toDouble()).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mVolume, 0)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val textHardwareVolume = findViewById<TextView>(R.id.textMediaVolume)
        textHardwareVolume.text = currentVolume.toString()
        sliderHardware.max = maxVolume
        sliderHardware.min = 0
        sliderHardware.progress = currentVolume
        sliderHardware.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                textHardwareVolume.text = progress.toString()
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {
            }
            override fun onStopTrackingTouch(p0: SeekBar?) {
            }
        })

        // player wav file peak value setting, origin is -1 ~ 1
        val maxPeakEdit = findViewById<EditText>(R.id.rawVolumeEdit)
        var maxPeak = maxPeakEdit.text.toString().toFloat()
        if(maxPeak > 1f)
            maxPeak = 1f
        else if(maxPeak <= 0f)
            maxPeak = 1.0E-5F

        player = AudioPlayer(filePath, sampleRate, isStereo)
        player.start() // make/run audio player thread

        val groupChannel = findViewById<RadioGroup>(R.id.radioGroupChannel)
        groupChannel.setOnCheckedChangeListener { _, i ->
            if(i==R.id.radioStereo){
                isStereo = true
                player.interrupt()
                player = AudioPlayer(filePath, sampleRate, isStereo)
                player.start()
            }
            if(i==R.id.radioMono) {
                isStereo = false
                player.interrupt()
                player = AudioPlayer(filePath, sampleRate, isStereo)
                player.start()
            }
        }

        //********* Start / Stop button *************//
        btnStart = findViewById<Button>(R.id.startDual)
        btnStop = findViewById<Button>(R.id.stopDual)

        btnStart.setOnClickListener {
            if(isRecord) {
                recorder.startRecording()
                btnStart.isEnabled=false
                btnStop.isEnabled=true
            }
            if(isPlayable){
                player.interrupt()
                player = AudioPlayer(filePath, sampleRate, isStereo)
                player.start()

                maxPeak = maxPeakEdit.text.toString().toFloat()
                if(maxPeak > 1f)
                    maxPeak = 1f
                else if(maxPeak <= 0f)
                    maxPeak = 1.0E-5F
                player.volume = maxPeak
                player.filePath = this.filePath
                player.isNew = true

                if(isLoop){
                    btnStop.isEnabled=true
                    btnStart.isEnabled=false
                }
            }
        }

        btnStop.setOnClickListener {
            if(isRecord) {
                recorder.stopRecording()
                isRecordStart=false
                btnStart.isEnabled = true
                btnStop.isEnabled = false

                var name = findViewById<EditText>(R.id.recordFileName).text
                if (isInteger(name.toString().split("_").last())){
                    var s = name.split("_").toMutableList()
                    s[s.size-1] = (s[s.size-1].toInt()+1).toString()

                    findViewById<EditText>(R.id.recordFileName).setText(s.joinToString("_"))
                }
            }
            if(isPlayable && isLoop){
                btnStart.isEnabled=true
                btnStop.isEnabled=false
                player.requestStop()
                player.interrupt()
            }
        }

    }

    private fun isInteger(s: String): Boolean {
        return try {
            s.toInt()
            true
        } catch (e: NumberFormatException) {
            false
        }
    }

    private fun setBtnCond(){
        if(isPlayable && !isRecord){
            // only play audio
            btnStart.text="재생 시작"
            btnStop.text="재생 중지"

            btnStart.isVisible=true
            if(isLoop){
                btnStop.isVisible=true
                btnStop.isEnabled=false
            }
            else{
                btnStop.isVisible=false
            }
        }
        else if(!isPlayable && isRecord){
            // only recording
            btnStart.text="녹음 시작"
            btnStop.text="녹음 중지"

            btnStart.isVisible=true
            btnStop.isVisible=true
            btnStop.isEnabled=false
        }
        else if(isPlayable && isRecord){
            // play & record both
            btnStart.text="재생/녹음 시작"
            btnStop.text="재생/녹음 중지"

            btnStart.isVisible=true
            btnStop.isVisible=true
            btnStop.isEnabled=false
        }
        else{
            // cannot play & record
            btnStart.isVisible=false
            btnStop.isVisible=false
        }
    }


    private fun requestPermission() {
        if(Build.VERSION.SDK_INT >= 33){
            ActivityCompat.requestPermissions(
                this@MainActivity,
                arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.INTERNET
                ),
                REQUEST_PERMISSION_CODE
            )
        } else{
            ActivityCompat.requestPermissions(
                this@MainActivity,
                arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.INTERNET
                ),
                REQUEST_PERMISSION_CODE
            )
        }

    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(Build.VERSION.SDK_INT >= 33){
            when (requestCode) {
                REQUEST_PERMISSION_CODE -> if (grantResults.size > 0) {
                    val RecordPermission = grantResults[0] == PackageManager.PERMISSION_GRANTED
                    val ReadStoragePermission = grantResults[1] == PackageManager.PERMISSION_GRANTED
                    val InternetPermission = grantResults[2] == PackageManager.PERMISSION_GRANTED
                    if (RecordPermission && ReadStoragePermission && InternetPermission) {
                        Toast.makeText(this@MainActivity, "Permission Granted", Toast.LENGTH_LONG)
                            .show()
                    } else {
                        Toast.makeText(this@MainActivity, "Permission Denied", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }else{
            when (requestCode) {
                REQUEST_PERMISSION_CODE -> if (grantResults.size > 0) {
                    val StoragePermission = grantResults[0] == PackageManager.PERMISSION_GRANTED
                    val RecordPermission = grantResults[1] == PackageManager.PERMISSION_GRANTED
                    val ReadStoragePermission = grantResults[2] == PackageManager.PERMISSION_GRANTED
                    val InternetPermission = grantResults[2] == PackageManager.PERMISSION_GRANTED
                    if (StoragePermission && RecordPermission && ReadStoragePermission && InternetPermission) {
                        Toast.makeText(this@MainActivity, "Permission Granted", Toast.LENGTH_LONG)
                            .show()
                    } else {
                        Toast.makeText(this@MainActivity, "Permission Denied", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

    }

    fun checkPermission(): Boolean {
        if(Build.VERSION.SDK_INT >= 33){
            val result3: Int = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.INTERNET
            )
            val result2: Int = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.READ_MEDIA_AUDIO
            )
            val result1: Int = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.RECORD_AUDIO
            )
            return result2 == PackageManager.PERMISSION_GRANTED  && result1 == PackageManager.PERMISSION_GRANTED && result3 == PackageManager.PERMISSION_GRANTED
        } else {
            val result3: Int = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.INTERNET
            )
            val result2: Int = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            val result: Int = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            val result1: Int = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.RECORD_AUDIO
            )
            return result2 == PackageManager.PERMISSION_GRANTED && result == PackageManager.PERMISSION_GRANTED && result1 == PackageManager.PERMISSION_GRANTED && result3 == PackageManager.PERMISSION_GRANTED
        }
    }
    companion object{
        var isRecord = false
        var isRecordStart = false;
        var isLoop = false
        var isStopStreamRequested = false
    }
}