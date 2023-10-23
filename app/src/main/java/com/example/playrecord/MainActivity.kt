package com.example.playrecord

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import java.net.Socket
import kotlin.math.ceil


class MainActivity : AppCompatActivity() {
    // Permission req code
    val REQUEST_PERMISSION_CODE = 1
    val REQUEST_FILE_SELECT = 2
    val REQUEST_INTERNET = 3
    val REQUST_PHONE_STATE =4

    // thread for recorder / player
    lateinit var recorder: WavRecorder
    lateinit var player : AudioPlayer

    // button for start / stop
    lateinit var btnStart : Button
    lateinit var btnStop : Button

    // variable for player
    var filePath = "init"
    var audioName = ""

    var pastSR = 0
    var sampleRate = 48000
    var isStereo = true

    // variable for recorder
    var isAuto = false

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

//        player = AudioPlayer(filePath, sampleRate, isStereo)
//        player.start() // make/run audio player thread

//        val groupChannel = findViewById<RadioGroup>(R.id.radioGroupChannel)
//        groupChannel.setOnCheckedChangeListener { _, i ->
//            if(i==R.id.radioStereo){
//                isStereo = true
//                //player.interrupt()
//                player = AudioPlayer(filePath, sampleRate, isStereo)
//                player.start()
//                player.requestStop()
//                player.interrupt()
//            }
//            if(i==R.id.radioMono) {
//                isStereo = false
//                //player.interrupt()
//                player = AudioPlayer(filePath, sampleRate, isStereo)
//                player.start()
//                player.requestStop()
//                player.interrupt()
//            }
//        }

        //********* Start / Stop button *************//
        btnStart = findViewById<Button>(R.id.startDual)
        btnStop = findViewById<Button>(R.id.stopDual)

        val groupMic = findViewById<RadioGroup>(R.id.radioGroupMic)
        groupMic.setOnCheckedChangeListener { _, i ->
            if(i==R.id.radioAuto){
                isAuto=true
                isLoop = false
                chkLoop.isEnabled=false
                setBtnCond()
            }
            if(i==R.id.radioManual) {
                isAuto=false
                setBtnCond()
            }
        }

        btnStart.setOnClickListener {
            if(isAuto){
                val ct = ClientThread()
                ct.start()
            }
            else{
                if(isRecord) {
                    if(receivedFileName != null){
                        findViewById<EditText>(R.id.recordFileName).setText(receivedFileName!!)
                    }
                    recorder.startRecording()
                    btnStart.isEnabled=false
                    btnStop.isEnabled=true
                }
                if(isPlayable){
                    //player.interrupt()
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
                    playEnd = false
                    Log.d("PLAYER", "onCreate: start Player ${playEnd}")
                    if(isLoop){
                        btnStop.isEnabled=true
                        btnStart.isEnabled=false
                    }
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
    inner class ClientThread: Thread(){
        private var output_msg: String? = null
        private var input_msg: String? = null

        val ip = "192.168.0.75"
        val port = 3000

        var dataSize = -1

        override fun run(){
            val socket = Socket(ip, port)

            val outStream = socket.getOutputStream()
            val inStream = socket.getInputStream()
            var isInit = true
            try{
                if(isPlayable && isInit){
                    output_msg ="player"
                    outStream.write(output_msg!!.toByteArray())
                    isInit = false
                }
                if(isRecord && isInit){
                    output_msg="recorder"
                    outStream.write(output_msg!!.toByteArray())
                    isInit = false
                }
                while(true){
                    if(socket.isConnected){
                        while(dataSize < 1) {
                            dataSize = inStream.available()
                        }
                        val dataArr = ByteArray(dataSize)
                        dataSize = -1
                        inStream.read(dataArr)
                        input_msg = String(dataArr)

                        Log.d("SOCKET", "run: $input_msg")

                        val msg = input_msg!!.split("_")
                        val chkMsg = "${msg[0]}_${msg[1]}"

                        if(chkMsg == "is_ready"){
                            output_msg = "ack"
                            outStream.write(output_msg!!.toByteArray())
                        }
                        if(chkMsg == "start_play"){
                            playEnd = false
                            playEnd_second = false
                            runOnUiThread{
                                isAuto = false
                                btnStart.performClick()
                                isAuto = true
                            }
                            Log.d("PLAYER", "run b: ${playEnd}")
                            while(!playEnd){
                                continue
                            }
                            output_msg = "end"
                            outStream.write(output_msg!!.toByteArray())
                            Log.d("PLAYER", "run a: ${playEnd}")
                        }

                        if(chkMsg== "start_record"){
                            runOnUiThread{
                                isAuto = false
                                val fileName = msg.drop(2).joinToString("_")
                                Log.d("SOCKET", "run: $fileName")
                                receivedFileName = fileName
                                btnStart.performClick()
                                isAuto = true
                            }

                        }
                        if(chkMsg == "stop_record"){
                            runOnUiThread {
                                isAuto = false
                                btnStop.performClick()
                                isAuto = true
                            }
                            output_msg = "stopped"
                            outStream.write(output_msg!!.toByteArray())
                        }
                    }

                }
            }
            catch(e:Exception){
                e.printStackTrace()
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
        if(isAuto){
            if(isPlayable && !isRecord){
                // only play audio
                btnStart.text="자동 재생 시작"
                btnStop.text="자동 재생 중지"

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
                btnStart.text="자동 녹음 시작"
                btnStop.text="자동 녹음 중지"

                btnStart.isVisible=true
                //btnStop.isVisible=true
                btnStop.isEnabled=false
            }
            else if(isPlayable && isRecord){
                // play & record both
                btnStart.text="자동 재생/녹음 시작"
                btnStop.text="자동 재생/녹음 중지"

                btnStart.isVisible=true
                //btnStop.isVisible=true
                btnStop.isEnabled=false
            }
            else{
                // cannot play & record
                btnStart.isVisible=false
                btnStop.isVisible=false
            }
        }else{
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
    }


    private fun requestPermission() {
        if(Build.VERSION.SDK_INT >= 33){
            ActivityCompat.requestPermissions(
                this@MainActivity,
                arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.INTERNET,
                    Manifest.permission.READ_PHONE_STATE
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
                    Manifest.permission.INTERNET,
                    Manifest.permission.READ_PHONE_STATE
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
                    val PhoneState = grantResults[3] == PackageManager.PERMISSION_GRANTED
                    if (RecordPermission && ReadStoragePermission && InternetPermission && PhoneState) {
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
                    val PhoneState = grantResults[3] == PackageManager.PERMISSION_GRANTED
                    if (StoragePermission && RecordPermission && ReadStoragePermission && InternetPermission && PhoneState) {
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
            val result4: Int = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.READ_PHONE_STATE
            )
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
            return result2 == PackageManager.PERMISSION_GRANTED  && result1 == PackageManager.PERMISSION_GRANTED && result3 == PackageManager.PERMISSION_GRANTED && result4 == PackageManager.PERMISSION_GRANTED
        } else {
            val result4: Int = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.READ_PHONE_STATE
            )
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
            return result2 == PackageManager.PERMISSION_GRANTED && result == PackageManager.PERMISSION_GRANTED && result1 == PackageManager.PERMISSION_GRANTED && result3 == PackageManager.PERMISSION_GRANTED  && result4 == PackageManager.PERMISSION_GRANTED
        }
    }
    companion object{
        var isPlayable = false
        var isPlaying = false
        var isRecord = false
        var isRecordStart = false;
        var isLoop = false
        var isStopStreamRequested = false
        var receivedFileName: String? = null
        var playEnd = false
        var playEnd_second = false

    }
}