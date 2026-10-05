package com.svartifoss.snfell.support

import android.util.TypedValue
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.svartifoss.snfell.R
import com.svartifoss.snfell.view.LyraAccent
import com.svartifoss.snfell.view.applyLyraDialogStyling

/**
 * The amounts, as Play formats them. Choosing one closes this dialog and hands over to Play's own
 * purchase sheet; what happens after that is reported through [TipJar.events], not here, because the
 * answer can arrive after this dialog (or the whole activity) is gone.
 *
 * The wording says plainly that a tip changes nothing in the app. It has to: the listing is paid, so
 * somebody opening a dialog full of prices in the app they already bought could reasonably wonder
 * whether the rest of it is locked.
 */
internal object TipDialog {

    fun show(activity: AppCompatActivity) {
        val offers = TipJar.offers.value
        if (offers.isEmpty()) return

        val amounts = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val side = activity.dp(24f)
            setPadding(side, activity.dp(4f), side, 0)
        }
        var dialog: AlertDialog? = null
        offers.forEach { offer ->
            val amount = activity.layoutInflater.inflate(R.layout.item_tip_option, amounts, false) as MaterialButton
            amount.text = offer.formattedPrice
            amount.setOnClickListener {
                dialog?.dismiss()
                TipJar.start(activity, offer.productId)
            }
            amounts.addView(amount)
        }

        dialog = AlertDialog.Builder(activity)
                .setTitle(R.string.tip_dialog_title)
                .setMessage(R.string.tip_dialog_message)
                .setView(amounts)
                .setNegativeButton(android.R.string.cancel, null)
                .show()
                .also { it.applyLyraDialogStyling(accent = LyraAccent.resolve(activity)) }
    }

    private fun AppCompatActivity.dp(value: Float): Int =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()
}
