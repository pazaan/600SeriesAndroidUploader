package info.nightscout.android.settings;

import android.os.Bundle;

import info.nightscout.android.R;

public class SettingsUrchinFragment extends SettingsFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        initPrefs(R.xml.prefs_urchin, rootKey);
        setActionBarTitle(getString(R.string.pref_urchin_title));
    }

}