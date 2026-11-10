package net.llego.ihatemoney.theme

import android.graphics.Color

/**
 * Minimal color scheme derived from the app's primary brand color.
 * Replaces the Nextcloud `MaterialSchemes` dependency used by the original theming.
 */
class MoneyBusterSchemes(val primary: Int) {
    /** Text/icon color drawn on top of [primary]. */
    val onPrimary: Int = Color.WHITE
}