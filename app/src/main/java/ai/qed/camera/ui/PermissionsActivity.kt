package ai.qed.camera.ui

import ai.qed.camera.R
import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

class PermissionsActivity : ComponentActivity() {
    private lateinit var activityResultLauncher: ActivityResultLauncher<Intent>

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){ permissionMap ->
            permissionMap.entries.forEach {
                if (it.key == Manifest.permission.CAMERA) {
                    if (it.value) {
                        val extras = intent.extras
                        Intent(this, CaptureMultipleImagesActivity::class.java).apply {
                            if (extras != null) {
                                putExtras(extras)
                            }
                            activityResultLauncher.launch(this)
                        }
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
        activityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                setResult(RESULT_OK, result.data)
            }
            finish()
        }

        requestPermissionLauncher.launch(
            listOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ).toTypedArray()
        )
    }
}