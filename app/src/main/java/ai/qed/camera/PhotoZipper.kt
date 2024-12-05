package ai.qed.camera

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object PhotoZipper {
    fun zip(dir: File, questionNamePrefix: String, maxNumberOfPackages: Int): List<File> {
        val files =
            dir.listFiles { file -> file.name.startsWith(PHOTO_NAME_PREFIX) } ?: return emptyList()
        val zipFiles = mutableListOf<File>()
        var currentZipIndex = 1
        var currentZipSize = 0L
        var zipOut = createNewPackage(dir, questionNamePrefix, currentZipIndex)

        try {
            for (file in files) {
                val fileSize = file.length()
                if (shouldCreateNextPackage(
                        currentZipSize,
                        fileSize,
                        zipFiles.size,
                        maxNumberOfPackages
                    )
                ) {
                    zipOut.close()
                    zipFiles.add(File(dir, "$questionNamePrefix$currentZipIndex.zip"))

                    if (zipFiles.size >= maxNumberOfPackages) break

                    currentZipIndex++
                    currentZipSize = 0L
                    zipOut = createNewPackage(dir, questionNamePrefix, currentZipIndex)
                }

                addFileToPackage(zipOut, file)
                currentZipSize += fileSize
            }

            if (currentZipSize > 0 && zipFiles.size < maxNumberOfPackages) {
                zipOut.close()
                zipFiles.add(File(dir, "$questionNamePrefix$currentZipIndex.zip"))
            }
        } catch (e: IOException) {
            Log.e("ZipFunction", "Error while zipping files", e)
        } finally {
            try {
                zipOut.close()
            } catch (e: IOException) {
                Log.e("ZipFunction", "Error while closing zip output stream", e)
            }
        }

        return zipFiles
    }

    private fun createNewPackage(dir: File, prefix: String, index: Int): ZipOutputStream {
        val zipFile = File(dir, "$prefix$index.zip")
        return ZipOutputStream(zipFile.outputStream().buffered()).apply {
            setLevel(Deflater.NO_COMPRESSION)
        }
    }

    private fun shouldCreateNextPackage(
        currentPackageSize: Long,
        fileSize: Long,
        packagesCount: Int,
        maxPackagesCount: Int
    ): Boolean {
        val maxPackageSizeInBytes = MAX_PACKAGE_SIZE_IN_MEGABYTES * 1024 * 1024L
        return (currentPackageSize + fileSize > maxPackageSizeInBytes) || (packagesCount >= maxPackagesCount)
    }

    private fun addFileToPackage(zipOut: ZipOutputStream, file: File) {
        try {
            FileInputStream(file).use { fis ->
                val entry = ZipEntry(file.name)
                zipOut.putNextEntry(entry)
                fis.copyTo(zipOut)
                zipOut.closeEntry()
            }
        } catch (e: IOException) {
            Log.e("ZipFunction", "Error while adding file to package", e)
        }
    }
}
