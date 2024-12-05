package ai.qed.camera.domain

import android.content.Context

fun Context.clearFilesDir() {
    filesDir.listFiles()?.forEach { file ->
        file.delete()
    }
}
