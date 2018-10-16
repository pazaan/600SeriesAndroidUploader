package info.nightscout.android.medtronic.service;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;

import java.util.Date;

import info.nightscout.android.history.PumpHistoryHandler;
import info.nightscout.android.medtronic.Stats;
import info.nightscout.android.model.medtronicNg.PumpHistorySystem;
import info.nightscout.android.model.store.StatCnl;
import info.nightscout.android.pushover.PushoverUploadService;
import info.nightscout.android.R;
import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.UploaderApplication;
import info.nightscout.android.medtronic.MainActivity;
import info.nightscout.android.medtronic.StatusNotification;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.model.store.DataStore;
import info.nightscout.android.medtronic.UserLogMessage;
import info.nightscout.android.upload.nightscout.NightscoutUploadService;
import info.nightscout.android.utils.RealmKit;
import info.nightscout.android.xdrip_plus.XDripPlusUploadService;
import info.nightscout.android.urchin.Urchin;
import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmResults;
import io.realm.Sort;

import static android.support.v4.app.NotificationCompat.PRIORITY_MAX;
import static android.support.v4.app.NotificationCompat.VISIBILITY_PUBLIC;
import static info.nightscout.android.medtronic.service.MedtronicCnlService.POLL_GRACE_PERIOD_MS;
import static info.nightscout.android.medtronic.service.MedtronicCnlService.POLL_PERIOD_MS;
import static info.nightscout.android.medtronic.service.MedtronicCnlService.POLL_PRE_GRACE_PERIOD_MS;
import static info.nightscout.android.medtronic.service.MedtronicCnlService.POLL_RECOVERY_PERIOD_MS;
import static info.nightscout.android.medtronic.service.MedtronicCnlService.POLL_WARMUP_PERIOD_MS;
import static info.nightscout.android.utils.ToolKit.getWakeLock;
import static info.nightscout.android.utils.ToolKit.releaseWakeLock;

/**
 * Created by Pogman on 13.9.17.
 */

public class MasterService extends Service {
    private static final String TAG = MasterService.class.getSimpleName();

    public static final int USB_DISCONNECT_NOFICATION_ID = 1;
    public static final int SERVICE_NOTIFICATION_ID = 2;

    private Context mContext;

    private Realm storeRealm;
    private DataStore dataStore;

    private MasterServiceReceiver masterServiceReceiver;
    private UserLogMessageReceiver userLogMessageReceiver;
    private BatteryReceiver batteryReceiver;
    private UsbReceiver usbReceiver;

    private StatusNotification statusNotification;

    private Urchin urchin;

    private final static int uploaderBatteryLow = 15;
    private final static int uploaderBatteryVeryLow = 5;
    private static int uploaderBatteryLevel = 100;
    public static int getUploaderBatteryLevel() {
        return uploaderBatteryLevel;
    }

    private void updateUploaderBattery(int level) {
        if (level > -1) {
            // skip checks if previous level 100 (initial startup value)
            if (uploaderBatteryLevel != 100) {
                if ((100 - level) / (100 - uploaderBatteryVeryLow) > (100 - uploaderBatteryLevel) / (100 - uploaderBatteryVeryLow)) {

                    new PumpHistoryHandler(this)
                            .systemStatus(PumpHistorySystem.STATUS.UPLOADER_BATTERY_VERYLOW,
                                    new Date(System.currentTimeMillis()), String.format("%16X", System.currentTimeMillis()),
                                    new RealmList<>(Integer.toString(level)))
                            .close();

                } else if ((100 - level) / (100 - uploaderBatteryLow) > (100 - uploaderBatteryLevel) / (100 - uploaderBatteryLow)) {

                    new PumpHistoryHandler(this)
                            .systemStatus(PumpHistorySystem.STATUS.UPLOADER_BATTERY_LOW,
                                    new Date(System.currentTimeMillis()), String.format("%16X", System.currentTimeMillis()),
                                    new RealmList<>(Integer.toString(level)))
                            .close();

                } else if (level == 100 && level > uploaderBatteryLevel) {

                    new PumpHistoryHandler(this)
                            .systemStatus(PumpHistorySystem.STATUS.UPLOADER_BATTERY_FULL,
                                    new Date(System.currentTimeMillis()), String.format("%16X", System.currentTimeMillis()),
                                    new RealmList<>(Integer.toString(level)))
                            .close();

                }
            }
            uploaderBatteryLevel = level;
        }
    }

    private boolean serviceActive = true;

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate called");

        mContext = this.getBaseContext();

        RealmKit.compact(mContext);

        storeRealm = Realm.getInstance(UploaderApplication.getStoreConfiguration());
        dataStore = storeRealm.where(DataStore.class).findFirst();

