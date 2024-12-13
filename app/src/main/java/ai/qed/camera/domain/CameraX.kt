package ai.qed.camera.domain

import ai.qed.camera.R
import ai.qed.camera.data.DeviceOrientationProvider
import ai.qed.camera.data.LocationProvider
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class CameraX(
    private val locationProvider: LocationProvider,
    private val deviceOrientationProvider: DeviceOrientationProvider
) {
    private var activity: ComponentActivity? = null
    private lateinit var imageCapture: ImageCapture

    fun initialize(
        activity: ComponentActivity,
        previewView: View,
        onReady: () -> Unit,
        onError: () -> Unit
    ) {
        this.activity = activity

        val cameraProviderFuture = ProcessCameraProvider.getInstance(activity)
        cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build()
                preview.surfaceProvider = (previewView as PreviewView).surfaceProvider

                imageCapture = ImageCapture.Builder()
                    .setTargetRotation(activity.windowManager.defaultDisplay.rotation)
                    .build()

                try {
                    cameraProvider.bindToLifecycle(
                        activity,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture
                    )

                    onReady()
                } catch (e: IllegalArgumentException) {
                    onError()
                }
            },
            ContextCompat.getMainExecutor(activity)
        )
    }

    fun takePicture(
        imagePath: String,
        onImageSaved: () -> Unit,
        onImageSaveError: (String?) -> Unit
    ) {
        activity.let { context ->
            if (context == null) {
                return
            }

            val outputFile = File(imagePath)
            val outputFileOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

            imageCapture.takePicture(
                outputFileOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                compressImage(outputFile)
                                saveExifData(outputFile)
                                onImageSaved()
                            } catch (e: Exception) {
                                onImageSaveError(e.message)
                            }
                        }
                    }

                    override fun onError(error: ImageCaptureException) {
                        var message = error.cause?.message
                        if (message == null) {
                            message = activity?.getString(R.string.take_photo_error_toast_message)
                        }
                        onImageSaveError(message)
                    }
                }
            )
        }
    }

    private fun compressImage(file: File) {
        val bitmap = rotateImageIfNeeded(file.absolutePath)
        FileOutputStream(file).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.WEBP, 75, outputStream)
        }
        bitmap.recycle()
    }

    private fun saveExifData(file: File) {
        ExifDataSaver.saveLocationAttributes(
            file,
            locationProvider.lastKnownLocation,
            deviceOrientationProvider.azimuth,
            deviceOrientationProvider.pitch,
            deviceOrientationProvider.roll
        )
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
}
