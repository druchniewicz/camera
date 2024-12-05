package ai.qed.camera.domain

// parameter keys
const val MODE_PARAM_KEY = "mode"
const val CAPTURE_INTERVAL_PARAM_KEY = "captureInterval"
const val MAX_PHOTO_COUNT_PARAM_KEY = "maxPhotoCount"
const val MAX_SESSION_DURATION_PARAM_KEY = "maxSessionDuration"
const val PHOTO_FORMAT_PARAM_KEY = "photoFormat"
const val QUESTION_NAME_PREFIX_KEY = "questionNamePrefix"
const val MAX_NUMBER_OF_PACKAGES_KEY = "maxNumberOfPackages"

const val ZERO = 0

// parameter default values
const val MODE_PARAM_DEFAULT_VALUE = "automatic"
const val CAPTURE_INTERVAL_DEFAULT_VALUE = 5
const val MAX_PHOTO_COUNT_DEFAULT_VALUE = ZERO
const val MAX_SESSION_DURATION_DEFAULT_VALUE = ZERO
const val PHOTO_FORMAT_DEFAULT_VALUE = "jpg"
const val QUESTION_NAME_PREFIX_DEFAULT_VALUE = "part"
const val MAX_NUMBER_OF_PACKAGES_DEFAULT_VALUE = 100

// other
const val PHOTO_NAME_PREFIX = "photo_"
const val MAX_PACKAGE_SIZE_IN_MEGABYTES = 240