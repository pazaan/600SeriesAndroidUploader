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
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;

import java.util.Date;

import info.nightscout.android.R;
import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.MainActivity;
import info.nightscout.android.medtronic.StatusNotification;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.upload.nightscout.NightscoutUploadReceiver;
import info.nightscout.android.utils.StatusMessage;
import info.nightscout.android.xdrip_plus.XDripPlusUploadReceiver;
import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;

import static android.support.v4.app.NotificationCompat.VISIBILITY_PUBLIC;

/**
 * Created by John on 13.9.17.
 */

public class MasterService extends Service {
    private static final String TAG = MasterService.class.getSimpleName();

    // TODO - use a message type and insert icon as part of ui status message handling
    public static final String ICON_WARN = "{ion_alert_circled} ";
    public static final String ICON_BGL = "{ion_waterdrop} ";
    public static final String ICON_USB = "{ion_usb} ";
    public static final String ICON_INFO = "{ion_information_circled} ";
    public static final String ICON_HELP = "{ion_ios_lightbulb} ";
    public static final String ICON_SETTING = "{ion_android_settings} ";
    public static final String ICON_HEART = "{ion_heart} ";
    public static final String ICON_LOW = "{ion_battery_low} ";
    public static final String ICON_FULL = "{ion_battery_full} ";
    public static final String ICON_CGM = "{ion_ios_pulse_strong} ";
    public static final String ICON_SUSPEND = "{ion_pause} ";
    public static final String ICON_RESUME = "{ion_play} ";
    public static final String ICON_BOLUS = "{ion_skip_forward} ";
    public static final String ICON_BASAL = "{ion_skip_forward} ";
    public static final String ICON_CHANGE = "{ion_android_hand} ";
    public static final String ICON_BELL = "{ion_android_notifications} ";
    public static final String ICON_NOTE = "{ion_clipboard} ";

    public static final int USB_DISCONNECT_NOFICATION_ID = 1;
    public static final int SERVICE_NOTIFICATION_ID = 2;

    private Context mContext;
    private Realm realm;

    private MasterServiceReceiver masterServiceReceiver = new MasterServiceReceiver();
    private StatusMessageReceiver statusMessageReceiver = new StatusMessageReceiver();
    private BatteryReceiver batteryReceiver = new BatteryReceiver();
    private UsbReceiver usbReceiver = new UsbReceiver();

    private StatusMessage statusMessage = StatusMessage.getInstance();
    private StatusNotification statusNotification = StatusNotification.getInstance();

    private static int uploaderBatteryLevel = 0;
    private static boolean serviceActive = true;

    public static boolean commsActive = false;

    public static int getUploaderBatteryLevel() {return uploaderBatteryLevel;}

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

        IntentFilter masterServiceIntentFilter = new IntentFilter();
        masterServiceIntentFilter.addAction(Constants.ACTION_CNL_COMMS_FINISHED);
        masterServiceIntentFilter.addAction(Constants.ACTION_STOP_SERVICE);
        masterServiceIntentFilter.addAction(Constants.ACTION_READ_NOW);
        //masterServiceIntentFilter.addAction(MedtronicCnlIntentService.Constants.ACTION_STATUS_MESSAGE);
        registerReceiver(masterServiceReceiver, masterServiceIntentFilter);

        registerReceiver(
                statusMessageReceiver,
                new IntentFilter(Constants.ACTION_STATUS_MESSAGE));

        IntentFilter batteryIntentFilter = new IntentFilter();
        batteryIntentFilter.addAction(Intent.ACTION_BATTERY_LOW);
        batteryIntentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
        batteryIntentFilter.addAction(Intent.ACTION_BATTERY_OKAY);
        registerReceiver(batteryReceiver, batteryIntentFilter);

        IntentFilter usbIntentFilter = new IntentFilter();
        usbIntentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        usbIntentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        usbIntentFilter.addAction(Constants.ACTION_USB_PERMISSION);
        usbIntentFilter.addAction(Constants.ACTION_NO_USB_PERMISSION);
        registerReceiver(usbReceiver, usbIntentFilter);

        // setup self handling alarm receiver
        MedtronicCnlAlarmManager.setContext(mContext);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.d(TAG, "onDestroy called");
        statusMessage.add(TAG + " onDestroy called");

        statusNotification.endNotification();

