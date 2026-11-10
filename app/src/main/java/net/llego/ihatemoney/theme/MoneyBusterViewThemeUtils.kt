package net.llego.ihatemoney.theme

import android.content.res.ColorStateList
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.ActionMenuItemView
import androidx.appcompat.widget.ActionBarContextView
import androidx.appcompat.widget.AppCompatImageView
import com.google.android.material.button.MaterialButton
import com.nextcloud.android.common.ui.theme.MaterialSchemes
import com.nextcloud.android.common.ui.theme.ViewThemeUtilsBase
import com.nextcloud.android.common.ui.theme.utils.MaterialViewThemeUtils
import net.llego.ihatemoney.R


// TODO: upstream these to android-common:ui library
class MoneyBusterViewThemeUtils(
    val material: MaterialViewThemeUtils,
    schemes: MaterialSchemes,
) : ViewThemeUtilsBase(schemes) {

    /**
     * Call this AFTER `dialog.show()`, otherwise the buttons will be null!
     */
    fun colorDialogButtons(dialog: AlertDialog) {
        val buttons = listOf(
            AlertDialog.BUTTON_NEGATIVE,
            AlertDialog.BUTTON_NEUTRAL,
            AlertDialog.BUTTON_POSITIVE,
        )
        buttons.forEach { buttonInt ->
            dialog.getButton(buttonInt)?.let { button ->
                if (button is MaterialButton) {
                    this.material.colorMaterialButtonText(button)
                    this.material.colorMaterialTextButton(button)
                }
            }
        }
    }

    fun themeActionModeActionBar(actionBar: ActionBarContextView, vararg menuIds: Int) {
        withScheme(actionBar.context) { scheme ->
            actionBar.setBackgroundColor(scheme.primary)

            val title = actionBar.findViewById<TextView>(R.id.action_bar_title)
            title.setTextColor(scheme.onPrimary)

            val closeButton =
                actionBar.findViewById<AppCompatImageView>(R.id.action_mode_close_button)
            closeButton.imageTintList = ColorStateList.valueOf(scheme.onPrimary)

            menuIds.forEach { id ->
                val actionMenuItemView = actionBar.findViewById<ActionMenuItemView>(id)
                actionMenuItemView.compoundDrawableTintList =
                    ColorStateList.valueOf(scheme.onPrimary)
            }
        }
    }

}
