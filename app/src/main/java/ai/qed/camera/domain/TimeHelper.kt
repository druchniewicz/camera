package ai.qed.camera.domain

object TimeHelper {
    fun formatSecondsToReadableStringRepresentation(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return "${hours}h ${minutes}m ${secs}s"
    }
}