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
import android.content.pm.PackageManager;
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
import info.nightscout.android.urchin.UrchinService;
import info.nightscout.android.utils.FormatKit;
import info.nightscout.android.utils.RealmKit;
import info.nightscout.android.xdrip_plus.XDripPlusUploadService;
import io.realm.Realm;
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
    private static final int ALARM_ID = 102;

    public static final long HEARTBEAT_PERIOD_MS = 5 * 60000L;
    public static final long CNL_JITTER_MS = 5 * 60000L;

    private Context mContext;

    private MasterServiceReceiver masterServiceReceiver;

    private StatusNotification statusNotification;

    private static PendingIntent pendingIntent;
    private static AlarmManager alarmManager;

    private boolean serviceStart;
    private boolean serviceActive;

    private final static int uploaderBatteryLow = 15;
    private final static int uploaderBatteryVeryLow = 5;
    private static int uploaderBatteryLevel = 100;

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate called");

        mContext = this.getBaseContext();

        final Realm storeRealm = Realm.getInstance(UploaderApplication.getStoreConfiguration());
        final DataStore dataStore = storeRealm.where(DataStore.class).findFirst();

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
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(Constants.ACTION_CNL_COMMS_ACTIVE);
            intentFilter.addAction(Constants.ACTION_CNL_COMMS_FINISHED);
            intentFilter.addAction(Constants.ACTION_CNL_COMMS_READY);
            intentFilter.addAction(Constants.ACTION_STOP_SERVICE);
            intentFilter.addAction(Constants.ACTION_READ_NOW);
            intentFilter.addAction(Constants.ACTION_READ_PROFILE);
            intentFilter.addAction(Constants.ACTION_READ_OVERDUE);
            intentFilter.addAction(Constants.ACTION_SETTINGS_CHANGED);
            intentFilter.addAction(Constants.ACTION_URCHIN_UPDATE);
            intentFilter.addAction(Constants.ACTION_STATUS_UPDATE);
            intentFilter.addAction(Constants.ACTION_HEARTBEAT);
            intentFilter.addAction(Constants.ACTION_USERLOG_MESSAGE);

            intentFilter.addAction(Intent.ACTION_BATTERY_LOW);
            intentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
            intentFilter.addAction(Intent.ACTION_BATTERY_OKAY);
            intentFilter.addAction(Intent.ACTION_POWER_CONNECTED);
            intentFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);

            intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
            intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
            intentFilter.addAction(Constants.ACTION_USB_ACTIVITY);
            intentFilter.addAction(Constants.ACTION_USB_PERMISSION);
            intentFilter.addAction(Constants.ACTION_NO_USB_PERMISSION);

            intentFilter.addAction(Intent.ACTION_LOCALE_CHANGED);
            intentFilter.addAction(Intent.ACTION_TIME_CHANGED);
            intentFilter.addAction(Intent.ACTION_DATE_CHANGED);

            registerReceiver(masterServiceReceiver, intentFilter);

            statusNotification = new StatusNotification();

            serviceStart = true;

        } else {

            serviceStart = false;
        }

        storeRealm.close();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy called");

        cancelAlarm();

        stopService(new Intent(mContext, UrchinService.class));

        if (statusNotification != null) statusNotification.endNotification();
        if (masterServiceReceiver != null) unregisterReceiver(masterServiceReceiver);
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

        if (!serviceStart) {
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

            serviceActive = true;
            setHeartbeatAlarm();

            // Android OS can kill and restart processes at any time
            // ask the CNL service to check state as it may already be active
            startService(new Intent(mContext, MedtronicCnlService.class)
                    .setAction(MasterService.Constants.ACTION_CNL_CHECKSTATE));
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
            if (!action.equals(Constants.ACTION_USERLOG_MESSAGE))
                Log.d(TAG, "receiver: " + action);

            switch (action) {

                case Constants.ACTION_CNL_COMMS_ACTIVE:
                    setHeartbeatAlarm();
                    statusNotification.updateNotification(StatusNotification.NOTIFICATION.BUSY);
                    break;

                case Constants.ACTION_CNL_COMMS_FINISHED:
                    if (serviceActive) {
                        PowerManager.WakeLock wl = getWakeLock(context, TAG, 10000);

                        runUploadServices();

                        long nextpoll = intent.getLongExtra("nextpoll", 0);

                        if (nextpoll > 0) {
                            setPollingAlarm(nextpoll);
                            statusNotification.updateNotification(StatusNotification.NOTIFICATION.NORMAL, nextpoll);
                        } else {
                            statusNotification.updateNotification(StatusNotification.NOTIFICATION.ERROR);
                        }

                        releaseWakeLock(wl);

                    } else {
                        Log.d(TAG, "onReceive : stopping master service");
                        stopSelf();
                    }
                    break;

                case Constants.ACTION_CNL_COMMS_READY:
                    RealmKit.compact(mContext);
                    statusNotification.updateNotification(StatusNotification.NOTIFICATION.NORMAL);
                    setHeartbeatAlarm();
                    runUploadServices();
                    if (checkUsbDevice()) {
                        if (hasUsbPermission()) startCgmService();
                        else usbNoPermission();
                    }
                    break;

                case Constants.ACTION_HEARTBEAT:
                    setHeartbeatAlarm();
                    runUploadServices();
                    break;

                case Constants.ACTION_STOP_SERVICE:
                    cancelAlarm();
                    serviceActive = false;
                    stopSelf();
                    break;

                case Constants.ACTION_READ_NOW:
                    UserLogMessage.send(mContext, R.string.ul_main__requesting_poll_now);
                    startService(new Intent(mContext, MedtronicCnlService.class)
                            .setAction(MasterService.Constants.ACTION_CNL_READPUMP));
                    break;

                case Constants.ACTION_READ_PROFILE:
                    UserLogMessage.send(mContext, R.string.ul_main__requesting_pump_profile);
                    Realm storeRealm = Realm.getInstance(UploaderApplication.getStoreConfiguration());
                    storeRealm.executeTransaction(new Realm.Transaction() {
                        @Override
                        public void execute(@NonNull Realm realm) {
                            realm.where(DataStore.class).findFirst().setRequestProfile(true);
                        }
                    });
                    storeRealm.close();
                    break;

                case Constants.ACTION_READ_OVERDUE:
                    if (isUsbOperational()
                            && System.currentTimeMillis() - lastPollSuccess() > POLL_PERIOD_MS) {
                        startService(new Intent(mContext, MedtronicCnlService.class)
                                .setAction(MasterService.Constants.ACTION_CNL_READPUMP));
                    }
                    break;

                case Constants.ACTION_SETTINGS_CHANGED:
                    runUploadServices();
                    break;

                case Constants.ACTION_URCHIN_UPDATE:
                    startService(new Intent(mContext, UrchinService.class).setAction("update"));
                    break;

                case Intent.ACTION_LOCALE_CHANGED:
                case Intent.ACTION_TIME_CHANGED:
                case Intent.ACTION_DATE_CHANGED:
                case Constants.ACTION_STATUS_UPDATE:
                    statusNotification.updateNotification();
                    break;

                case Constants.ACTION_USERLOG_MESSAGE:
                    UserLogMessage.getInstance().addAsync(
                            (UserLogMessage.TYPE) intent.getSerializableExtra("type"),
                            (UserLogMessage.FLAG) intent.getSerializableExtra("flag"),
                            intent.getStringExtra("message"));
                    break;

                // received from OS
                case Intent.ACTION_BATTERY_LOW:
                case Intent.ACTION_BATTERY_CHANGED:
                case Intent.ACTION_BATTERY_OKAY:
                    updateUploaderBattery(intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1));
                    break;
                case Intent.ACTION_POWER_CONNECTED:
                    new PumpHistoryHandler(mContext).systemEvent()
                            .dismiss(PumpHistorySystem.STATUS.UPLOADER_BATTERY_VERYLOW)
                            .dismiss(PumpHistorySystem.STATUS.UPLOADER_BATTERY_LOW)
                            .closeHandler();
                    break;
                case Intent.ACTION_POWER_DISCONNECTED:
                    new PumpHistoryHandler(mContext).systemEvent()
                            .dismiss(PumpHistorySystem.STATUS.UPLOADER_BATTERY_FULL)
                            .closeHandler();
                    break;

                // received from UsbActivity
                case Constants.ACTION_USB_ACTIVITY:
                    usbActivity();
                    break;

                // received from OS
                case Constants.ACTION_USB_PERMISSION:
                    usbPermission();
                    break;

                // received from OS
                case UsbManager.ACTION_USB_DEVICE_ATTACHED:
                    usbAttached();
                    break;

                // received from OS
                case UsbManager.ACTION_USB_DEVICE_DETACHED:
                    usbDetached();
                    break;

                // received from CnlService
                case Constants.ACTION_NO_USB_PERMISSION:
                    usbNoPermission();
                    break;
            }

        }
    }

    private void setPollingAlarm(long time) {
        Intent intent = new Intent(this, MedtronicCnlService.class).setAction(Constants.ACTION_CNL_READPUMP);
        PendingIntent pi = PendingIntent.getService(this, ALARM_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        setAlarm(time, pi);
    }

    private void setHeartbeatAlarm() {
        Intent intent = new Intent(Constants.ACTION_HEARTBEAT);
        PendingIntent pi = PendingIntent.getBroadcast(this, ALARM_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        setAlarm(System.currentTimeMillis() + HEARTBEAT_PERIOD_MS, pi);
    }

    private void setAlarm(long time, PendingIntent pi) {
        Log.d(TAG, "request to set Alarm at " + new Date(time));

        if (alarmManager == null)
            alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);

        if (pendingIntent != null)
            cancelAlarm();

        pendingIntent = pi;

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
        setPollingAlarm(start);
        statusNotification.updateNotification(StatusNotification.NOTIFICATION.NORMAL, start);

        if (start - now > 10000L)
            UserLogMessage.send(mContext, UserLogMessage.TYPE.INFO, String.format("{id;%s} {time.poll;%s}", R.string.ul_poll__next_poll_due_at, start));
    }

    private long lastPollSuccess() {
        long last = 0;
        Realm realm = Realm.getDefaultInstance();

        RealmResults<PumpStatusEvent> results = realm.where(PumpStatusEvent.class)
                .greaterThan("eventDate", new Date(System.currentTimeMillis() - (24 * 60 * 60000L)))
                .sort("eventDate", Sort.DESCENDING)
                .findAll();

        if (results.size() > 0)
            last = results.first().getEventDate().getTime();

        realm.close();
        return last;
    }

    private long nextPollTime() {

        long now = System.currentTimeMillis();

        long lastRecievedEventTime;
        long lastActualEventTime;
        long nextExpectedEventTime;
        long nextRequestedPollTime;

        final Realm realm = Realm.getDefaultInstance();
        final Realm storeRealm = Realm.getInstance(UploaderApplication.getStoreConfiguration());
        final DataStore dataStore = storeRealm.where(DataStore.class).findFirst();

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

        storeRealm.close();
        realm.close();

        return nextRequestedPollTime;
    }

    private void runUploadServices() {
        Log.d(TAG, "Running upload services");
        startService(new Intent(mContext, UrchinService.class).setAction("update"));
        startService(new Intent(mContext, PushoverUploadService.class));
        startService(new Intent(mContext, XDripPlusUploadService.class));
        startService(new Intent(mContext, NightscoutUploadService.class));
    }

    public static int getUploaderBatteryLevel() {
        return uploaderBatteryLevel;
    }

    private int batteryUpdateLastLevel = 100;
    private long batteryUpdateTimestamp;

    private void updateUploaderBattery(int level) {
        if (level < 0) return;
        uploaderBatteryLevel = level;

        long now = System.currentTimeMillis();

        // skip checks if previous level 100 (initial startup value)

        if (batteryUpdateTimestamp < now && batteryUpdateLastLevel != 100) {

                if ((100 - level) / (100 - uploaderBatteryVeryLow)
                        > (100 - batteryUpdateLastLevel) / (100 - uploaderBatteryVeryLow)) {

                    new PumpHistoryHandler(this).systemEvent(PumpHistorySystem.STATUS.UPLOADER_BATTERY_VERYLOW)
                            .dismiss(PumpHistorySystem.STATUS.UPLOADER_BATTERY_VERYLOW)
                            .dismiss(PumpHistorySystem.STATUS.UPLOADER_BATTERY_LOW)
                            .data(level)
                            .process()
                            .closeHandler();
                    batteryUpdateTimestamp = now + 5 * 60000L;

                } else if ((100 - level) / (100 - uploaderBatteryLow)
                        > (100 - batteryUpdateLastLevel) / (100 - uploaderBatteryLow)) {

                    new PumpHistoryHandler(this).systemEvent(PumpHistorySystem.STATUS.UPLOADER_BATTERY_LOW)
                            .dismiss(PumpHistorySystem.STATUS.UPLOADER_BATTERY_VERYLOW)
                            .dismiss(PumpHistorySystem.STATUS.UPLOADER_BATTERY_LOW)
                            .data(level)
                            .process()
                            .closeHandler();
                    batteryUpdateTimestamp = now + 5 * 60000L;

                } else if (level == 100 && level > batteryUpdateLastLevel) {

                    new PumpHistoryHandler(this).systemEvent(PumpHistorySystem.STATUS.UPLOADER_BATTERY_FULL)
                            .dismiss(PumpHistorySystem.STATUS.UPLOADER_BATTERY_FULL)
                            .data(level)
                            .process()
                            .closeHandler();
                    batteryUpdateTimestamp = now + 60 * 60000L;

                }
            }

        batteryUpdateLastLevel = level;
    }

    private void usbActivity() {
        clearDisconnectionNotification();
        if (hasUsbPermission()) {
            cnlReady();
            UserLogMessage.send(mContext, UserLogMessage.TYPE.INFO, R.string.ul_usb__got_permission_for_usb);
            statusNotification.updateNotification(StatusNotification.NOTIFICATION.NORMAL);
            startCgmServiceDelayed(MedtronicCnlService.USB_WARMUP_TIME_MS);
        } else {
            usbNoPermission();
        }
    }

    private void usbPermission() {
        clearDisconnectionNotification();
        if (hasUsbPermission()) {
            cnlReady();
            UserLogMessage.send(mContext, UserLogMessage.TYPE.INFO, R.string.ul_usb__got_permission_for_usb);
            statusNotification.updateNotification(StatusNotification.NOTIFICATION.NORMAL);
            startCgmServiceDelayed(MedtronicCnlService.USB_WARMUP_TIME_MS);
        } else {
            UserLogMessage.send(mContext, UserLogMessage.TYPE.INFO, R.string.ul_usb__user_has_not_granted_permission_for_usb);
        }
    }

    private void cnlReady() {
        Realm storeRealm = Realm.getInstance(UploaderApplication.getStoreConfiguration());
        storeRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(@NonNull Realm realm) {
                DataStore dataStore = realm.where(DataStore.class).findFirst();
                dataStore.clearAllCommsErrors();

                long plug = System.currentTimeMillis();
                dataStore.setCnlPlugTimestamp(plug);
                long unplug = dataStore.getCnlUnplugTimestamp();
                if (unplug == 0) unplug = plug;
                if (plug - unplug > dataStore.getPushoverBackfillLimiter() * 60000L)
                    dataStore.setCnlLimiterTimestamp(plug);
            }
        });
        storeRealm.close();

        StatCnl statCnl = ((StatCnl) Stats.open().readRecord(StatCnl.class));
        statCnl.connected();
        UserLogMessage.sendE(mContext, UserLogMessage.TYPE.NOTE, statCnl.toString());
        Stats.close();
    }

    private void usbAttached() {
        clearDisconnectionNotification();
        UserLogMessage.send(mContext, UserLogMessage.TYPE.INFO, R.string.ul_usb__contour_next_link_plugged_in);
        if (!hasUsbPermission()) {
            Log.d(TAG, "No permission for USB. Waiting.");
            UserLogMessage.send(mContext, UserLogMessage.TYPE.INFO, R.string.ul_usb__waiting_for_usb_permission);
        }
    }

    private void usbDetached() {
        showDisconnectionNotification(FormatKit.getInstance().getString(R.string.ul_usb__usb_error),
                FormatKit.getInstance().getString(R.string.ul_usb__contour_next_link_unplugged));
        UserLogMessage.send(mContext, UserLogMessage.TYPE.WARN, String.format("{id;%s}. {id;%s}",
                R.string.ul_usb__usb_error,
                R.string.ul_usb__contour_next_link_unplugged));

        statusNotification.updateNotification(StatusNotification.NOTIFICATION.ERROR);

        ((StatCnl) Stats.open().readRecord(StatCnl.class)).disconnected();
        Stats.close();

        Realm storeRealm = Realm.getInstance(UploaderApplication.getStoreConfiguration());
        storeRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(@NonNull Realm realm) {
                DataStore dataStore = realm.where(DataStore.class).findFirst();
                long now = System.currentTimeMillis();
                // allow for jitter, only change timestamp when >period since last plug
                if (dataStore.getCnlUnplugTimestamp() == 0
                        || now - dataStore.getCnlPlugTimestamp() > CNL_JITTER_MS)
                    dataStore.setCnlUnplugTimestamp(now);
            }
        });
        storeRealm.close();
    }

    private void usbNoPermission() {
        Log.w(TAG, "No permission to read the USB device.");
        UserLogMessage.send(mContext, UserLogMessage.TYPE.WARN, R.string.ul_usb__no_permission_to_read_the_usb_device);

        statusNotification.updateNotification(StatusNotification.NOTIFICATION.ERROR);

        Realm storeRealm = Realm.getInstance(UploaderApplication.getStoreConfiguration());
        DataStore dataStore = storeRealm.where(DataStore.class).findFirst();
        if (dataStore.isSysEnableUsbPermissionDialog()) {
            requestUsbPermission();
        } else {
            UserLogMessage.send(mContext, UserLogMessage.TYPE.HELP, R.string.ul_usb__help_permission);
        }
        storeRealm.close();
    }

    private boolean hasUsbPermission() {
        UsbManager usbManager = (UsbManager) this.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) return false;
        UsbDevice cnlDevice = UsbHidDriver.getUsbDevice(usbManager, MedtronicCnlService.USB_VID, MedtronicCnlService.USB_PID);
        return !(cnlDevice != null && !usbManager.hasPermission(cnlDevice));
    }

    private void requestUsbPermission() {
        Log.d(TAG, "requestUsbPermission");
        UsbManager usbManager = (UsbManager) this.getSystemService(Context.USB_SERVICE);
        UsbDevice cnlDevice = UsbHidDriver.getUsbDevice(usbManager, MedtronicCnlService.USB_VID, MedtronicCnlService.USB_PID);
        PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(Constants.ACTION_USB_PERMISSION), 0);
        usbManager.requestPermission(cnlDevice, permissionIntent);
    }

    private boolean checkUsbDevice() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST)) {
            Log.e(TAG, "Device does not support USB OTG");
            statusNotification.updateNotification(StatusNotification.NOTIFICATION.ERROR);
            UserLogMessage.send(mContext, UserLogMessage.TYPE.WARN, getString(R.string.ul_usb__no_support));
            return false;
        }

        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        if (usbManager == null) {
            Log.e(TAG, "USB connection error. mUsbManager == null");
            statusNotification.updateNotification(StatusNotification.NOTIFICATION.ERROR);
            UserLogMessage.send(mContext, UserLogMessage.TYPE.WARN, getString(R.string.ul_usb__no_connection));
            return false;
        }

        if (UsbHidDriver.getUsbDevice(usbManager, MedtronicCnlService.USB_VID, MedtronicCnlService.USB_PID) == null) {
            Log.w(TAG, "USB connection error. Is the CNL plugged in?");
            statusNotification.updateNotification(StatusNotification.NOTIFICATION.ERROR);
            UserLogMessage.send(mContext, UserLogMessage.TYPE.WARN, getString(R.string.ul_usb__no_connection));
            return false;
        }

        return true;
    }

    private boolean isUsbOperational() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST)) {
            return false;
        }

        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        if (usbManager == null) {
            return false;
        }

        if (UsbHidDriver.getUsbDevice(usbManager, MedtronicCnlService.USB_VID, MedtronicCnlService.USB_PID) == null) {
            return false;
        }

        return true;
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
        public static final String ACTION_USERLOG_MESSAGE = "info.nightscout.android.medtronic.USERLOG_MESSAGE";
        public static final String ACTION_CNL_COMMS_ACTIVE = "info.nightscout.android.medtronic.CNL_COMMS_ACTIVE";
        public static final String ACTION_CNL_COMMS_FINISHED = "info.nightscout.android.medtronic.CNL_COMMS_FINISHED";
        public static final String ACTION_CNL_COMMS_READY = "info.nightscout.android.medtronic.CNL_COMMS_READY";
        public static final String ACTION_CNL_READPUMP = "info.nightscout.android.medtronic.CNL_READPUMP";
        public static final String ACTION_CNL_SHUTDOWN = "info.nightscout.android.medtronic.CNL_SHUTDOWN";
        public static final String ACTION_CNL_CHECKSTATE = "info.nightscout.android.medtronic.CNL_STATE";
        public static final String ACTION_STOP_SERVICE = "info.nightscout.android.medtronic.STOP_SERVICE";
        public static final String ACTION_READ_NOW = "info.nightscout.android.medtronic.READ_NOW";
        public static final String ACTION_READ_PROFILE = "info.nightscout.android.medtronic.READ_PROFILE";
        public static final String ACTION_READ_OVERDUE = "info.nightscout.android.medtronic.READ_OVERDUE";
        public static final String ACTION_SETTINGS_CHANGED = "info.nightscout.android.medtronic.SETTINGS_CHANGED";
        public static final String ACTION_URCHIN_UPDATE = "info.nightscout.android.medtronic.URCHIN_UPDATE";
        public static final String ACTION_STATUS_UPDATE = "info.nightscout.android.medtronic.STATUS_UPDATE";
        public static final String ACTION_HEARTBEAT = "info.nightscout.android.medtronic.HEARTBEAT";
        public static final String ACTION_NO_USB_PERMISSION = "info.nightscout.android.medtronic.NO_USB_PERMISSION";
        public static final String ACTION_USB_PERMISSION = "info.nightscout.android.medtronic.USB_PERMISSION";
        public static final String ACTION_USB_ACTIVITY = "info.nightscout.android.medtronic.USB_ACTIVITY";
    }
}
