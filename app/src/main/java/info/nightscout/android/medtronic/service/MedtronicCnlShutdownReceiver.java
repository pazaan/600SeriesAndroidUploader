package info.nightscout.android.medtronic.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * Created by Pogman on 1.10.17.
 */

public class MedtronicCnlShutdownReceiver extends BroadcastReceiver {
    private static final String TAG = MedtronicCnlShutdownReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "sending shutdown request to CNL service");
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(new Intent(context, MedtronicCnlService.class)
                        .setAction(MasterService.Constants.ACTION_CNL_SHUTDOWN));
            } else {
                context.startService(new Intent(context, MedtronicCnlService.class)
                        .setAction(MasterService.Constants.ACTION_CNL_SHUTDOWN));
            }
        } catch (Exception ignored) {
        }
    }
}