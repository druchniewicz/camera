package ai.qed.camera

import android.app.Activity
import android.app.ProgressDialog
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaActionSound
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.Executor
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CaptureMultipleImagesActivity : AppCompatActivity() {
    private lateinit var imageCapture: ImageCapture
    private lateinit var handler: Handler
    private lateinit var outputDirectory: File
    private lateinit var previewView: PreviewView
    private lateinit var shutterButton: Button
    private lateinit var modeInfoTextView: TextView
    private lateinit var savePhotosButton: Button
    private lateinit var cancelButton: Button
    private lateinit var shutterButtonProgressBar: ProgressBar
    private lateinit var photoIntervalLabel: TextView

    private var mode: String = MODE_PARAM_DEFAULT_VALUE
    private var captureInterval: Int = CAPTURE_INTERVAL_DEFAULT_VALUE
    private var maxPhotoCount: Int = MAX_PHOTO_COUNT_DEFAULT_VALUE
    private var maxSessionDuration: Int = MAX_SESSION_DURATION_DEFAULT_VALUE
    private var photoFormat: String = PHOTO_FORMAT_DEFAULT_VALUE
    private var photoCounter = 0
    private var isAutomaticMode = false
    private var shutterJob: Job? = null

    private val sound = MediaActionSound()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_capture_multiple_images)

        if (isCameraPermissionNotGranted()) {
            requestCameraPermission()
        } else {
            startApplication()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startApplication()
            } else {
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        sound.release()
        finish()
    }

    private fun isCameraPermissionNotGranted() : Boolean {
        return ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION_NAME) != PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(CAMERA_PERMISSION_NAME),
            REQUEST_CAMERA_PERMISSION_CODE
        )
    }

    private fun startApplication() {
        mode = getStringOrDefaultFromString(intent.getStringExtra(MODE_PARAM_KEY) ?: "", MODE_PARAM_DEFAULT_VALUE)
        captureInterval = getIntegerOrDefaultFromString(intent.getStringExtra(CAPTURE_INTERVAL_PARAM_KEY) ?: "", CAPTURE_INTERVAL_DEFAULT_VALUE)
        maxPhotoCount = getIntegerOrDefaultFromString(intent.getStringExtra(MAX_PHOTO_COUNT_PARAM_KEY) ?: "", MAX_PHOTO_COUNT_DEFAULT_VALUE)
        maxSessionDuration = getIntegerOrDefaultFromString(intent.getStringExtra(MAX_SESSION_DURATION_PARAM_KEY) ?: "", MAX_SESSION_DURATION_DEFAULT_VALUE)
        photoFormat = getStringOrDefaultFromString(intent.getStringExtra(PHOTO_FORMAT_PARAM_KEY) ?: "", PHOTO_FORMAT_DEFAULT_VALUE)

        sound.load(MediaActionSound.SHUTTER_CLICK)

        initializeUIElements()

        outputDirectory = getOutputDirectory()
        clearOutputDirector()

        startCamera()
        setupIntervalLabel()
        setupShutterButtonListeners()
        setupSaveButtonListener()
        setupCancelButtonListener()
        updateIntervalFieldsVisibility()
    }

    private fun initializeUIElements() {
        previewView = findViewById(R.id.preview)
        modeInfoTextView = findViewById(R.id.label_modeInfo)
        shutterButton = findViewById(R.id.btn_shutter)
        savePhotosButton = findViewById(R.id.btn_savePhotos)
        cancelButton = findViewById(R.id.btn_cancel)
        shutterButtonProgressBar = findViewById(R.id.progressBar_shutterBtn)
        photoIntervalLabel = findViewById(R.id.label_photoInterval)
    }

    private fun setupIntervalLabel() {
        if (isAutomaticMode) {
            photoIntervalLabel.text = getString(R.string.photo_interval_label, captureInterval)
            photoIntervalLabel.visibility = View.VISIBLE
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
                updateIntervalFieldsVisibility()

                if (isAutomaticMode) {
                    photoIntervalLabel.text = getString(R.string.photo_interval_label, captureInterval)
                    startImageCapture()
                }
            } catch (ex: Exception) {
                Log.e("CameraBinding", "Something went wrong during camera binding")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startImageCapture() {
        val handlerThread = HandlerThread("CameraBackground").apply { start() }
        handler = Handler(handlerThread.looper)

        takePicturesInSeries()
    }

    private fun takePicturesInSeries() {
        handler.postDelayed({
            if (photoCounter < maxPhotoCount) {
                takeSinglePicture()
                photoCounter++
                takePicturesInSeries()
            } else {
                handler.removeCallbacksAndMessages(null)
            }
        }, captureInterval * 1000L)
    }

    private fun takeSinglePicture() {
        val photoFile = File(outputDirectory, "${PHOTO_NAME_PREFIX}${System.currentTimeMillis()}.${photoFormat}")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        sound.play(MediaActionSound.SHUTTER_CLICK)

        imageCapture.takePicture(outputOptions, getExecutor(), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {}
            override fun onError(exception: ImageCaptureException) {}
        })
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

    private fun setupSaveButtonListener() {
        savePhotosButton.setOnClickListener {
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
    }

    private fun createPackageWithPhotos() : File {
        val zipFile = File(outputDirectory, "photos_${System.currentTimeMillis()}.zip")
        val files = outputDirectory.listFiles { file -> file.name.startsWith(PHOTO_NAME_PREFIX) }

        if (files != null && files.isNotEmpty()) {
            try {
                ZipOutputStream(zipFile.outputStream().buffered()).use { zipOut ->
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

    private fun getUriForFile(file: File) : Uri {
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
            finish()
        }
    }

    private fun toggleAutoMode() {
        isAutomaticMode = !isAutomaticMode
        updateModeText()
        updateIntervalFieldsVisibility()

        if (isAutomaticMode) {
            startImageCapture()
        } else {
            handler.removeCallbacksAndMessages(null)
        }
    }

    private fun updateModeText() {
        if (isAutomaticMode) {
            modeInfoTextView.text = getString(R.string.automatic_mode_label)
        } else {
            modeInfoTextView.text = getString(R.string.manual_mode_label)
        }
    }

    private fun updateIntervalFieldsVisibility() {
        val visibility = if (isAutomaticMode) View.VISIBLE else View.GONE
        photoIntervalLabel.visibility = visibility
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, PHOTOS_DIRECTORY_NAME).apply { mkdirs() }
        }

        return if (mediaDir != null && mediaDir.exists()) mediaDir else filesDir
    }

    private fun clearOutputDirector() {
        if (outputDirectory.exists()) {
            outputDirectory.listFiles()?.forEach { file ->
                file.delete()
            }
        }
    }

    private fun getIntegerOrDefaultFromString(value: String, defaultValue: Int) : Int {
        return if (value.isEmpty()) {
            defaultValue
        } else {
            value.toIntOrNull() ?: defaultValue
        }
    }

    private fun getStringOrDefaultFromString(value: String, defaultValue: String) : String {
        return value.ifEmpty {
            defaultValue
        }
    }

    private fun getExecutor(): Executor {
        return ContextCompat.getMainExecutor(this)
    }
}