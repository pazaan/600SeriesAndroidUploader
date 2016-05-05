package com.nightscout.android.medtronic.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
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

import com.nightscout.android.R;
import com.nightscout.android.USB.UsbHidDriver;
import com.nightscout.android.dexcom.DexcomG4Activity;
import com.nightscout.android.medtronic.MedtronicCNLReader;
import com.nightscout.android.medtronic.message.ChecksumException;
import com.nightscout.android.medtronic.message.EncryptionException;
import com.nightscout.android.medtronic.message.UnexpectedMessageException;
import com.nightscout.android.service.AbstractService;
import com.nightscout.android.upload.Medtronic640gPumpRecord;
import com.nightscout.android.upload.UploadHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeoutException;

@SuppressLint("NewApi")
public class MedtronicCNLService extends AbstractService {
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
    private SharedPreferences prefs;

    @Override
    public void onStartService() {
        Log.i(TAG, "onStartService called");
        mContext = this.getBaseContext();
        mWifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
        mUsbManager = (UsbManager) this.getSystemService(Context.USB_SERVICE);
        prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        // Add a small start delay - for some reason, having no start delay causes initial
        // binding/rendering issues
        mTimer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                doReadAndUpload();
            }
        }, 250, FIVE_MINS_MS);
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

        // Load the initial data to the display
        Medtronic640gPumpRecord pumpRecord = loadData();
        send(Message.obtain(null, DexcomG4Activity.DexcomG4ActivityHandler.MSG_DATA, pumpRecord));

        if (!isOnline()) {
            String title = "Internet connection error";
            String msg = "Please check that you're connected to the Internet";
            //showNotification(title, msg);
            send(Message.obtain(null, DexcomG4Activity.DexcomG4ActivityHandler.MSG_ERROR, title + "\n" + msg));
        } else if (mHidDevice == null) {
            String title = "USB connection error";
            String msg = "Is the Bayer Contour NextLink plugged in?";
            //showNotification(title, msg);
            send(Message.obtain(null, DexcomG4Activity.DexcomG4ActivityHandler.MSG_ERROR, title + "\n" + msg));
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
                send(Message.obtain(null, DexcomG4Activity.DexcomG4ActivityHandler.MSG_STATUS, "Connecting to the Contour CareLink Next..."));
                cnlReader.requestDeviceInfo();
                cnlReader.enterControlMode();
                cnlReader.enterPassthroughMode();
                cnlReader.openConnection();
                cnlReader.requestReadInfo();
                byte radioChannel = cnlReader.negotiateChannel();
                if (radioChannel == 0) {
                    send(Message.obtain(null, DexcomG4Activity.DexcomG4ActivityHandler.MSG_ERROR, "Could not communicate with the 640g. Are you near the pump?"));
                } else {
                    send(Message.obtain(null, DexcomG4Activity.DexcomG4ActivityHandler.MSG_STATUS, String.format("Connected to Contour CareLink Next on channel %d.", (int) radioChannel)));
                    cnlReader.beginEHSMSession();

                    cnlReader.getPumpTime(pumpRecord);
                    cnlReader.getPumpStatus(pumpRecord);
                    writeData(pumpRecord);
                    send(Message.obtain(null, DexcomG4Activity.DexcomG4ActivityHandler.MSG_DATA, pumpRecord));
                    cnlReader.endEHSMSession();
                }
                cnlReader.closeConnection();
                cnlReader.endPassthroughMode();
                cnlReader.endControlMode();
            } catch (IOException e) {
                Log.e(TAG, "Error getting BGLs", e);
                send(Message.obtain(null, DexcomG4Activity.DexcomG4ActivityHandler.MSG_ERROR, "Error connecting to Contour CareLink Next."));
            } catch (ChecksumException e) {
                Log.e(TAG, "Checksum error", e);
                send(Message.obtain(null, DexcomG4Activity.DexcomG4ActivityHandler.MSG_ERROR, "Checksum error getting message from the Contour CareLink Next."));
            } catch (EncryptionException e) {
                Log.e(TAG, "Encryption exception", e);
                send(Message.obtain(null, DexcomG4Activity.DexcomG4ActivityHandler.MSG_ERROR, "Error decrypting messages from Contour CareLink Next."));
            } catch (TimeoutException e) {
                Log.e(TAG, "Timeout communicating with Contour", e);
                send(Message.obtain(null, DexcomG4Activity.DexcomG4ActivityHandler.MSG_ERROR, "Timeout communicating with the Contour CareLink Next."));
            } catch (UnexpectedMessageException e) {
                Log.e(TAG, "Unexpected Message", e);
                send(Message.obtain(null, DexcomG4Activity.DexcomG4ActivityHandler.MSG_ERROR, "Communication Error: " + e.getMessage()));
            }

            mUploader.execute(pumpRecord);

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

    private void showNotification(String title, String message) {
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // The PendingIntent to launch our activity if the user selects this notification
        nm = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, DexcomG4Activity.class), 0);
        Notification n = new Notification.Builder(mContext)
                .setDefaults(Notification.DEFAULT_ALL)
                .setContentTitle(title)
                .setStyle(new Notification.BigTextStyle()
                        .bigText(message))
                .setContentText(message)
                .setTicker(message)
                        //.setSmallIcon(R.drawable.ic_action_warning)
                .setSmallIcon(R.drawable.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher))
                .setContentIntent(contentIntent)
                .getNotification();
        nm.notify(R.string.app_name, n);
    }

    private void writeData(Medtronic640gPumpRecord mostRecentData) {
        //Write most recent data
        try {
            Context context = getBaseContext();
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(new File(context.getFilesDir(), "save.bin"))); //Select where you wish to save the file...
            oos.writeObject(mostRecentData); // write the class as an 'object'
            oos.flush(); // flush the stream to insure all of the information was written to 'save.bin'
            oos.close();// close the stream
        } catch (Exception e) {
            Log.e(TAG, "write to OutputStream failed", e);
        }
    }

    private Medtronic640gPumpRecord loadData() {
        ObjectInputStream ois = null;
        try {
            Context context = getBaseContext();
            ois = new ObjectInputStream(new FileInputStream(new File(context.getFilesDir(), "save.bin")));
            Object o = ois.readObject();
            ois.close();
            return (Medtronic640gPumpRecord) o;
        } catch (Exception ex) {
            Log.w(TAG, " unable to load Medtronic640g data");
            try {
                if (ois != null)
                    ois.close();
            } catch (Exception e) {
                Log.e(TAG, " Error closing ObjectInputStream");
            }
        }
        return new Medtronic640gPumpRecord();
    }
}
