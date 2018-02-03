package info.nightscout.android.medtronic.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import info.nightscout.android.HistoryDebug;
import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.UploaderApplication;
import info.nightscout.android.medtronic.MedtronicCnlReader;
import info.nightscout.android.medtronic.PumpHistoryHandler;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;
import info.nightscout.android.medtronic.message.ContourNextLinkCommandMessage;
import info.nightscout.android.model.medtronicNg.ContourNextLinkInfo;
import info.nightscout.android.model.medtronicNg.PumpInfo;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.model.store.DataStore;
import info.nightscout.android.utils.HexDump;
import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;

import static info.nightscout.android.medtronic.UserLogMessage.Icons.ICON_CGM;
import static info.nightscout.android.medtronic.UserLogMessage.Icons.ICON_HELP;
import static info.nightscout.android.medtronic.UserLogMessage.Icons.ICON_INFO;
import static info.nightscout.android.medtronic.UserLogMessage.Icons.ICON_REFRESH;
import static info.nightscout.android.medtronic.UserLogMessage.Icons.ICON_SETTING;
import static info.nightscout.android.medtronic.UserLogMessage.Icons.ICON_WARN;
import static info.nightscout.android.utils.ToolKit.getWakeLock;
import static info.nightscout.android.utils.ToolKit.releaseWakeLock;

public class MedtronicCnlService extends Service {
    private static final String TAG = MedtronicCnlService.class.getSimpleName();

    public final static int USB_VID = 0x1a79;
    public final static int USB_PID = 0x6210;
    public final static long USB_WARMUP_TIME_MS = 5000L;

    // Poll intervals
    public final static long POLL_PERIOD_MS = 300000L;
    public final static long LOW_BATTERY_POLL_PERIOD_MS = 900000L;
    // Number of additional seconds to wait after the next expected CGM poll, so that we don't interfere with CGM radio comms.
    public final static long POLL_GRACE_PERIOD_MS = 30000L;
    // Number of seconds before the next expected CGM poll that we will allow uploader comms to start
    public final static long POLL_PRE_GRACE_PERIOD_MS = 30000L;
    // Extended grace period after a lost sensor
    public final static long POLL_RECOVERY_PERIOD_MS = 90000L;
    // Extended grace period during sensor warmup
    public final static long POLL_WARMUP_PERIOD_MS = 90000L;
    // Retry after comms errors
    public final static long POLL_ERROR_RETRY_MS = 90000L;
    // Retry after old sgv received from pump
    public final static long POLL_OLDSGV_RETRY_MS = 90000L;
    // Ongoing cgm n/a events to trigger extra safe anti clash poll timing
    public final static int POLL_ANTI_CLASH = 3;

    // show warning message after repeated errors
    private final static int ERROR_COMMS_AT = 3;
    private final static int ERROR_CONNECT_AT = 6;
    private final static int ERROR_SIGNAL_AT = 6;
    private final static int ERROR_PUMPLOSTSENSOR_AT = 6;
    private final static int ERROR_PUMPBATTERY_AT = 1;
    private final static int ERROR_PUMPCLOCK_AT = 8;
    private final static long ERROR_PUMPCLOCK_MS = 10 * 60 * 1000L;

    private Context mContext;
    private static UsbHidDriver mHidDevice;
    private UsbManager mUsbManager;
    private ReadPump readPump;
    private Realm realm;
    private Realm storeRealm;
    private DataStore dataStore;
    private PumpHistoryHandler pumpHistoryHandler;

    // DataStore local copy
    private int PumpCgmNA;
    private int CommsSuccess;
    private int CommsError;
    private int CommsConnectError;
    private int CommsSignalError;
    private int CommsSgvSuccess;
    private int PumpLostSensorError;
    private int PumpClockError;
    private int PumpBatteryError;

    private long pumpClockDifference;

    private boolean shutdownProtect = false;

    // debug use: temporary for error investigation
    public static int cnlClear = 0;
    public static int cnl0x81 = 0;

    private DateFormat dateFormatter = new SimpleDateFormat("HH:mm:ss", Locale.US);

    protected void userLogMessage(String message) {
        try {
            Intent intent =
                    new Intent(MasterService.Constants.ACTION_USERLOG_MESSAGE)
                            .putExtra(MasterService.Constants.EXTENDED_DATA, message);
            sendBroadcast(intent);
        } catch (Exception ignored) {
        }
    }

