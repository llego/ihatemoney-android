package net.llego.ihatemoney.theme

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Dialog theming. Material alert dialogs already follow the app theme; kept for API parity. */
class MoneyBusterDialogUtils(private val schemes: MoneyBusterSchemes) {

    fun colorMaterialAlertDialogBackground(context: Context, builder: MaterialAlertDialogBuilder) {
        // The Material theme already styles dialogs; nothing extra is required.
    }
}