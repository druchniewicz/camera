package ai.qed.camera

import android.location.Location
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ExifDataSaverTest {
    @Test
    fun `location is correctly converted and saved to exif data`() {
        listOf(
            LocationTestData(40.748817, -73.985428, exifLatitude = "40/1,44/1,55741/1000", exifLongitude = "73/1,59/1,7540/1000"),
            LocationTestData(48.858844, 2.294351, exifLatitude = "48/1,51/1,31838/1000", exifLongitude = "2/1,17/1,39663/1000"),
            LocationTestData(-33.868820, 151.209296, exifLatitude = "33/1,52/1,7751/1000", exifLongitude = "151/1,12/1,33465/1000"),
        ).forEach {
            val photo = File.createTempFile("sample", ".jpg")

            val location = Location("test_provider").apply {
                this.latitude = it.latitude
                this.longitude = it.longitude
            }

            ExifDataSaver.saveLocationAttributes(photo, location, 0.0, 0.0, 0.0)

            val exif = ExifInterface(photo)

            assertThat(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE), equalTo(it.exifLatitude))
            assertThat(exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE), equalTo(it.exifLongitude))
        }
    }

    @Test
    fun `cardinal directions are calculated and saved to exif data`() {
        listOf(
            LocationTestData(40.748817, -73.985428, exifLatitudeRef = "N", exifLongitudeRef = "W"),  // New York
            LocationTestData(48.858844, 2.294351, exifLatitudeRef = "N", exifLongitudeRef = "E"),    // Paris
            LocationTestData(0.0, 0.0, exifLatitudeRef = "N", exifLongitudeRef = "E"),               // Equator and Prime Meridian
            LocationTestData(-40.0, -73.0, exifLatitudeRef = "S", exifLongitudeRef = "W"),           // Southern Hemisphere
            LocationTestData(90.0, 0.0, exifLatitudeRef = "N", exifLongitudeRef = "E"),              // North Pole
            LocationTestData(-90.0, 0.0, exifLatitudeRef = "S", exifLongitudeRef = "E"),             // South Pole
            LocationTestData(0.0, 180.0, exifLatitudeRef = "N", exifLongitudeRef = "E"),             // Equator and 180° Longitude (International Date Line)
            LocationTestData(0.0, -180.0, exifLatitudeRef = "N", exifLongitudeRef = "W")             // Equator and -180° Longitude (International Date Line)
        ).forEach {
            val photo = File.createTempFile("sample", ".jpg")

            val location = Location("test_provider").apply {
                this.latitude = it.latitude
                this.longitude = it.longitude
            }

            ExifDataSaver.saveLocationAttributes(photo, location, 0.0, 0.0, 0.0)

            val exif = ExifInterface(photo)

            assertThat(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF), equalTo(it.exifLatitudeRef))
            assertThat(exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF), equalTo(it.exifLongitudeRef))
        }
    }

    @Test
    fun `device orientation is saved to exif data`() {
        listOf(
            Triple(0.0, -45.3, -160.25),
            Triple(100.54, 0.0, 0.0),
            Triple(170.24, 24.54, 120.15)
        ).forEach {
            val photo = File.createTempFile("sample", ".jpg")

            ExifDataSaver.saveLocationAttributes(photo, Location("test_provider"), it.first, it.second, it.third)

            val exif = ExifInterface(photo)

            assertThat(exif.getAttribute(ExifInterface.TAG_USER_COMMENT), equalTo("Azimuth: ${it.first}, Pitch: ${it.second}, Roll: ${it.third}"))
        }
    }

    private data class LocationTestData(
        val latitude: Double,
        val longitude: Double,
        val exifLatitude: String = "",
        val exifLongitude: String = "",
        val exifLatitudeRef: String = "",
        val exifLongitudeRef: String = ""
    )
}
