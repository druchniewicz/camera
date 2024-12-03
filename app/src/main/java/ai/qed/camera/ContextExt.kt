package ai.qed.camera

import android.content.Context

fun Context.clearFilesDir() {
    filesDir.listFiles()?.forEach { file ->
        file.delete()
    }
}
