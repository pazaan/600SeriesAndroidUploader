package info.nightscout.android.medtronic.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.Date;

import info.nightscout.android.UploaderApplication;
import info.nightscout.android.utils.ConfigurationStore;

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

        MasterService.commsActive = true;

        Intent serviceintent = new Intent(context, MedtronicCnlIntentService.class)
                .putExtra("PollInterval", ConfigurationStore.getInstance().getPollInterval())
                .putExtra("LowBatteryPollInterval",  ConfigurationStore.getInstance().getLowBatteryPollInterval())
                .putExtra("ReducePollOnPumpAway", ConfigurationStore.getInstance().isReducePollOnPumpAway());
        context.startService(serviceintent);

        MedtronicCnlAlarmManager.restartAlarm();
    }
}
