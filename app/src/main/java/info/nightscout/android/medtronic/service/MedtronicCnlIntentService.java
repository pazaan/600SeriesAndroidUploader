package info.nightscout.android.medtronic.service;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import info.nightscout.android.R;
import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.MainActivity;
import info.nightscout.android.medtronic.MedtronicCnlReader;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;
import info.nightscout.android.medtronic.message.MessageUtils;
import info.nightscout.android.model.medtronicNg.ContourNextLinkInfo;
import info.nightscout.android.model.medtronicNg.PumpInfo;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.upload.nightscout.NightscoutUploadReceiver;
import info.nightscout.android.utils.ConfigurationStore;
import info.nightscout.android.utils.DataStore;
import info.nightscout.android.xdrip_plus.XDripPlusUploadReceiver;
import io.realm.Realm;
import io.realm.RealmResults;

public class MedtronicCnlIntentService extends IntentService {
    public final static int USB_VID = 0x1a79;
    public final static int USB_PID = 0x6210;
    public final static long USB_WARMUP_TIME_MS = 5000L;
    public final static long POLL_PERIOD_MS = 300000L;
    public final static long LOW_BATTERY_POLL_PERIOD_MS = 900000L;
    // Number of additional seconds to wait after the next expected CGM poll, so that we don't interfere with CGM radio comms.
    public final static long POLL_GRACE_PERIOD_MS = 30000L;
    private static final String TAG = MedtronicCnlIntentService.class.getSimpleName();

    private UsbHidDriver mHidDevice;
    private Context mContext;
    private NotificationManagerCompat nm;
    private UsbManager mUsbManager;
    private DataStore dataStore = DataStore.getInstance();
    private ConfigurationStore configurationStore = ConfigurationStore.getInstance();

    public MedtronicCnlIntentService() {
        super(MedtronicCnlIntentService.class.getName());
    }

