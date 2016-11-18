package info.nightscout.android.xdrip_plus;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;


/**
 * Created by jamorham on 17/11/2016.
 */
public class XDripPlusUploadReceiver extends WakefulBroadcastReceiver {
    private static final String TAG = XDripPlusUploadReceiver.class.getSimpleName();

    @Override
    public void onReceive(final Context context, Intent intent) {
        // Start the IntentService
        Log.d(TAG, "Received broadcast message");
        Intent service = new Intent(context, XDripPlusUploadIntentService.class);
        startWakefulService(context, service);
    }
}
