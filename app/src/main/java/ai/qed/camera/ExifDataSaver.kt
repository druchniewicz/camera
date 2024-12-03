package ai.qed.camera

import android.location.Location
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException
import kotlin.math.abs
import kotlin.math.floor

object ExifDataSaver {
    fun saveLocationAttributes(
        file: File,
        location: Location?,
        azimuth: Double,
        pitch: Double,
        roll: Double
    ) {
        try {
            val latitude = location?.latitude
            val longitude = location?.longitude

            ExifInterface(file.absolutePath).apply {
                if (latitude != null) {
                    setAttribute(ExifInterface.TAG_GPS_LATITUDE, convertToExifFormat(latitude))
                    setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, if (latitude >= 0) "N" else "S")
                }

                if (longitude != null) {
                    setAttribute(ExifInterface.TAG_GPS_LONGITUDE, convertToExifFormat(longitude))
                    setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, if (longitude >= 0) "E" else "W")
                }

                setAttribute(
                    ExifInterface.TAG_USER_COMMENT,
                    "Azimuth: $azimuth, Pitch: $pitch, Roll: $roll"
                )

                saveAttributes()
            }
        } catch (e: IOException) {
            Log.e("CaptureMultiplePhotos", "Failed to save EXIF data", e)
        }
    }

    private fun convertToExifFormat(coordinate: Double): String {
        val absolute = abs(coordinate)
        val degrees = floor(absolute).toInt()
        val minutes = floor((absolute - degrees) * 60).toInt()
        val seconds = ((absolute - degrees - minutes / 60.0) * 3600 * 1000).toInt()

        return "$degrees/1,$minutes/1,$seconds/1000"
    }
}
