package ai.qed.camera.ui

import ai.qed.camera.R
import ai.qed.camera.databinding.DialogCameraSettingsBinding
import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog

object SettingsDialog {
    fun show(
        context: Context,
        captureInterval: String,
        isAutomaticMode: Boolean,
        onConfirm: (captureInterval: String, isAutomaticMode: Boolean) -> Unit,
        onCancel: () -> Unit
    ) {
        val binding = DialogCameraSettingsBinding.inflate(LayoutInflater.from(context))
        binding.inputCaptureInterval.setText(captureInterval)
        binding.switchMode.isChecked = isAutomaticMode

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.settings_dialog_title))
            .setView(binding.root)
            .setCancelable(false)
            .setPositiveButton(context.getString(R.string.save_dialog_button)) { _, _ ->
                onConfirm(
                    binding.inputCaptureInterval.text.toString(),
                    binding.switchMode.isChecked
                )
            }
            .setNegativeButton(context.getString(R.string.cancel_button_label)) { _, _ ->
                onCancel()
            }
            .create()
            .show()
    }
}