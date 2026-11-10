package net.llego.ihatemoney.android.fragment;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.kizitonwose.colorpreferencecompat.ColorPreferenceCompat;

import net.llego.ihatemoney.R;
import net.llego.ihatemoney.android.dialogs.ColorPickerDialog;
import net.llego.ihatemoney.service.SyncWorker;
import net.llego.ihatemoney.theme.ThemedMaterialAlertDialogBuilder;
import net.llego.ihatemoney.util.MoneyBuster;
import net.llego.ihatemoney.util.SupportUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import at.bitfire.cert4android.CustomCertStore;

public class PreferencesFragment extends PreferenceFragmentCompat implements PreferenceFragmentCompat.OnPreferenceStartScreenCallback {

    public final static String STOP_SYNC_SERVICE = "net.llego.ihatemoney.STOP_SYNC_SERVICE";
    public final static String CHANGE_SYNC_INTERVAL = "net.llego.ihatemoney.CHANGE_SYNC_INTERVAL";

    @Override
    public boolean onPreferenceStartScreen(PreferenceFragmentCompat caller, PreferenceScreen pref) {
        caller.setPreferenceScreen(pref);
        return true;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootkey) {

    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        Preference resetTrust = findPreference(getString(R.string.pref_key_reset_trust));
        resetTrust.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                CustomCertStore.Companion.getInstance(requireContext()).clearUserDecisions();
                SupportUtil.invalidateTlsConfiguration();
                Toast.makeText(getActivity(), getString(R.string.settings_cert_reset_toast), Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());

        final Preference appColorPref = findPreference(getString(R.string.pref_key_color));

        appColorPref.setVisible(true);

        setupLanguagePreference();

        // night mode
        final Preference nightModePref = findPreference(getString(R.string.pref_key_night_mode));
        ListPreference nightModeListPref = (ListPreference) nightModePref;
        List<String> nightModeList = new ArrayList<>();
        nightModeList.add(getString(R.string.pref_value_theme_light));
        nightModeList.add(getString(R.string.pref_value_theme_dark));
        nightModeList.add(getString(R.string.pref_value_theme_system));
        CharSequence[] providerEntries = nightModeList.toArray(new CharSequence[nightModeList.size()]);
        nightModeListPref.setEntries(providerEntries);

        String nightModeValue = sp.getString(getString(R.string.pref_key_night_mode), getString(R.string.pref_value_night_mode_system));
        setNightModeSummary(nightModePref, nightModeValue);
        setNightModePreferenceIcon(nightModePref, nightModeValue);

        nightModePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                int nightMode = Integer.parseInt((String) newValue);
                MoneyBuster.setAppTheme(nightMode);
                setNightModeSummary(nightModePref, (String) newValue);
                setNightModePreferenceIcon(nightModePref, (String) newValue);
                // no need to recreate the activity
                // this is done since AppCompat v1.1.0 according to https://developer.android.com/guide/topics/ui/look-and-feel/darktheme#change-themes
                /*if (getActivity() != null) {
                    getActivity().recreate();
                }*/
                return true;
            }
        });

        appColorPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                showColorDialog(preference);
                return true;
            }
        });

        final EditTextPreference syncIntervalPref = (EditTextPreference) findPreference(getString(R.string.pref_key_sync_interval));
        String interval = sp.getString(getString(R.string.pref_key_sync_interval), "60");
        syncIntervalPref.setSummary(interval);
        syncIntervalPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                String newValueString = (String) newValue;
                long newInterval;
                try {
                    newInterval = Long.valueOf(newValueString);
                } catch (Exception e) {
                    showToast(getString(R.string.error_invalid_sync_interval), Toast.LENGTH_LONG);
                    return false;
                }
                if (newInterval > 1440 || newInterval < 15) {
                    showToast(getString(R.string.error_invalid_sync_interval), Toast.LENGTH_LONG);
                    return false;
                } else {
                    preference.setSummary((CharSequence) newValue);
                    SyncWorker.submitWork(requireContext());
                    return true;
                }
            }
        });

        final CheckBoxPreference notifyNewPref = (CheckBoxPreference) findPreference(getString(R.string.pref_key_notify_new));
        final CheckBoxPreference notifyUpdatedPref = (CheckBoxPreference) findPreference(getString(R.string.pref_key_notify_updated));
        final CheckBoxPreference notifyDeletedPref = (CheckBoxPreference) findPreference(getString(R.string.pref_key_notify_deleted));

        final SwitchPreferenceCompat autostartPref = (SwitchPreferenceCompat) findPreference(getString(R.string.pref_key_autostart));

        final SwitchPreferenceCompat periodicalSyncPref = (SwitchPreferenceCompat) findPreference(getString(R.string.pref_key_periodical_sync));
        periodicalSyncPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Boolean newPeriodicalSync = (Boolean) newValue;
                Log.d("preference", "PERIOSYNC " + newPeriodicalSync);
                if (newPeriodicalSync) {
                    syncIntervalPref.setVisible(true);
                    notifyNewPref.setVisible(true);
                    notifyUpdatedPref.setVisible(true);
                    notifyDeletedPref.setVisible(true);
                    autostartPref.setVisible(true);

                    Log.d("preference", "not running => launch");
                    SyncWorker.submitWork(requireContext());
                } else {
                    syncIntervalPref.setVisible(false);
                    notifyNewPref.setVisible(false);
                    notifyUpdatedPref.setVisible(false);
                    notifyDeletedPref.setVisible(false);
                    autostartPref.setVisible(false);

                    Log.d("preference", "running => stop");
                    SyncWorker.cancelWork(requireContext());
                }
                return true;
            }
        });

        if (!periodicalSyncPref.isChecked()) {
            syncIntervalPref.setVisible(false);
            notifyNewPref.setVisible(false);
            notifyUpdatedPref.setVisible(false);
            notifyDeletedPref.setVisible(false);
            autostartPref.setVisible(false);
        }
    }

    protected void showToast(CharSequence text, int duration) {
        Context context = getContext();
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }

    private void setNightModeSummary(Preference nightModePref, String nightModeValue) {
        if (nightModeValue.equals(getString(R.string.pref_value_night_mode_system))) {
            nightModePref.setSummary(getString(R.string.pref_value_theme_system));
        } else if (nightModeValue.equals(getString(R.string.pref_value_night_mode_no))) {
            nightModePref.setSummary(getString(R.string.pref_value_theme_light));
        } else if (nightModeValue.equals(getString(R.string.pref_value_night_mode_yes))) {
            nightModePref.setSummary(getString(R.string.pref_value_theme_dark));
        }
    }

    private void setNightModePreferenceIcon(Preference preference, String nightModeValue) {
        if (nightModeValue.equals(getString(R.string.pref_value_night_mode_system))) {
            preference.setIcon(R.drawable.ic_settings_grey600_24dp);
        } else if (nightModeValue.equals(getString(R.string.pref_value_night_mode_no))) {
            preference.setIcon(R.drawable.ic_sunny_grey_24dp);
        } else if (nightModeValue.equals(getString(R.string.pref_value_night_mode_yes))) {
            preference.setIcon(R.drawable.ic_brightness_2_grey_24dp);
        }
    }

    private void setupLanguagePreference() {
        ListPreference languagePref = (ListPreference) findPreference(getString(R.string.pref_key_language));
        if (languagePref == null) return;

        // Only the languages the app is actually translated into (mirrors app/src/main/res/values-*).
        final String[] supported = {
                "af", "ar", "bg", "bn", "ca", "cs", "da", "de", "el", "en",
                "eo", "es", "fa", "fi", "fr", "hi", "hu", "id", "it", "he",
                "ja", "ko", "lt", "mk", "mn", "ne", "nl", "no", "oc", "pl",
                "pt", "qu", "ro", "ru", "sk", "sl", "sq", "sr", "sv", "ta",
                "te", "th", "tr", "uk", "ur", "vi", "zh-CN", "zh-TW"
        };

        List<String> values = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        values.add("");
        labels.add(getString(R.string.language_system));
        for (String tag : supported) {
            Locale locale = Locale.forLanguageTag(tag);
            values.add(tag);
            labels.add(locale.getDisplayName(locale));
        }
        languagePref.setEntries(labels.toArray(new String[0]));
        languagePref.setEntryValues(values.toArray(new String[0]));

        String current = PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getString(getString(R.string.pref_key_language), "");
        updateLanguageSummary(languagePref, current, values, labels);

        languagePref.setOnPreferenceChangeListener((preference, newValue) -> {
            String tag = String.valueOf(newValue);
            updateLanguageSummary(languagePref, tag, values, labels);
            // Apply the chosen language directly (the pref isn't persisted until we return true),
            // then rebuild so the whole UI loads in the new language.
            MoneyBuster.applyAppLocale(requireContext(), tag);
            if (getActivity() != null) {
                getActivity().recreate();
            }
            return true;
        });
    }

    private void updateLanguageSummary(ListPreference languagePref, String tag,
                                       List<String> values, List<String> labels) {
        int index = values.indexOf(tag);
        if (index >= 0) {
            languagePref.setSummary(labels.get(index));
        }
    }

    private void showColorDialog(final Preference preference) {
        int current = PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getInt(getString(R.string.pref_key_color), Color.BLUE);
        ColorPickerDialog.show(requireActivity(), current, color -> {
            ((ColorPreferenceCompat) preference).setValue(color);
            requireActivity().recreate();
        });
    }
}
