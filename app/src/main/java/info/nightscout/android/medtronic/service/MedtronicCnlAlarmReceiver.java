package info.nightscout.android.medtronic.service;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;

/**
 * Created by lgoedhart on 14/07/2016.
 */
public class MedtronicCnlAlarmReceiver extends WakefulBroadcastReceiver {
    private static final String TAG = MedtronicCnlAlarmReceiver.class.getSimpleName();

    @Override
    public void onReceive(final Context context, Intent intent) {
        // Start the IntentService
        Log.d(TAG, "Received broadcast message");
        Intent service = new Intent(context, MedtronicCnlIntentService.class);
        startWakefulService(context, service);
    }
}