    protected void sendMessage(String action) {
        try {
            Intent intent =
                    new Intent(action);
            sendBroadcast(intent);
        } catch (Exception ignored) {
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate called");

        mContext = this.getBaseContext();
        mUsbManager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy called");

        if (mHidDevice != null) {
            Log.i(TAG, "Closing serial device...");
            mHidDevice.close();
            mHidDevice = null;
        }

        // kill process if it's been around too long, stops android killing us without warning due to process age (if mid-comms can crash the CNL E86/E81)
        long uptime = UploaderApplication.getUptime() / 60000;
        if (uptime > 60) {
            Log.d(TAG, "process uptime exceeded, killing process now. Uptime: " + uptime + " minutes");
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }

    @Override
    public void onTaskRemoved(Intent intent) {
        Log.i(TAG, "onTaskRemoved called");

        // Protection for older Android versions (v4 and v5)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M  && readPump != null) {
            sendBroadcast(new Intent(MasterService.Constants.ACTION_CNL_COMMS_FINISHED).putExtra("nextpoll", System.currentTimeMillis() + 30000L));
            pullEmergencyBrake();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Received start id " + startId + "  : " + intent);

        if (intent == null) {
            return START_NOT_STICKY;
        }

        String action = intent.getAction();

        if (MasterService.Constants.ACTION_CNL_READPUMP.equals(action) && readPump == null) {

            readPump = new ReadPump();
            readPump.setPriority(Thread.MIN_PRIORITY);
            readPump.start();

            return START_STICKY;

        } else if (MasterService.Constants.ACTION_CNL_SHUTDOWN.equals(action) && readPump != null) {
            // device is shutting down, pull the emergency brake!
            pullEmergencyBrake();
        }

        return START_NOT_STICKY;
    }

    private void pullEmergencyBrake() {

        // less then ideal but we need to stop CNL comms asap before android kills us while protecting comms that must complete to avoid a CNL E86 error

        if (mHidDevice != null) {
            Log.d(TAG, "comms in progress, pull the emergency brake!");

            long now = System.currentTimeMillis();
            while (shutdownProtect && (System.currentTimeMillis() - now) < 1000L) {
                Log.d(TAG, "shutdownProtect");
                readPump.interrupt();
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ignored) {
                }
            }

            try {
                new ContourNextLinkCommandMessage(ContourNextLinkCommandMessage.ASCII.EOT)
                        .sendNoResponse(mHidDevice);
                Thread.sleep(10);
            } catch (IOException | InterruptedException | EncryptionException | ChecksumException | UnexpectedMessageException | TimeoutException ignored) {
            }

            mHidDevice.close();
            mHidDevice = null;
        }

        stopSelf();
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    private void readDataStore() {
        PumpCgmNA = dataStore.getPumpCgmNA();
        CommsSuccess = dataStore.getCommsSuccess();
        CommsError = dataStore.getCommsError();
        CommsConnectError = dataStore.getCommsConnectError();
        CommsSignalError = dataStore.getCommsSignalError();
        CommsSgvSuccess = dataStore.getCommsSgvSuccess();
        PumpLostSensorError = dataStore.getPumpLostSensorError();
        PumpClockError = dataStore.getPumpClockError();
        PumpBatteryError = dataStore.getPumpBatteryError();
    }

    private void writeDataStore() {
        storeRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                dataStore.setPumpCgmNA(PumpCgmNA);
                dataStore.setCommsSuccess(CommsSuccess);
                dataStore.setCommsError(CommsError);
                dataStore.setCommsConnectError(CommsConnectError);
                dataStore.setCommsSignalError(CommsSignalError);
                dataStore.setCommsSgvSuccess(CommsSgvSuccess);
                dataStore.setPumpLostSensorError(PumpLostSensorError);
                dataStore.setPumpClockError(PumpClockError);
                dataStore.setPumpBatteryError(PumpBatteryError);
            }
        });
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