        MedtronicCnlAlarmManager.cancelAlarm();

        unregisterReceiver(statusMessageReceiver);
        unregisterReceiver(usbReceiver);
        unregisterReceiver(batteryReceiver);
        unregisterReceiver(masterServiceReceiver);
    }

    @Override
    public void onTaskRemoved(Intent intent) {
        Log.i(TAG, "onTaskRemoved called");
        statusMessage.add(TAG + " onTaskRemoved");

//        statusNotification.endNotification();
//        MedtronicCnlAlarmManager.cancelAlarm();

//        MedtronicCnlAlarmManager.setAlarmAfterMillis(10000);

/*
        statusNotification.endNotification();

        MedtronicCnlAlarmManager.cancelAlarm();

        unregisterReceiver(statusMessageReceiver);
        unregisterReceiver(usbReceiver);
        unregisterReceiver(batteryReceiver);
        unregisterReceiver(masterServiceReceiver);
*/
    }

    @Override
    public void onLowMemory() {
        Log.i(TAG, "onLowMemory called");
        statusMessage.add(TAG + " onLowMemory");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Received start id " + startId + ": " + intent);
        statusMessage.add(TAG + " Received start id " + startId + ": " + (intent == null ? "null" : ""));
/*
        if (intent == null) {
            // do nothing and return
            return START_STICKY;
        }
*/
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

        serviceActive = true;

        return START_STICKY;
    }

    private class MasterServiceReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "onReceive : " + action);

            if (Constants.ACTION_CNL_COMMS_FINISHED.equals(action)) {
                commsActive = false;

                if (serviceActive) {
                    Log.d(TAG, "onReceive : cnl comms finished");

                    uploadPollResults();

                    long nextpoll = intent.getLongExtra("nextpoll", 0);
                    statusNotification.updateNotification(nextpoll);

                    if (nextpoll > 0) {
                        MedtronicCnlAlarmManager.setAlarm(nextpoll);
                    } else {
                        MedtronicCnlAlarmManager.cancelAlarm();
                    }

                } else {
                    Log.d(TAG, "onReceive : stopping master service");
                    stopSelf();
                }

            } else if (Constants.ACTION_STOP_SERVICE.equals(action)) {
                MedtronicCnlAlarmManager.cancelAlarm();
                serviceActive = false;
                if (!commsActive)
                    stopSelf();

            } else if (Constants.ACTION_READ_NOW.equals(action)) {
                MedtronicCnlAlarmManager.setAlarm(System.currentTimeMillis() + 1000);
            }

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

    private class StatusMessageReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String message = intent.getStringExtra(Constants.EXTENDED_DATA);
            Log.i(TAG, "Message Receiver: " + message);
            statusMessage.add(message);
        }
    }

    private class BatteryReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context arg0, Intent arg1) {
            if (arg1.getAction().equalsIgnoreCase(Intent.ACTION_BATTERY_LOW)
                    || arg1.getAction().equalsIgnoreCase(Intent.ACTION_BATTERY_CHANGED)
                    || arg1.getAction().equalsIgnoreCase(Intent.ACTION_BATTERY_OKAY)) {
                uploaderBatteryLevel = arg1.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            }
        }
    }

    private class UsbReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Constants.ACTION_USB_PERMISSION.equals(action)) {
                boolean permissionGranted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                if (permissionGranted) {
                    Log.d(TAG, "Got permission to access USB");
                    statusMessage.add(ICON_INFO + "Got permission to access USB.");
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
////                dataStore.clearAllCommsErrors();
                statusMessage.add(ICON_INFO + "Contour Next Link plugged in.");
                if (hasUsbPermission()) {
                    // Give the USB a little time to warm up first
                    startCgmServiceDelayed(MedtronicCnlIntentService.USB_WARMUP_TIME_MS);
                } else {
                    Log.d(TAG, "No permission for USB. Waiting.");
                    statusMessage.add(ICON_INFO + "Waiting for USB permission.");
                    waitForUsbPermission();
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                Log.d(TAG, "USB unplugged");
//                if (mEnableCgmService) {
                showDisconnectionNotification("USB Error", "Contour Next Link unplugged.");
                statusMessage.add(ICON_WARN + "USB error. Contour Next Link unplugged.");
//                }
            } else if (Constants.ACTION_NO_USB_PERMISSION.equals(action)) {
                Log.d(TAG, "No permission to read the USB device.");
                statusMessage.add(ICON_WARN + "No permission to read the USB device.");
                statusMessage.add(ICON_HELP + "Unplug/plug the Contour Next Link and select 'use by default for this device' to make permission permanent.");

//                statusMessage.add(MedtronicCnlIntentService.ICON_INFO + "Requesting USB permission.");
//                requestUsbPermission();
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
        Intent permissionIntent = new Intent(Constants.ACTION_USB_PERMISSION);
        permissionIntent.putExtra(UsbManager.EXTRA_PERMISSION_GRANTED, hasUsbPermission());
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, permissionIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1000L, pendingIntent);
    }

    private void requestUsbPermission() {
        if (!hasUsbPermission()) {
            UsbManager usbManager = (UsbManager) this.getSystemService(Context.USB_SERVICE);
            UsbDevice cnlDevice = UsbHidDriver.getUsbDevice(usbManager, MedtronicCnlIntentService.USB_VID, MedtronicCnlIntentService.USB_PID);
            PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(Constants.ACTION_USB_PERMISSION), 0);
            usbManager.requestPermission(cnlDevice, permissionIntent);
        }
    }

    private void showDisconnectionNotification(String title, String message) {
        android.support.v7.app.NotificationCompat.Builder mBuilder =
                (android.support.v7.app.NotificationCompat.Builder) new android.support.v7.app.NotificationCompat.Builder(this)
                        .setPriority(android.support.v7.app.NotificationCompat.PRIORITY_MAX)
//                        .setSmallIcon(R.drawable.ic_error)
                        .setSmallIcon(android.R.drawable.stat_notify_error)
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

    public final class Constants {
        public static final String ACTION_STATUS_MESSAGE = "info.nightscout.android.medtronic.STATUS_MESSAGE";
        public static final String ACTION_CNL_COMMS_FINISHED = "info.nightscout.android.medtronic.CNL_COMMS_FINISHED";
        public static final String ACTION_STOP_SERVICE = "info.nightscout.android.medtronic.STOP_SERVICE";
        public static final String ACTION_READ_NOW = "info.nightscout.android.medtronic.READ_NOW";

        public static final String ACTION_NO_USB_PERMISSION = "info.nightscout.android.medtronic.NO_USB_PERMISSION";
        public static final String ACTION_USB_PERMISSION = "info.nightscout.android.medtronic.USB_PERMISSION";

        public static final String ACTION_USB_REGISTER = "info.nightscout.android.medtronic.USB_REGISTER";
        public static final String ACTION_UPDATE_PUMP = "info.nightscout.android.medtronic.UPDATE_PUMP";
        public static final String ACTION_UPDATE_STATUS = "info.nightscout.android.medtronic.UPDATE_STATUS";

        public static final String EXTENDED_DATA = "info.nightscout.android.medtronic.DATA";
    }

    // reliable wake alarm manager wake up for all android versions
    public static void wakeUpIntent(Context context, long wakeTime, PendingIntent pendingIntent) {
        final AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wakeTime, pendingIntent);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarm.setExact(AlarmManager.RTC_WAKEUP, wakeTime, pendingIntent);
        } else
            alarm.set(AlarmManager.RTC_WAKEUP, wakeTime, pendingIntent);
    }

    private void uploadPollResults() {
        sendToXDrip();
        uploadToNightscout();
    }

    private void sendToXDrip() {
        final Intent receiverIntent = new Intent(this, XDripPlusUploadReceiver.class);
        final long timestamp = System.currentTimeMillis() + 100L; //500L
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(this, (int) timestamp, receiverIntent, PendingIntent.FLAG_ONE_SHOT);
        Log.d(TAG, "Scheduling xDrip+ send");
        wakeUpIntent(getApplicationContext(), timestamp, pendingIntent);
    }

    private void uploadToNightscout() {
        // TODO - set status if offline or Nightscout not reachable
        Intent receiverIntent = new Intent(this, NightscoutUploadReceiver.class);
        final long timestamp = System.currentTimeMillis() + 200L; //1000L
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(this, (int) timestamp, receiverIntent, PendingIntent.FLAG_ONE_SHOT);
        Log.d(TAG, "Scheduling Nightscout upload");
        wakeUpIntent(getApplicationContext(), timestamp, pendingIntent);
    }
}