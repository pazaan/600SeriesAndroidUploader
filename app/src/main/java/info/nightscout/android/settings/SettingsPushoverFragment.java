package info.nightscout.android.settings;

import android.os.Bundle;

import info.nightscout.android.R;

public class SettingsPushoverFragment extends SettingsFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        initPrefs(R.xml.prefs_pushover, rootKey);
        setActionBarTitle(getString(R.string.pref_pushover_category));
    }

}