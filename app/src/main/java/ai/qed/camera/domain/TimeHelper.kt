package ai.qed.camera.domain

object TimeHelper {
    fun formatSecondsToReadableStringRepresentation(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return buildString {
            if (hours > 0) append("${hours}h ")
            if (minutes > 0 || hours > 0) append("${minutes}m ")
            append("${secs}s")
        }.trim()
    }
}