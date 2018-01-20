package info.nightscout.android.medtronic.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
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
        Log.d(TAG, "Received broadcast message at " + new Date(System.currentTimeMillis()));

        context.startService(new Intent(context, MedtronicCnlService.class)
                .setAction(MasterService.Constants.ACTION_CNL_READPUMP)
        );

//        MedtronicCnlAlarmManager.restartAlarm();
    }
}
