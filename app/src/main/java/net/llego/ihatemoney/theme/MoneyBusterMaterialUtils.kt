package net.llego.ihatemoney.theme

import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.CheckBox
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputLayout
import net.llego.ihatemoney.R

/** Material-component theming. Replaces Nextcloud `MaterialViewThemeUtils` / `AndroidXViewThemeUtils`. */
class MoneyBusterMaterialUtils(private val schemes: MoneyBusterSchemes) {

    fun colorMaterialButtonPrimaryFilled(button: MaterialButton) {
        button.backgroundTintList = ColorStateList.valueOf(schemes.primary)
        button.setTextColor(schemes.onPrimary)
    }

    fun colorMaterialButtonText(button: MaterialButton) {
        button.setTextColor(schemes.primary)
    }

    fun colorTextInputLayout(layout: TextInputLayout) {
        layout.boxStrokeColor = schemes.primary
        layout.defaultHintTextColor = ColorStateList.valueOf(schemes.primary)
    }

    fun themeTabLayout(tabLayout: TabLayout) {
        tabLayout.setSelectedTabIndicatorColor(schemes.primary)
        tabLayout.setTabTextColors(ContextCompat.getColor(tabLayout.context, R.color.fg_default_low), schemes.primary)
    }

    fun themeSnackbar(snackbar: Snackbar) {
        snackbar.view.backgroundTintList = ColorStateList.valueOf(schemes.primary)
        snackbar.setActionTextColor(schemes.onPrimary)
    }

    fun colorProgressBar(indicator: CircularProgressIndicator) {
        indicator.setIndicatorColor(schemes.primary)
    }

    fun colorMaterialSwitch(materialSwitch: MaterialSwitch) {
        val checked = intArrayOf(android.R.attr.state_checked)
        val unchecked = intArrayOf(0)
        materialSwitch.trackTintList = ColorStateList(
            arrayOf(checked, unchecked),
            intArrayOf(schemes.primary, ContextCompat.getColor(materialSwitch.context, R.color.fg_default_low))
        )
        materialSwitch.thumbTintList = ColorStateList(
            arrayOf(checked, unchecked),
            intArrayOf(schemes.primary, ContextCompat.getColor(materialSwitch.context, R.color.fg_default_low))
        )
    }

    fun colorMaterialCheckBox(checkBox: MaterialCheckBox) {
        applyCheckTint(checkBox)
    }

    private fun applyCheckTint(checkBox: CheckBox) {
        val checked = intArrayOf(android.R.attr.state_checked)
        val unchecked = intArrayOf(0)
        checkBox.buttonTintList = ColorStateList(
            arrayOf(checked, unchecked),
            intArrayOf(schemes.primary, ContextCompat.getColor(checkBox.context, R.color.fg_default_low))
        )
    }
}