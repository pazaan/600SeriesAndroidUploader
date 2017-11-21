package info.nightscout.android.medtronic.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.Date;

/**
 * Created by lgoedhart on 14/07/2016.
 */
public class MedtronicCnlAlarmReceiver extends BroadcastReceiver {
    private static final String TAG = MedtronicCnlAlarmReceiver.class.getSimpleName();

    public MedtronicCnlAlarmReceiver() {
        super();
    }

    @Override
    public void onReceive(final Context context, Intent intent) {
        // Start the IntentService
        Log.d(TAG, "Received broadcast message at " + new Date(System.currentTimeMillis()));

        context.sendBroadcast(new Intent(MasterService.Constants.ACTION_CNL_COMMS_ACTIVE));

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        context.startService(new Intent(context, MedtronicCnlService.class)
                .setAction(MasterService.Constants.ACTION_CNL_READPUMP)
                .putExtra("PollInterval", Long.parseLong(prefs.getString("pollInterval", Long.toString(MedtronicCnlService.POLL_PERIOD_MS))))
                .putExtra("LowBatteryPollInterval", Long.parseLong(prefs.getString("lowBatPollInterval", Long.toString(MedtronicCnlService.LOW_BATTERY_POLL_PERIOD_MS))))
                .putExtra("ReducePollOnPumpAway", prefs.getBoolean("doublePollOnPumpAway", false))
        );

//        MedtronicCnlAlarmManager.restartAlarm();
    }
}
