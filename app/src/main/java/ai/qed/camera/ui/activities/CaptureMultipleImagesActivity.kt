package ai.qed.camera.ui.activities

import ai.qed.camera.domain.CameraX
import ai.qed.camera.data.DeviceOrientationProvider
import ai.qed.camera.data.LocationProvider
import ai.qed.camera.R
import ai.qed.camera.domain.ResultIntentHelper
import ai.qed.camera.domain.clearFilesDir
import ai.qed.camera.databinding.ActivityCaptureMultipleImagesBinding
import ai.qed.camera.data.toCameraConfig
import ai.qed.camera.domain.TimeHelper
import ai.qed.camera.ui.CaptureMultipleImagesViewModel
import ai.qed.camera.ui.dialogs.ExitSessionDialog
import ai.qed.camera.ui.dialogs.ProgressDialog
import ai.qed.camera.ui.dialogs.SaveSessionDialog
import ai.qed.camera.ui.dialogs.SettingsDialog
import ai.qed.camera.ui.shutterEffect
import android.annotation.SuppressLint
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.os.Bundle
import android.view.MotionEvent
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

class CaptureMultipleImagesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCaptureMultipleImagesBinding

    private val viewmodel: CaptureMultipleImagesViewModel by viewModels()

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

    @SuppressLint("ClickableViewAccessibility")
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
            viewmodel.startProgress()
            true
        }
        binding.btnShutter.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                viewmodel.stopProgress()
            }
            false
        }
    }

    private fun setupObservers() {
        viewmodel.files.observe(this) { files ->
            if (!files.isConsumed()) {
                files.consume()
                ResultIntentHelper.returnIntent(this, files.value)
            }
        }
        viewmodel.timer.observe(this) { time ->
            val maxSessionDuration = viewmodel.getMaxSessionDuration()
            binding.labelElapsedTime.text = getString(
                R.string.elapsed_time_label,
                TimeHelper.formatSecondsToReadableStringRepresentation(time)
            )
            binding.labelSessionTime.text = getString(
                R.string.session_time_label,
                TimeHelper.formatSecondsToReadableStringRepresentation(maxSessionDuration - time)
            )
            if (maxSessionDuration != 0 && maxSessionDuration - time <= 0) {
                pauseSession()
                saveSession()
            }
        }
        viewmodel.photoCounter.observe(this) { photoCounter ->
            binding.labelPhotosTaken.text = getString(R.string.photos_taken_label, photoCounter)
            if (viewmodel.isPhotoCountLimited() && photoCounter == viewmodel.getMaxPhotoCount()) {
                pauseSession()
                saveSession()
            }
        }
        viewmodel.error.observe(this) { error ->
            if (!error.isConsumed()) {
                error.consume()
                if (error.value != null) {
                    Toast.makeText(this, error.value, Toast.LENGTH_LONG).show()
                }
            }
        }
        viewmodel.isAutoMode.observe(this) { isAutoMode ->
            if (isAutoMode) {
                binding.labelModeInfo.text = getString(R.string.automatic_mode_label)
                takePicturesInSeries()
            } else {
                viewmodel.stopTakingPhotos()
                binding.labelModeInfo.text = getString(R.string.manual_mode_label)
            }
        }
        viewmodel.isSoundOn.observe(this) { isSoundOn ->
            binding.btnVolume.setImageResource(
                if (isSoundOn) R.drawable.ic_volume_on else R.drawable.ic_volume_off
            )
        }
        viewmodel.isCameraInitialized.observe(this) { isCameraInitialized ->
            if (isCameraInitialized) {
                resumeSession()
            } else {
                pauseSession()
            }
        }
        viewmodel.progress.observe(this) { progress ->
            if (progress == 100) {
                viewmodel.setCameraMode(viewmodel.isAutoMode.value != true)
                binding.progressBarShutterBtn.progress = 0
            } else {
                binding.progressBarShutterBtn.progress = progress
            }
            binding.progressBarShutterBtn.invalidate()
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
        pauseSession()
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
        pauseSession()
        SaveSessionDialog.show(
            this,
            { saveSession() },
            { resumeSession() }
        )
    }

    private fun showExitConfirmationDialog() {
        pauseSession()
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

    private fun pauseSession() {
        viewmodel.stopTimer()
        viewmodel.stopTakingPhotos()
    }

    private fun resumeSession() {
        if (viewmodel.isSessionEnding.value == false) {
            viewmodel.startTimer()
            takePicturesInSeries()
        }
    }
}