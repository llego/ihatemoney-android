package net.llego.ihatemoney.android.fragment.about;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import net.llego.ihatemoney.R;
import net.llego.ihatemoney.theme.ThemeUtils;
import net.llego.ihatemoney.theme.ThemedFragment;
import net.llego.ihatemoney.util.SupportUtil;

public class AboutFragmentCreditsTab extends ThemedFragment {

    TextView aboutVersion;
    TextView aboutMaintainer;
    TextView aboutTranslators;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_about_credits_tab, container, false);
        aboutVersion = v.findViewById(R.id.about_version);
        aboutMaintainer = v.findViewById(R.id.about_maintainer);
        aboutTranslators = v.findViewById(R.id.about_translators);

        String versionName = SupportUtil.getAppVersionName(requireContext());
        SupportUtil.setHtml(aboutVersion, R.string.about_version, "v" + versionName);
        SupportUtil.setHtml(aboutMaintainer, R.string.about_maintainer);
        SupportUtil.setHtml(aboutTranslators, R.string.about_translators_crowdin, getString(R.string.url_translations));
        return v;
    }

    @Override
    public void applyTheme(int color) {
        final var utils = ThemeUtils.of(color, requireContext());
        utils.platform.colorTextViewLinks(aboutTranslators);
    }
}