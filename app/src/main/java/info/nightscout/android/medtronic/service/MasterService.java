package info.nightscout.android.medtronic.service;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.BatteryManager;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import info.nightscout.android.R;
import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.MainActivity;
import info.nightscout.android.medtronic.StatusNotification;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.utils.ConfigurationStore;
import info.nightscout.android.utils.DataStore;
import info.nightscout.android.utils.StatusMessage;
import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;

import static android.support.v4.app.NotificationCompat.VISIBILITY_PUBLIC;

/**
 * Created by John on 13.9.17.
 */

public class MasterService extends Service {
    private static final String TAG = MasterService.class.getSimpleName();

    private UsbHidDriver mHidDevice;
    private Context mContext;
    private UsbManager mUsbManager;
    private DataStore dataStore = DataStore.getInstance();
    private ConfigurationStore configurationStore = ConfigurationStore.getInstance();
    private DateFormat dateFormatter = new SimpleDateFormat("HH:mm:ss", Locale.US);
    private DateFormat dateFormatterNote = new SimpleDateFormat("E HH:mm", Locale.US);
    private Realm realm;
    private long pumpOffset;

    private long testkill = 0;

    public static final int USB_DISCONNECT_NOFICATION_ID = 1;
    private static final int SERVICE_NOTIFICATION_ID = 2;

    private CnlIntentMessageReceiver cnlIntentMessageReceiver = new CnlIntentMessageReceiver();
    private boolean commsActive = false;
    private boolean commsDestroy = false;

    private StatusMessage statusMessage = StatusMessage.getInstance();
    private BatteryReceiver batteryReceiver = new BatteryReceiver();
    private UsbReceiver usbReceiver = new UsbReceiver();

    private StatusNotification statusNotification = StatusNotification.getInstance();

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "onCreate called");
        statusMessage.add(TAG + " onCreate called");

        mContext = this.getBaseContext();
        mUsbManager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);
/*
        IntentFilter cnlIntentMessageFilter = new IntentFilter();
        cnlIntentMessageFilter.addAction(MedtronicCnlIntentService.Constants.ACTION_READ_PUMP);
        registerReceiver(cnlIntentMessageReceiver, cnlIntentMessageFilter);
*/
        LocalBroadcastManager.getInstance(this).registerReceiver(
                cnlIntentMessageReceiver,
                new IntentFilter(MedtronicCnlIntentService.Constants.ACTION_READ_PUMP));


        IntentFilter batteryIntentFilter = new IntentFilter();
        batteryIntentFilter.addAction(Intent.ACTION_BATTERY_LOW);
        batteryIntentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
        batteryIntentFilter.addAction(Intent.ACTION_BATTERY_OKAY);
        registerReceiver(batteryReceiver, batteryIntentFilter);

        IntentFilter usbIntentFilter = new IntentFilter();
        usbIntentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        usbIntentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        usbIntentFilter.addAction(MedtronicCnlIntentService.Constants.ACTION_USB_PERMISSION);
        registerReceiver(usbReceiver, usbIntentFilter);
        LocalBroadcastManager.getInstance(this).registerReceiver(
                usbReceiver,
                new IntentFilter(MedtronicCnlIntentService.Constants.ACTION_NO_USB_PERMISSION));

        // setup self handling alarm receiver
        MedtronicCnlAlarmManager.setContext(mContext);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.d(TAG, "onDestroy called");
        statusMessage.add(TAG + " onDestroy called");

        if (commsActive) {
            Log.d(TAG, "comms are active!!!");
            statusMessage.add(TAG + " comms are active!!!");
            commsDestroy = true;
        } else {

            if (mHidDevice != null) {
                Log.i(TAG, "Closing serial device...");
                mHidDevice.close();
                mHidDevice = null;
            }

            statusNotification.endNotification();
        }

        MedtronicCnlAlarmManager.cancelAlarm();

        LocalBroadcastManager.getInstance(this).unregisterReceiver(usbReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(usbReceiver);
        unregisterReceiver(usbReceiver);
        unregisterReceiver(batteryReceiver);
//        unregisterReceiver(cnlIntentMessageReceiver);
    }

    @Override
    public void onTaskRemoved(Intent intent) {
        Log.i(TAG, "onTaskRemoved called");
        statusMessage.add(TAG + " onTaskRemoved, comms active=" + commsActive);
    }

    @Override
    public void onLowMemory() {
        Log.i(TAG, "onLowMemory called");
        statusMessage.add(TAG + " onLowMemory, comms active=" + commsActive);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Received start id " + startId + ": " + intent);
        statusMessage.add(TAG + " Received start id " + startId + ": " + (intent == null ? "null" : ""));

        if (intent == null) {
            // do nothing and return
            return START_STICKY;
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        Notification notification = new NotificationCompat.Builder(this)
                .setContentTitle("600 Series Uploader")
                .setSmallIcon(R.drawable.ic_launcher)
                .setVisibility(VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent)
                .setTicker("600 Series Nightscout Uploader")
                .build();
        startForeground(SERVICE_NOTIFICATION_ID, notification);

        statusNotification.initNotification(mContext);

        startCgm();

        Log.i(TAG, "Starting in foreground mode");
        statusMessage.add(TAG + " Starting in foreground mode");

        return START_STICKY;
    }

    private class CnlIntentMessageReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

//            statusMessage.add("got it");
            killer();

//            commsActive = true;
//            doitnow();
//            commsActive = false;
        }
