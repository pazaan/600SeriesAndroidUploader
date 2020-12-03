package info.nightscout.android.settings;

import android.os.Bundle;

import info.nightscout.android.R;

public class SettingsDebugFragment extends SettingsFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        initPrefs(R.xml.prefs_debug, rootKey);
        setActionBarTitle(getString(R.string.pref_debug_settings_category));
    }

}