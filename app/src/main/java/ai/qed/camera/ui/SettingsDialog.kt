package ai.qed.camera.ui

import ai.qed.camera.R
import ai.qed.camera.databinding.DialogCameraSettingsBinding
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AlertDialog

object SettingsDialog {
    fun show(
        context: Context,
        captureInterval: String,
        isAutomaticMode: Boolean,
        onConfirm: (captureInterval: String, isAutomaticMode: Boolean) -> Unit
    ) {
        val binding = DialogCameraSettingsBinding.inflate(LayoutInflater.from(context))
        binding.inputCaptureInterval.setText(captureInterval)
        binding.switchMode.isChecked = isAutomaticMode

        val dialog = AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.settings_dialog_title))
            .setView(binding.root)
            .setPositiveButton(context.getString(R.string.save_dialog_button), null)
            .setNegativeButton(context.getString(R.string.cancel_button_label), null)
            .create()

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            binding.inputCaptureInterval.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    validateCaptureIntervalValue(s?.toString(), binding, saveButton)
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })

            saveButton.setOnClickListener {
                onConfirm(
                    binding.inputCaptureInterval.text.toString(),
                    binding.switchMode.isChecked
                )
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun validateCaptureIntervalValue(
        inputValue: String?,
        binding: DialogCameraSettingsBinding,
        saveButton: Button
    ) {
        val isValueValid =
            !inputValue.isNullOrBlank() && inputValue.toIntOrNull()?.let { it > 0 } == true
        binding.labelCaptureIntervalValidationMessage.visibility =
            if (isValueValid) View.GONE else View.VISIBLE
        saveButton.isEnabled = isValueValid
    }
}