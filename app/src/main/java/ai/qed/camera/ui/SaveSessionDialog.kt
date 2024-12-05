package ai.qed.camera.ui

import ai.qed.camera.R
import android.content.Context
import androidx.appcompat.app.AlertDialog

object SaveSessionDialog {
    fun show(
        context: Context,
        onConfirm: () -> Unit,
        onCancel: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.save_dialog_title))
            .setMessage(context.getString(R.string.save_dialog_message))
            .setCancelable(false)
            .setPositiveButton(context.getString(R.string.yes_button_label)) { _, _ ->
                onConfirm()
            }
            .setNegativeButton(context.getString(R.string.cancel_button_label)) { _, _ ->
                onCancel()
            }
            .create()
            .show()
    }
}