        // safety check: don't proceed until main ui has been run and datastore initialised
        if (dataStore != null) {

            storeRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {
                    dataStore.clearAllCommsErrors();
                    // check Xdrip available
                    dataStore.setXdripPlusUploadAvailable(false);
                    // check Nightscout site available
                    dataStore.setNightscoutReportTime(0);
                    dataStore.setNightscoutAvailable(false);
                    // revalidate Pushover account
                    dataStore.setPushoverAPItokenCheck("");
                    dataStore.setPushoverUSERtokenCheck("");
                }
            });

            masterServiceReceiver = new MasterServiceReceiver();
            IntentFilter masterServiceIntentFilter = new IntentFilter();
            masterServiceIntentFilter.addAction(Constants.ACTION_CNL_COMMS_ACTIVE);
            masterServiceIntentFilter.addAction(Constants.ACTION_CNL_COMMS_FINISHED);
            masterServiceIntentFilter.addAction(Constants.ACTION_STOP_SERVICE);
            masterServiceIntentFilter.addAction(Constants.ACTION_READ_NOW);
            masterServiceIntentFilter.addAction(Constants.ACTION_READ_PROFILE);
            masterServiceIntentFilter.addAction(Constants.ACTION_URCHIN_UPDATE);
            masterServiceIntentFilter.addAction(Constants.ACTION_ALARM_RECEIVED);
            registerReceiver(masterServiceReceiver, masterServiceIntentFilter);

            userLogMessageReceiver = new UserLogMessageReceiver();
            registerReceiver(
                    userLogMessageReceiver,
                    new IntentFilter(Constants.ACTION_USERLOG_MESSAGE));

            batteryReceiver = new BatteryReceiver();
            IntentFilter batteryIntentFilter = new IntentFilter();
            batteryIntentFilter.addAction(Intent.ACTION_BATTERY_LOW);
            batteryIntentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
            batteryIntentFilter.addAction(Intent.ACTION_BATTERY_OKAY);
            registerReceiver(batteryReceiver, batteryIntentFilter);

            usbReceiver = new UsbReceiver();
            IntentFilter usbIntentFilter = new IntentFilter();
            usbIntentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
            usbIntentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
            usbIntentFilter.addAction(Constants.ACTION_HAS_USB_PERMISSION);
            usbIntentFilter.addAction(Constants.ACTION_NO_USB_PERMISSION);
            registerReceiver(usbReceiver, usbIntentFilter);

            urchin = new Urchin(mContext);

            statusNotification = StatusNotification.getInstance();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy called");

        cancelAlarm();

        if (userLogMessageReceiver != null) unregisterReceiver(userLogMessageReceiver);
        if (usbReceiver != null) unregisterReceiver(usbReceiver);
        if (batteryReceiver != null) unregisterReceiver(batteryReceiver);
        if (masterServiceReceiver != null) unregisterReceiver(masterServiceReceiver);

        if (urchin != null) urchin.close();
        if (statusNotification != null) statusNotification.endNotification();

        if (storeRealm != null && !storeRealm.isClosed()) storeRealm.close();
    }

    @Override
    public void onTaskRemoved(Intent intent) {
        Log.w(TAG, "onTaskRemoved called");
    }

    @Override
    public void onLowMemory() {
        Log.w(TAG, "onLowMemory called");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Received start id " + startId + ": " + intent);
        //userLogMessage.add(TAG + " Received start id " + startId + ": " + (intent == null ? "null" : intent));

        if (dataStore == null) {
            // safety check: don't proceed until main ui has been run and datastore initialised
            stopSelf();
            return START_NOT_STICKY;

        } else if (intent == null || startId == 1) {
            Log.i(TAG, "service start");
            //userLogMessage.add(TAG + " service start");

            Intent notificationIntent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

            String channel;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createNotificationChannels();
                channel = "status";
            } else channel = "";

            Notification notification = new NotificationCompat.Builder(this, channel)
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

    @TargetApi(26)
    private synchronized void createNotificationChannels() {

        NotificationManager notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            Log.e(TAG, "Could not create notification channels. NotificationManager is null");
            stopSelf();
        }

        NotificationChannel statusChannel = new NotificationChannel("status", "Status", NotificationManager.IMPORTANCE_LOW);
        statusChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        notificationManager.createNotificationChannel(statusChannel);

        NotificationChannel errorChannel = new NotificationChannel("error", "Errors", NotificationManager.IMPORTANCE_HIGH);
        errorChannel.enableLights(true);
        errorChannel.setLightColor(Color.RED);
        errorChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        notificationManager.createNotificationChannel(errorChannel);
    }

    private class MasterServiceReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "onReceive : " + action);

            Log.d(TAG, "R-default=" + Realm.getGlobalInstanceCount(Realm.getDefaultConfiguration()));
            Log.d(TAG, "R-store=" + Realm.getGlobalInstanceCount(UploaderApplication.getStoreConfiguration()));
            Log.d(TAG, "R-log=" + Realm.getGlobalInstanceCount(UploaderApplication.getUserLogConfiguration()));
            Log.d(TAG, "R-history=" + Realm.getGlobalInstanceCount(UploaderApplication.getHistoryConfiguration()));

            if (Constants.ACTION_CNL_COMMS_FINISHED.equals(action)) {

                if (serviceActive) {
                    PowerManager.WakeLock wl = getWakeLock(context, TAG, 10000);

                    Log.d(TAG, "onReceive : cnl comms finished");

                    uploadPollResults();

                    long nextpoll = intent.getLongExtra("nextpoll", 0);

                    if (nextpoll > 0) {
                        setAlarm(nextpoll);
                        statusNotification.updateNotification(StatusNotification.NOTIFICATION.NORMAL, nextpoll);
                    } else {
                        cancelAlarm();
                        statusNotification.updateNotification(StatusNotification.NOTIFICATION.ERROR);
                    }

                    releaseWakeLock(wl);

                } else {
                    Log.d(TAG, "onReceive : stopping master service");
                    stopSelf();
                }

            } else if (Constants.ACTION_STOP_SERVICE.equals(action)) {
                cancelAlarm();
                serviceActive = false;
                stopSelf();

            } else if (Constants.ACTION_READ_NOW.equals(action)) {
                UserLogMessage.send(mContext, R.string.main_requesting_poll_now);
                cancelAlarm();
                startService(new Intent(mContext, MedtronicCnlService.class)
                        .setAction(MasterService.Constants.ACTION_CNL_READPUMP));

            } else if (Constants.ACTION_READ_PROFILE.equals(action)) {
                UserLogMessage.send(mContext, R.string.main_requesting_pump_profile);
                storeRealm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(@NonNull Realm realm) {
                        dataStore.setRequestProfile(true);
                    }
                });
                setAlarm(System.currentTimeMillis() + 1000L);

            } else if (Constants.ACTION_CNL_COMMS_ACTIVE.equals(action)) {
                statusNotification.updateNotification(StatusNotification.NOTIFICATION.BUSY);

            } else if (Constants.ACTION_URCHIN_UPDATE.equals(action)) {
                urchin.update();
/*
            } else if (Constants.ACTION_ALARM_RECEIVED.equals(action)) {
                long time = intent.getLongExtra("time", 0);
                Log.d(TAG, "Received alarm broadcast message at " + new Date(time));
                userLogMessage.add("* alarm: " + new Date(time));
                startService(new Intent(mContext, MedtronicCnlService.class)
                        .setAction(MasterService.Constants.ACTION_CNL_READPUMP));
*/
            }

        }
    }

    private static PendingIntent pendingIntent = null;
    private static AlarmManager alarmManager = null;
    private static final int ALARM_ID = 102;

    private void setAlarm(long time) {

        if (alarmManager == null) {
            alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        }

        if (pendingIntent == null) {
            Intent intent = new Intent(this, MedtronicCnlService.class).setAction(Constants.ACTION_CNL_READPUMP);
            pendingIntent = PendingIntent.getService(this, ALARM_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }

        Log.d(TAG, "request to set Alarm at " + new Date(time));

        cancelAlarm();

        Log.d(TAG, "Alarm set to fire at " + new Date(time));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAlarmClock(new AlarmManager.AlarmClockInfo(time, null), pendingIntent);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // Android 5.0.0 + 5.0.1 (e.g. Galaxy S4) has a bug.
            // Alarms are not exact. Fixed in 5.0.2 and CM12
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, time, pendingIntent);
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, time, pendingIntent);
        }
    }

    private void cancelAlarm() {
        if (alarmManager == null || pendingIntent == null)
            return;

        alarmManager.cancel(pendingIntent);
    }

    private void startCgmService() {
        startCgmServiceDelayed(1000L);
    }

    private void startCgmServiceDelayed(long delay) {
        long now = System.currentTimeMillis();
        long start = nextPollTime();

        if (start - now < delay) start = now + delay;
        setAlarm(start);
        statusNotification.updateNotification(StatusNotification.NOTIFICATION.NORMAL, start);

        if (start - now > 10000L)
            UserLogMessage.send(mContext, UserLogMessage.TYPE.INFO, String.format("{id;%s} {time.poll;%s}", R.string.next_poll_due_at, start));
    }

    private long nextPollTime() {

        long now = System.currentTimeMillis();

        long lastRecievedEventTime;
        long lastActualEventTime;
        long nextExpectedEventTime;
        long nextRequestedPollTime;

        Realm realm = Realm.getDefaultInstance();

        RealmResults<PumpStatusEvent> results = realm.where(PumpStatusEvent.class)
                .greaterThan("eventDate", new Date(now - (24 * 60 * 60000L)))
                .sort("eventDate", Sort.DESCENDING)
                .findAll();

        RealmResults<PumpStatusEvent> cgmresults = results.where()
                .equalTo("cgmActive", true)
                .sort("cgmDate", Sort.DESCENDING)
                .findAll();

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

        } else {

            // no cgm event available to sync with
            nextRequestedPollTime = now;
        }

        realm.close();
        return nextRequestedPollTime;
    }

    private void uploadPollResults() {

        // TODO - make urchin function as a service
        urchin.update();

        startService(new Intent(this, PushoverUploadService.class));
        startService(new Intent(this, XDripPlusUploadService.class));
        startService(new Intent(this, NightscoutUploadService.class));
    }

    private class UserLogMessageReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            UserLogMessage.getInstance().addAsync(
                    (UserLogMessage.TYPE) intent.getSerializableExtra("type"),
                    (UserLogMessage.FLAG) intent.getSerializableExtra("flag"),
                    intent.getStringExtra("message"));
        }
    }

    private class BatteryReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context arg0, Intent arg1) {
            if (arg1.getAction().equalsIgnoreCase(Intent.ACTION_BATTERY_LOW)
                    || arg1.getAction().equalsIgnoreCase(Intent.ACTION_BATTERY_CHANGED)
                    || arg1.getAction().equalsIgnoreCase(Intent.ACTION_BATTERY_OKAY)) {
                updateUploaderBattery(arg1.getIntExtra(BatteryManager.EXTRA_LEVEL, -1));
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
                    UserLogMessage.send(mContext, UserLogMessage.TYPE.INFO, "Got permission for USB.");
                    statusNotification.updateNotification(StatusNotification.NOTIFICATION.NORMAL);
                    startCgmServiceDelayed(MedtronicCnlService.USB_WARMUP_TIME_MS);
                }

                // received from OS
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                Log.d(TAG, "USB plugged in");
                UserLogMessage.send(mContext, UserLogMessage.TYPE.INFO, "Contour Next Link plugged in.");
                clearDisconnectionNotification();

                storeRealm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(@NonNull Realm realm) {
                        dataStore.clearAllCommsErrors();
                    }
                });

                ((StatCnl) Stats.open().readRecord(StatCnl.class)).connected();
                Stats.close();

                if (hasUsbPermission()) {

                    statusNotification.updateNotification(StatusNotification.NOTIFICATION.NORMAL);

                } else {
                    Log.d(TAG, "No permission for USB. Waiting.");
                    UserLogMessage.send(mContext, UserLogMessage.TYPE.INFO, "Waiting for USB permission.");
                }

                // received from OS
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                Log.d(TAG, "USB unplugged");
                showDisconnectionNotification("USB Error", "Contour Next Link unplugged.");
                UserLogMessage.send(mContext, UserLogMessage.TYPE.WARN, "USB error. Contour Next Link unplugged.");

                statusNotification.updateNotification(StatusNotification.NOTIFICATION.ERROR);

                ((StatCnl) Stats.open().readRecord(StatCnl.class)).disconnected();
                Stats.close();

                // received from CnlService
            } else if (Constants.ACTION_NO_USB_PERMISSION.equals(action)) {
                Log.d(TAG, "No permission to read the USB device.");
                UserLogMessage.send(mContext, UserLogMessage.TYPE.WARN, "No permission to read the USB device.");

                statusNotification.updateNotification(StatusNotification.NOTIFICATION.ERROR);

                if (dataStore.isSysEnableUsbPermissionDialog()) {
                    requestUsbPermission();
                } else {
                    UserLogMessage.send(mContext, UserLogMessage.TYPE.HELP, "Unplug/plug the Contour Next Link and select 'use by default for this device' to make permission permanent.");
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

        String channel = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? "error" : "";

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this, channel)
                        .setPriority(PRIORITY_MAX)
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

        public static final String ACTION_ALARM_RECEIVED = "info.nightscout.android.medtronic.ACTION_ALARM_RECEIVED";

        public static final String ACTION_UPDATE_STATUS = "info.nightscout.android.medtronic.UPDATE_STATUS";

        public static final String EXTENDED_DATA = "info.nightscout.android.medtronic.DATA";
    }
}