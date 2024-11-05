package ai.qed.camera

import android.media.MediaActionSound
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.Executor
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CaptureMultipleImagesActivity : AppCompatActivity() {
    private lateinit var mode: String
    private lateinit var captureInterval: String
    private lateinit var maxPhotoCount: String
    private lateinit var maxSessionDuration: String
    private lateinit var photoFormat: String

    private lateinit var imageCapture: ImageCapture
    private lateinit var handler: Handler
    private lateinit var outputDirectory: File
    private lateinit var previewView: PreviewView
    private lateinit var shutterButton: Button
    private lateinit var modeInfoTextView: TextView
    private lateinit var savePhotosButton: Button
    private lateinit var cancelButton: Button
    private lateinit var shutterButtonProgressBar: ProgressBar
    private lateinit var appearanceParametersMap: Map<String, String>
    private lateinit var photoIntervalLabel: TextView
    private lateinit var photoIntervalInput: EditText

    private var photoCounter = 0
    private var isAutomaticMode = false
    private var appearance: String? = null
    private var shutterJob: Job? = null
    private var capturePhotoInterval = 3

    private val sound = MediaActionSound()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_capture_multiple_images)

        mode = intent.getStringExtra(MODE) ?: ""
        captureInterval = intent.getStringExtra(CAPTURE_INTERVAL) ?: ""
        maxPhotoCount = intent.getStringExtra(MAX_PHOTO_COUNT) ?: ""
        maxSessionDuration = intent.getStringExtra(MAX_SESSION_DURATION) ?: ""
        photoFormat = intent.getStringExtra(PHOTO_FORMAT) ?: ""

        sound.load(MediaActionSound.SHUTTER_CLICK)

        initializeUIElements()

        appearance = intent.getStringExtra("appearance")
        appearanceParametersMap = buildAppearanceParametersMap(appearance)
        capturePhotoInterval = getIntegerValueFromParam("capturePhotoInterval", capturePhotoInterval)

        outputDirectory = getOutputDirectory()
        clearOutputDirector()

        startCamera()
        setupIntervalInput()
        setupShutterButtonListeners()
        setupCreateButtonListener()
        setupCancelButtonListener()

        updateIntervalFieldsVisibility()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        sound.release()
        finish()
    }

    private fun initializeUIElements() {
        previewView = findViewById(R.id.preview)
        modeInfoTextView = findViewById(R.id.mode_info)
        shutterButton = findViewById(R.id.btn_shutter)
        savePhotosButton = findViewById(R.id.btn_savePhotos)
        cancelButton = findViewById(R.id.btn_cancel)
        shutterButtonProgressBar = findViewById(R.id.progressBar_shutterBtn)
        photoIntervalLabel = findViewById(R.id.photo_interval_label)
        photoIntervalInput = findViewById(R.id.photo_interval_input)
    }

    private fun setupIntervalInput() {
        if (isAutomaticMode) {
            photoIntervalLabel.visibility = View.VISIBLE
            photoIntervalInput.visibility = View.VISIBLE

            photoIntervalInput.setText(capturePhotoInterval.toString())
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

                val cameraMode = getStringValueFromParam("mode", "automatic")
                isAutomaticMode = cameraMode == "automatic"

                updateModeText()
                updateIntervalFieldsVisibility()

                if (isAutomaticMode) {
                    photoIntervalInput.setText(capturePhotoInterval.toString())
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
        val maxPhotoCount = getIntegerValueFromParam("maxPhotoCount", 500)
        handler.postDelayed({
            if (photoCounter < maxPhotoCount) {
                takeSinglePicture()
                photoCounter++
                takePicturesInSeries()
            } else {
                handler.removeCallbacksAndMessages(null)
            }
        }, capturePhotoInterval * 1000L)
    }

    private fun takeSinglePicture() {
        val photoExtension = getStringValueFromParam("photoFormat", "jpg")
        val photoFile = File(outputDirectory, "photo_${System.currentTimeMillis()}.${photoExtension}")
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

    private fun setupCreateButtonListener() {
        savePhotosButton.setOnClickListener {
            createPackageWithPhotos()
            finish()
        }
    }

    private fun createPackageWithPhotos() {
        val zipFileName = File(outputDirectory, "photos_${System.currentTimeMillis()}.zip")
        val files = outputDirectory.listFiles { file -> file.name.startsWith("photo_") }

        if (files != null && files.isNotEmpty()) {
            try {
                ZipOutputStream(zipFileName.outputStream().buffered()).use { zipOut ->
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
            modeInfoTextView.text = "[Automatic Mode]"
        } else {
            modeInfoTextView.text = "[Manual Mode]"
        }
    }

    private fun updateIntervalFieldsVisibility() {
        val visibility = if (isAutomaticMode) View.VISIBLE else View.GONE
        photoIntervalLabel.visibility = visibility
        photoIntervalInput.visibility = visibility
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, "Collect").apply { mkdirs() }
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

    private fun buildAppearanceParametersMap(appearance: String?): Map<String, String> {
        val paramsPrefix = "multi-image("
        val paramsSuffix = ")"
        if (appearance != null && appearance.startsWith(paramsPrefix) && appearance.endsWith(paramsSuffix)) {
            val rawParams = appearance.substringAfter(paramsPrefix).substringBeforeLast(paramsSuffix)
            return rawParams.split(",").associate {
                val (key, value) = it.split("=")
                key to value
            }
        }

        return emptyMap()
    }

    private fun getIntegerValueFromParam(paramName: String, defaultValue: Int) : Int {
        val paramStringValue = appearanceParametersMap[paramName]
        return if (paramStringValue == null) {
            defaultValue
        } else {
            paramStringValue.toIntOrNull() ?: defaultValue
        }
    }

    private fun getStringValueFromParam(paramName: String, defaultValue: String) : String {
        val paramStringValue = appearanceParametersMap[paramName]
        return paramStringValue ?: defaultValue
    }

    private fun getExecutor(): Executor {
        return ContextCompat.getMainExecutor(this)
    }
}