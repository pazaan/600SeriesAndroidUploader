package info.nightscout.android.settings;

import android.os.Bundle;

import info.nightscout.android.R;

public class SettingsNightscoutFragment extends SettingsFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        initPrefs(R.xml.prefs_nightscout, rootKey);
        setActionBarTitle(getString(R.string.pref_ns_advanced_nightscout_settings_category));
    }

}