package ai.qed.camera.data

import android.content.Intent

private const val MODE_PARAM_KEY = "mode"
private const val CAPTURE_INTERVAL_PARAM_KEY = "captureInterval"
private const val MAX_PHOTO_COUNT_PARAM_KEY = "maxPhotoCount"
private const val MAX_SESSION_DURATION_PARAM_KEY = "maxSessionDuration"
private const val PHOTO_FORMAT_PARAM_KEY = "photoFormat"
private const val QUESTION_NAME_PREFIX_KEY = "questionNamePrefix"
private const val MAX_NUMBER_OF_PACKAGES_KEY = "maxNumberOfPackages"

private const val MODE_PARAM_DEFAULT_VALUE = "automatic"
private const val CAPTURE_INTERVAL_DEFAULT_VALUE = 5
private const val MAX_PHOTO_COUNT_DEFAULT_VALUE = 0
private const val MAX_SESSION_DURATION_DEFAULT_VALUE = 0
private const val PHOTO_FORMAT_DEFAULT_VALUE = "jpg"
private const val QUESTION_NAME_PREFIX_DEFAULT_VALUE = "part"
private const val MAX_NUMBER_OF_PACKAGES_DEFAULT_VALUE = 100

data class CameraConfig(
    var mode: String,
    var captureInterval: Int,
    val maxPhotoCount: Int,
    val maxSessionDuration: Int,
    val photoFormat: String,
    val questionNamePrefix: String,
    val maxNumberOfPackages: Int
)

fun CameraConfig.isAutomaticMode(): Boolean {
    return mode == MODE_PARAM_DEFAULT_VALUE
}

fun toCameraConfig(intent: Intent): CameraConfig {
    val mode = getStringOrDefaultFromString(
        intent.getStringExtra(MODE_PARAM_KEY),
        MODE_PARAM_DEFAULT_VALUE
    )
    val captureInterval = getIntegerOrDefaultFromString(
        intent.getStringExtra(CAPTURE_INTERVAL_PARAM_KEY),
        CAPTURE_INTERVAL_DEFAULT_VALUE
    )
    val maxPhotoCount = getIntegerOrDefaultFromString(
        intent.getStringExtra(MAX_PHOTO_COUNT_PARAM_KEY),
        MAX_PHOTO_COUNT_DEFAULT_VALUE
    )
    val maxSessionDuration = getIntegerOrDefaultFromString(
        intent.getStringExtra(MAX_SESSION_DURATION_PARAM_KEY),
        MAX_SESSION_DURATION_DEFAULT_VALUE
    )
    val photoFormat = getStringOrDefaultFromString(
        intent.getStringExtra(PHOTO_FORMAT_PARAM_KEY),
        PHOTO_FORMAT_DEFAULT_VALUE
    )
    val questionNamePrefix = getStringOrDefaultFromString(
        intent.getStringExtra(QUESTION_NAME_PREFIX_KEY),
        QUESTION_NAME_PREFIX_DEFAULT_VALUE
    )
    val maxNumberOfPackages = getIntegerOrDefaultFromString(
        intent.getStringExtra(MAX_NUMBER_OF_PACKAGES_KEY),
        MAX_NUMBER_OF_PACKAGES_DEFAULT_VALUE
    )

    return CameraConfig(
        mode,
        captureInterval,
        maxPhotoCount,
        maxSessionDuration,
        photoFormat,
        questionNamePrefix,
        maxNumberOfPackages
    )
}

private fun getStringOrDefaultFromString(value: String?, defaultValue: String): String {
    return if (value.isNullOrBlank()) {
        defaultValue
    } else {
        value
    }
}

private fun getIntegerOrDefaultFromString(value: String?, defaultValue: Int): Int {
    return if (value.isNullOrBlank()) {
        defaultValue
    } else {
        value.toIntOrNull() ?: defaultValue
    }
}
