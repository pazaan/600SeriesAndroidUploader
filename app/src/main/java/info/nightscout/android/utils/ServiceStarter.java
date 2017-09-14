package info.nightscout.android.utils;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import info.nightscout.android.UploaderApplication;
import info.nightscout.android.medtronic.service.MasterService;
import info.nightscout.android.medtronic.service.MedtronicCnlIntentService;

/**
 * Created by John on 4.9.17.
 */

public class ServiceStarter {
    private static final String TAG = ServiceStarter.class.getSimpleName();

    private Context mContext;

    public static void newStart(Context context) {
        Log.d(TAG, "newStart(Context context)");
        ServiceStarter serviceStarter = new ServiceStarter(context);
        serviceStarter.start(context);
    }

    public void start(Context context, String collection_method) {
        Log.d(TAG, "start(Context context, String collection_method)");
        this.mContext = context;
        UploaderApplication.checkAppContext(context);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

//        stopCNLService();
        startCNLService();

    }

    public void start(Context context) {
        Log.d(TAG, "start(Context context)");
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String collection_method = "CNLReader";

        start(context, collection_method);
    }

    public ServiceStarter(Context context) {
        Log.d(TAG, "ServiceStarter(Context context)");
        if (context == null) context = UploaderApplication.getAppContext();
        this.mContext = context;
    }


    private void startCNLService() {
        Log.d(TAG, "starting CNL service");
//        this.mContext.startService(new Intent(this.mContext, MedtronicCnlIntentService.class));
        this.mContext.startService(new Intent(this.mContext, MasterService.class));
    }

    private void stopCNLService() {
        Log.d(TAG, "stopping CNL service");
//        this.mContext.stopService(new Intent(this.mContext, MedtronicCnlIntentService.class));
        this.mContext.stopService(new Intent(this.mContext, MasterService.class));
    }

}