//    }

//    private void doitnow () {
//        startService(new Intent(this, MedtronicCnlIntentService.class));
    }


    private void killer() {
        if (++testkill >= 30) {
            Log.d(TAG, "!!! kill with fire !!!");
            statusMessage.add("!!! kill with fire !!!");
/*
        if (commsActive) {
            Log.d(TAG, "onDestroy comms are active!!!");
            statusMessage.add("onDestroy comms are active!!!");
            commsDestroy = true;
        } else {

            if (mHidDevice != null) {
                Log.i(TAG, "Closing serial device...");
                mHidDevice.close();
                mHidDevice = null;
            }

            statusNotification.endNotification();
        }
*/
            MedtronicCnlAlarmManager.cancelAlarm();

            LocalBroadcastManager.getInstance(this).unregisterReceiver(cnlIntentMessageReceiver);
            LocalBroadcastManager.getInstance(this).unregisterReceiver(usbReceiver);
            unregisterReceiver(usbReceiver);
            unregisterReceiver(batteryReceiver);
//            unregisterReceiver(cnlIntentMessageReceiver);

            if (mHidDevice != null) {
                Log.i(TAG, "Closing serial device...");
                mHidDevice.close();
                mHidDevice = null;
            }

            statusNotification.endNotification();

            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }

    private void startCgmService() {
        startCgmServiceDelayed(0);
    }

    private void startCgmServiceDelayed(long delay) {
        startCgm();
    }

    private void startCgm() {
        long now = System.currentTimeMillis();
        long start = now + 1000;

        realm = Realm.getDefaultInstance();

        RealmResults<PumpStatusEvent> results = realm.where(PumpStatusEvent.class)
                .greaterThan("eventDate", new Date(System.currentTimeMillis() - (24 * 60 * 1000)))
                .equalTo("validCGM", true)
                .findAllSorted("cgmDate", Sort.DESCENDING);

        if (results.size() > 0) {
            long timeLastCGM = results.first().getCgmDate().getTime();
            if (now - timeLastCGM < MedtronicCnlIntentService.POLL_GRACE_PERIOD_MS + MedtronicCnlIntentService.POLL_PERIOD_MS)
                start = timeLastCGM + MedtronicCnlIntentService.POLL_GRACE_PERIOD_MS + MedtronicCnlIntentService.POLL_PERIOD_MS;
        }

        MedtronicCnlAlarmManager.setAlarm(start);
        statusNotification.updateNotification(start);

        if (!realm.isClosed()) realm.close();
    }

    private class BatteryReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context arg0, Intent arg1) {
            if (arg1.getAction().equalsIgnoreCase(Intent.ACTION_BATTERY_LOW)
                    || arg1.getAction().equalsIgnoreCase(Intent.ACTION_BATTERY_CHANGED)
                    || arg1.getAction().equalsIgnoreCase(Intent.ACTION_BATTERY_OKAY)) {
                dataStore.setUploaderBatteryLevel(arg1.getIntExtra(BatteryManager.EXTRA_LEVEL, 0));
            }
        }
    }


    private class UsbReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (MedtronicCnlIntentService.Constants.ACTION_USB_PERMISSION.equals(action)) {
                boolean permissionGranted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                if (permissionGranted) {
                    Log.d(TAG, "Got permission to access USB");
                    statusMessage.add(MedtronicCnlIntentService.ICON_INFO + "Got permission to access USB.");
                    startCgmService();
                } else {
                    Log.d(TAG, "Still no permission for USB. Waiting...");
                    waitForUsbPermission();
                }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                Log.d(TAG, "USB plugged in");
//                if (mEnableCgmService) {
                clearDisconnectionNotification();
//                }
                dataStore.clearAllCommsErrors();
                statusMessage.add(MedtronicCnlIntentService.ICON_INFO + "Contour Next Link plugged in.");
                if (hasUsbPermission()) {
                    // Give the USB a little time to warm up first
                    startCgmServiceDelayed(MedtronicCnlIntentService.USB_WARMUP_TIME_MS);
                } else {
                    Log.d(TAG, "No permission for USB. Waiting.");
                    statusMessage.add(MedtronicCnlIntentService.ICON_INFO + "Waiting for USB permission.");
                    waitForUsbPermission();
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                Log.d(TAG, "USB unplugged");
//                if (mEnableCgmService) {
                showDisconnectionNotification("USB Error", "Contour Next Link unplugged.");
                statusMessage.add(MedtronicCnlIntentService.ICON_WARN + "USB error. Contour Next Link unplugged.");
//                }
            } else if (MedtronicCnlIntentService.Constants.ACTION_NO_USB_PERMISSION.equals(action)) {
                Log.d(TAG, "No permission to read the USB device.");
                statusMessage.add(MedtronicCnlIntentService.ICON_INFO + "Requesting USB permission.");
                requestUsbPermission();
            }
        }
    }

    private boolean hasUsbPermission() {
        UsbManager usbManager = (UsbManager) this.getSystemService(Context.USB_SERVICE);
        UsbDevice cnlDevice = UsbHidDriver.getUsbDevice(usbManager, MedtronicCnlIntentService.USB_VID, MedtronicCnlIntentService.USB_PID);

        return !(usbManager != null && cnlDevice != null && !usbManager.hasPermission(cnlDevice));
    }

    private void waitForUsbPermission() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent permissionIntent = new Intent(MedtronicCnlIntentService.Constants.ACTION_USB_PERMISSION);
        permissionIntent.putExtra(UsbManager.EXTRA_PERMISSION_GRANTED, hasUsbPermission());
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, permissionIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1000L, pendingIntent);
    }

    private void requestUsbPermission() {
        if (!hasUsbPermission()) {
            UsbManager usbManager = (UsbManager) this.getSystemService(Context.USB_SERVICE);
            UsbDevice cnlDevice = UsbHidDriver.getUsbDevice(usbManager, MedtronicCnlIntentService.USB_VID, MedtronicCnlIntentService.USB_PID);
            PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(MedtronicCnlIntentService.Constants.ACTION_USB_PERMISSION), 0);
            usbManager.requestPermission(cnlDevice, permissionIntent);
        }
    }

    private void showDisconnectionNotification(String title, String message) {
        android.support.v7.app.NotificationCompat.Builder mBuilder =
                (android.support.v7.app.NotificationCompat.Builder) new android.support.v7.app.NotificationCompat.Builder(this)
                        .setPriority(android.support.v7.app.NotificationCompat.PRIORITY_MAX)
                        .setSmallIcon(R.drawable.ic_launcher) // FIXME - this icon doesn't follow the standards (ie, it has black in it)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setTicker(message)
                        .setVibrate(new long[]{1000, 1000, 1000, 1000, 1000});
        // Creates an explicit intent for an Activity in your app
        Intent resultIntent = new Intent(this, MainActivity.class);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        // Adds the back stack for the Intent (but not the Intent itself)
        stackBuilder.addParentStack(MainActivity.class);
        // Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(
                        0,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
        mBuilder.setContentIntent(resultPendingIntent);
        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(USB_DISCONNECT_NOFICATION_ID, mBuilder.build());
    }

    private void clearDisconnectionNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(USB_DISCONNECT_NOFICATION_ID);
    }

}