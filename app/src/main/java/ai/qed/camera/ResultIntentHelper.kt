package ai.qed.camera

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object ResultIntentHelper {
    fun returnIntent(activity: Activity, file: File) {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
        val uri = getUriForFile(activity, file)
        addItem(intent, uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        activity.setResult(Activity.RESULT_OK, intent)
        activity.finish()
    }

    private fun getUriForFile(activity: Activity,file: File): Uri {
        return FileProvider.getUriForFile(activity, "ai.qed.camera.fileprovider", file)
    }

    private fun addItem(intent: Intent, uri: Uri) {
        intent.putExtra("value", uri)
        if (intent.clipData == null) {
            intent.clipData = ClipData.newRawUri(null, uri)
        } else {
            intent.clipData?.addItem(ClipData.Item(uri))
        }
    }
}
