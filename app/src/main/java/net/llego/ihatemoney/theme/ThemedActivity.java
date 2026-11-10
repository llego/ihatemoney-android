package net.llego.ihatemoney.theme;

import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import net.llego.ihatemoney.util.ColorUtils;


public abstract class ThemedActivity extends AppCompatActivity implements Themed {

    @Override
    protected void onStart() {
        super.onStart();

        // FIXME: Use a LiveData to constantly observe this and react to changes? -> See Nextcloud Deck
        int color = ColorUtils.primaryColor(this);
        applyTheme(color);
    }
}
