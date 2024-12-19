package ai.qed.camera.domain

object StorageHelper {
    fun formatMBToReadableStringRepresentation(sizeInMB: Double): String {
        if (sizeInMB <= 0) return "0 bytes"

        val units = arrayOf("bytes", "KB", "MB", "GB", "TB")
        var sizeInBytes = sizeInMB * 1024 * 1024
        var unitIndex = 0

        while (sizeInBytes >= 1024 && unitIndex < units.size - 1) {
            sizeInBytes /= 1024
            unitIndex++
        }

        return String.format("%.2f %s", sizeInBytes, units[unitIndex])
    }
}
