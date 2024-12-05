package ai.qed.camera.ui

import ai.qed.camera.domain.CameraX
import ai.qed.camera.domain.DeviceOrientationProvider
import ai.qed.camera.domain.LocationProvider
import ai.qed.camera.R
import ai.qed.camera.domain.ResultIntentHelper
import ai.qed.camera.domain.clearFilesDir
import ai.qed.camera.databinding.ActivityCaptureMultipleImagesBinding
import ai.qed.camera.domain.toCameraConfig
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.distinctUntilChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CaptureMultipleImagesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCaptureMultipleImagesBinding

    private val viewmodel: CaptureMultipleImagesViewModel by viewModels()

    private var shutterJob: Job? = null

    private lateinit var locationProvider: LocationProvider
    private lateinit var deviceOrientationProvider: DeviceOrientationProvider
    private lateinit var cameraX: CameraX
    private lateinit var mediaPlayer: MediaPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityCaptureMultipleImagesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        disableBackButton()
        hideSystemBars()

        if (savedInstanceState == null) {
            clearFilesDir()
            viewmodel.setCameraConfig(toCameraConfig(intent))
        }

        locationProvider = LocationProvider(this)
        deviceOrientationProvider = DeviceOrientationProvider(getSystemService(SENSOR_SERVICE) as? SensorManager)
        lifecycle.addObserver(locationProvider)
        lifecycle.addObserver(deviceOrientationProvider)
        cameraX = CameraX(locationProvider, deviceOrientationProvider)
        mediaPlayer = MediaPlayer.create(this, R.raw.camera_shutter_sound)

        setupUi()
        setupObservers()
        setupCamera()
    }

    override fun onPause() {
        super.onPause()
        viewmodel.setCameraState(false)
        mediaPlayer.release()
    }

    private fun disableBackButton() {
        onBackPressedDispatcher.addCallback(object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })
    }

    private fun hideSystemBars() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { view, windowInsets ->
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            ViewCompat.onApplyWindowInsets(view, windowInsets)
        }
    }

    private fun setupUi() {
        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }
        binding.btnVolume.setOnClickListener {
            viewmodel.toggleSound()
        }
        binding.btnSavePhotos.setOnClickListener {
            showSaveConfirmationDialog()
        }
        binding.btnCancel.setOnClickListener {
            showExitConfirmationDialog()
        }
        binding.labelSessionTime.isVisible = viewmodel.isSessionTimeLimited()
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

                viewmodel.setCameraMode(viewmodel.isAutoMode.value != true)
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

    private fun setupObservers() {
        viewmodel.files.distinctUntilChanged().observe(this) { files ->
            ResultIntentHelper.returnIntent(this, files)
        }
        viewmodel.timer.distinctUntilChanged().observe(this) { time ->
            val maxSessionDuration = viewmodel.getMaxSessionDuration()
            binding.labelElapsedTime.text = getString(R.string.elapsed_time_label, time)
            binding.labelSessionTime.text = getString(R.string.session_time_label, maxSessionDuration - time)
            if (maxSessionDuration != 0 && maxSessionDuration - time <= 0) {
                viewmodel.stopTimer()
                viewmodel.stopTakingPhotos()
                saveSession()
            }
        }
        viewmodel.photoCounter.distinctUntilChanged().observe(this) { photoCounter ->
            binding.labelPhotosTaken.text = getString(R.string.photos_taken_label, photoCounter)
        }
        viewmodel.error.distinctUntilChanged().observe(this) { error ->
            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
        }
        viewmodel.isAutoMode.distinctUntilChanged().observe(this) { isAutoMode ->
            if (isAutoMode) {
                binding.labelModeInfo.text = getString(R.string.automatic_mode_label)
                takePicturesInSeries()
            } else {
                viewmodel.stopTakingPhotos()
                binding.labelModeInfo.text = getString(R.string.manual_mode_label)
            }
        }
        viewmodel.isSoundOn.distinctUntilChanged().observe(this) { isSoundOn ->
            binding.btnVolume.setImageResource(
                if (isSoundOn) R.drawable.ic_volume_on else R.drawable.ic_volume_off
            )
        }
        viewmodel.isCameraInitialized.distinctUntilChanged().observe(this) { isCameraInitialized ->
            if (isCameraInitialized) {
                viewmodel.startTimer()
                takePicturesInSeries()
            } else {
                viewmodel.stopTimer()
                viewmodel.stopTakingPhotos()
            }
        }
    }

    private fun setupCamera() {
        cameraX.initialize(
            this,
            binding.preview,
            { viewmodel.setCameraState(true) },
            {
                Toast.makeText(this, "Something went wrong during camera binding", Toast.LENGTH_LONG).show()
                finish()
            }
        )
    }

    private fun takeSinglePicture() {
        if (viewmodel.isSoundOn.value == true) {
            mediaPlayer.start()
        }
        binding.shutterEffectView.shutterEffect()
        viewmodel.takePicture(cameraX, filesDir)
    }

    private fun takePicturesInSeries() {
        if (viewmodel.isAutoMode.value == true && viewmodel.isCameraInitialized.value == true) {
            viewmodel.startTakingPictures {
                takeSinglePicture()
            }
        }
    }

    private fun showSettingsDialog() {
        viewmodel.stopTimer()
        viewmodel.stopTakingPhotos()

        SettingsDialog.show(
            this,
            viewmodel.getCaptureInterval().toString(),
            viewmodel.isAutoMode.value == true,
            { captureInterval, isAutomaticMode ->
                viewmodel.setCameraMode(isAutomaticMode)
                viewmodel.setCaptureInterval(captureInterval.toIntOrNull())
                resumeSession()
            },
            { resumeSession() }
        )
    }

    private fun showSaveConfirmationDialog() {
        viewmodel.stopTimer()
        viewmodel.stopTakingPhotos()

        SaveSessionDialog.show(
            this,
            { saveSession() },
            { resumeSession() }
        )
    }

    private fun showExitConfirmationDialog() {
        viewmodel.stopTimer()
        viewmodel.stopTakingPhotos()

        ExitSessionDialog.show(
            this,
            { finish() },
            { resumeSession() }
        )
    }

    private fun saveSession() {
        viewmodel.zipFiles(filesDir)
        ProgressDialog.showOn(this, viewmodel.isLoading, supportFragmentManager)
    }

    private fun resumeSession() {
        viewmodel.startTimer()
        takePicturesInSeries()
    }
}