package com.nightscout.android.settings;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;

import com.nightscout.android.R;

public class SettingsFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /* set preferences */
        addPreferencesFromResource(R.xml.preferences);
        
        addMedtronicOptionsListener();
       
        // iterate through all preferences and update to saved value
        for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); i++) {
            initSummary(getPreferenceScreen().getPreference(i));
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        updatePrefSummary(findPreference(key));
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
    
    private void addMedtronicOptionsListener(){
         final ListPreference mon_type = (ListPreference)findPreference("monitor_type");
         final EditTextPreference med_id = (EditTextPreference)findPreference("medtronic_cgm_id");
         final EditTextPreference gluc_id = (EditTextPreference)findPreference("glucometer_cgm_id");
         final EditTextPreference sensor_id = (EditTextPreference)findPreference("sensor_cgm_id");
         final ListPreference calib_type = (ListPreference)findPreference("calibrationType");
         final ListPreference pumpPeriod = (ListPreference)findPreference("pumpPeriod");
         final ListPreference glucSrcType = (ListPreference)findPreference("glucSrcTypes");
         final ListPreference historicPeriod = (ListPreference)findPreference("historicPeriod");
         final ListPreference historicMixPeriod = (ListPreference)findPreference("historicMixPeriod");
         final SwitchPreference enableRest = (SwitchPreference)findPreference("EnableRESTUpload");
         final SwitchPreference enableMongo = (SwitchPreference)findPreference("EnableMongoUpload");
         enableRest.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
           public boolean onPreferenceChange(Preference preference, Object newValue) {
             final Boolean val = (Boolean)newValue;
             if (enableMongo.isChecked() == val && val)
            	 enableMongo.setChecked(!val);
             return true;
           }
         });
         enableMongo.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
             public boolean onPreferenceChange(Preference preference, Object newValue) {
               final Boolean val = (Boolean)newValue;
               if (enableRest.isChecked() == val && val)
              	 enableRest.setChecked(!val);
               return true;
             }
           });
         int index = mon_type.findIndexOfValue(mon_type.getValue());
         int index2 = calib_type.findIndexOfValue(calib_type.getValue());
         if(index==1){
             med_id.setEnabled(true);
             gluc_id.setEnabled(true);
             sensor_id.setEnabled(true);
         }else{
            med_id.setEnabled(false);
            gluc_id.setEnabled(false);
            sensor_id.setEnabled(false);
         }
         index = glucSrcType.findIndexOfValue(glucSrcType.getValue());
         
        
         calib_type.setEnabled(med_id.isEnabled());
         pumpPeriod.setEnabled(index2==1);
         glucSrcType.setEnabled(med_id.isEnabled());
         historicPeriod.setEnabled(index==1 && med_id.isEnabled() );
         historicMixPeriod.setEnabled(index==2 && med_id.isEnabled() );
         glucSrcType.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
             public boolean onPreferenceChange(Preference preference, Object newValue) {
            	 final String val = newValue.toString();
                 int index = glucSrcType.findIndexOfValue(val);
                 historicPeriod.setEnabled(index==1 && med_id.isEnabled());
                 historicMixPeriod.setEnabled(index==2 && med_id.isEnabled() );
                 return true; 
             }
         });
         mon_type.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
           public boolean onPreferenceChange(Preference preference, Object newValue) {
             final String val = newValue.toString();
             int index = mon_type.findIndexOfValue(val);
             if(index==1){
                 med_id.setEnabled(true);
                 gluc_id.setEnabled(true);
                 sensor_id.setEnabled(true);
             }else{
                med_id.setEnabled(false);
                gluc_id.setEnabled(false);
                sensor_id.setEnabled(false);
             }
             index = glucSrcType.findIndexOfValue(glucSrcType.getValue());
             calib_type.setEnabled(med_id.isEnabled());
             pumpPeriod.setEnabled(med_id.isEnabled());
             glucSrcType.setEnabled(med_id.isEnabled());
             historicPeriod.setEnabled(index==1 && med_id.isEnabled());
             historicMixPeriod.setEnabled(index==2 && med_id.isEnabled() );
             return true;
           }
         });
    }
}