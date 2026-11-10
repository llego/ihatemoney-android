package net.llego.ihatemoney.android.fragment.about;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import net.llego.ihatemoney.R;
import net.llego.ihatemoney.theme.ThemeUtils;
import net.llego.ihatemoney.theme.ThemedFragment;
import net.llego.ihatemoney.util.SupportUtil;

public class AboutFragmentLicenseTab extends ThemedFragment {

    TextView iconsDisclaimer;
    MaterialButton appLicenseButton;

    void openLicense() {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.url_license))));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_about_license_tab, container, false);
        iconsDisclaimer = v.findViewById(R.id.about_icons_disclaimer);
        appLicenseButton = v.findViewById(R.id.about_app_license_button);
        appLicenseButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                openLicense();
            }
        });
        //ButterKnife.bind(this, v);
        SupportUtil.setHtml(iconsDisclaimer, R.string.about_icons_disclaimer, getString(R.string.about_app_icon_author));
        return v;
    }

    @Override
    public void applyTheme(int color) {
        final var utils = ThemeUtils.of(color, requireContext());
        utils.material.colorMaterialButtonPrimaryFilled(appLicenseButton);
        utils.platform.colorTextViewLinks(iconsDisclaimer);
    }
}
