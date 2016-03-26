package com.nightscout.android.medtronic.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask.Status;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.nightscout.android.R;
import com.nightscout.android.USB.UsbHidDriver;
import com.nightscout.android.medtronic.MedtronicCNLReader;
import com.nightscout.android.service.AbstractService;
import com.nightscout.android.upload.UploadHelper;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

@SuppressLint("NewApi")
public class MedtronicCNLService extends AbstractService {
    public static final int MSG_STATUS = 1;

    public final static int USB_VID = 0x1a79;
    public final static int USB_PID = 0x6210;

    private UsbHidDriver mHidDevice;
    private Timer mTimer = new Timer();
    private static final String TAG = MedtronicCNLService.class.getSimpleName();
    private Context mContext;
    private NotificationManager nm;
    private final static long FIVE_MINS_MS = 300000L;
    private UploadHelper mUploader;
    private WifiManager mWifiManager;
    private UsbManager mUsbManager;

    @Override
    public void onStartService() {
        Log.i(TAG, "onStartService called");
        mContext = this.getBaseContext();
        mWifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
        mUsbManager = (UsbManager) this.getSystemService(Context.USB_SERVICE);

        mTimer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                doReadAndUpload();
            }
        }, 0, FIVE_MINS_MS);
    }

    @Override
    public void onStopService() {
        Log.d(TAG, "onStopService called");

        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }

        if (nm != null) {
            nm.cancelAll();
            nm = null;
        }

        if (mHidDevice != null) {
            Log.i(TAG, "Closing serial device...");
            mHidDevice.close();
            mHidDevice = null;
        }
    }

    @Override
    public void onReceiveMessage(Message msg) {

    }

    protected void doReadAndUpload() {
        mUploader = new UploadHelper(getBaseContext());
        mHidDevice = UsbHidDriver.acquire(mUsbManager, USB_VID, USB_PID);

        if( !isOnline() ) {
            displayMessage("NET connection error");
        } else if ( mHidDevice == null ) {
            displayMessage("CNL connection error");
        } else {
            try {
                mHidDevice.open();
            } catch (Exception e) {
                Log.e(TAG, "Unable to open serial device", e);
            }

            // Go get the data
            MedtronicCNLReader cnlReader = new MedtronicCNLReader(mHidDevice);

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

            try {
                send(Message.obtain(null, MSG_STATUS, "Connecting to the Contour CareLink Next..."));
                cnlReader.requestDeviceInfo();
                cnlReader.enterControlMode();
                cnlReader.enterPassthroughMode();
                cnlReader.openConnection();
                cnlReader.requestReadInfo();
                send(Message.obtain(null, MSG_STATUS, "Connected to Contour CareLink Next."));
            } catch (IOException e) {
                Log.e(TAG, "Error getting BGLs", e);
            }

            //mUploader.execute(dexcomReader.mRD[dexcomReader.mRD.length - 1]);

            if (prefs.getBoolean("EnableWifiHack", false)) {
                doWifiHack();
            }
        }
    }

    private void doWifiHack() {
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            //Interesting case: location with lousy wifi
            //toggle it off to use cellular
            //toggle back on for next try
            public void run() {
                Status dataUp = mUploader.getStatus();
                if (dataUp == Status.RUNNING) {
                    mUploader.cancel(true);

                    if (mWifiManager.isWifiEnabled()) {
                        mWifiManager.setWifiEnabled(false);
                        try {
                            Thread.sleep(2500);
                        } catch (InterruptedException e) {
                            Log.e(TAG, "Sleep after setWifiEnabled(false) interrupted", e);
                        }
                        mWifiManager.setWifiEnabled(true);
                        try {
                            Thread.sleep(2500);
                        } catch (InterruptedException e) {
                            Log.e(TAG, "Sleep after setWifiEnabled(true) interrupted", e);
                        }
                    }
                }
            }
        }, 22500);
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

    private void displayMessage(String message) {
        Toast toast = Toast.makeText(getBaseContext(), message, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.CENTER, 0, 0);
        LinearLayout toastLayout = (LinearLayout) toast.getView();
        TextView toastTV = (TextView) toastLayout.getChildAt(0);
        if (toastTV != null) {
            toastTV.setTextSize(20);
            toastTV.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        }
        toast.show();
    }

    private void showNotification() {
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // In this sample, we'll use the same text for the ticker and the expanded notification
        // Set the icon, scrolling text and timestamp
        // The PendingIntent to launch our activity if the user selects this notification
        // Set the info for the views that show in the notification panel.

        String text = "Test service, yo";

        nm = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification n = new Notification.Builder(mContext)
                .setDefaults(Notification.DEFAULT_ALL)
                .setContentTitle("Devices time mismatch!")
                .setStyle(new Notification.BigTextStyle()
                        .bigText(text))
                .setContentText("Check that devices times match")
                .setTicker("Devices time mismatch!")
                .setSmallIcon(R.drawable.ic_action_warning)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher))
                .getNotification();
        nm.notify(R.string.app_name, n);
    }
}
