package ai.qed.camera.ui

import ai.qed.camera.data.CameraConfig
import ai.qed.camera.domain.CameraX
import ai.qed.camera.domain.PhotoZipper
import ai.qed.camera.domain.PhotoZipper.PHOTO_NAME_PREFIX
import ai.qed.camera.data.isAutomaticMode
import ai.qed.camera.domain.Consumable
import ai.qed.camera.domain.PhotoCompressor
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class CaptureMultipleImagesViewModel : ViewModel() {
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _files = MutableLiveData<Consumable<List<File>>>()
    val files: LiveData<Consumable<List<File>>> = _files

    private val _timer = MutableLiveData(0)
    val timer: LiveData<Int> = _timer

    private val _photoCounter = MutableLiveData(0)
    val photoCounter: LiveData<Int> = _photoCounter

    private val _error = MutableLiveData<Consumable<String?>>()
    val error: LiveData<Consumable<String?>> = _error

    private val _isAutoMode = MutableLiveData<Boolean>()
    val isAutoMode: LiveData<Boolean> = _isAutoMode

    private val _isSoundOn = MutableLiveData(true)
    val isSoundOn: LiveData<Boolean> = _isSoundOn

    private val _isCameraInitialized = MutableLiveData(false)
    val isCameraInitialized: LiveData<Boolean> = _isCameraInitialized

    private val _progress= MutableLiveData(0)
    val progress: LiveData<Int> = _progress

    private val photoQueue = MutableSharedFlow<File>()
    private var compressedPhotos = 0

    private lateinit var cameraConfig: CameraConfig
    private var timerJob: Job? = null
    private var photosJob: Job? = null
    private var progressJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            photoQueue.collect { photo ->
                PhotoCompressor.compress(photo)
                compressedPhotos += 1
            }
        }
    }

    fun setCameraConfig(cameraConfig: CameraConfig) {
        this.cameraConfig = cameraConfig
        setCameraMode(cameraConfig.isAutomaticMode())
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
                "$PHOTO_NAME_PREFIX${System.currentTimeMillis()}.webp"
            )

            cameraX.takePicture(
                photoFile.absolutePath,
                onImageSaved = { file ->
                    _photoCounter.postValue(_photoCounter.value?.plus(1))
                    viewModelScope.launch {
                        photoQueue.emit(file)
                    }
                },
                onImageProcessingError = { message ->
                    _photoCounter.postValue(_photoCounter.value?.minus(1))
                    _error.postValue(Consumable(message))
                },
                onError = { message -> _error.postValue(Consumable(message)) }
            )
        }
    }

    fun startProgress() {
        progressJob = viewModelScope.launch(Dispatchers.IO) {
            for (i in 1..100) {
                delay(30)
                _progress.postValue(i)
            }
        }
    }

    fun stopProgress() {
        progressJob?.cancel()
        _progress.postValue(0)
    }

    fun zipFiles(storage: File) {
        stopTimer()
        stopTakingPhotos()

        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)

            var compressionInProgress = true
            while (compressionInProgress) {
                if (compressedPhotos >= photoCounter.value!!) {
                    compressionInProgress = false
                } else {
                    delay(1000)
                }
            }

            val files = PhotoZipper.zip(
                storage,
                cameraConfig.questionNamePrefix,
                cameraConfig.maxNumberOfPackages
            )
            _isLoading.postValue(false)
            _files.postValue(Consumable(files))
        }
    }

    fun isSessionTimeLimited(): Boolean = cameraConfig.maxSessionDuration != 0

    fun getCaptureInterval(): Int = cameraConfig.captureInterval

    fun setCaptureInterval(captureInterval: Int?) {
        if (captureInterval != null) {
            cameraConfig.captureInterval = captureInterval
        }
    }

    fun getMaxSessionDuration():Int = cameraConfig.maxSessionDuration

    fun isPhotoCountLimited(): Boolean = cameraConfig.maxPhotoCount != 0

    fun getMaxPhotoCount(): Int = cameraConfig.maxPhotoCount
}
