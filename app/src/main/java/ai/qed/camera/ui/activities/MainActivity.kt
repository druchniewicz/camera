package ai.qed.camera.ui.activities

import ai.qed.camera.R
import android.os.Bundle
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableFullScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
