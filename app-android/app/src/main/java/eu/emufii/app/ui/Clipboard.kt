package eu.emufii.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import eu.emufii.app.R

/** [label] names the clip for the system, and is not shown to the user. */
fun copyToClipboard(context: Context, label: String, value: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(
        context,
        context.getString(R.string.common_copied, value),
        Toast.LENGTH_SHORT
    ).show()
}
