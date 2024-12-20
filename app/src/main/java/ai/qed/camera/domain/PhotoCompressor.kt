package ai.qed.camera.domain

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

object PhotoCompressor {
    fun compress(photo: File) {
        backupExifData(photo.absolutePath)
        compressPhoto(photo)
        restoreExifData(photo.absolutePath)
    }

    private fun compressPhoto(photo: File) {
        try {
            val bitmap = rotateImageIfNeeded(photo.absolutePath)
            FileOutputStream(photo).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.WEBP, 75, outputStream)
            }
            bitmap.recycle()
        } catch (e: Throwable) {
            // ignore
        }
    }

    private fun backupExifData(imagePath: String) {
        try {
            val exif = ExifInterface(imagePath)
            for ((key, _) in exifDataBackup) {
                exifDataBackup[key] = exif.getAttribute(key)
            }
        } catch (e: Throwable) {
            // ignore
        }
    }

    private fun rotateImageIfNeeded(imagePath: String): Bitmap {
        val exif = ExifInterface(imagePath)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

        val bitmap = BitmapFactory.decodeFile(imagePath)
        var rotatedBitmap = bitmap

        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> {
                rotatedBitmap = rotateBitmap(bitmap, 90f)
            }
            ExifInterface.ORIENTATION_ROTATE_180 -> {
                rotatedBitmap = rotateBitmap(bitmap, 180f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> {
                rotatedBitmap = rotateBitmap(bitmap, 270f)
            }
        }

        return rotatedBitmap
    }

    private fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun restoreExifData(imagePath: String) {
        try {
            val exif = ExifInterface(imagePath)
            for ((key, value) in exifDataBackup) {
                exif.setAttribute(key, value)
            }
            exif.saveAttributes()
        } catch (e: Throwable) {
            // ignore
        }
    }

    private val exifDataBackup = mutableMapOf<String, String?>(
        ExifInterface.TAG_DATETIME to null,
        ExifInterface.TAG_DATETIME_ORIGINAL to null,
        ExifInterface.TAG_DATETIME_DIGITIZED to null,
        ExifInterface.TAG_OFFSET_TIME to null,
        ExifInterface.TAG_OFFSET_TIME_ORIGINAL to null,
        ExifInterface.TAG_OFFSET_TIME_DIGITIZED to null,
        ExifInterface.TAG_SUBSEC_TIME to null,
        ExifInterface.TAG_SUBSEC_TIME_ORIGINAL to null,
        ExifInterface.TAG_SUBSEC_TIME_DIGITIZED to null,
        ExifInterface.TAG_IMAGE_DESCRIPTION to null,
        ExifInterface.TAG_MAKE to null,
        ExifInterface.TAG_MODEL to null,
        ExifInterface.TAG_SOFTWARE to null,
        ExifInterface.TAG_ARTIST to null,
        ExifInterface.TAG_COPYRIGHT to null,
        ExifInterface.TAG_MAKER_NOTE to null,
        ExifInterface.TAG_USER_COMMENT to null,
        ExifInterface.TAG_IMAGE_UNIQUE_ID to null,
        ExifInterface.TAG_CAMERA_OWNER_NAME to null,
        ExifInterface.TAG_BODY_SERIAL_NUMBER to null,
        ExifInterface.TAG_GPS_ALTITUDE to null,
        ExifInterface.TAG_GPS_ALTITUDE_REF to null,
        ExifInterface.TAG_GPS_DATESTAMP to null,
        ExifInterface.TAG_GPS_TIMESTAMP to null,
        ExifInterface.TAG_GPS_LATITUDE to null,
        ExifInterface.TAG_GPS_LATITUDE_REF to null,
        ExifInterface.TAG_GPS_LONGITUDE to null,
        ExifInterface.TAG_GPS_LONGITUDE_REF to null,
        ExifInterface.TAG_GPS_SATELLITES to null,
        ExifInterface.TAG_GPS_STATUS to null,
        ExifInterface.TAG_ORIENTATION to null
    )
}
