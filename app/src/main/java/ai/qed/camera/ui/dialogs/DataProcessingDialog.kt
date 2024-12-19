package ai.qed.camera.ui.dialogs

import ai.qed.camera.R
import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData

class DataProcessingDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreateDialog(savedInstanceState)

        isCancelable = false

        val dialogView = layoutInflater.inflate(R.layout.data_processing_dialog, null, false)
        val dialog: AlertDialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        return dialog
    }

    companion object {
        fun showOn(
            lifecycleOwner: LifecycleOwner,
            liveData: LiveData<Boolean>,
            fragmentManager: FragmentManager
        ) {
            liveData.observe(lifecycleOwner) { isLoading: Boolean ->
                if (isLoading) {
                    val dialog = DataProcessingDialog()
                    showIfNotShowing(
                        dialog,
                        DataProcessingDialog::class.java.name,
                        fragmentManager
                    )
                } else {
                    dismissDialog(
                        DataProcessingDialog::class.java.name,
                        fragmentManager
                    )
                }
            }
        }

        private fun <T : DialogFragment> showIfNotShowing(
            newDialog: T,
            tag: String,
            fragmentManager: FragmentManager
        ) {
            if (fragmentManager.isStateSaved) {
                return
            }
            val existingDialog = fragmentManager.findFragmentByTag(tag) as T?
            if (existingDialog == null) {
                newDialog.show(fragmentManager.beginTransaction(), tag)

                try {
                    fragmentManager.executePendingTransactions()
                } catch (e: IllegalStateException) {
                    // ignore
                }
            }
        }

        private fun dismissDialog(tag: String, fragmentManager: FragmentManager) {
            val existingDialog = fragmentManager.findFragmentByTag(tag) as DialogFragment?
            if (existingDialog != null) {
                existingDialog.dismissAllowingStateLoss()
                try {
                    fragmentManager.executePendingTransactions()
                } catch (e: IllegalStateException) {
                    // ignore
                }
            }
        }
    }
}