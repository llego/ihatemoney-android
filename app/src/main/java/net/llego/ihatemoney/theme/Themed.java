package net.llego.ihatemoney.theme;

import androidx.annotation.ColorInt;
import androidx.annotation.UiThread;


/* Taken from Nextcloud Deck */
public interface Themed {
    @UiThread
    void applyTheme(@ColorInt int color);
}
