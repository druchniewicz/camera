package ai.qed.camera

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object PhotoZipper {
    fun zip(dir: File): File {
        val zipFile = File(dir, "photos_${System.currentTimeMillis()}.zip")
        val files = dir.listFiles { file -> file.name.startsWith(PHOTO_NAME_PREFIX) }

        if (files != null && files.isNotEmpty()) {
            try {
                ZipOutputStream(zipFile.outputStream().buffered()).use { zipOut ->
                    zipOut.setLevel(Deflater.NO_COMPRESSION)
                    for (file in files) {
                        FileInputStream(file).use { fis ->
                            val entry = ZipEntry(file.name)
                            zipOut.putNextEntry(entry)
                            fis.copyTo(zipOut)
                            zipOut.closeEntry()
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e("CaptureMultiplePhotos", "Error when creating zip package", e)
            }
        }

        return zipFile
    }
}
