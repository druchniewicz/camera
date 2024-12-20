package ai.qed.camera.domain

import ai.qed.camera.R
import ai.qed.camera.data.DeviceOrientationProvider
import ai.qed.camera.data.LocationProvider
import android.view.View
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File

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

    fun takePhoto(
        imagePath: String,
        onImageSaved: (File) -> Unit,
        onError: (String?) -> Unit,
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
                        saveExifData(outputFile)
                        onImageSaved(outputFile)
                    }

                    override fun onError(error: ImageCaptureException) {
                        var message = error.cause?.message
                        if (message == null) {
                            message = activity?.getString(R.string.take_photo_error_toast_message)
                        }
                        onError(message)
                    }
                }
            )
        }
    }

    fun cleanup() {
        activity?.let {
            val cameraProvider = ProcessCameraProvider.getInstance(it).get()
            cameraProvider.unbindAll()
        }
        activity = null
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
}
