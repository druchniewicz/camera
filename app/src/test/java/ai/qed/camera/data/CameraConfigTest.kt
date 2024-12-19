package ai.qed.camera.data

import ai.qed.camera.ui.activities.CaptureMultipleImagesActivity
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraConfigTest {
    @Test
    fun `should create CameraConfig with default values when no extras are provided`() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), CaptureMultipleImagesActivity::class.java)

        val result = toCameraConfig(intent)

        assertThat(result.mode, equalTo("automatic"))
        assertThat(result.captureInterval, equalTo(5))
        assertThat(result.maxPhotoCount, equalTo(0))
        assertThat(result.maxSessionDuration, equalTo(0))
        assertThat(result.questionNamePrefix, equalTo("part"))
        assertThat(result.maxNumberOfPackages, equalTo(100))
    }

    @Test
    fun `should create CameraConfig with provided extras overriding defaults`() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), CaptureMultipleImagesActivity::class.java).apply {
            putExtra("mode", "manual")
            putExtra("captureInterval", "10")
            putExtra("maxPhotoCount", "50")
            putExtra("maxSessionDuration", "120")
            putExtra("photoFormat", "png")
            putExtra("questionNamePrefix", "test")
            putExtra("maxNumberOfPackages", "200")
        }

        val result = toCameraConfig(intent)

        assertThat(result.mode, equalTo("manual"))
        assertThat(result.captureInterval, equalTo(10))
        assertThat(result.maxPhotoCount, equalTo(50))
        assertThat(result.maxSessionDuration, equalTo(120))
        assertThat(result.questionNamePrefix, equalTo("test"))
        assertThat(result.maxNumberOfPackages, equalTo(200))
    }

    @Test
    fun `should handle invalid integer values by using defaults`() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), CaptureMultipleImagesActivity::class.java).apply {
            putExtra("captureInterval", "invalid")
            putExtra("maxPhotoCount", "not_a_number")
            putExtra("maxSessionDuration", "")
            putExtra("maxNumberOfPackages", "blah")
        }

        val result = toCameraConfig(intent)

        assertThat(result.captureInterval, equalTo(5))
        assertThat(result.maxPhotoCount, equalTo(0))
        assertThat(result.maxSessionDuration, equalTo(0))
        assertThat(result.maxNumberOfPackages, equalTo(100))
    }

    @Test
    fun `should detect automatic mode when mode is default value`() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), CaptureMultipleImagesActivity::class.java).apply {
            putExtra("mode", "automatic")
        }

        val result = toCameraConfig(intent)

        assertThat(result.isAutomaticMode(), equalTo(true))
    }

    @Test
    fun `should detect non-automatic mode when mode is not default`() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), CaptureMultipleImagesActivity::class.java).apply {
            putExtra("mode", "manual")
        }

        val result = toCameraConfig(intent)

        assertThat(result.isAutomaticMode(), equalTo(false))
    }
}
