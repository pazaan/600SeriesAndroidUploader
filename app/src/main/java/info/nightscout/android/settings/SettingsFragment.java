package info.nightscout.android.settings;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import android.util.Log;

import java.util.List;

import info.nightscout.android.R;
import info.nightscout.android.urchin.UrchinService;
import info.nightscout.android.utils.FormatKit;

public class SettingsFragment extends PreferenceFragmentCompat implements OnSharedPreferenceChangeListener {
    private static final String TAG = SettingsFragment.class.getSimpleName();

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Log.d(TAG, "onCreatePreferences called");
        initPrefs(R.xml.preferences, rootKey);
        setActionBarTitle(getString(R.string.main_menu__settings));
        setMinBatPollIntervall((ListPreference) findPreference("pollInterval"), (ListPreference) findPreference("lowBatPollInterval"));
    }

    public void initPrefs(int resID, String rootKey) {
        setPreferencesFromResource(resID, rootKey);
        // iterate through all preferences and update to saved value
        for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); i++) {
            initSummary(getPreferenceScreen().getPreference(i));
        }
    }

    private CharSequence actionBarTitle;

    public void setActionBarTitle(String actionBarTitle) {
        this.actionBarTitle = actionBarTitle;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Log.d(TAG, "onSharedPreferenceChanged called");
        Preference pref = findPreference(key);

        if ("pollInterval".equals(key)) {
            setMinBatPollIntervall((ListPreference) pref, (ListPreference) findPreference("lowBatPollInterval"));
        }

        updatePrefSummary(pref);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy called");
        super.onDestroy();
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume called");
        super.onResume();
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        if (actionBarTitle != null) ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(actionBarTitle);
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause called");
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    /**
     * set lowBatPollInterval to normal poll interval at least
     * and adapt the selectable values
     *
     * @param pollIntervalPref
     * @param lowBatPollIntervalPref
     */
    private void setMinBatPollIntervall(ListPreference pollIntervalPref, ListPreference lowBatPollIntervalPref) {
        final String currentValue = lowBatPollIntervalPref.getValue();
        final int pollIntervalPos = (pollIntervalPref.findIndexOfValue(pollIntervalPref.getValue()) >= 0?pollIntervalPref.findIndexOfValue(pollIntervalPref.getValue()):0),
                length = pollIntervalPref.getEntries().length;

        CharSequence[] entries = new String[length - pollIntervalPos],
                entryValues = new String[length - pollIntervalPos];

        // generate temp Entries and EntryValues
        for(int i = pollIntervalPos; i < length; i++) {
            entries[i - pollIntervalPos] = pollIntervalPref.getEntries()[i];
            entryValues[i - pollIntervalPos] = pollIntervalPref.getEntryValues()[i];
        }
        lowBatPollIntervalPref.setEntries(entries);
        lowBatPollIntervalPref.setEntryValues(entryValues);

        // and set the correct one
        if (lowBatPollIntervalPref.findIndexOfValue(currentValue) == -1) {
            lowBatPollIntervalPref.setValueIndex(0);
        }
    }

    // iterate through all preferences and update to saved value
    private void initSummary(Preference p) {
        if (p instanceof PreferenceScreen) {
            PreferenceScreen pScr = (PreferenceScreen) p;
            for (int i = 0; i < pScr.getPreferenceCount(); i++) {
                initSummary(pScr.getPreference(i));
            }
        } else if (p instanceof PreferenceCategory) {
            PreferenceCategory pCat = (PreferenceCategory) p;
            for (int i = 0; i < pCat.getPreferenceCount(); i++) {
                initSummary(pCat.getPreference(i));
            }
        } else {
            updatePrefSummary(p);
        }
    }

    // update preference summary
    private void updatePrefSummary(Preference p) {
        if (p instanceof ListPreference) {
            ListPreference listPref = (ListPreference) p;
            if (p.getKey().contains("urchinStatusLayout"))
                urchinStatusLayout(listPref);
            p.setSummary(listPref.getEntry());
        }

        else if (p instanceof info.nightscout.android.utils.EditTextPreference) {
            EditTextPreference editTextPref = (EditTextPreference) p;
            String t = editTextPref.getText();
            if (t.isEmpty()) {
                if (((info.nightscout.android.utils.EditTextPreference) p).getMode().equals("preset")) {
                    t = FormatKit.getInstance().getString("default_" + editTextPref.getKey());
                } else {
                    t = getString(R.string.dots);
                }
            }
            p.setSummary(t);
        }

        else if (p instanceof EditTextPreference) {
            EditTextPreference editTextPref = (EditTextPreference) p;
            p.setSummary(editTextPref.getText());
        }

        else if (p instanceof MultiSelectListPreference) {
            EditTextPreference editTextPref = (EditTextPreference) p;
            p.setSummary(editTextPref.getText());
        }
    }

    private CharSequence[] values;
    private CharSequence[] entries;

    private void urchinStatusLayout(ListPreference listPref) {

        if (values == null || entries == null) {
            List<String> items = UrchinService.getListPreferenceItems();

            int size = items.size() / 2;
            values = new String[size];
            entries = new String[size];

            for (int i = 0, p = 0; i < size; i++) {
                values[i] = items.get(p++);
                entries[i] = items.get(p++);
            }
        }

        listPref.setEntryValues(values);
        listPref.setEntries(entries);
    }

}