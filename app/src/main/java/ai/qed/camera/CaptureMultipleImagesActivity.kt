package ai.qed.camera

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
import android.location.Location
import android.media.AudioManager
import android.media.SoundPool
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.Executor
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.floor

class CaptureMultipleImagesActivity : AppCompatActivity() {
    private lateinit var imageCapture: ImageCapture
    private lateinit var handler: Handler
    private lateinit var outputDirectory: File
    private lateinit var previewView: PreviewView
    private lateinit var shutterButton: ImageButton
    private lateinit var modeInfoTextView: TextView
    private lateinit var savePhotosButton: ImageButton
    private lateinit var cancelButton: ImageButton
    private lateinit var shutterButtonProgressBar: ProgressBar
    private lateinit var settingsButton: ImageButton
    private lateinit var sessionTimeLabel: TextView
    private lateinit var volumeButton: ImageButton
    private lateinit var soundPool: SoundPool
    private lateinit var shutterEffectView: View
    private lateinit var elapsedTimeTextView: TextView
    private lateinit var photosTakenTextView: TextView

    private var mode: String = MODE_PARAM_DEFAULT_VALUE
    private var captureInterval: Int = CAPTURE_INTERVAL_DEFAULT_VALUE
    private var maxPhotoCount: Int = MAX_PHOTO_COUNT_DEFAULT_VALUE
    private var maxSessionDuration: Int = MAX_SESSION_DURATION_DEFAULT_VALUE
    private var photoFormat: String = PHOTO_FORMAT_DEFAULT_VALUE
    private var photoCounter = 0
    private var isAutomaticMode = false
    private var shutterJob: Job? = null
    private var sessionTimeJob: Job? = null
    private var remainingSessionTime = maxSessionDuration
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
        setContentView(R.layout.activity_capture_multiple_images)

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
        mode = getStringOrDefaultFromString(
            intent.getStringExtra(MODE_PARAM_KEY) ?: "",
            MODE_PARAM_DEFAULT_VALUE
        )
        captureInterval = getIntegerOrDefaultFromString(
            intent.getStringExtra(CAPTURE_INTERVAL_PARAM_KEY) ?: "",
            CAPTURE_INTERVAL_DEFAULT_VALUE
        )
        maxPhotoCount = getIntegerOrDefaultFromString(
            intent.getStringExtra(MAX_PHOTO_COUNT_PARAM_KEY) ?: "",
            MAX_PHOTO_COUNT_DEFAULT_VALUE
        )
        maxSessionDuration = getIntegerOrDefaultFromString(
            intent.getStringExtra(MAX_SESSION_DURATION_PARAM_KEY) ?: "",
            MAX_SESSION_DURATION_DEFAULT_VALUE
        )
        photoFormat = getStringOrDefaultFromString(
            intent.getStringExtra(PHOTO_FORMAT_PARAM_KEY) ?: "",
            PHOTO_FORMAT_DEFAULT_VALUE
        )

        isUnlimitedSession = maxSessionDuration == ZERO
        isUnlimitedPhotoCount = maxPhotoCount == ZERO

        initializeUIElements()
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

    private fun initializeUIElements() {
        previewView = findViewById(R.id.preview)
        modeInfoTextView = findViewById(R.id.label_modeInfo)
        shutterButton = findViewById(R.id.btn_shutter)
        savePhotosButton = findViewById(R.id.btn_savePhotos)
        cancelButton = findViewById(R.id.btn_cancel)
        shutterButtonProgressBar = findViewById(R.id.progressBar_shutterBtn)
        settingsButton = findViewById(R.id.btn_settings)
        sessionTimeLabel = findViewById(R.id.label_sessionTime)
        volumeButton = findViewById(R.id.btn_volume)
        shutterEffectView = findViewById(R.id.shutter_effect_view)
        elapsedTimeTextView = findViewById(R.id.label_elapsedTime)
        photosTakenTextView = findViewById(R.id.label_photosTaken)
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
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setTargetRotation(windowManager.defaultDisplay.rotation)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)

                isAutomaticMode = mode == MODE_PARAM_DEFAULT_VALUE

                updateModeText()

