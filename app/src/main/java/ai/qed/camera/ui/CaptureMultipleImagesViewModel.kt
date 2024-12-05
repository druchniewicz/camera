package ai.qed.camera.ui

import ai.qed.camera.CameraConfig
import ai.qed.camera.CameraX
import ai.qed.camera.MODE_PARAM_DEFAULT_VALUE
import ai.qed.camera.PHOTO_NAME_PREFIX
import ai.qed.camera.PhotoZipper
import ai.qed.camera.R
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class CaptureMultipleImagesViewModel : ViewModel() {
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _files = MutableLiveData<List<File>>()
    val files: LiveData<List<File>> = _files

    private val _timer = MutableLiveData(0)
    val timer: LiveData<Int> = _timer

    private val _photoCounter = MutableLiveData(0)
    val photoCounter: LiveData<Int> = _photoCounter

    private val _error = MutableLiveData<Int>()
    val error: LiveData<Int> = _error

    private val _isAutoMode = MutableLiveData<Boolean>()
    val isAutoMode: LiveData<Boolean> = _isAutoMode

    private val _isSoundOn = MutableLiveData(true)
    val isSoundOn: LiveData<Boolean> = _isSoundOn

    private val _isCameraInitialized = MutableLiveData(false)
    val isCameraInitialized: LiveData<Boolean> = _isCameraInitialized

    private lateinit var cameraConfig: CameraConfig
    private var timerJob: Job? = null
    private var photosJob: Job? = null

    fun setCameraConfig(cameraConfig: CameraConfig) {
        this.cameraConfig = cameraConfig
        setCameraMode(cameraConfig.mode == MODE_PARAM_DEFAULT_VALUE)
    }

    fun setCameraState(isCameraInitialized: Boolean) {
        _isCameraInitialized.value = isCameraInitialized
    }

    fun setCameraMode(isAutoMode: Boolean) {
        _isAutoMode.value = isAutoMode
    }

    fun toggleSound() {
        _isSoundOn.value = isSoundOn.value != true
    }

    fun startTimer() {
        timerJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(1000)
                _timer.postValue(_timer.value?.plus(1))
            }
        }
    }

    fun stopTimer() {
        timerJob?.cancel()
    }

    fun startTakingPictures(onTakePicture: () -> Unit) {
        photosJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(cameraConfig.captureInterval * 1000L)
                withContext(Dispatchers.Main) {
                    onTakePicture()
                }
            }
        }
    }

    fun stopTakingPhotos() {
        photosJob?.cancel()
    }

    fun takePicture(cameraX: CameraX, storage: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val photoFile = File(
                storage,
                "$PHOTO_NAME_PREFIX${System.currentTimeMillis()}.${cameraConfig.photoFormat}"
            )

            cameraX.takePicture(
                photoFile.absolutePath,
                { _photoCounter.postValue(_photoCounter.value?.plus(1)) },
                { _error.postValue(R.string.take_photo_error_toast_message) }
            )
        }
    }

    fun zipFiles(storage: File) {
        stopTimer()
        stopTakingPhotos()

        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            val files = PhotoZipper.zip(
                storage,
                cameraConfig.questionNamePrefix,
                cameraConfig.maxNumberOfPackages
            )
            _isLoading.postValue(false)
            _files.postValue(files)
        }
    }

    fun isSessionTimeLimited(): Boolean {
        return cameraConfig.maxSessionDuration != 0
    }

    fun getCaptureInterval(): Int {
        return cameraConfig.captureInterval
    }

    fun setCaptureInterval(captureInterval: Int?) {
        if (captureInterval != null) {
            cameraConfig.captureInterval = captureInterval
        }
    }

    fun getMaxSessionDuration(): Int {
        return cameraConfig.maxSessionDuration
    }
}
