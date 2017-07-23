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
    public final static long POLL_PRE_GRACE_PERIOD_MS = 45000L;

    public static final String ICON_WARN = "{ion-alert-circled} ";
    public static final String ICON_BGL = "{ion-waterdrop} ";
    public static final String ICON_USB = "{ion-usb} ";
    public static final String ICON_INFO = "{ion-information_circled} ";
    public static final String ICON_HELP = "{ion-ios-lightbulb} ";
    public static final String ICON_SETTING = "{ion-android-settings} ";
    public static final String ICON_HEART = "{ion-heart} ";
    public static final String ICON_STAR = "{ion-ios-star} ";

    // show warning message after repeated errors
    private final static int ERROR_COMMS_AT = 4;
    private final static int ERROR_CONNECT_AT = 4;
    private final static int ERROR_SIGNAL_AT = 4;
    private final static float ERROR_UNAVAILABLE_AT = 12;       // warning at
    private final static float ERROR_UNAVAILABLE_RATE = 12 / 3; // expected per hour / acceptable unavailable per hour
    private final static float ERROR_UNAVAILABLE_DECAY = -1;    // decay rate for each good sgv received

    private static final String TAG = MedtronicCnlIntentService.class.getSimpleName();

    private UsbHidDriver mHidDevice;
    private Context mContext;
    private NotificationManagerCompat nm;
    private UsbManager mUsbManager;
    private DataStore dataStore = DataStore.getInstance();
    private ConfigurationStore configurationStore = ConfigurationStore.getInstance();
    private DateFormat dateFormatter = new SimpleDateFormat("HH:mm:ss", Locale.US);


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

