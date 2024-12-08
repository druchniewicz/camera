package ai.qed.camera.domain

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class TimeHelperTest {
    @Test
    fun `should return '0s' for zero seconds`() {
        val result = TimeHelper.formatSecondsToReadableStringRepresentation(0)
        assertThat(result, equalTo("0s"))
    }

    @Test
    fun `should return seconds for less than one minute`() {
        val result = TimeHelper.formatSecondsToReadableStringRepresentation(45)
        assertThat(result, equalTo("45s"))
    }

    @Test
    fun `should return '1m' for exactly one minute`() {
        val result = TimeHelper.formatSecondsToReadableStringRepresentation(60)
        assertThat(result, equalTo("1m"))
    }

    @Test
    fun `should return minutes and seconds for more than one minute`() {
        val result = TimeHelper.formatSecondsToReadableStringRepresentation(75)
        assertThat(result, equalTo("1m 15s"))
    }

    @Test
    fun `should return '1h' for exactly one hour`() {
        val result = TimeHelper.formatSecondsToReadableStringRepresentation(3600)
        assertThat(result, equalTo("1h"))
    }

    @Test
    fun `should return hours and seconds for one hour and seconds`() {
        val result = TimeHelper.formatSecondsToReadableStringRepresentation(3601)
        assertThat(result, equalTo("1h 1s"))
    }

    @Test
    fun `should return hours, minutes, and seconds for one hour, minutes and seconds`() {
        val result = TimeHelper.formatSecondsToReadableStringRepresentation(3661)
        assertThat(result, equalTo("1h 1m 1s"))
    }

    @Test
    fun `should return hours, minutes, and seconds for more than one hour`() {
        val result = TimeHelper.formatSecondsToReadableStringRepresentation(7322)
        assertThat(result, equalTo("2h 2m 2s"))
    }
}