    private class ReadPump extends Thread {
        public void run() {
            Log.d(TAG, "readPump called");

            PowerManager.WakeLock wl = getWakeLock(mContext, TAG, 60000);

            sendBroadcast(new Intent(MasterService.Constants.ACTION_CNL_COMMS_ACTIVE));

            long timePollStarted = System.currentTimeMillis();
            long nextpoll = 0;

            // note: Realm use only in this thread!
            realm = Realm.getDefaultInstance();
            storeRealm = Realm.getInstance(UploaderApplication.getStoreConfiguration());
            dataStore = storeRealm.where(DataStore.class).findFirst();

            pumpHistoryHandler = new PumpHistoryHandler(mContext);

            readDataStore();

            // debug use:
            cnlClear = 0;
            cnl0x81 = 0;

            try {
/*
                if (false) {
                    userLogMessage("Adding history from file");
                    new HistoryDebug(mContext).run();
                    userLogMessage("Processing done");
                    nextpoll = 0;
                    return;
                }
*/
                long pollInterval = dataStore.getPollInterval();

                if (!openUsbDevice()) {
                    Log.w(TAG, "Could not open usb device");
//                userLogMessage(ICON_WARN + "Could not open usb device");
                    return;
                }

                long due = checkPollTime();
                if (due > 0) {
                    if (dataStore.isSysEnableClashProtect()) {
                        userLogMessage("Please wait: Pump is expecting sensor communication. Poll due in " + ((due - System.currentTimeMillis()) / 1000L) + " seconds");
                        nextpoll = due;
                        return;
                    } else {
                        userLogMessage("Pump is expecting sensor communication. Poll due in " + ((due - System.currentTimeMillis()) / 1000L) + " seconds");
                        userLogMessage(ICON_SETTING + "Radio clash protection is disabled.");
                    }
                }

                final MedtronicCnlReader cnlReader = new MedtronicCnlReader(mHidDevice);
                if (dataStore.isSysEnableWait500ms()) cnlReader.setCnlCommandMessageSleepMS(500);

                try {
                    userLogMessage("Connecting to Contour Next Link ");
                    Log.d(TAG, "Connecting to Contour Next Link [pid" + android.os.Process.myPid() + "]");

                    shutdownProtect = true;
                    cnlReader.requestDeviceInfo();

                    // Is the device already configured?
                    if (realm.where(ContourNextLinkInfo.class).equalTo("serialNumber", cnlReader.getStickSerial()).findFirst() == null) {
                        realm.executeTransaction(new Realm.Transaction() {
                            @Override
                            public void execute(Realm realm) {
                                realm.createObject(ContourNextLinkInfo.class, cnlReader.getStickSerial());
                            }
                        });
                    }
                    final ContourNextLinkInfo info = realm
                            .where(ContourNextLinkInfo.class)
                            .equalTo("serialNumber", cnlReader.getStickSerial())
                            .findFirst();

                    cnlReader.getPumpSession().setStickSerial(info.getSerialNumber());

                    cnlReader.enterControlMode();

                    try {
                        cnlReader.enterPassthroughMode();
                        shutdownProtect = false;
                        cnlReader.openConnection();

                        cnlReader.requestReadInfo();

                        // investigation: Negotiate Chan 0x81 empty response issue, always requesting key seems to stop this happening? why that?
                        // negotiateChannel has more details
                        cnlReader.requestLinkKey();
                        //info.setKey(MessageUtils.byteArrayToHexString(cnlReader.getPumpSession().getKey()));

                        final long pumpMAC = cnlReader.getPumpSession().getPumpMAC();
                        Log.i(TAG, "PumpInfo MAC: " + (pumpMAC & 0xffffff));

                        // Is activePump already configured?
                        if (realm.where(PumpInfo.class).equalTo("pumpMac", pumpMAC).findFirst() == null) {
                            realm.executeTransaction(new Realm.Transaction() {
                                @Override
                                public void execute(Realm realm) {
                                    realm.createObject(PumpInfo.class, pumpMAC);
                                }
                            });
                        }
                        final PumpInfo activePump = realm
                                .where(PumpInfo.class)
                                .equalTo("pumpMac", pumpMAC)
                                .findFirst();

                        final byte radioChannel = cnlReader.negotiateChannel(activePump.getLastRadioChannel());
                        if (radioChannel == 0) {
                            userLogMessage(ICON_WARN + "Could not communicate with the pump. Is it nearby?");
                            Log.i(TAG, "Could not communicate with the pump. Is it nearby?");
                            CommsConnectError++;
                            pollInterval = POLL_PERIOD_MS / (dataStore.isDoublePollOnPumpAway() ? 2L : 1L); // reduce polling interval to half until pump is available
                        } else if (cnlReader.getPumpSession().getRadioRSSIpercentage() < 5) {
                            userLogMessage(String.format(Locale.getDefault(), "Connected on channel %d  RSSI: %d%%", (int) radioChannel, cnlReader.getPumpSession().getRadioRSSIpercentage()));
                            userLogMessage(ICON_WARN + "Warning: pump signal too weak. Is it nearby?");
                            Log.i(TAG, "Warning: pump signal too weak. Is it nearby?");
                            CommsConnectError++;
                            CommsSignalError++;
                            pollInterval = POLL_PERIOD_MS / (dataStore.isDoublePollOnPumpAway() ? 2L : 1L); // reduce polling interval to half until pump is available
                        } else {
                            if (CommsConnectError > 0) CommsConnectError--;
                            if (cnlReader.getPumpSession().getRadioRSSIpercentage() < 20)
                                CommsSignalError++;
                            else if (CommsSignalError > 0) CommsSignalError--;

                            userLogMessage(String.format(Locale.getDefault(), "Connected on channel %d  RSSI: %d%%", (int) radioChannel, cnlReader.getPumpSession().getRadioRSSIpercentage()));
                            Log.d(TAG, String.format("Connected to Contour Next Link on channel %d.", (int) radioChannel));

                            // read pump status
                            final PumpStatusEvent pumpRecord = new PumpStatusEvent();

                            final String deviceName = String.format("medtronic-600://%s", cnlReader.getStickSerial());

                            // TODO - this should not be necessary. We should reverse lookup the device name from PumpInfo
                            pumpRecord.setDeviceName(deviceName);

                            cnlReader.getPumpTime();
                            pumpClockDifference = cnlReader.getSessionClockDifference();

                            // TODO - add a check to see of status update is needed? may just need history update?

                            pumpRecord.setEventDate(cnlReader.getSessionDate());
                            pumpRecord.setEventRTC(cnlReader.getSessionRTC());
                            pumpRecord.setEventOFFSET(cnlReader.getSessionOFFSET());
                            pumpRecord.setClockDifference(pumpClockDifference);
                            cnlReader.updatePumpStatus(pumpRecord);

                            validatePumpRecord(pumpRecord, activePump);

                            // write completed record to storage
                            realm.executeTransaction(new Realm.Transaction() {
                                @Override
                                public void execute(Realm realm) {
                                    activePump.setLastRadioChannel(radioChannel);
                                    activePump.setDeviceName(deviceName);
                                    activePump.getPumpHistory().add(pumpRecord);
                                }
                            });

                            CommsSuccess++;
                            CommsError = 0;

                            if (pumpRecord.getBatteryPercentage() <= 25) {
                                PumpBatteryError++;
                                pollInterval = dataStore.getLowBatPollInterval();
                            } else {
                                PumpBatteryError = 0;
                            }

                            if (pumpRecord.isCgmActive()) {
                                PumpCgmNA = 0; // poll clash detection
                                PumpLostSensorError = 0;

                                if (pumpRecord.isCgmWarmUp())
                                    userLogMessage(ICON_CGM + "sensor is in warm-up phase");
                                else if (pumpRecord.getCalibrationDueMinutes() == 0)
                                    userLogMessage(ICON_CGM + "sensor calibration is due now!");
                                else if (pumpRecord.getSgv() == 0 && pumpRecord.isCgmCalibrating())
                                    userLogMessage(ICON_CGM + "sensor is calibrating");
                                else if (pumpRecord.getSgv() == 0)
                                    userLogMessage(ICON_CGM + "sensor error (pump graph gap)");
                                else {
                                    CommsSgvSuccess++;
                                    userLogMessage("SGV: ¦" + pumpRecord.getSgv()
                                            + "¦  At: " + dateFormatter.format(pumpRecord.getCgmDate().getTime())
                                            + "  Pump: " + (pumpClockDifference > 0 ? "+" : "") + (pumpClockDifference / 1000L) + "sec");
                                    if (pumpRecord.isCgmCalibrating())
                                        userLogMessage(ICON_CGM + "sensor is calibrating");
                                    if (pumpRecord.isOldSgvWhenNewExpected()) {
                                        userLogMessage(ICON_CGM + "old SGV event received");
                                        // pump may have missed sensor transmission or be delayed in posting to status message
                                        // in most cases the next scheduled poll will have latest sgv, occasionally it is available this period after a delay
                                        pollInterval = dataStore.isSysEnablePollOverride() ? dataStore.getSysPollOldSgvRetry() : POLL_OLDSGV_RETRY_MS;
                                    }
                                }

                            } else {
                                PumpCgmNA++; // poll clash detection
                                if (CommsSgvSuccess > 0) {
                                    PumpLostSensorError++; // only count errors if cgm is being used
                                    userLogMessage(ICON_CGM + "cgm n/a (pump lost sensor)");
                                } else {
                                    userLogMessage(ICON_CGM + "cgm n/a");
                                }
                            }

                            // debug use:
                            //debugStatusMessage();

                            // history

                            pumpHistoryHandler.cgm(pumpRecord);

                            if (dataStore.isSysEnablePumpHistory() && isHistoryNeeded()) {
                                storeRealm.executeTransaction(new Realm.Transaction() {
                                    @Override
                                    public void execute(Realm realm) {
                                        dataStore.setRequestPumpHistory(true);
                                    }
                                });
                            }

                            // skip history processing for this poll when old SGV event received as we want to end comms asap
                            // due to the possibility of a late sensor-pump sgv send, the retry after 90 seconds will handle the history if needed
                            // also skip if pump battery is low and interval times are different

                            // TODO - if in low battery mode should we run a backfill after a time? 30/60 minutes?

                            if (!pumpRecord.isOldSgvWhenNewExpected() &&
                                    !(PumpBatteryError > 0 && dataStore.getLowBatPollInterval() > POLL_PERIOD_MS)) {

                                if (dataStore.isRequestProfile()) {
                                    pumpHistoryHandler.profile(cnlReader);
                                    storeRealm.executeTransaction(new Realm.Transaction() {
                                        @Override
                                        public void execute(Realm realm) {
                                            dataStore.setRequestProfile(false);
                                        }
                                    });
                                }

                                pumpHistoryHandler.checkGramsPerExchangeChanged();

                                pumpHistoryHandler.update(cnlReader);
                            }
                        }

                    } catch (UnexpectedMessageException e) {
                        CommsError++;
                        pollInterval = dataStore.isSysEnablePollOverride() ? dataStore.getSysPollErrorRetry() : POLL_ERROR_RETRY_MS;
                        Log.e(TAG, "Unexpected Message", e);
                        if (dataStore.isDbgEnableExtendedErrors())
                            userLogMessage(ICON_WARN + "Communication Error: " + e.getMessage());
                        else
                            userLogMessage(ICON_WARN + "Communication Error: busy/noisy");
                    } catch (TimeoutException e) {
                        CommsError++;
                        pollInterval = dataStore.isSysEnablePollOverride() ? dataStore.getSysPollErrorRetry() : POLL_ERROR_RETRY_MS;
                        Log.e(TAG, "Timeout communicating with the Contour Next Link.", e);
                        if (dataStore.isDbgEnableExtendedErrors())
                            userLogMessage(ICON_WARN + "Timeout Error: " + e.getMessage());
                        else
                            userLogMessage(ICON_WARN + "Timeout communicating with the pump.");
                    } catch (ChecksumException e) {
                        CommsError++;
                        Log.e(TAG, "Checksum error getting message from the Contour Next Link.", e);
                        if (dataStore.isDbgEnableExtendedErrors())
                            userLogMessage(ICON_WARN + "Checksum Error: " + e.getMessage());
                        else
                            userLogMessage(ICON_WARN + "Checksum error getting message from the Contour Next Link.");
                    } catch (EncryptionException e) {
                        CommsError++;
                        Log.e(TAG, "Error decrypting messages from Contour Next Link.", e);
                        if (dataStore.isDbgEnableExtendedErrors())
                            userLogMessage(ICON_WARN + "Decryption Error: " + e.getMessage());
                        else
                            userLogMessage(ICON_WARN + "Error decrypting messages from Contour Next Link.");
                    } catch (NoSuchAlgorithmException e) {
                        CommsError++;
                        Log.e(TAG, "Could not determine CNL HMAC", e);
                        userLogMessage(ICON_WARN + "Error connecting to Contour Next Link: Hashing error.");
                    } finally {
                        try {
                            cnlReader.closeConnection();
                            shutdownProtect = true;
                            cnlReader.endPassthroughMode();
                            shutdownProtect = false;
                            cnlReader.endControlMode();
                        } catch (NoSuchAlgorithmException ignored) {
                        }
                    }
                } catch (IOException e) {
                    CommsError++;
                    Log.e(TAG, "Error connecting to Contour Next Link.", e);
                    if (dataStore.isDbgEnableExtendedErrors())
                        userLogMessage(ICON_WARN + "Error connecting to Contour Next Link: " + e.getMessage());
                    else
                        userLogMessage(ICON_WARN + "Error connecting to Contour Next Link.");
                    if (cnlReader.resetCNL()) userLogMessage(ICON_INFO + "CNL reset successful.");
                } catch (ChecksumException e) {
                    CommsError++;
                    Log.e(TAG, "Checksum error getting message from the Contour Next Link.", e);
                    userLogMessage(ICON_WARN + "Checksum error getting message from the Contour Next Link.");
                } catch (EncryptionException e) {
                    CommsError++;
                    Log.e(TAG, "Error decrypting messages from Contour Next Link.", e);
                    userLogMessage(ICON_WARN + "Error decrypting messages from Contour Next Link.");
                } catch (TimeoutException e) {
                    CommsError++;
                    Log.e(TAG, "Timeout communicating with the Contour Next Link.", e);
                    userLogMessage(ICON_WARN + "Timeout communicating with the Contour Next Link.");
                    if (cnlReader.resetCNL()) userLogMessage(ICON_INFO + "CNL reset successful.");
                } catch (UnexpectedMessageException e) {
                    CommsError++;
                    Log.e(TAG, "Could not close connection.", e);
                    if (dataStore.isDbgEnableExtendedErrors())
                        userLogMessage(ICON_WARN + "Could not close connection: " + e.getMessage());
                    else
                        userLogMessage(ICON_WARN + "Could not close connection.");
                    if (cnlReader.resetCNL()) userLogMessage(ICON_INFO + "CNL reset successful.");
                } finally {
                    shutdownProtect = false;

                    // debug use:
                    //if (cnlClear > 0 || cnl0x81 > 0) userLogMessage("*** cnlClear=" + cnlClear + " cnl0x81=" + cnl0x81);

                    nextpoll = requestPollTime(timePollStarted, pollInterval);
                    if (dataStore.isDbgEnableExtendedErrors())
                        userLogMessage("Next poll due at: " + dateFormatter.format(nextpoll) + " [" + (System.currentTimeMillis() - timePollStarted) + "ms]");
                    else
                        userLogMessage("Next poll due at: " + dateFormatter.format(nextpoll));

                    RemoveOutdatedRecords();
                }

            } finally {
                statusWarnings();
                writeDataStore();

                if (!storeRealm.isClosed()) storeRealm.close();
                if (!realm.isClosed()) realm.close();

                if (pumpHistoryHandler != null) pumpHistoryHandler.close();

                if (mHidDevice != null) {
                    Log.i(TAG, "Closing serial device...");
                    mHidDevice.close();
                    mHidDevice = null;
                }

                sendBroadcast(new Intent(MasterService.Constants.ACTION_CNL_COMMS_FINISHED).putExtra("nextpoll", nextpoll));

                releaseWakeLock(wl);
                stopSelf();
            }

            readPump = null;
        } // thread end
    }

