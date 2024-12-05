package ai.qed.camera

import android.content.Intent

data class CameraConfig(
    var mode: String,
    var captureInterval: Int,
    val maxPhotoCount: Int,
    val maxSessionDuration: Int,
    val photoFormat: String,
    val questionNamePrefix: String,
    val maxNumberOfPackages: Int
)

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
