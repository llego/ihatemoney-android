package net.eneiluj.moneybuster.android.activity;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.NavUtils;

import net.eneiluj.moneybuster.R;
import net.eneiluj.moneybuster.android.fragment.PreferencesFragment;
import net.eneiluj.moneybuster.util.EdgeToEdge;

/**
 * Allows to change application settings.
 */
public class PreferencesActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        View view = LayoutInflater.from(this).inflate(R.layout.activity_preferences, null);
        setContentView(view);
        EdgeToEdge.apply(this, view);
        Toolbar toolbar = view.findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container_view, new PreferencesFragment())
                    .commit();
        }
    }

    @Override
    public void onBackPressed() {
        NavUtils.navigateUpFromSameTask(this);
        //finish();
    }
}
