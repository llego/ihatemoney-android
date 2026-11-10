package net.llego.ihatemoney.android.activity;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.google.android.material.tabs.TabLayoutMediator;

import net.llego.ihatemoney.R;
import net.llego.ihatemoney.android.fragment.about.AboutFragmentContributingTab;
import net.llego.ihatemoney.android.fragment.about.AboutFragmentCreditsTab;
import net.llego.ihatemoney.android.fragment.about.AboutFragmentLicenseTab;
import net.llego.ihatemoney.databinding.ActivityAboutBinding;
import net.llego.ihatemoney.theme.ThemeUtils;
import net.llego.ihatemoney.theme.ThemedActivity;
import net.llego.ihatemoney.util.ColorUtils;

public class AboutActivity extends ThemedActivity {

    private ActivityAboutBinding binding;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAboutBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.aboutToolbar);

        binding.aboutTabLayout.setSelectedTabIndicatorColor(ColorUtils.primaryColor(this));
        final TabsStateAdapter adapter = new TabsStateAdapter(this);
        binding.aboutViewPager2.setAdapter(adapter);
        new TabLayoutMediator(binding.aboutTabLayout, binding.aboutViewPager2, (tab, position) ->
                tab.setText(adapter.getPageTitle(position))
        ).attach();
    }

    @Override
    public void onBackPressed() {
        //NavUtils.navigateUpFromSameTask(this);
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                //Toast.makeText(getApplicationContext(),"Back button clicked", Toast.LENGTH_LONG).show();
                finish();
                break;
        }
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish(); // close this activity as oppose to navigating up
        return true;
    }

    @Override
    public void applyTheme(int color) {
        final var utils = ThemeUtils.of(color, this);
        utils.material.themeTabLayout(binding.aboutTabLayout);
    }

    private static class TabsStateAdapter extends FragmentStateAdapter {
        private static final int POS_CREDITS = 0;
        private static final int POS_CONTRIBUTING = 1;
        private static final int POS_LICENSE = 2;
        private static final int TOTAL_COUNT = 3;

        TabsStateAdapter(final FragmentActivity fa) {
            super(fa);
        }

        @Override
        public int getItemCount() {
            return TOTAL_COUNT;
        }

        /**
         * return the right fragment for the given position
         */
        @NonNull
        @Override
        public Fragment createFragment(final int position) {
            switch (position) {
                case POS_CREDITS:
                    return new AboutFragmentCreditsTab();

                case POS_CONTRIBUTING:
                    return new AboutFragmentContributingTab();

                case POS_LICENSE:
                    return new AboutFragmentLicenseTab();

                default:
                    throw new IllegalStateException("Position " + position + " is not supported.");
            }
        }

        /**
         * generate title based on given position
         */
        public int getPageTitle(final int position) {
            switch (position) {
                case POS_CREDITS:
                    return R.string.about_credits_tab_title;

                case POS_CONTRIBUTING:
                    return R.string.about_contribution_tab_title;

                case POS_LICENSE:
                    return R.string.about_license_tab_title;

                default:
                    throw new IllegalStateException("Position " + position + " is not supported.");
            }
        }
    }
}