/*

Notes on Errors:

CNL-PUMP pairing and registered devices

CNL: paired PUMP: paired UPLOADER: registered = ok
CNL: paired PUMP: paired UPLOADER: unregistered = ok
CNL: paired PUMP: unpaired UPLOADER: registered = "Could not communicate with the pump. Is it nearby?"
CNL: paired PUMP: unpaired UPLOADER: unregistered = "Could not communicate with the pump. Is it nearby?"
CNL: unpaired PUMP: paired UPLOADER: registered = "Timeout communicating with the Contour Next Link."
CNL: unpaired PUMP: paired UPLOADER: unregistered = "Invalid message received for requestLinkKey, Contour Next Link is not paired with pump."
CNL: unpaired PUMP: unpaired UPLOADER: registered = "Timeout communicating with the Contour Next Link."
CNL: unpaired PUMP: unpaired UPLOADER: unregistered = "Invalid message received for requestLinkKey, Contour Next Link is not paired with pump."

*/

    protected void onHandleIntent(Intent intent) {
        Log.d(TAG, "onHandleIntent called");
        try {
            // TODO use of ConfigurationStore is confusinng if pollInterval uses the CS, which
            // uses the POLL_PERIOD_MS, while the latter constant is also used directly.

            // Note that the variable pollInterval refers to the poll we'd like to make to the pump,
            // based on settings and battery level, while POLL_PERIOD_MS is used to calculate
            // when the pump is going to poll data from the transmitter again.
            // Thus POLL_PERIOD_MS is important to calculate times we'd be clashing with transmitter
            // to pump transmissions, which are then checked against the time the uploader would
            // like to poll, which is calculated using the pollInterval variable.
            // TODO find better variable names to make this distinction clearer and/or if possible
            // do more method extraction refactorings to make this method easier to grasp

            final long timePollStarted = System.currentTimeMillis();

            long timeLastGoodSGV = dataStore.getLastPumpStatus().getSgvDate().getTime();
            if (dataStore.getLastPumpStatus().getSgv() == 0
                || timePollStarted - timeLastGoodSGV > 24 * 60 * 60 * 1000) {
                timeLastGoodSGV = 0;
            }

            final long timePollExpected;
            if (timeLastGoodSGV != 0) {
                timePollExpected = timeLastGoodSGV + POLL_PERIOD_MS + POLL_GRACE_PERIOD_MS + (POLL_PERIOD_MS * ((timePollStarted - 1000L - (timeLastGoodSGV + POLL_GRACE_PERIOD_MS)) / POLL_PERIOD_MS));
            } else {
                timePollExpected = timePollStarted;
            }

            // avoid polling when too close to sensor-pump comms
            if (((timePollExpected - timePollStarted) > 5000L) && ((timePollExpected - timePollStarted) < (POLL_PRE_GRACE_PERIOD_MS + POLL_GRACE_PERIOD_MS))) {
                sendStatus("Please wait: Pump is expecting sensor communication. Poll due in " + ((timePollExpected - timePollStarted) / 1000L) + " seconds");
                MedtronicCnlAlarmManager.setAlarm(timePollExpected);
                return;
            }

            final short pumpBatteryLevel = dataStore.getLastPumpStatus().getBatteryPercentage();
            long pollInterval = configurationStore.getPollInterval();
            if ((pumpBatteryLevel > 0) && (pumpBatteryLevel <= 25)) {
                pollInterval = configurationStore.getLowBatteryPollInterval();
                sendStatus(ICON_WARN + "Warning: pump battery low");
                if (pollInterval != configurationStore.getPollInterval()) {
                    sendStatus(ICON_SETTING + "Low battery poll interval: " + (pollInterval / 60000) +" minutes");
               }
            }

            // TODO - throw, don't return
            if (!openUsbDevice())
                return;

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

                    // always get LinkKey on startup to handle re-paired CNL-PUMP key changes
                    String key = null;
                    if (dataStore.getCommsSuccessCount() > 0) {
                        key = info.getKey();
                    }

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
                        sendStatus(ICON_WARN + "Could not communicate with the pump. Is it nearby?");
                        Log.i(TAG, "Could not communicate with the pump. Is it nearby?");
                        dataStore.incCommsConnectThreshold();
                        pollInterval = configurationStore.getPollInterval() / (configurationStore.isReducePollOnPumpAway() ? 2L : 1L); // reduce polling interval to half until pump is available
                    } else if (cnlReader.getPumpSession().getRadioRSSIpercentage() < 5) {
                        sendStatus(String.format(Locale.getDefault(), "Connected on channel %d  RSSI: %d%%", (int) radioChannel, cnlReader.getPumpSession().getRadioRSSIpercentage()));
                        sendStatus(ICON_WARN + "Warning: pump signal too weak. Is it nearby?");
                        Log.i(TAG, "Warning: pump signal too weak. Is it nearby?");
                        dataStore.incCommsConnectThreshold();
                        dataStore.incCommsSignalThreshold();
                        pollInterval = configurationStore.getPollInterval() / (configurationStore.isReducePollOnPumpAway() ? 2L : 1L); // reduce polling interval to half until pump is available
                    } else {
                        dataStore.decCommsConnectThreshold();
                        if (cnlReader.getPumpSession().getRadioRSSIpercentage() < 20) {
                            if (dataStore.getCommsSignalThreshold() < ERROR_SIGNAL_AT) dataStore.incCommsSignalThreshold();
                        } else {
                            dataStore.decCommsSignalThreshold();
                        }

                        dataStore.setActivePumpMac(pumpMAC);

                        activePump.setLastRadioChannel(radioChannel);
                        sendStatus(String.format(Locale.getDefault(), "Connected on channel %d  RSSI: %d%%", (int) radioChannel, cnlReader.getPumpSession().getRadioRSSIpercentage()));
                        Log.d(TAG, String.format("Connected to Contour Next Link on channel %d.", (int) radioChannel));

                        // read pump status
                        PumpStatusEvent pumpRecord = realm.createObject(PumpStatusEvent.class);

                        String deviceName = String.format("medtronic-600://%s", cnlReader.getStickSerial());
                        activePump.setDeviceName(deviceName);

                        // TODO - this should not be necessary. We should reverse lookup the device name from PumpInfo
                        pumpRecord.setDeviceName(deviceName);

                        long pumpTime = cnlReader.getPumpTime().getTime();
                        long pumpOffset = pumpTime - System.currentTimeMillis();
                        Log.d(TAG, "Time offset between pump and device: " + pumpOffset + " millis.");

                        if (Math.abs(pumpOffset) > 10 * 60 * 1000) {
                            sendStatus(ICON_WARN + "Warning: Time difference between Pump and Uploader excessive."
                                    + " Pump is over " + (Math.abs(pumpOffset) / 60000L) + " minutes " + (pumpOffset > 0 ? "ahead" : "behind") + " of time used by uploader.");
                            sendStatus(ICON_HELP + "The uploader phone/device should have the current time provided by network. Pump clock drifts forward and needs to be set to correct time occasionally.");
                        }

                        // TODO - send ACTION to MainActivity to show offset between pump and uploader.
                        pumpRecord.setPumpTimeOffset(pumpOffset);
                        pumpRecord.setPumpDate(new Date(pumpTime - pumpOffset));
                        cnlReader.updatePumpStatus(pumpRecord);

                        if (pumpRecord.getSgv() != 0) {
                            sendStatus("SGV: " + MainActivity.strFormatSGV(pumpRecord.getSgv())
                                + "  At: " + dateFormatter.format(pumpRecord.getSgvDate().getTime())
                                + "  Pump: " + (pumpOffset > 0 ? "+" : "") + (pumpOffset / 1000L) + "sec");
                            // Check if pump sent old event when new expected
                            if (dataStore.getLastPumpStatus() != null &&
                                    dataStore.getLastPumpStatus().getSgvDate() != null &&
                                    pumpRecord.getSgvDate().getTime() - dataStore.getLastPumpStatus().getSgvDate().getTime() < 5000L &&
                                    timePollExpected - timePollStarted < 5000L) {
                                sendStatus(ICON_WARN + "Pump sent old SGV event");
                                if (dataStore.getCommsUnavailableThreshold() < ERROR_UNAVAILABLE_AT) dataStore.addCommsUnavailableThreshold(ERROR_UNAVAILABLE_RATE / (configurationStore.isReducePollOnPumpAway() ? 2L : 1L));
                                // pump may have missed sensor transmission or be delayed in posting to status message
                                // in most cases the next scheduled poll will have latest sgv, occasionally it is available this period after a delay
                                // if user selects double poll option we try again this period or wait until next
                                pollInterval = POLL_PERIOD_MS / (configurationStore.isReducePollOnPumpAway() ? 2L : 1L);
                            } else {
                                dataStore.addCommsUnavailableThreshold(ERROR_UNAVAILABLE_DECAY);
                            }

                            dataStore.clearUnavailableSGVCount(); // reset unavailable sgv count

                            // Check that the record doesn't already exist before committing
                            RealmResults<PumpStatusEvent> checkExistingRecords = activePump.getPumpHistory()
                                    .where()
                                    .equalTo("sgvDate", pumpRecord.getSgvDate())
                                    .equalTo("sgv", pumpRecord.getSgv())
                                    .findAll();

                            // There should be the 1 record we've already added in this transaction.
                            if (checkExistingRecords.size() == 0) {
                                timeLastGoodSGV = pumpRecord.getSgvDate().getTime();
                                activePump.getPumpHistory().add(pumpRecord);
                                dataStore.setLastPumpStatus(pumpRecord);
                                if (pumpRecord.getBolusWizardBGL() != 0) {
                                    sendStatus(ICON_BGL +"Recent finger BG: " + MainActivity.strFormatSGV(pumpRecord.getBolusWizardBGL()));
                                }
                            }

                        } else {
                            sendStatus(ICON_WARN + "SGV: unavailable from pump");
                            dataStore.incUnavailableSGVCount(); // poll clash detection
                            if (dataStore.getCommsUnavailableThreshold() < ERROR_UNAVAILABLE_AT) dataStore.addCommsUnavailableThreshold(ERROR_UNAVAILABLE_RATE);
                        }

                        realm.commitTransaction();
                        // Tell the Main Activity we have new data
                        sendMessage(Constants.ACTION_UPDATE_PUMP);
                        dataStore.incCommsSuccessCount();
                        dataStore.clearCommsErrorCount();
                    }

                } catch (UnexpectedMessageException e) {
                    dataStore.incCommsErrorCount();
                    pollInterval = 60000L; // retry once during this poll period, this allows for transient radio noise
                    Log.e(TAG, "Unexpected Message", e);
                    sendStatus(ICON_WARN + "Communication Error: " + e.getMessage());
                } catch (TimeoutException e) {
                    dataStore.incCommsErrorCount();
                    pollInterval = 90000L; // retry once during this poll period, this allows for transient radio noise
                    Log.e(TAG, "Timeout communicating with the Contour Next Link.", e);
                    sendStatus(ICON_WARN + "Timeout communicating with the Contour Next Link / Pump.");
                } catch (NoSuchAlgorithmException e) {
                    Log.e(TAG, "Could not determine CNL HMAC", e);
                    sendStatus(ICON_WARN + "Error connecting to Contour Next Link: Hashing error.");
                } finally {
                    try {
                        cnlReader.closeConnection();
                        cnlReader.endPassthroughMode();
                        cnlReader.endControlMode();
                    } catch (NoSuchAlgorithmException e) {
                    }

                }
            } catch (IOException e) {
                dataStore.incCommsErrorCount();
                Log.e(TAG, "Error connecting to Contour Next Link.", e);
                sendStatus(ICON_WARN + "Error connecting to Contour Next Link.");
            } catch (ChecksumException e) {
                dataStore.incCommsErrorCount();
                Log.e(TAG, "Checksum error getting message from the Contour Next Link.", e);
                sendStatus(ICON_WARN + "Checksum error getting message from the Contour Next Link.");
            } catch (EncryptionException e) {
                dataStore.incCommsErrorCount();
                Log.e(TAG, "Error decrypting messages from Contour Next Link.", e);
                sendStatus(ICON_WARN + "Error decrypting messages from Contour Next Link.");
            } catch (TimeoutException e) {
                dataStore.incCommsErrorCount();
                Log.e(TAG, "Timeout communicating with the Contour Next Link.", e);
                sendStatus(ICON_WARN + "Timeout communicating with the Contour Next Link.");
            } catch (UnexpectedMessageException e) {
                dataStore.incCommsErrorCount();
                Log.e(TAG, "Could not close connection.", e);
                sendStatus(ICON_WARN + "Could not close connection: " + e.getMessage());
            } finally {
                if (!realm.isClosed()) {
                    if (realm.isInTransaction()) {
                        // If we didn't commit the transaction, we've run into an error. Let's roll it back
                        realm.cancelTransaction();
                    }
                    realm.close();
                }

                uploadPollResults();
                scheduleNextPoll(timePollStarted, timeLastGoodSGV, pollInterval);

                // TODO - Refactor warning system
                if (dataStore.getCommsErrorCount() >= ERROR_COMMS_AT) {
                    sendStatus(ICON_WARN + "Warning: multiple comms/timeout errors detected.");
                    sendStatus(ICON_HELP + "Try: disconnecting and reconnecting the Contour Next Link to phone / restarting phone / check pairing of CNL with Pump.");
                }
                if (dataStore.getCommsUnavailableThreshold() >= ERROR_UNAVAILABLE_AT) {
                    dataStore.clearCommsUnavailableThreshold();
                    sendStatus(ICON_WARN + "Warning: SGV unavailable from pump is happening often. The pump is missing transmissions from the sensor / in warm-up phase / environment radio noise.");
                    sendStatus(ICON_HELP + "Keep pump on same side of body as sensor. Avoid using body sensor locations that can block radio signal. Sensor may be old / faulty and need changing (check pump graph for gaps).");
                }
                if (dataStore.getCommsConnectThreshold() >= ERROR_CONNECT_AT * (configurationStore.isReducePollOnPumpAway() ? 2 : 1)) {
                    dataStore.clearCommsConnectThreshold();
                    sendStatus(ICON_WARN + "Warning: connecting to pump is failing often.");
                    sendStatus(ICON_HELP + "Keep pump nearby to uploader phone/device. The body can block radio signals between pump and uploader.");
                }
                if (dataStore.getCommsSignalThreshold() >= ERROR_SIGNAL_AT) {
                    dataStore.clearCommsSignalThreshold();
                    sendStatus(ICON_WARN + "Warning: RSSI radio signal from pump is generally weak and may increase errors.");
                    sendStatus(ICON_HELP + "Keep pump nearby to uploader phone/device. The body can block radio signals between pump and uploader.");
                }

            }
        } finally {
            MedtronicCnlAlarmReceiver.completeWakefulIntent(intent);
        }
    }

    // TODO - Refactor polling system and make super clear how polling is calculated and why certain precautions are needed
    private void scheduleNextPoll(long timePollStarted, long timeLastGoodSGV, long pollInterval) {
        // smart polling and pump-sensor poll clash detection
        long now = System.currentTimeMillis();
        long lastActualPollTime = timePollStarted;
        if (timeLastGoodSGV > 0) {
            lastActualPollTime = timeLastGoodSGV + POLL_GRACE_PERIOD_MS + (POLL_PERIOD_MS * ((now - timeLastGoodSGV + POLL_GRACE_PERIOD_MS) / POLL_PERIOD_MS));
        }
        long nextActualPollTime = lastActualPollTime + POLL_PERIOD_MS;
        long nextRequestedPollTime = lastActualPollTime + pollInterval;
        // check if request is really needed
        if (nextRequestedPollTime - now < 10000L) {
            nextRequestedPollTime = nextActualPollTime;
        }
        // extended unavailable SGV may be due to clash with the current polling time
        // while we wait for a good SGV event, polling is auto adjusted by offsetting the next poll based on miss count
        if (dataStore.getUnavailableSGVCount() > 0) {
            if (timeLastGoodSGV == 0) {
                nextRequestedPollTime += POLL_PERIOD_MS / 5L; // if there is a uploader/sensor poll clash on startup then this will push the next attempt out by 60 seconds
            } else if (dataStore.getUnavailableSGVCount() > 2) {
                sendStatus(ICON_WARN + "Warning: No SGV available from pump for " + dataStore.getUnavailableSGVCount() + " attempts");
                long offsetPollTime = ((long) ((dataStore.getUnavailableSGVCount() - 2) % 5)) * (POLL_PERIOD_MS / 10L); // adjust poll time in 1/10 steps to avoid potential poll clash (max adjustment at 5/10)
                sendStatus("Adjusting poll: "  + dateFormatter.format(nextRequestedPollTime) +  " +" + (offsetPollTime / 1000) + "sec");
                nextRequestedPollTime += offsetPollTime;
            }
        }
        // check if requested poll time is too close to next actual poll time
        if (nextRequestedPollTime > nextActualPollTime - POLL_GRACE_PERIOD_MS - POLL_PRE_GRACE_PERIOD_MS
                && nextRequestedPollTime < nextActualPollTime) {
            nextRequestedPollTime = nextActualPollTime;
        }
        MedtronicCnlAlarmManager.setAlarm(nextRequestedPollTime);
        sendStatus("Next poll due at: " + dateFormatter.format(nextRequestedPollTime));
    }

    /**
     * @return if device acquisition was successful
     */
    private boolean openUsbDevice() {
        if (!hasUsbHostFeature()) {
            sendStatus(ICON_WARN + "It appears that this device doesn't support USB OTG.");
            Log.e(TAG, "Device does not support USB OTG");
            return false;
        }

        UsbDevice cnlStick = UsbHidDriver.getUsbDevice(mUsbManager, USB_VID, USB_PID);
        if (cnlStick == null) {
            sendStatus(ICON_WARN + "USB connection error. Is the Contour Next Link plugged in?");
            Log.w(TAG, "USB connection error. Is the CNL plugged in?");
            return false;
        }

        if (!mUsbManager.hasPermission(UsbHidDriver.getUsbDevice(mUsbManager, USB_VID, USB_PID))) {
            sendMessage(Constants.ACTION_NO_USB_PERMISSION);
            return false;
        }
        mHidDevice = UsbHidDriver.acquire(mUsbManager, cnlStick);

        try {
            mHidDevice.open();
        } catch (Exception e) {
            sendStatus(ICON_WARN + "Unable to open USB device");
            Log.e(TAG, "Unable to open serial device", e);
            return false;
        }

        return true;
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
        // TODO - set status if offline or Nightscout not reachable
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
        public static final String ACTION_USB_REGISTER = "info.nightscout.android.medtronic.USB_REGISTER";
        public static final String ACTION_UPDATE_PUMP = "info.nightscout.android.medtronic.UPDATE_PUMP";

        public static final String EXTENDED_DATA = "info.nightscout.android.medtronic.service.DATA";
    }
}