                if (isAutomaticMode) {
                    startImageCapture()
                }
            } catch (ex: Exception) {
                Log.e("CameraBinding", "Something went wrong during camera binding")
            }
        }, ContextCompat.getMainExecutor(this))

        if (!isUnlimitedSession) {
            setupSessionTimer()
            sessionTimeLabel.visibility = View.VISIBLE
        } else {
            sessionTimeLabel.visibility = View.GONE
        }
    }

    private fun startImageCapture() {
        val handlerThread = HandlerThread("CameraBackground").apply { start() }
        handler = Handler(handlerThread.looper)

        takePicturesInSeries()
    }

    private fun setupSettingsButtonListener() {
        settingsButton.setOnClickListener {
            showSettingsDialog()
        }
    }

    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_camera_settings, null)
        val captureIntervalInput = dialogView.findViewById<EditText>(R.id.input_captureInterval)
        val modeSwitch = dialogView.findViewById<SwitchCompat>(R.id.switch_mode)

        captureIntervalInput.setText(captureInterval.toString())
        modeSwitch.isChecked = isAutomaticMode

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_dialog_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save_dialog_button)) { _, _ ->
                isAutomaticMode = modeSwitch.isChecked
                captureInterval =
                    captureIntervalInput.text.toString().toIntOrNull() ?: captureInterval
                updateModeText()
                restartCameraIfNeeded()
            }
            .setNegativeButton(getString(R.string.cancel_button_label), null)
            .create()

        dialog.show()
    }

    private fun setupVolumeButtonListener() {
        volumeButton.setOnClickListener {
            isSoundOn = !isSoundOn
            updateVolumeIcon()
        }
    }

    private fun updateVolumeIcon() {
        if (isSoundOn) {
            volumeButton.setImageResource(R.drawable.ic_volume_on)
        } else {
            volumeButton.setImageResource(R.drawable.ic_volume_off)
        }
    }

    private fun setupShutterButtonListeners() {
        shutterButton.setOnClickListener {
            takeSinglePicture()
        }

        shutterButton.setOnLongClickListener {
            shutterButtonProgressBar.visibility = View.VISIBLE
            shutterButtonProgressBar.progress = 0

            shutterJob = CoroutineScope(Dispatchers.Main).launch {
                for (i in 1..100) {
                    delay(30)
                    shutterButtonProgressBar.progress = i
                    shutterButtonProgressBar.invalidate()
                }

                toggleAutoMode()
                shutterButtonProgressBar.visibility = View.GONE
            }
            true
        }

        shutterButton.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                shutterJob?.cancel()
                shutterButtonProgressBar.visibility = View.GONE
            }
            false
        }
    }

    private fun takeSinglePicture() {
        if (!isUnlimitedPhotoCount && photoCounter >= maxPhotoCount) {
            Toast.makeText(
                this,
                getString(R.string.max_photo_limit_reached_toast_message),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val photoFile = File(
            outputDirectory,
            "${PHOTO_NAME_PREFIX}${System.currentTimeMillis()}.${photoFormat}"
        )
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        applyVisualAndAudioEffects()

        imageCapture.takePicture(
            outputOptions,
            getExecutor(),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    photoCounter++
                    updatePhotosTakenLabel()
                    saveGeoExifData(photoFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(
                        this@CaptureMultipleImagesActivity,
                        getString(R.string.take_photo_error_toast_message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
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
            shutterEffectView.visibility = View.VISIBLE
            shutterEffectView.alpha = 1f
            shutterEffectView.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction { shutterEffectView.visibility = View.GONE }
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

        if (!isUnlimitedPhotoCount && photoCounter >= maxPhotoCount) {
            photosTakenTextView.text = "$baseText ${getString(R.string.limit_reached_label)}"
        } else {
            photosTakenTextView.text = baseText
        }
    }

    private fun saveGeoExifData(photoFile: File) {
        getLocation { location ->
            if (location != null) {
                addGeoExifData(photoFile, location)
            }
        }
    }

    private fun getLocation(onLocationFetched: (Location?) -> Unit) {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val locationRequest = LocationRequest.create().apply {
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                interval = 1000
                fastestInterval = 500
                numUpdates = 1
            }

            val fusedLocationProvider = LocationServices.getFusedLocationProviderClient(this)
            fusedLocationProvider.requestLocationUpdates(
                locationRequest,
                object : LocationCallback() {
                    override fun onLocationResult(locationResult: LocationResult) {
                        fusedLocationProvider.removeLocationUpdates(this)
                        onLocationFetched(locationResult.lastLocation)
                    }
                },
                Looper.getMainLooper()
            )
        } else {
            onLocationFetched(null)
        }
    }

    private fun addGeoExifData(photoFile: File, location: Location) {
        try {
            val exif = ExifInterface(photoFile.absolutePath)

            val latitude = location.latitude
            val longitude = location.longitude
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, convertToExifFormat(latitude))
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, if (latitude >= 0) "N" else "S")
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, convertToExifFormat(longitude))
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, if (longitude >= 0) "E" else "W")

            val gyroData = "Azimuth: $currentAzimuth, Pitch: $currentPitch, Roll: $currentRoll"
            exif.setAttribute(ExifInterface.TAG_USER_COMMENT, gyroData)

            exif.saveAttributes()
        } catch (e: IOException) {
            Log.e("CaptureMultiplePhotos", "Failed to save EXIF data", e)
        }
    }

    private fun takePicturesInSeries() {
        handler.removeCallbacksAndMessages(null)

        if (!isAutomaticMode) return

        handler.postDelayed({
            if (isUnlimitedPhotoCount || photoCounter < maxPhotoCount) {
                takeSinglePicture()
                takePicturesInSeries()
            } else {
                handler.removeCallbacksAndMessages(null)
            }
        }, captureInterval * 1000L)
    }

    private fun setupSessionTimer() {
        remainingSessionTime = maxSessionDuration
        sessionTimeJob = CoroutineScope(Dispatchers.Main).launch {
            while (remainingSessionTime >= 0) {
                sessionTimeLabel.text = getString(R.string.session_time_label, remainingSessionTime)
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
                elapsedTimeTextView.text =
                    getString(R.string.elapsed_time_label, elapsedTimeInSeconds)
                elapsedTimeInSeconds++
                delay(1000)
            }
        }
    }

    private fun setupSaveButtonListener() {
        savePhotosButton.setOnClickListener {
            showSaveConfirmationDialog()
        }
    }

    private fun showSaveConfirmationDialog() {
        remainingSessionTimeBeforePause = remainingSessionTime
        elapsedTimeBeforePause = elapsedTimeInSeconds
        sessionTimeJob?.cancel()
        elapsedTimeJob?.cancel()
        handler.removeCallbacksAndMessages(null)

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.save_dialog_title))
            .setMessage(getString(R.string.save_dialog_message))
            .setPositiveButton(getString(R.string.yes_button_label)) { _, _ ->
                handleSaveButton()
            }
            .setNegativeButton(getString(R.string.cancel_button_label)) { dialog, _ ->
                dialog.dismiss()
                resumeSession()
            }
            .create()

        dialog.show()
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
                elapsedTimeTextView.text =
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
                sessionTimeLabel.text = getString(R.string.session_time_label, remainingSessionTime)
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
            val outputPackage = createPackageWithPhotos()
            withContext(Dispatchers.Main) {
                progressDialog.dismiss()
                returnAnswer(outputPackage)
            }
        }
    }

    private fun createPackageWithPhotos(): File {
        val zipFile = File(outputDirectory, "photos_${System.currentTimeMillis()}.zip")
        val files = outputDirectory.listFiles { file -> file.name.startsWith(PHOTO_NAME_PREFIX) }

        if (files != null && files.isNotEmpty()) {
            try {
                ZipOutputStream(zipFile.outputStream().buffered()).use { zipOut ->
                    zipOut.setLevel(Deflater.NO_COMPRESSION)
                    for (file in files) {
                        FileInputStream(file).use { fis ->
                            val entry = ZipEntry(file.name)
                            zipOut.putNextEntry(entry)
                            fis.copyTo(zipOut)
                            zipOut.closeEntry()
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e("CaptureMultiplePhotos", "Error when creating zip package", e)
            }
        }

        return zipFile
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
        cancelButton.setOnClickListener {
            showExitConfirmationDialog()
        }
    }

    private fun showExitConfirmationDialog() {
        remainingSessionTimeBeforePause = remainingSessionTime
        elapsedTimeBeforePause = elapsedTimeInSeconds
        sessionTimeJob?.cancel()
        elapsedTimeJob?.cancel()
        handler.removeCallbacksAndMessages(null)

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.exit_dialog_title))
            .setMessage(getString(R.string.exit_dialog_message))
            .setPositiveButton(getString(R.string.yes_button_label)) { _, _ ->
                finish()
            }
            .setNegativeButton(getString(R.string.cancel_button_label)) { dialog, _ ->
                dialog.dismiss()
                resumeSession()
            }
            .create()

        dialog.show()
    }

    private fun restartCameraIfNeeded() {
        handler.removeCallbacksAndMessages(null)
        if (isAutomaticMode) {
            startImageCapture()
        }
    }

    private fun updateModeText() {
        modeInfoTextView.text =
            if (isAutomaticMode) getString(R.string.automatic_mode_label) else getString(R.string.manual_mode_label)
    }

    private fun getIntegerOrDefaultFromString(value: String, defaultValue: Int): Int {
        return if (value.isEmpty()) {
            defaultValue
        } else {
            value.toIntOrNull() ?: defaultValue
        }
    }

    private fun getStringOrDefaultFromString(value: String, defaultValue: String): String {
        return value.ifEmpty {
            defaultValue
        }
    }

    private fun getExecutor(): Executor {
        return ContextCompat.getMainExecutor(this)
    }

    private fun convertToExifFormat(coordinate: Double): String {
        val absolute = abs(coordinate)
        val degrees = floor(absolute).toInt()
        val minutes = floor((absolute - degrees) * 60).toInt()
        val seconds = ((absolute - degrees - minutes / 60.0) * 3600 * 1000).toInt()
        return "$degrees/1,$minutes/1,$seconds/1000"
    }
}