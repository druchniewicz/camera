package ai.qed.camera.domain

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class StorageHelperTest {
    @Test
    fun `should return '0 bytes' for zero size`() {
        val result = StorageHelper.formatMBToReadableStringRepresentation(0.0)
        assertThat(result, equalTo("0 bytes"))
    }

    @Test
    fun `should return bytes for very small size`() {
        val result = StorageHelper.formatMBToReadableStringRepresentation(0.0005)
        assertThat(result, equalTo("524.29 bytes"))
    }

    @Test
    fun `should return bytes for size less than 1 KB`() {
        val result = StorageHelper.formatMBToReadableStringRepresentation(0.0001)
        assertThat(result, equalTo("104.86 bytes"))
    }

    @Test
    fun `should return kilobytes for size in KB range`() {
        val result = StorageHelper.formatMBToReadableStringRepresentation(0.01)
        assertThat(result, equalTo("10.24 KB"))
    }

    @Test
    fun `should return megabytes for size in MB range`() {
        val result = StorageHelper.formatMBToReadableStringRepresentation(1.0)
        assertThat(result, equalTo("1.00 MB"))
    }

    @Test
    fun `should return gigabytes for fractional size in MB range`() {
        val result = StorageHelper.formatMBToReadableStringRepresentation(1234.56)
        assertThat(result, equalTo("1.21 GB"))
    }

    @Test
    fun `should return gigabytes for size in GB range`() {
        val result = StorageHelper.formatMBToReadableStringRepresentation(2048.0)
        assertThat(result, equalTo("2.00 GB"))
    }

    @Test
    fun `should return terabytes for size in TB range`() {
        val result = StorageHelper.formatMBToReadableStringRepresentation(1048576.0)
        assertThat(result, equalTo("1.00 TB"))
    }

    @Test
    fun `should return terabytes for very large size`() {
        val result = StorageHelper.formatMBToReadableStringRepresentation(2097152.0)
        assertThat(result, equalTo("2.00 TB"))
    }
}