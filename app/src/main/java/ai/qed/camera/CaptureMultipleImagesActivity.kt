package ai.qed.camera

import ai.qed.camera.databinding.ActivityCaptureMultipleImagesBinding
import ai.qed.camera.ui.ExitSessionDialog
import ai.qed.camera.ui.SaveSessionDialog
import android.Manifest
import android.app.Activity
import android.app.ProgressDialog
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.SoundPool
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class CaptureMultipleImagesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCaptureMultipleImagesBinding
    private lateinit var cameraConfig: CameraConfig
    private val cameraX = CameraX()

    private lateinit var handler: Handler
    private lateinit var outputDirectory: File
    private lateinit var soundPool: SoundPool

    private var photoCounter = 0
    private var isAutomaticMode = false
    private var shutterJob: Job? = null
    private var sessionTimeJob: Job? = null
    private var remainingSessionTime = 0
    private var isUnlimitedSession = false
    private var isSoundOn = true
    private var shutterSoundId: Int = 0
    private var remainingSessionTimeBeforePause: Int? = null
    private var elapsedTimeJob: Job? = null
    private var elapsedTimeInSeconds = 0
    private var elapsedTimeBeforePause: Int? = null
    private var isUnlimitedPhotoCount = false
    private var currentAzimuth: Double = 0.0
    private var currentPitch: Double = 0.0
    private var currentRoll: Double = 0.0
    private var sensorManager: SensorManager? = null
    private var rotationSensor: Sensor? = null

    private lateinit var locationProvider: LocationProvider

    private val rotationListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            event?.let {
                if (it.values != null && it.values.size >= 3) {
                    val rotationMatrix = FloatArray(9)
                    val orientationAngles = FloatArray(3)

                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientationAngles)

                    currentAzimuth = Math.toDegrees(orientationAngles[0].toDouble())
                    currentPitch = Math.toDegrees(orientationAngles[1].toDouble())
                    currentRoll = Math.toDegrees(orientationAngles[2].toDouble())
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityCaptureMultipleImagesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        locationProvider = LocationProvider(this)
        setupRotationSensor()

        if (isPermissionGranted(Manifest.permission.CAMERA)) {
            startApplicationWithLocationRequest()
        } else {
            requestCameraPermission()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_CAMERA_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    requestLocationPermission()
                } else {
                    finish()
                }
            }

            REQUEST_LOCATION_PERMISSION_CODE -> {
                startApplication()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        rotationSensor?.let { sensor ->
            sensorManager?.registerListener(
                rotationListener,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(rotationListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        locationProvider.stop()
        handler.removeCallbacksAndMessages(null)
        sessionTimeJob?.cancel()
        elapsedTimeJob?.cancel()
        soundPool.release()
        finish()
    }

    private fun setupRotationSensor() {
        sensorManager = getSystemService(SENSOR_SERVICE) as? SensorManager
        if (sensorManager != null) {
            rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        }
    }

    private fun startApplicationWithLocationRequest() {
        if (isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            startApplication()
        } else {
            requestLocationPermission()
        }
    }

    private fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            REQUEST_CAMERA_PERMISSION_CODE
        )
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            REQUEST_LOCATION_PERMISSION_CODE
        )
    }

    private fun startApplication() {
        cameraConfig = toCameraConfig(intent)
        locationProvider.start()

        isUnlimitedSession = cameraConfig.maxSessionDuration == ZERO
        isUnlimitedPhotoCount = cameraConfig.maxPhotoCount == ZERO

        initializeSoundPool()

        outputDirectory = getOutputDirectory()
        clearOutputDirectory()

        startCamera()
        setupSettingsButtonListener()
        setupVolumeButtonListener()
        setupShutterButtonListeners()
        setupSaveButtonListener()
        setupCancelButtonListener()
        startElapsedTimeCounter()
    }

    private fun initializeSoundPool() {
        soundPool = SoundPool.Builder().setMaxStreams(1).build()
        shutterSoundId = soundPool.load(this, R.raw.camera_shutter_sound, 1)
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, PHOTOS_DIRECTORY_NAME).apply { mkdirs() }
        }

        return if (mediaDir != null && mediaDir.exists()) mediaDir else filesDir
    }

    private fun clearOutputDirectory() {
        if (outputDirectory.exists()) {
            outputDirectory.listFiles()?.forEach { file ->
                file.delete()
            }
        }
    }

    private fun startCamera() {
        cameraX.initialize(
            this,
            binding.preview,
            {
                isAutomaticMode = cameraConfig.mode == MODE_PARAM_DEFAULT_VALUE

                updateModeText()

                if (isAutomaticMode) {
                    startImageCapture()
                }
            },
            {
                Log.e("CameraBinding", "Something went wrong during camera binding")
            }
        )

        if (!isUnlimitedSession) {
            setupSessionTimer()
            binding.labelSessionTime.visibility = View.VISIBLE
        } else {
            binding.labelSessionTime.visibility = View.GONE
        }
    }

    private fun startImageCapture() {
        val handlerThread = HandlerThread("CameraBackground").apply { start() }
        handler = Handler(handlerThread.looper)

        takePicturesInSeries()
    }

    private fun setupSettingsButtonListener() {
        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }
    }

    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_camera_settings, null)
        val captureIntervalInput = dialogView.findViewById<EditText>(R.id.input_captureInterval)
        val modeSwitch = dialogView.findViewById<SwitchCompat>(R.id.switch_mode)

        captureIntervalInput.setText(cameraConfig.captureInterval.toString())
        modeSwitch.isChecked = isAutomaticMode

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_dialog_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save_dialog_button)) { _, _ ->
                isAutomaticMode = modeSwitch.isChecked
                cameraConfig.captureInterval =
                    captureIntervalInput.text.toString().toIntOrNull() ?: cameraConfig.captureInterval
                updateModeText()
                restartCameraIfNeeded()
            }
            .setNegativeButton(getString(R.string.cancel_button_label), null)
            .create()

        dialog.show()
    }

    private fun setupVolumeButtonListener() {
        binding.btnVolume.setOnClickListener {
            isSoundOn = !isSoundOn
            updateVolumeIcon()
        }
    }

    private fun updateVolumeIcon() {
        if (isSoundOn) {
            binding.btnVolume.setImageResource(R.drawable.ic_volume_on)
        } else {
            binding.btnVolume.setImageResource(R.drawable.ic_volume_off)
        }
    }

    private fun setupShutterButtonListeners() {
        binding.btnShutter.setOnClickListener {
            takeSinglePicture()
        }

        binding.btnShutter.setOnLongClickListener {
            binding.progressBarShutterBtn.visibility = View.VISIBLE
            binding.progressBarShutterBtn.progress = 0

            shutterJob = CoroutineScope(Dispatchers.Main).launch {
                for (i in 1..100) {
                    delay(30)
                    binding.progressBarShutterBtn.progress = i
                    binding.progressBarShutterBtn.invalidate()
                }

                toggleAutoMode()
                binding.progressBarShutterBtn.visibility = View.GONE
            }
            true
        }

        binding.btnShutter.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                shutterJob?.cancel()
                binding.progressBarShutterBtn.visibility = View.GONE
            }
            false
        }
    }

    private fun takeSinglePicture() {
        if (!isUnlimitedPhotoCount && photoCounter >= cameraConfig.maxPhotoCount) {
            Toast.makeText(
                this,
                getString(R.string.max_photo_limit_reached_toast_message),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val photoFile = File(
            outputDirectory,
            "${PHOTO_NAME_PREFIX}${System.currentTimeMillis()}.${cameraConfig.photoFormat}"
        )

        applyVisualAndAudioEffects()

        cameraX.takePicture(
            photoFile.absolutePath,
            {
                photoCounter++
                updatePhotosTakenLabel()
                saveGeoExifData(photoFile)
            },
            {
                Toast.makeText(
                    this@CaptureMultipleImagesActivity,
                    getString(R.string.take_photo_error_toast_message),
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    private fun applyVisualAndAudioEffects() {
        ensureVolumeIsNotMuted()
        if (isSoundOn) {
            soundPool.play(shutterSoundId, 1f, 1f, 0, 0, 1f)
        }
        triggerShutterEffect()
    }

    private fun ensureVolumeIsNotMuted() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                1,
                0
            )
        }
    }

    private fun triggerShutterEffect() {
        runOnUiThread {
            binding.shutterEffectView.visibility = View.VISIBLE
            binding.shutterEffectView.alpha = 1f
            binding.shutterEffectView.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction { binding.shutterEffectView.visibility = View.GONE }
                .start()
        }
    }

    private fun toggleAutoMode() {
        isAutomaticMode = !isAutomaticMode
        updateModeText()

        restartCameraIfNeeded()
    }

    private fun updatePhotosTakenLabel() {
        val baseText = getString(R.string.photos_taken_label, photoCounter)

        if (!isUnlimitedPhotoCount && photoCounter >= cameraConfig.maxPhotoCount) {
            binding.labelPhotosTaken.text = "$baseText ${getString(R.string.limit_reached_label)}"
        } else {
            binding.labelPhotosTaken.text = baseText
        }
    }

    private fun saveGeoExifData(photoFile: File) {
        ExifDataSaver.saveLocationAttributes(photoFile, locationProvider.lastKnownLocation, currentAzimuth, currentPitch, currentRoll)
    }

    private fun takePicturesInSeries() {
        handler.removeCallbacksAndMessages(null)

        if (!isAutomaticMode) return

        handler.postDelayed({
            if (isUnlimitedPhotoCount || photoCounter < cameraConfig.maxPhotoCount) {
                takeSinglePicture()
                takePicturesInSeries()
            } else {
                handler.removeCallbacksAndMessages(null)
            }
        }, cameraConfig.captureInterval * 1000L)
    }

    private fun setupSessionTimer() {
        remainingSessionTime = cameraConfig.maxSessionDuration
        sessionTimeJob = CoroutineScope(Dispatchers.Main).launch {
            while (remainingSessionTime >= 0) {
                binding.labelSessionTime.text = getString(R.string.session_time_label, remainingSessionTime)
                delay(1000)
                remainingSessionTime--
            }
            saveAndExit()
        }
    }

    private fun saveAndExit() {
        sessionTimeJob?.cancel()
        elapsedTimeJob?.cancel()
        handleSaveButton()
    }

    private fun startElapsedTimeCounter() {
        elapsedTimeJob = CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                binding.labelElapsedTime.text =
                    getString(R.string.elapsed_time_label, elapsedTimeInSeconds)
                elapsedTimeInSeconds++
                delay(1000)
            }
        }
    }

    private fun setupSaveButtonListener() {
        binding.btnSavePhotos.setOnClickListener {
            showSaveConfirmationDialog()
        }
    }

    private fun showSaveConfirmationDialog() {
        remainingSessionTimeBeforePause = remainingSessionTime
        elapsedTimeBeforePause = elapsedTimeInSeconds
        sessionTimeJob?.cancel()
        elapsedTimeJob?.cancel()
        handler.removeCallbacksAndMessages(null)

        SaveSessionDialog.show(
            this,
            { handleSaveButton() },
            { resumeSession() }
        )
    }

    private fun resumeSession() {
        if (!isUnlimitedSession && remainingSessionTimeBeforePause != null) {
            resumeSessionTimer(remainingSessionTimeBeforePause!!)
        }

        if (isAutomaticMode) {
            takePicturesInSeries()
        }

        elapsedTimeJob = CoroutineScope(Dispatchers.Main).launch {
            var currentElapsedTime = elapsedTimeBeforePause ?: elapsedTimeInSeconds
            while (true) {
                binding.labelElapsedTime.text =
                    getString(R.string.elapsed_time_label, currentElapsedTime)
                currentElapsedTime++
                elapsedTimeInSeconds = currentElapsedTime
                delay(1000)
            }
        }
    }

    private fun resumeSessionTimer(remainingTime: Int) {
        sessionTimeJob = CoroutineScope(Dispatchers.Main).launch {
            remainingSessionTime = remainingTime
            while (remainingSessionTime >= 0) {
                binding.labelSessionTime.text = getString(R.string.session_time_label, remainingSessionTime)
                delay(1000)
                remainingSessionTime--
            }
            saveAndExit()
        }
    }

    private fun handleSaveButton() {
        handler.removeCallbacksAndMessages(null)

        val progressDialog = ProgressDialog(this).apply {
            setMessage(getString(R.string.saving_toast_message))
            setCancelable(false)
            show()
        }

        CoroutineScope(Dispatchers.IO).launch {
            val outputPackage = PhotoZipper.zip(outputDirectory)
            withContext(Dispatchers.Main) {
                progressDialog.dismiss()
                returnAnswer(outputPackage)
            }
        }
    }

    private fun returnAnswer(file: File) {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
        val uri = getUriForFile(file)
        addItem(intent, uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    private fun getUriForFile(file: File): Uri {
        return FileProvider.getUriForFile(applicationContext, "ai.qed.camera.fileprovider", file)
    }

    private fun addItem(intent: Intent, uri: Uri) {
        intent.putExtra("value", uri)
        if (intent.clipData == null) {
            intent.clipData = ClipData.newRawUri(null, uri)
        } else {
            intent.clipData?.addItem(ClipData.Item(uri))
        }
    }

    private fun setupCancelButtonListener() {
        binding.btnCancel.setOnClickListener {
            showExitConfirmationDialog()
        }
    }

    private fun showExitConfirmationDialog() {
        remainingSessionTimeBeforePause = remainingSessionTime
        elapsedTimeBeforePause = elapsedTimeInSeconds
        sessionTimeJob?.cancel()
        elapsedTimeJob?.cancel()
        handler.removeCallbacksAndMessages(null)

        ExitSessionDialog.show(
            this,
            { finish() },
            { resumeSession() }
        )
    }

    private fun restartCameraIfNeeded() {
        handler.removeCallbacksAndMessages(null)
        if (isAutomaticMode) {
            startImageCapture()
        }
    }

    private fun updateModeText() {
        binding.labelModeInfo.text =
            if (isAutomaticMode) getString(R.string.automatic_mode_label) else getString(R.string.manual_mode_label)
    }
}