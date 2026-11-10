package net.llego.ihatemoney.android.activity;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NavUtils;

import net.llego.ihatemoney.R;
import net.llego.ihatemoney.databinding.ActivityPreferencesBinding;
import net.llego.ihatemoney.android.fragment.PreferencesFragment;
import net.llego.ihatemoney.util.EdgeToEdge;

/**
 * Allows to change application settings.
 */
public class PreferencesActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        ActivityPreferencesBinding binding = ActivityPreferencesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        EdgeToEdge.apply(this, binding.getRoot());
        setSupportActionBar(binding.toolbar);
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