    private void statusWarnings() {
/*
        if (pumpHistoryHandler.isLoopActive()) {
            userLogMessage(ICON_INFO + "Auto mode is active");
        }

        if (dataStore.isNsEnableTreatments() && !dataStore.isNsEnableHistorySync()) {
            userLogMessage(ICON_HELP + "Past historical data can be uploaded to Nightscout via the History Sync option in Advanced Nightscout Settings.");
        }
*/
        if (dataStore.isNightscoutUpload() && dataStore.isNsEnableProfileUpload()) {
            if (!pumpHistoryHandler.isProfileUploaded()) {
                userLogMessage(ICON_INFO + "No basal pattern profile has been uploaded.");
                userLogMessage(ICON_HELP + "Profiles can be uploaded from the main menu.");
            } else if (dataStore.isNameBasalPatternChanged() &&
                    (dataStore.isNsEnableProfileSingle() || dataStore.isNsEnableProfileOffset())) {
                userLogMessage(ICON_INFO + "Basal pattern names have changed, your profile should be updated.");
                userLogMessage(ICON_HELP + "Profiles can be updated from the main menu.");
            }
        }

        if (PumpBatteryError >= ERROR_PUMPBATTERY_AT) {
            PumpBatteryError = 0;
            if (dataStore.getLowBatPollInterval() > POLL_PERIOD_MS) {
                userLogMessage(ICON_WARN + "Warning: pump battery low. Poll interval increased, history and backfill processing disabled.");
                userLogMessage(ICON_SETTING + "Low battery poll interval: " + (dataStore.getLowBatPollInterval() / 60000) + " minutes");
            } else {
                userLogMessage(ICON_WARN + "Warning: pump battery low");
            }
        }

        if (Math.abs(pumpClockDifference) > ERROR_PUMPCLOCK_MS)
            PumpClockError++;
        if (PumpClockError >= ERROR_PUMPCLOCK_AT) {
            PumpClockError = 0;
            userLogMessage(ICON_WARN + "Warning: Time difference between Pump and Uploader excessive."
                    + " Pump is over " + (Math.abs(pumpClockDifference) / 60000L) + " minutes " + (pumpClockDifference > 0 ? "ahead" : "behind") + " of time used by uploader.");
            userLogMessage(ICON_HELP + "The uploader phone/device should have the current time provided by network. Pump clock drifts forward and needs to be set to correct time occasionally.");
        }

        if (CommsError >= ERROR_COMMS_AT) {
            userLogMessage(ICON_WARN + "Warning: multiple comms/timeout errors detected.");
            userLogMessage(ICON_HELP + "Try: disconnecting and reconnecting the Contour Next Link to phone / restarting phone / check pairing of CNL with Pump.");
        }

        if (PumpLostSensorError >= ERROR_PUMPLOSTSENSOR_AT) {
            PumpLostSensorError = 0;
            userLogMessage(ICON_WARN + "Warning: SGV is unavailable from pump often. The pump is missing transmissions from the sensor.");
            userLogMessage(ICON_HELP + "Keep pump on same side of body as sensor. Avoid using body sensor locations that can block radio signal.");
        }

        if (CommsConnectError >= ERROR_CONNECT_AT * (dataStore.isDoublePollOnPumpAway() ? 2 : 1)) {
            CommsConnectError = 0;
            userLogMessage(ICON_WARN + "Warning: connecting to pump is failing often.");
            userLogMessage(ICON_HELP + "Keep pump nearby to uploader phone/device. The body can block radio signals between pump and uploader.");
        }

        if (CommsSignalError >= ERROR_SIGNAL_AT) {
            CommsSignalError = 0;
            userLogMessage(ICON_WARN + "Warning: RSSI radio signal from pump is generally weak and may increase errors.");
            userLogMessage(ICON_HELP + "Keep pump nearby to uploader phone/device. The body can block radio signals between pump and uploader.");
        }
    }

