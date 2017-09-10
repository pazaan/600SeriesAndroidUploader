package info.nightscout.android.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Created by John on 4.9.17.
 */

public class AutoStart extends BroadcastReceiver {
    private static final String TAG = AutoStart.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Service auto starter, starting!");

        ServiceStarter.newStart(context);

//        CollectionServiceStarter.newStart(context);
//        PlusSyncService.startSyncService(context,"AutoStart");
    }
}
