package net.llego.ihatemoney.theme

import android.content.res.ColorStateList
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.ActionMenuItemView
import androidx.appcompat.widget.ActionBarContextView
import androidx.appcompat.widget.AppCompatImageView
import com.google.android.material.button.MaterialButton
import net.llego.ihatemoney.R

/** App-specific theming. Replaces Nextcloud `MoneyBusterViewThemeUtils` / `ViewThemeUtilsBase`. */
class MoneyBusterViewThemeUtils(private val schemes: MoneyBusterSchemes) {

    /** Call this AFTER `dialog.show()`, otherwise the buttons will be null! */
    fun colorDialogButtons(dialog: AlertDialog) {
        val utils = MoneyBusterMaterialUtils(schemes)
        val buttons = listOf(
            AlertDialog.BUTTON_NEGATIVE,
            AlertDialog.BUTTON_NEUTRAL,
            AlertDialog.BUTTON_POSITIVE,
        )
        buttons.forEach { buttonInt ->
            dialog.getButton(buttonInt)?.let { button ->
                if (button is MaterialButton) {
                    utils.colorMaterialButtonText(button)
                }
            }
        }
    }

    fun themeActionModeActionBar(actionBar: ActionBarContextView, vararg menuIds: Int) {
        actionBar.setBackgroundColor(schemes.primary)

        val title = actionBar.findViewById<TextView>(R.id.action_bar_title)
        title.setTextColor(schemes.onPrimary)

        val closeButton =
            actionBar.findViewById<AppCompatImageView>(R.id.action_mode_close_button)
        closeButton.imageTintList = ColorStateList.valueOf(schemes.onPrimary)

        menuIds.forEach { id ->
            val actionMenuItemView = actionBar.findViewById<ActionMenuItemView>(id)
            actionMenuItemView.compoundDrawableTintList =
                ColorStateList.valueOf(schemes.onPrimary)
        }
    }

}