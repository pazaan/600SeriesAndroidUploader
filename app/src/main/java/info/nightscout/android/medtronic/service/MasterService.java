package info.nightscout.android.medtronic.service;

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
import android.util.Log;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import info.nightscout.android.R;
import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.UploaderApplication;
import info.nightscout.android.medtronic.MainActivity;
import info.nightscout.android.medtronic.StatusNotification;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.model.store.DataStore;
import info.nightscout.android.medtronic.UserLogMessage;
import info.nightscout.android.upload.nightscout.NightscoutUploadService;
import info.nightscout.android.xdrip_plus.XDripPlusUploadService;
import info.nightscout.urchin.Urchin;
import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;

import static android.support.v4.app.NotificationCompat.VISIBILITY_PUBLIC;
import static info.nightscout.android.medtronic.UserLogMessage.Icons.ICON_HELP;
import static info.nightscout.android.medtronic.UserLogMessage.Icons.ICON_INFO;
import static info.nightscout.android.medtronic.UserLogMessage.Icons.ICON_WARN;
import static info.nightscout.android.medtronic.service.MedtronicCnlService.POLL_GRACE_PERIOD_MS;
import static info.nightscout.android.medtronic.service.MedtronicCnlService.POLL_PERIOD_MS;
import static info.nightscout.android.medtronic.service.MedtronicCnlService.POLL_PRE_GRACE_PERIOD_MS;
import static info.nightscout.android.medtronic.service.MedtronicCnlService.POLL_RECOVERY_PERIOD_MS;
import static info.nightscout.android.medtronic.service.MedtronicCnlService.POLL_WARMUP_PERIOD_MS;

/**
 * Created by Pogman on 13.9.17.
 */

public class MasterService extends Service {
    private static final String TAG = MasterService.class.getSimpleName();

    public static final int USB_DISCONNECT_NOFICATION_ID = 1;
    public static final int SERVICE_NOTIFICATION_ID = 2;

    private Context mContext;

    private Realm realm;
    private Realm storeRealm;
    private DataStore dataStore;

    private MasterServiceReceiver masterServiceReceiver = new MasterServiceReceiver();
    private UserLogMessageReceiver userLogMessageReceiver = new UserLogMessageReceiver();
    private BatteryReceiver batteryReceiver = new BatteryReceiver();
    private UsbReceiver usbReceiver = new UsbReceiver();

    private UserLogMessage userLogMessage = UserLogMessage.getInstance();
    private StatusNotification statusNotification = StatusNotification.getInstance();

    private Urchin urchin;

    private static int uploaderBatteryLevel = 0;
    public static int getUploaderBatteryLevel() {
        return uploaderBatteryLevel;
    }

    private boolean serviceActive = true;

    private DateFormat dateFormatter = new SimpleDateFormat("HH:mm:ss", Locale.US);

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate called");
        //userLogMessage.add(TAG + " onCreate called");

        mContext = this.getBaseContext();

        storeRealm = Realm.getInstance(UploaderApplication.getStoreConfiguration());
        dataStore = storeRealm.where(DataStore.class).findFirst();