    private void RemoveOutdatedRecords() {
        final RealmResults<PumpStatusEvent> results =
                realm.where(PumpStatusEvent.class)
                        .lessThan("eventDate", new Date(System.currentTimeMillis() - (48 * 60 * 60000L)))
                        .findAll();

        if (results.size() > 0) {
            realm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    // Delete all matches
                    Log.d(TAG, "Deleting " + results.size() + " records from realm");
                    results.deleteAllFromRealm();
                }
            });
        }
    }

    private void validatePumpRecord(PumpStatusEvent pumpRecord, PumpInfo activePump) {

        // validate that this contains a new SGV record
        if (pumpRecord.isCgmActive()) {
            if (pumpRecord.getEventRTC() - pumpRecord.getCgmRTC() > (POLL_PERIOD_MS + 10000) / 1000)
                pumpRecord.setOldSgvWhenNewExpected(true);
            else if (!pumpRecord.isCgmWarmUp() && pumpRecord.getSgv() > 0 &&
                    activePump.getPumpHistory().where().equalTo("cgmRTC", pumpRecord.getCgmRTC()).findAll().size() == 0)
                pumpRecord.setValidSGV(true);
        }

    }

    private boolean isHistoryNeeded() {
        boolean historyNeeded = false;
        String info = ICON_REFRESH + "history: ";

        long recency = pumpHistoryHandler.pumpHistoryRecency() / 60000L;
        if (recency == -1 || recency > 24 * 60) {
            userLogMessage(info + "no recent data");
            historyNeeded = true;
        } else if (recency >= 6 * 60) {
            userLogMessage(info + "recency " + (recency < 120 ? recency + " minutes" : ">" + recency / 60 + " hours"));
            historyNeeded = true;
        } else if (dataStore.getSysPumpHistoryFrequency() > 0 && (recency >= dataStore.getSysPumpHistoryFrequency() && pumpHistoryHandler.isLoopActivePotential())) {
            userLogMessage(info + "auto mode");
            historyNeeded = true;
        }

        RealmResults<PumpStatusEvent> results = realm
                .where(PumpStatusEvent.class)
                .findAllSorted("eventDate", Sort.DESCENDING);

        if (results.size() > 1) {

            // time between status data points
            long ageMS = results.first().getEventDate().getTime() - results.get(1).getEventDate().getTime();
            int ageMinutes = (int) Math.ceil(ageMS / 60000L);

            if (ageMinutes > 15) {
                userLogMessage(info + "stale status " + (ageMinutes < 120 ? ageMinutes + " minutes" : ">" + ageMinutes / 60 + " hours"));
                historyNeeded = true;

            } else {

                // basal pattern changed?
                if (results.first().getActiveBasalPattern() != 0 && results.get(1).getActiveBasalPattern() != 0
                        && results.first().getActiveBasalPattern() != results.get(1).getActiveBasalPattern()) {
                    userLogMessage(info + "basal pattern changed");
                    historyNeeded = true;
                }

                // suspend/resume?
                if (results.first().isSuspended() != results.get(1).isSuspended()) {
                    userLogMessage(info + (results.first().isSuspended() ? "basal suspend" : "basal resume"));
                    historyNeeded = true;
                }

                // new temp basal?
                if (results.first().isTempBasalActive() && !results.get(1).isTempBasalActive()) {
                    userLogMessage(info + "temp basal");
                    historyNeeded = true;
                }

                // was temp ended before expected duration?
                if (!results.first().isTempBasalActive() && results.get(1).isTempBasalActive()) {
                    int diff = results.get(1).getTempBasalMinutesRemaining() - ageMinutes;
                    if (diff < -4 || diff > 4) {
                        userLogMessage(info + "temp ended");
                        historyNeeded = true;
                    }
                }

                // was a new temp started while one was in progress?
                if (results.first().isTempBasalActive() && results.get(1).isTempBasalActive()) {
                    int diff = results.get(1).getTempBasalMinutesRemaining() - results.first().getTempBasalMinutesRemaining() - ageMinutes;
                    if (diff < -4 || diff > 4) {
                        userLogMessage(info + "temp extended");
                        historyNeeded = true;
                    }
                }

                // bolus part delivered? normal (ref change) / square (ref change) / dual (ref can change due to normal bolus mid delivery of square part)
                if (!results.first().isBolusingNormal() && results.first().getLastBolusReference() != results.get(1).getLastBolusReference()) {
                    // end of a dual bolus?
                    if (!results.first().isBolusingDual() && results.get(1).isBolusingDual()) {
                        // was dual ended before expected duration?
                        int diff = results.get(1).getBolusingMinutesRemaining() - ageMinutes;
                        if (diff < -4 || diff > 4) {
                            userLogMessage(info + "dual ended");
                            historyNeeded = true;
                        }
                    }
                    // end of a square bolus?
                    else if (!results.first().isBolusingSquare() && results.get(1).isBolusingSquare() && !results.get(1).isBolusingNormal()) {
                        // was square ended before expected duration?
                        int diff = results.get(1).getBolusingMinutesRemaining() - ageMinutes;
                        if (diff < -4 || diff > 4) {
                            userLogMessage(info + "square ended");
                            historyNeeded = true;
                        }
                    }
                    // dual bolus normal part delivered?
                    else if (!results.first().isBolusingSquare() && !results.get(1).isBolusingDual() && results.first().isBolusingDual()) {
                        userLogMessage(info + "dual bolus");
                        historyNeeded = true;
                    }
                    // normal bolus delivered
                    else {
                        userLogMessage(info + "normal bolus");
                        historyNeeded = true;
                    }
                    // dual bolus ended? or ended before expected duration?
                } else if (!results.first().isBolusingDual() && results.get(1).isBolusingDual()) {
                    // was dual ended before expected duration?
                    int diff = results.get(1).getBolusingMinutesRemaining() - ageMinutes;
                    if (diff < -4 || diff > 4) {
                        userLogMessage(info + "dual ended");
                        historyNeeded = true;
                    }
                }

                // square bolus started?
                if (!results.first().isBolusingNormal() && results.first().isBolusingSquare() && !results.get(1).isBolusingSquare()) {
                    userLogMessage(info + "square bolus");
                    historyNeeded = true;
                }

                // recentBGL is in the status message for up to 20 minutes, check for this or if there was a new reading with a different bgl value
                if (results.first().getRecentBGL() != 0 &&
                        results.where()
                                .greaterThan("eventDate", new Date(System.currentTimeMillis() - (20 * 60000L)))
                                .equalTo("recentBGL", results.first().getRecentBGL())
                                .findAll()
                                .size() == 1) {
                    userLogMessage(info + "recent finger bg");
                    historyNeeded = true;
                }

                // calibration factor info available?
                if (dataStore.isNsEnableCalibrationInfo() && dataStore.isNsEnableCalibrationInfoNow()
                        && (results.first().isCgmCalibrationComplete() && !results.get(1).isCgmCalibrationComplete())) {
                    userLogMessage(info + "calibration info");
                    historyNeeded = true;
                }

                // reservoir/battery changes need to pull history after pump has resumed
                // to ensure that we don't miss the resume entry in the history
                if (results.first().getActiveBasalPattern() != 0) {

                    if (results.first().getReservoirAmount() > results.get(1).getReservoirAmount()) {
                        userLogMessage(info + "reservoir changed");
                        historyNeeded = true;
                    }

                    if (results.first().getBatteryPercentage() > results.get(1).getBatteryPercentage()) {
                        userLogMessage(info + "battery changed");
                        historyNeeded = true;
                    }

                    if (!historyNeeded && results.get(1).getActiveBasalPattern() == 0) {
                        userLogMessage(info + "pattern resume");
                        historyNeeded = true;
                    }
                }

                if (results.first().isCgmWarmUp()) {
                    results = results.where()
                            .equalTo("cgmActive", true)
                            .findAllSorted("eventDate", Sort.DESCENDING);
                    if (results.size() > 1 && !results.get(1).isCgmWarmUp()) {
                        userLogMessage(info + "sensor changed");
                        historyNeeded = true;
                    }
                }
            }
        }

        return historyNeeded;
    }

    private long requestPollTime(long lastPollTime, long pollInterval) {

        long sysPollNoCgmPeriod = 15000L;

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
                    && cgmresults.first().getCgmRTC() != results.first().getCgmRTC()) {
                pollOffset = dataStore.isSysEnablePollOverride() ? dataStore.getSysPollRecoveryPeriod() : POLL_RECOVERY_PERIOD_MS;

                // extended anti-clash protection used to make sure we don't lock into a time frame that stops pump receiving any sensor comms
                if (PumpCgmNA >= POLL_ANTI_CLASH)
                    pollOffset += (((PumpCgmNA - POLL_ANTI_CLASH) % 3)) * 15 * 1000L;
            }

            // check if sensor is in warmup phase
            else if (now - lastRecievedEventTime <= 120 * 60000L
                    && cgmresults.first().isCgmWarmUp())
                pollOffset = dataStore.isSysEnablePollOverride() ? dataStore.getSysPollWarmupPeriod() : POLL_WARMUP_PERIOD_MS;

            nextExpectedEventTime = lastActualEventTime + POLL_PERIOD_MS;
            nextRequestedPollTime = lastActualEventTime + pollInterval + pollOffset;

            // check if request is already stale
            if (nextRequestedPollTime < now)
                nextRequestedPollTime = nextExpectedEventTime + pollOffset;

        } else {

            // no cgm event available to sync with
            nextRequestedPollTime = lastPollTime + pollInterval + sysPollNoCgmPeriod;
        }

        return nextRequestedPollTime;
    }

    private long checkPollTime() {

        long now = System.currentTimeMillis();

        long lastRecievedEventTime;
        long lastActualEventTime;

        long due = 0;

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

            // post expected event check
            if (now < lastActualEventTime + pollOffset - 5000L)
                due = lastActualEventTime + pollOffset;

                // pre expected event check
            else if (now > lastActualEventTime + POLL_PERIOD_MS - POLL_PRE_GRACE_PERIOD_MS)
                due = lastActualEventTime + POLL_PERIOD_MS + pollOffset;
        }

        return due;
    }

    /**
     * @return if device acquisition was successful
     */
    private boolean openUsbDevice() {
        if (!hasUsbHostFeature()) {
            userLogMessage(ICON_WARN + "It appears that this device doesn't support USB OTG.");
            Log.e(TAG, "Device does not support USB OTG");
            return false;
        }

        UsbDevice cnlStick = UsbHidDriver.getUsbDevice(mUsbManager, USB_VID, USB_PID);
        if (cnlStick == null) {
            userLogMessage(ICON_WARN + "USB connection error. Is the Contour Next Link plugged in?");
            Log.w(TAG, "USB connection error. Is the CNL plugged in?");
            return false;
        }

        if (!mUsbManager.hasPermission(UsbHidDriver.getUsbDevice(mUsbManager, USB_VID, USB_PID))) {
            sendMessage(MasterService.Constants.ACTION_NO_USB_PERMISSION);
            return false;
        }
        mHidDevice = UsbHidDriver.acquire(mUsbManager, cnlStick);

        try {
            mHidDevice.open();
        } catch (Exception e) {
            userLogMessage(ICON_WARN + "Unable to open USB device");
            Log.e(TAG, "Unable to open serial device", e);
            return false;
        }

        return true;
    }

    private boolean hasUsbHostFeature() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST);
    }

    private void debugStatusMessage() {
        long now = System.currentTimeMillis();

        Date lastDate = pumpHistoryHandler.debugNoteLastDate();
        if (lastDate != null && now - lastDate.getTime() < 30 * 60000L) return;

        RealmResults<PumpStatusEvent> results = realm
                .where(PumpStatusEvent.class)
                .greaterThan("eventDate", new Date(now - 33 * 60000L))
                .findAllSorted("eventDate", Sort.DESCENDING);

        if (results.size() < 1) return;

        String note = "DEBUG:";
        byte[] payload;

        for (PumpStatusEvent record : results) {
            note += " [" + dateFormatter.format(record.getEventDate()) + "] ";

            byte status = record.getPumpStatus();

            note += " ST:" + String.format(Locale.US,"%8s", Integer.toBinaryString(status)).replace(' ', '0')
                    + " BP:" + record.getActiveBasalPattern() + "/" + record.getActiveTempBasalPattern()
                    + " LB:" + (record.getLastBolusReference() & 0xFF) + "/" + (record.getBolusingReference() & 0xFF)
                    + " BR:" + record.getBasalRate() + "/" + record.getBasalUnitsDeliveredToday()
                    + " AI:" + record.getActiveInsulin();

            payload = record.getPayload();
            if (payload != null && payload.length >= 0x60) {
                if ((payload[0x08] | payload[0x09] | payload[0x0A] | payload[0x0B]) > 0) {
                    note += " 0x08: " + HexDump.toHexString(payload, 0x08, 4);
                }

                if ((payload[0x55] | payload[0x56] | payload[0x57]) > 0) {
                    note += " 0x55: " + HexDump.toHexString(payload, 0x55, 3);
                }

                if ((payload[0x0F] | payload[0x19]) > 0) {
                    note += " 0x0F: " + HexDump.toHexString(payload[0x0F]) + " 0x19: " + HexDump.toHexString(payload[0x19]);
                }

                if (payload.length > 0x60) {
                    note += " 0x60: " + HexDump.toHexString(payload, 0x60, payload.length - 0x60);
                }
            }
        }

        pumpHistoryHandler.debugNote(new Date(now), note);
    }
}
