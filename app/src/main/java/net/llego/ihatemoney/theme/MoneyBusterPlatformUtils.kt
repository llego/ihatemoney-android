package net.llego.ihatemoney.theme

import android.widget.CheckBox
import android.widget.TextView

/** Plain Android-view theming. Replaces Nextcloud `AndroidViewThemeUtils` / `AndroidXViewThemeUtils`. */
class MoneyBusterPlatformUtils(private val schemes: MoneyBusterSchemes) {

    fun colorTextView(textView: TextView) {
        textView.setTextColor(schemes.primary)
    }

    fun colorTextViewLinks(textView: TextView) {
        textView.setLinkTextColor(schemes.primary)
    }

    fun themeCheckbox(checkBox: CheckBox) {
        val checked = intArrayOf(android.R.attr.state_checked)
        val unchecked = intArrayOf(0)
        checkBox.buttonTintList = android.content.res.ColorStateList(
            arrayOf(checked, unchecked),
            intArrayOf(schemes.primary, androidx.core.content.ContextCompat.getColor(checkBox.context, net.llego.ihatemoney.R.color.fg_default_low))
        )
    }
}