    protected void sendStatus(String message) {
        Intent localIntent =
                new Intent(Constants.ACTION_STATUS_MESSAGE)
                        .putExtra(Constants.EXTENDED_DATA, message);
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    protected void sendMessage(String action) {
        Intent localIntent =
                new Intent(action);
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "onCreate called");
        mContext = this.getBaseContext();
        mUsbManager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.d(TAG, "onDestroy called");

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

    protected void onHandleIntent(Intent intent) {
        Log.d(TAG, "onHandleIntent called");

        long timePollStarted = System.currentTimeMillis(),
                timePollExpected = timePollStarted,
                timeLastGoodSGV = dataStore.getLastPumpStatus().getEventDate().getTime();

        short pumpBatteryLevel = dataStore.getLastPumpStatus().getBatteryPercentage();


        if (timeLastGoodSGV != 0) {
            timePollExpected = timeLastGoodSGV + POLL_PERIOD_MS + POLL_GRACE_PERIOD_MS + (POLL_PERIOD_MS * ((timePollStarted - 1000L - (timeLastGoodSGV + POLL_GRACE_PERIOD_MS)) / POLL_PERIOD_MS));
        }

        // avoid polling when too close to sensor-pump comms
        if (((timePollExpected - timePollStarted) > 5000L) && ((timePollExpected - timePollStarted) < (POLL_GRACE_PERIOD_MS + 45000L))) {
            sendStatus("Please wait: Poll due in " + ((timePollExpected - timePollStarted) / 1000L) + " seconds");
            MedtronicCnlAlarmManager.setAlarm(timePollExpected);
            MedtronicCnlAlarmReceiver.completeWakefulIntent(intent);
            return;
        }

        long pollInterval = configurationStore.getPollInterval();
        if ((pumpBatteryLevel > 0) && (pumpBatteryLevel <= 25)) {
            pollInterval = configurationStore.getLowBatteryPollInterval();
        }

        if (!hasUsbHostFeature()) {
            sendStatus("It appears that this device doesn't support USB OTG.");
            Log.e(TAG, "Device does not support USB OTG");
            MedtronicCnlAlarmReceiver.completeWakefulIntent(intent);
            // TODO - throw, don't return
            return;
        }

        UsbDevice cnlStick = UsbHidDriver.getUsbDevice(mUsbManager, USB_VID, USB_PID);
        if (cnlStick == null) {
            sendStatus("USB connection error. Is the Bayer Contour Next Link plugged in?");
            Log.w(TAG, "USB connection error. Is the CNL plugged in?");

            // TODO - set status if offline or Nightscout not reachable
            uploadToNightscout();
            MedtronicCnlAlarmReceiver.completeWakefulIntent(intent);
            // TODO - throw, don't return
            return;
        }

        if (!mUsbManager.hasPermission(UsbHidDriver.getUsbDevice(mUsbManager, USB_VID, USB_PID))) {
            sendMessage(Constants.ACTION_NO_USB_PERMISSION);
            MedtronicCnlAlarmReceiver.completeWakefulIntent(intent);
            // TODO - throw, don't return
            return;
        }
        mHidDevice = UsbHidDriver.acquire(mUsbManager, cnlStick);

        try {
            mHidDevice.open();
        } catch (Exception e) {
            Log.e(TAG, "Unable to open serial device", e);
            MedtronicCnlAlarmReceiver.completeWakefulIntent(intent);
            // TODO - throw, don't return
            return;
        }

        DateFormat df = new SimpleDateFormat("HH:mm:ss");

        MedtronicCnlReader cnlReader = new MedtronicCnlReader(mHidDevice);

        Realm realm = Realm.getDefaultInstance();
        realm.beginTransaction();

        try {
            sendStatus("Connecting to Contour Next Link");
            Log.d(TAG, "Connecting to Contour Next Link");
            cnlReader.requestDeviceInfo();

            // Is the device already configured?
            ContourNextLinkInfo info = realm
                    .where(ContourNextLinkInfo.class)
                    .equalTo("serialNumber", cnlReader.getStickSerial())
                    .findFirst();

            if (info == null) {
                info = realm.createObject(ContourNextLinkInfo.class, cnlReader.getStickSerial());
            }

            cnlReader.getPumpSession().setStickSerial(info.getSerialNumber());

            cnlReader.enterControlMode();

            try {
                cnlReader.enterPassthroughMode();
                cnlReader.openConnection();

                cnlReader.requestReadInfo();

                String key = info.getKey();

                if (key == null) {
                    cnlReader.requestLinkKey();

                    info.setKey(MessageUtils.byteArrayToHexString(cnlReader.getPumpSession().getKey()));
                    key = info.getKey();
                }

                cnlReader.getPumpSession().setKey(MessageUtils.hexStringToByteArray(key));

                long pumpMAC = cnlReader.getPumpSession().getPumpMAC();
                Log.i(TAG, "PumpInfo MAC: " + (pumpMAC & 0xffffff));
                PumpInfo activePump = realm
                        .where(PumpInfo.class)
                        .equalTo("pumpMac", pumpMAC)
                        .findFirst();

                if (activePump == null) {
                    activePump = realm.createObject(PumpInfo.class, pumpMAC);
                }

                activePump.updateLastQueryTS();

                byte radioChannel = cnlReader.negotiateChannel(activePump.getLastRadioChannel());
                if (radioChannel == 0) {
                    sendStatus("Could not communicate with the 640g. Are you near the pump?");
                    Log.i(TAG, "Could not communicate with the 640g. Are you near the pump?");
                    pollInterval = configurationStore.getPollInterval() / (configurationStore.isReducePollOnPumpAway()?2L:1L); // reduce polling interval to half until pump is available
                } else {
                    dataStore.setActivePumpMac(pumpMAC);

                    activePump.setLastRadioChannel(radioChannel);
                    sendStatus(String.format(Locale.getDefault(), "Connected on channel %d  RSSI: %d%%", (int) radioChannel, cnlReader.getPumpSession().getRadioRSSIpercentage()));
                    Log.d(TAG, String.format("Connected to Contour Next Link on channel %d.", (int) radioChannel));

                    // read pump status
                    PumpStatusEvent pumpRecord = realm.createObject(PumpStatusEvent.class);

                    String deviceName = String.format("medtronic-640g://%s", cnlReader.getStickSerial());
                    activePump.setDeviceName(deviceName);

                    // TODO - this should not be necessary. We should reverse lookup the device name from PumpInfo
                    pumpRecord.setDeviceName(deviceName);

                    long pumpTime = cnlReader.getPumpTime().getTime();
                    long pumpOffset = pumpTime - System.currentTimeMillis();
                    Log.d(TAG, "Time offset between pump and device: " + pumpOffset + " millis.");

                    // TODO - send ACTION to MainActivity to show offset between pump and uploader.
                    pumpRecord.setPumpTimeOffset(pumpOffset);
                    pumpRecord.setPumpDate(new Date(pumpTime - pumpOffset));
                    cnlReader.updatePumpStatus(pumpRecord);

                    if (pumpRecord.getSgv() != 0) {
                        String offsetSign = "";
                        if (pumpOffset > 0) {
                            offsetSign = "+";
                        }
                        sendStatus("SGV: " + MainActivity.strFormatSGV(pumpRecord.getSgv()) + "  At: " + df.format(pumpRecord.getEventDate().getTime()) + "  Pump: " + offsetSign + (pumpOffset / 1000L) + "sec");  //note: event time is currently stored with offset

                        // Check if pump sent old event when new expected
                        if (((pumpRecord.getEventDate().getTime() - dataStore.getLastPumpStatus().getEventDate().getTime()) < 5000L) && ((timePollExpected - timePollStarted) < 5000L)) {
                            sendStatus("Pump sent old SGV event");
                        }

                        //MainActivity.timeLastGoodSGV =  pumpRecord.getEventDate().getTime(); // track last good sgv event time
                        //MainActivity.pumpBattery =  pumpRecord.getBatteryPercentage(); // track pump battery
                        timeLastGoodSGV = pumpRecord.getEventDate().getTime();
                        dataStore.clearUnavailableSGVCount(); // reset unavailable sgv count

                        // Check that the record doesn't already exist before committing
                        RealmResults<PumpStatusEvent> checkExistingRecords = activePump.getPumpHistory()
                                .where()
                                .equalTo("eventDate", pumpRecord.getEventDate())    // >>>>>>> check as event date may not = exact pump event date due to it being stored with offset added this could lead to dup events due to slight variability in time offset
                                .equalTo("sgv", pumpRecord.getSgv())
                                .findAll();

                        // There should be the 1 record we've already added in this transaction.
                        if (checkExistingRecords.size() == 0) {
                            activePump.getPumpHistory().add(pumpRecord);
                            dataStore.setLastPumpStatus(pumpRecord);
                        }

                    } else {
                        sendStatus("SGV: unavailable from pump");
                        dataStore.incUnavailableSGVCount(); // poll clash detection
                    }

                    realm.commitTransaction();
                    // Tell the Main Activity we have new data
                    sendMessage(Constants.ACTION_UPDATE_PUMP);
                }

            } catch (UnexpectedMessageException e) {
                Log.e(TAG, "Unexpected Message", e);
                sendStatus("Communication Error: " + e.getMessage());
                pollInterval = 60000L; // retry once during this poll period
            } catch (TimeoutException e) {
                Log.e(TAG, "Timeout communicating with the Contour Next Link.", e);
                sendStatus("Timeout communicating with the Contour Next Link.");
                pollInterval = 60000L; // retry once during this poll period
            } catch (NoSuchAlgorithmException e) {
                Log.e(TAG, "Could not determine CNL HMAC", e);
                sendStatus("Error connecting to Contour Next Link: Hashing error.");
            } finally {
                try {
                    cnlReader.closeConnection();
                    cnlReader.endPassthroughMode();
                    cnlReader.endControlMode();
                } catch (NoSuchAlgorithmException e) {}

            }
        } catch (IOException e) {
            Log.e(TAG, "Error connecting to Contour Next Link.", e);
            sendStatus("Error connecting to Contour Next Link.");
        } catch (ChecksumException e) {
            Log.e(TAG, "Checksum error getting message from the Contour Next Link.", e);
            sendStatus("Checksum error getting message from the Contour Next Link.");
        } catch (EncryptionException e) {
            Log.e(TAG, "Error decrypting messages from Contour Next Link.", e);
            sendStatus("Error decrypting messages from Contour Next Link.");
        } catch (TimeoutException e) {
            Log.e(TAG, "Timeout communicating with the Contour Next Link.", e);
            sendStatus("Timeout communicating with the Contour Next Link.");
        } catch (UnexpectedMessageException e) {
            Log.e(TAG, "Could not close connection.", e);
            sendStatus("Could not close connection: " + e.getMessage());
        } finally {
            if (!realm.isClosed()) {
                if (realm.isInTransaction()) {
                    // If we didn't commit the transaction, we've run into an error. Let's roll it back
                    realm.cancelTransaction();
                }
                realm.close();
            }
            // TODO - set status if offline or Nightscout not reachable
            sendToXDrip();
            uploadToNightscout();

            // smart polling and pump-sensor poll clash detection
            long lastActualPollTime = timePollStarted;
            if (timeLastGoodSGV > 0) {
                lastActualPollTime = timeLastGoodSGV + POLL_GRACE_PERIOD_MS + (POLL_PERIOD_MS * ((System.currentTimeMillis() - (timeLastGoodSGV + POLL_GRACE_PERIOD_MS)) / POLL_PERIOD_MS));
            }
            long nextActualPollTime = lastActualPollTime + POLL_PERIOD_MS;
            long nextRequestedPollTime = lastActualPollTime + pollInterval;
            if ((nextRequestedPollTime - System.currentTimeMillis()) < 10000L) {
                nextRequestedPollTime = nextActualPollTime;
            }
            // extended unavailable SGV may be due to clash with the current polling time
            // while we wait for a good SGV event, polling is auto adjusted by offsetting the next poll based on miss count
            if (dataStore.getUnavailableSGVCount() > 0) {
                if (timeLastGoodSGV == 0) {
                    nextRequestedPollTime += POLL_PERIOD_MS / 5L; // if there is a uploader/sensor poll clash on startup then this will push the next attempt out by 60 seconds
                }
                else if (dataStore.getUnavailableSGVCount() > 2) {
                    sendStatus("Warning: No SGV available from pump for " +dataStore.getUnavailableSGVCount() + " attempts");
                    nextRequestedPollTime += ((long) ((dataStore.getUnavailableSGVCount() - 2) % 5)) * (POLL_PERIOD_MS / 10L); // adjust poll time in 1/10 steps to avoid potential poll clash (max adjustment at 5/10)
                }
            }
            MedtronicCnlAlarmManager.setAlarm(nextRequestedPollTime);
            sendStatus("Next poll due at: " + df.format(nextRequestedPollTime));

            MedtronicCnlAlarmReceiver.completeWakefulIntent(intent);
        }
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

    private void sendToXDrip() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (prefs.getBoolean(getString(R.string.preference_enable_xdrip_plus), false)) {
            final Intent receiverIntent = new Intent(this, XDripPlusUploadReceiver.class);
            final long timestamp = System.currentTimeMillis() + 500L;
            final PendingIntent pendingIntent = PendingIntent.getBroadcast(this, (int) timestamp, receiverIntent, PendingIntent.FLAG_ONE_SHOT);
            Log.d(TAG, "Scheduling xDrip+ send");
            wakeUpIntent(getApplicationContext(), timestamp, pendingIntent);
        }
    }

    private void uploadToNightscout() {
        Intent receiverIntent = new Intent(this, NightscoutUploadReceiver.class);
        final long timestamp = System.currentTimeMillis() + 1000L;
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(this, (int) timestamp, receiverIntent, PendingIntent.FLAG_ONE_SHOT);
        wakeUpIntent(getApplicationContext(), timestamp, pendingIntent);
    }

    private boolean hasUsbHostFeature() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST);
    }

    public final class Constants {
        public static final String ACTION_STATUS_MESSAGE = "info.nightscout.android.medtronic.service.STATUS_MESSAGE";
        public static final String ACTION_NO_USB_PERMISSION = "info.nightscout.android.medtronic.service.NO_USB_PERMISSION";
        public static final String ACTION_USB_PERMISSION = "info.nightscout.android.medtronic.USB_PERMISSION";
        public static final String ACTION_REFRESH_DATA = "info.nightscout.android.medtronic.service.CGM_DATA";
        public static final String ACTION_USB_REGISTER = "info.nightscout.android.medtronic.USB_REGISTER";
        public static final String ACTION_UPDATE_PUMP = "info.nightscout.android.medtronic.UPDATE_PUMP";

        public static final String EXTENDED_DATA = "info.nightscout.android.medtronic.service.DATA";
    }
}
