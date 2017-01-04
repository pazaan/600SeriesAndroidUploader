package info.nightscout.android.settings;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;

import info.nightscout.android.R;

public class SettingsFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /* set preferences */
        addPreferencesFromResource(R.xml.preferences);

        // iterate through all preferences and update to saved value
        for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); i++) {
            initSummary(getPreferenceScreen().getPreference(i));
        }

        setMinBatPollIntervall((ListPreference) findPreference("pollInterval"), (ListPreference) findPreference("lowBatPollInterval"));
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Preference pref = findPreference(key);


        if ("pollInterval".equals(key)) {
            setMinBatPollIntervall((ListPreference) pref, (ListPreference) findPreference("lowBatPollInterval"));
        }
        updatePrefSummary(pref);
    }

    //

    /**
     * set lowBatPollInterval to normal poll interval at least
     * and adapt the selectable values
     *
     * @param pollIntervalPref
     * @param lowBatPollIntervalPref
     */
    private void setMinBatPollIntervall(ListPreference pollIntervalPref, ListPreference lowBatPollIntervalPref) {
        final String currentValue = lowBatPollIntervalPref.getValue();
        final int pollIntervalPos = pollIntervalPref.findIndexOfValue(pollIntervalPref.getValue()),
                lowBatPollIntervalPos = lowBatPollIntervalPref.findIndexOfValue(currentValue),
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

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    // iterate through all preferences and update to saved value
    private void initSummary(Preference p) {
        if (p instanceof PreferenceCategory) {
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
            p.setSummary(listPref.getEntry());
        }
        if (p instanceof EditTextPreference) {
            EditTextPreference editTextPref = (EditTextPreference) p;
            p.setSummary(editTextPref.getText());
        }
        if (p instanceof MultiSelectListPreference) {
            EditTextPreference editTextPref = (EditTextPreference) p;
            p.setSummary(editTextPref.getText());
        }
    }
}