        // safety check: don't proceed until main ui has been run and datastore initialised
        if (dataStore != null) {

            realm = Realm.getDefaultInstance();

            storeRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    dataStore.setNightscoutReportTime(0);
                    dataStore.setNightscoutAvailable(false);
                    dataStore.clearAllCommsErrors();
                }
            });

            IntentFilter masterServiceIntentFilter = new IntentFilter();
            masterServiceIntentFilter.addAction(Constants.ACTION_CNL_COMMS_ACTIVE);
            masterServiceIntentFilter.addAction(Constants.ACTION_CNL_COMMS_FINISHED);
            masterServiceIntentFilter.addAction(Constants.ACTION_STOP_SERVICE);
            masterServiceIntentFilter.addAction(Constants.ACTION_READ_NOW);
            masterServiceIntentFilter.addAction(Constants.ACTION_READ_PROFILE);
            masterServiceIntentFilter.addAction(Constants.ACTION_URCHIN_UPDATE);
            registerReceiver(masterServiceReceiver, masterServiceIntentFilter);

            registerReceiver(
                    userLogMessageReceiver,
                    new IntentFilter(Constants.ACTION_USERLOG_MESSAGE));

            IntentFilter batteryIntentFilter = new IntentFilter();
            batteryIntentFilter.addAction(Intent.ACTION_BATTERY_LOW);
            batteryIntentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
            batteryIntentFilter.addAction(Intent.ACTION_BATTERY_OKAY);
            registerReceiver(batteryReceiver, batteryIntentFilter);

            IntentFilter usbIntentFilter = new IntentFilter();
            usbIntentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
            usbIntentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
            usbIntentFilter.addAction(Constants.ACTION_HAS_USB_PERMISSION);
            usbIntentFilter.addAction(Constants.ACTION_NO_USB_PERMISSION);
            registerReceiver(usbReceiver, usbIntentFilter);

            MedtronicCnlAlarmManager.setContext(mContext);

            urchin = new Urchin(mContext);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy called");

        MedtronicCnlAlarmManager.cancelAlarm();

        if (userLogMessageReceiver != null) unregisterReceiver(userLogMessageReceiver);
        if (usbReceiver != null) unregisterReceiver(usbReceiver);
        if (batteryReceiver != null) unregisterReceiver(batteryReceiver);
        if (masterServiceReceiver != null) unregisterReceiver(masterServiceReceiver);

        if (urchin != null) urchin.close();
        if (statusNotification != null) statusNotification.endNotification();

        if (storeRealm != null && !storeRealm.isClosed()) storeRealm.close();
        if (realm != null && !realm.isClosed()) realm.close();
    }

    @Override
    public void onTaskRemoved(Intent intent) {
        Log.i(TAG, "onTaskRemoved called");
    }

    @Override
    public void onLowMemory() {
        Log.i(TAG, "onLowMemory called");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Received start id " + startId + ": " + intent);
        //userLogMessage.add(TAG + " Received start id " + startId + ": " + (intent == null ? "null" : ""));

        if (dataStore == null) {
            // safety check: don't proceed until main ui has been run and datastore initialised
            stopSelf();
        }

        if (intent == null || startId == 1) {
            Log.i(TAG, "service start");
            //userLogMessage.add(TAG + " service start");

            Intent notificationIntent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
            Notification notification = new NotificationCompat.Builder(this)
                    .setContentTitle("600 Series Uploader")
                    .setSmallIcon(R.drawable.ic_notification)
                    .setVisibility(VISIBILITY_PUBLIC)
                    .setContentIntent(pendingIntent)
                    .build();
            startForeground(SERVICE_NOTIFICATION_ID, notification);

            statusNotification.initNotification(mContext);

            startCgmService();

            serviceActive = true;
        }

        return START_STICKY;
    }

    private class MasterServiceReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "onReceive : " + action);

            if (Constants.ACTION_CNL_COMMS_FINISHED.equals(action)) {

                if (serviceActive) {
                    Log.d(TAG, "onReceive : cnl comms finished");

                    uploadPollResults();

                    long nextpoll = intent.getLongExtra("nextpoll", 0);

                    if (nextpoll > 0) {
                        MedtronicCnlAlarmManager.setAlarm(nextpoll);
                        statusNotification.updateNotification(StatusNotification.NOTIFICATION.NORMAL, nextpoll);
                    } else {
                        MedtronicCnlAlarmManager.cancelAlarm();
                        statusNotification.updateNotification(StatusNotification.NOTIFICATION.ERROR);
                    }

                } else {
                    Log.d(TAG, "onReceive : stopping master service");
                    stopSelf();
                }

            } else if (Constants.ACTION_STOP_SERVICE.equals(action)) {
                MedtronicCnlAlarmManager.cancelAlarm();
                serviceActive = false;
                stopSelf();

            } else if (Constants.ACTION_READ_NOW.equals(action)) {
                MedtronicCnlAlarmManager.setAlarm(System.currentTimeMillis() + 1000L);

            } else if (Constants.ACTION_READ_PROFILE.equals(action)) {
                storeRealm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(Realm realm) {
                        dataStore.setRequestProfile(true);
                    }
                });
                MedtronicCnlAlarmManager.setAlarm(System.currentTimeMillis() + 1000L);

            } else if (Constants.ACTION_CNL_COMMS_ACTIVE.equals(action)) {
                statusNotification.updateNotification(StatusNotification.NOTIFICATION.BUSY);

            } else if (Constants.ACTION_URCHIN_UPDATE.equals(action)) {
                urchin.update();
            }

        }
    }

    private void startCgmService() {
        startCgmServiceDelayed(1000L);
    }

    private void startCgmServiceDelayed(long delay) {
        long now = System.currentTimeMillis();
        long start = nextPollTime();

        if (start - now < delay) start = now + delay;
        MedtronicCnlAlarmManager.setAlarm(start);
        statusNotification.updateNotification(StatusNotification.NOTIFICATION.NORMAL, start);

        if (start - now > 10000L)
            userLogMessage.add("Next poll due at: " + dateFormatter.format(start));
    }

    private long nextPollTime() {

        long now = System.currentTimeMillis();

        long lastRecievedEventTime;
        long lastActualEventTime;
        long nextExpectedEventTime;
        long nextRequestedPollTime;

        RealmResults<PumpStatusEvent> results = realm.where(PumpStatusEvent.class)
                .greaterThan("eventDate", new Date(System.currentTimeMillis() - (24 * 60 * 1000L)))
                .findAllSorted("eventDate", Sort.DESCENDING);

        RealmResults<PumpStatusEvent> cgmresults = results.where()
                .equalTo("cgmActive", true)
                .findAllSorted("cgmDate", Sort.DESCENDING);

        long pollOffset = dataStore.isSysEnablePollOverride() ? dataStore.getSysPollGracePeriod() : POLL_GRACE_PERIOD_MS;

        if (cgmresults.size() > 0) {

            lastRecievedEventTime = cgmresults.first().getCgmDate().getTime();

            // normalise last received cgm time to current time window
            lastActualEventTime = lastRecievedEventTime + (((now - lastRecievedEventTime) / POLL_PERIOD_MS) * POLL_PERIOD_MS);

            // check if pump has lost sensor
            if (now - lastRecievedEventTime <= 60 * 60000L
                    && cgmresults.first().getCgmRTC() != results.first().getCgmRTC())
                pollOffset = dataStore.isSysEnablePollOverride() ? dataStore.getSysPollRecoveryPeriod() : POLL_RECOVERY_PERIOD_MS;

                // check if sensor is in warmup phase
            else if (now - lastRecievedEventTime <= 120 * 60000L
                    && cgmresults.first().isCgmWarmUp())
                pollOffset = dataStore.isSysEnablePollOverride() ? dataStore.getSysPollWarmupPeriod() : POLL_WARMUP_PERIOD_MS;

            nextExpectedEventTime = lastActualEventTime + POLL_PERIOD_MS;

            if (nextExpectedEventTime - lastRecievedEventTime > POLL_PERIOD_MS
                && now < nextExpectedEventTime - POLL_PRE_GRACE_PERIOD_MS)
                nextRequestedPollTime = now;
            else
                nextRequestedPollTime = lastActualEventTime + POLL_PERIOD_MS + pollOffset;

            return nextRequestedPollTime;

        } else {

            // no cgm event available to sync with
            return now;
        }
    }

    private void uploadPollResults() {

        urchin.update();

        startService(new Intent(this, XDripPlusUploadService.class));
        startService(new Intent(this, NightscoutUploadService.class));
    }

    private class UserLogMessageReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String message = intent.getStringExtra(Constants.EXTENDED_DATA);
            Log.i(TAG, "Message Receiver: " + message);
            userLogMessage.add(message);
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

            // received from UsbActivity via OS / app requested permission dialog
            if (Constants.ACTION_HAS_USB_PERMISSION.equals(action)) {

                if (hasUsbPermission()) {
                    userLogMessage.add(ICON_INFO + "Got permission for USB.");
                    statusNotification.updateNotification(StatusNotification.NOTIFICATION.NORMAL);
                    startCgmServiceDelayed(MedtronicCnlService.USB_WARMUP_TIME_MS);
                }

                // received from OS
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                Log.d(TAG, "USB plugged in");
                userLogMessage.add(ICON_INFO + "Contour Next Link plugged in.");
                clearDisconnectionNotification();

                storeRealm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(Realm realm) {
                        dataStore.clearAllCommsErrors();
                    }
                });

                if (hasUsbPermission()) {

                    statusNotification.updateNotification(StatusNotification.NOTIFICATION.NORMAL);

                } else {
                    Log.d(TAG, "No permission for USB. Waiting.");
                    userLogMessage.add(ICON_INFO + "Waiting for USB permission.");
                }

                // received from OS
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                Log.d(TAG, "USB unplugged");
                showDisconnectionNotification("USB Error", "Contour Next Link unplugged.");
                userLogMessage.add(ICON_WARN + "USB error. Contour Next Link unplugged.");

                MedtronicCnlAlarmManager.cancelAlarm();

                statusNotification.updateNotification(StatusNotification.NOTIFICATION.ERROR);

                // received from CnlService
            } else if (Constants.ACTION_NO_USB_PERMISSION.equals(action)) {
                Log.d(TAG, "No permission to read the USB device.");
                userLogMessage.add(ICON_WARN + "No permission to read the USB device.");

                MedtronicCnlAlarmManager.cancelAlarm();

                statusNotification.updateNotification(StatusNotification.NOTIFICATION.ERROR);

                if (dataStore.isSysEnableUsbPermissionDialog()) {
                    requestUsbPermission();
                } else {
                    userLogMessage.add(ICON_HELP + "Unplug/plug the Contour Next Link and select 'use by default for this device' to make permission permanent.");
                }

            }
        }
    }

    private boolean hasUsbPermission() {
        UsbManager usbManager = (UsbManager) this.getSystemService(Context.USB_SERVICE);
        UsbDevice cnlDevice = UsbHidDriver.getUsbDevice(usbManager, MedtronicCnlService.USB_VID, MedtronicCnlService.USB_PID);

        return !(usbManager != null && cnlDevice != null && !usbManager.hasPermission(cnlDevice));
    }

    private void requestUsbPermission() {
        UsbManager usbManager = (UsbManager) this.getSystemService(Context.USB_SERVICE);
        UsbDevice cnlDevice = UsbHidDriver.getUsbDevice(usbManager, MedtronicCnlService.USB_VID, MedtronicCnlService.USB_PID);
        PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(Constants.ACTION_HAS_USB_PERMISSION), 0);
        usbManager.requestPermission(cnlDevice, permissionIntent);
    }

    private void showDisconnectionNotification(String title, String message) {
        android.support.v7.app.NotificationCompat.Builder mBuilder =
                (android.support.v7.app.NotificationCompat.Builder) new android.support.v7.app.NotificationCompat.Builder(this)
                        .setPriority(android.support.v7.app.NotificationCompat.PRIORITY_MAX)
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
        public static final String ACTION_USERLOG_MESSAGE = "info.nightscout.android.medtronic.STATUS_MESSAGE";
        public static final String ACTION_CNL_COMMS_ACTIVE = "info.nightscout.android.medtronic.CNL_COMMS_ACTIVE";
        public static final String ACTION_CNL_COMMS_FINISHED = "info.nightscout.android.medtronic.CNL_COMMS_FINISHED";
        public static final String ACTION_STOP_SERVICE = "info.nightscout.android.medtronic.STOP_SERVICE";
        public static final String ACTION_READ_NOW = "info.nightscout.android.medtronic.READ_NOW";
        public static final String ACTION_READ_PROFILE = "info.nightscout.android.medtronic.READ_PROFILE";
        public static final String ACTION_URCHIN_UPDATE = "info.nightscout.android.medtronic.URCHIN_UPDATE";

        public static final String ACTION_TEST = "info.nightscout.android.medtronic.TEST";

        public static final String ACTION_NO_USB_PERMISSION = "info.nightscout.android.medtronic.NO_USB_PERMISSION";
        public static final String ACTION_HAS_USB_PERMISSION = "info.nightscout.android.medtronic.HAS_USB_PERMISSION";

        public static final String ACTION_USB_REGISTER = "info.nightscout.android.medtronic.USB_REGISTER";

        public static final String ACTION_CNL_READPUMP = "info.nightscout.android.medtronic.CNL_READPUMP";
        public static final String ACTION_CNL_SHUTDOWN = "info.nightscout.android.medtronic.CNL_SHUTDOWN";

        public static final String ACTION_UPDATE_STATUS = "info.nightscout.android.medtronic.UPDATE_STATUS";

        public static final String EXTENDED_DATA = "info.nightscout.android.medtronic.DATA";
    }
}