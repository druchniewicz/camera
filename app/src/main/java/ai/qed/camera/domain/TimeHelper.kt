package ai.qed.camera.domain

object TimeHelper {
    fun formatSecondsToReadableStringRepresentation(seconds: Int): String {
        if (seconds == 0) return "0s"

        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return buildString {
            if (hours > 0) append("${hours}h ")
            if (minutes > 0) append("${minutes}m ")
            if ((minutes > 0 && secs in 1..59) || (minutes == 0 && secs > 0)) append("${secs}s")
        }.trim()
    }
}