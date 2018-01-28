package info.nightscout.android.settings;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.util.Log;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.net.MalformedURLException;
import java.net.URL;

import info.nightscout.android.R;

public class SettingsFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener {
    private static final String TAG = SettingsFragment.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final SettingsFragment that = this;

        /* set preferences */
        addPreferencesFromResource(R.xml.preferences);

        // iterate through all preferences and update to saved value
        for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); i++) {
            initSummary(getPreferenceScreen().getPreference(i));
        }

        setMinBatPollIntervall((ListPreference) findPreference("pollInterval"), (ListPreference) findPreference("lowBatPollInterval"));

        Preference button = findPreference("scanButton");
        button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                IntentIntegrator integrator = new IntentIntegrator(that);
                integrator.initiateScan();

                return true;
            }
        });
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

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode==IntentIntegrator.REQUEST_CODE)
        {
            IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
            if (scanResult != null)
            {
                Log.d(TAG, "scanResult returns: " + scanResult.toString());

                JsonParser json = new JsonParser();
                String resultContents = scanResult.getContents() == null ? "" : scanResult.getContents();
                JsonElement jsonElement = json.parse(resultContents);
                if (jsonElement != null && jsonElement.isJsonObject()) {
                    jsonElement = (jsonElement.getAsJsonObject()).get("rest");
                    if (jsonElement != null && jsonElement.isJsonObject()) {
                        jsonElement = (jsonElement.getAsJsonObject()).get("endpoint");
                        if (jsonElement != null && jsonElement.isJsonArray() && jsonElement.getAsJsonArray().size() > 0) {
                            String endpoint = jsonElement.getAsJsonArray().get(0).getAsString();
                            Log.d(TAG, "endpoint: " + endpoint);

                            try {
                                URL uri = new URL(endpoint);

                                StringBuilder url = new StringBuilder(uri.getProtocol())
                                        .append("://").append(uri.getHost());
                                if (uri.getPort() > -1)
                                    url.append(":").append(uri.getPort());

                                EditTextPreference editPref = (EditTextPreference) findPreference(getString(R.string.preference_nightscout_url));
                                editPref.setText(url.toString());
                                updatePrefSummary(editPref);

                                editPref = (EditTextPreference) findPreference(getString(R.string.preference_api_secret));
                                editPref.setText(uri.getUserInfo());
                                updatePrefSummary(editPref);
                            } catch (MalformedURLException e) {
                                Log.w (TAG, e.getMessage());
                            }

                        }
                    }
                }
            }
            else
            {
                Log.d(TAG, "scanResult is null.");
            }
        }
    }
}