package info.nightscout.android.settings;

import android.os.Bundle;

import info.nightscout.android.R;

public class SettingsPOUploaderEventsFragment  extends SettingsFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        initPrefs(R.xml.prefs_po_uploader_events, rootKey);
    }

}