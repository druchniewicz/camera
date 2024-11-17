package ai.qed.camera

// parameter keys
const val MODE_PARAM_KEY = "mode"
const val CAPTURE_INTERVAL_PARAM_KEY = "captureInterval"
const val MAX_PHOTO_COUNT_PARAM_KEY = "maxPhotoCount"
const val MAX_SESSION_DURATION_PARAM_KEY = "maxSessionDuration"
const val PHOTO_FORMAT_PARAM_KEY = "photoFormat"

const val ZERO = 0

// parameter default values
const val MODE_PARAM_DEFAULT_VALUE = "automatic"
const val CAPTURE_INTERVAL_DEFAULT_VALUE = 5
const val MAX_PHOTO_COUNT_DEFAULT_VALUE = ZERO
const val MAX_SESSION_DURATION_DEFAULT_VALUE = ZERO
const val PHOTO_FORMAT_DEFAULT_VALUE = "jpg"

// other
const val PHOTO_NAME_PREFIX = "photo_"
const val PHOTOS_DIRECTORY_NAME = "CameraApp"
const val CAMERA_PERMISSION_NAME = "android.permission.CAMERA"
const val REQUEST_CAMERA_PERMISSION_CODE = 1001
const val REQUEST_LOCATION_PERMISSION_CODE = 1002