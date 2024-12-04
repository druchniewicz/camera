package ai.qed.camera.ui

import ai.qed.camera.R
import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class PermissionsActivity : ComponentActivity() {
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){ permissionMap ->
            permissionMap.entries.forEach {
                if (it.key == Manifest.permission.CAMERA) {
                    if (it.value) {
                        Intent(this, CaptureMultipleImagesActivity::class.java).apply {
                            startActivity(this)
                        }
                        finish()
                    } else {
                        Toast.makeText(
                            this,
                            getString(R.string.camera_permission_required),
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissionLauncher.launch(
            listOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ).toTypedArray()
        )
    }
}