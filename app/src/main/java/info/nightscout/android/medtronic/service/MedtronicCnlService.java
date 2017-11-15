package info.nightscout.android.medtronic.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
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

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.UploaderApplication;
import info.nightscout.android.medtronic.MedtronicCnlReader;
import info.nightscout.android.medtronic.PumpHistoryHandler;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;
import info.nightscout.android.medtronic.message.ContourNextLinkCommandMessage;
import info.nightscout.android.medtronic.message.MessageUtils;
import info.nightscout.android.model.medtronicNg.ContourNextLinkInfo;
import info.nightscout.android.model.medtronicNg.PumpInfo;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.upload.nightscout.NightscoutUploadReceiver;
import info.nightscout.android.xdrip_plus.XDripPlusUploadReceiver;
import info.nightscout.android.utils.DataStore;
import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;

import static info.nightscout.android.utils.ToolKit.getWakeLock;
import static info.nightscout.android.utils.ToolKit.releaseWakeLock;

public class MedtronicCnlService extends Service {
    private static final String TAG = MedtronicCnlService.class.getSimpleName();

    public final static int USB_VID = 0x1a79;
    public final static int USB_PID = 0x6210;
    public final static long USB_WARMUP_TIME_MS = 5000L;

    public final static long POLL_PERIOD_MS = 300000L;
    public final static long LOW_BATTERY_POLL_PERIOD_MS = 900000L;
    // Number of additional seconds to wait after the next expected CGM poll, so that we don't interfere with CGM radio comms.
    public final static long POLL_GRACE_PERIOD_MS = 30000L; //30000
    // Number of seconds before the next expected CGM poll that we will allow uploader comms to start
    public final static long POLL_PRE_GRACE_PERIOD_MS = 30000; //45000L;
    // cgm n/a events to trigger anti clash poll timing
    public final static int POLL_ANTI_CLASH = 1; //3

    // show warning message after repeated errors
    private final static int ERROR_COMMS_AT = 3;
    private final static int ERROR_CONNECT_AT = 6;
    private final static int ERROR_SIGNAL_AT = 6;
    private final static int ERROR_PUMPLOSTSENSOR_AT = 6;
    private final static int ERROR_PUMPBATTERY_AT = 3;
    private final static int ERROR_PUMPCLOCK_AT = 8;
    private final static int ERROR_PUMPCLOCK_MS = 10 * 60 * 1000;

    private Context mContext;
    private static UsbHidDriver mHidDevice;
    private UsbManager mUsbManager;
    private ReadPump readPump;
    private Realm realm;

    private PumpHistoryHandler pumpHistoryHandler;

    // DataStore local copy
    private boolean RequestPumpHistory;
    private int PumpCgmNA;
    private int CommsSuccess;
    private int CommsError;
    private int CommsConnectError;
    private int CommsSignalError;
    private int CommsSgvSuccess;
    private int PumpLostSensorError;
    private int PumpClockError;
    private int PumpBatteryError;

    private boolean prefReducePollOnPumpAway;
    private long prefPollInterval;
    private long prefLowBatteryPollInterval;

    private boolean commsActive = false;
    private boolean shutdownProtect = false;

    private DateFormat dateFormatter = new SimpleDateFormat("HH:mm:ss", Locale.US);
    private DateFormat dateFormatterNote = new SimpleDateFormat("E HH:mm", Locale.US);
    private DateFormat dateFormatterFull = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US);

    // WIP temporary
    public static int pumpRTC;
    public static int pumpOFFSET;
    public static long pumpEventTime;
    public static long pumpClockDiff;

    public static int cnlClear = 0;
    public static int cnl0x81 = 0;
    public static int cnlChannelNegotiateError = 0;

    protected void sendStatus(String message) {
        try {
            Intent intent =
                    new Intent(MasterService.Constants.ACTION_STATUS_MESSAGE)
                            .putExtra(MasterService.Constants.EXTENDED_DATA, message);
            sendBroadcast(intent);
        } catch (Exception e) {
        }
    }

    protected void sendMessage(String action) {
        try {
            Intent intent =
                    new Intent(action);
            sendBroadcast(intent);
        } catch (Exception e) {
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
        Log.d(TAG, "onDestroy called : commsActive=" + commsActive);

        if (mHidDevice != null) {
            Log.i(TAG, "Closing serial device...");
            mHidDevice.close();
            mHidDevice = null;
        }

        if (UploaderApplication.killer >= 12) {
            Log.d(TAG, "!!! ninja kill cnl process !!!");
            sendStatus("!!! ninja kill cnl process !!!");
//                System.runFinalization();
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }

    @Override
    public void onTaskRemoved(Intent intent) {
        Log.d(TAG, "onTaskRemoved called : " + intent);
        sendStatus(TAG + " onTaskRemoved : commsActive=" + commsActive);
    }

    @Override
    public void onLowMemory() {
        Log.d(TAG, "onLowMemory called");
        sendStatus(TAG + " onLowMemory : commsActive=" + commsActive);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Received start id " + startId + "  : " + intent);

        if (intent == null) {
            return START_NOT_STICKY;
        }

        String action = intent.getAction();

        if (MasterService.Constants.ACTION_CNL_READPUMP.equals(action) && readPump == null) {
            prefPollInterval = intent.getLongExtra("PollInterval", POLL_PERIOD_MS);
            prefLowBatteryPollInterval = intent.getLongExtra("LowBatteryPollInterval", LOW_BATTERY_POLL_PERIOD_MS);
            prefReducePollOnPumpAway = intent.getBooleanExtra("ReducePollOnPumpAway", false);

            readPump = new ReadPump();
            readPump.setPriority(Thread.MIN_PRIORITY);
            readPump.start();

            return START_STICKY;
        } else if (MasterService.Constants.ACTION_CNL_SHUTDOWN.equals(action) && readPump != null) {
            // device is shutting down, pull the emergency brake!
            // less then ideal but we need to stop CNL comms asap before android kills us while protecting comms that must complete to avoid a CNL E86 error
            if (mHidDevice != null) {
                Log.d(TAG, "device is shutting down, pull the emergency brake!");

                long now = System.currentTimeMillis();
                while (shutdownProtect && (System.currentTimeMillis() - now) < 1000) {
                    Log.d(TAG, "shutdownProtect");
                    readPump.interrupt();
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                    }
                }

                try {
                    new ContourNextLinkCommandMessage(ContourNextLinkCommandMessage.ASCII.EOT)
                            .sendNoResponse(mHidDevice);
                    Thread.sleep(10);
                } catch (IOException e) {
                } catch (TimeoutException e) {
                } catch (UnexpectedMessageException e) {
                } catch (ChecksumException e) {
                } catch (EncryptionException e) {
                } catch (InterruptedException e) {
                }

                mHidDevice.close();
                mHidDevice = null;
            }
            stopSelf();
            android.os.Process.killProcess(android.os.Process.myPid());
        }

        return START_NOT_STICKY;
    }

    private DataStore dataStore;

    private void readDataStore() {
        dataStore = realm.where(DataStore.class).findFirst();
        RequestPumpHistory = dataStore.isRequestPumpHistory();
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
        realm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                dataStore.setRequestPumpHistory(RequestPumpHistory);
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

//            sendStatus("PollInterval=" + prefPollInterval + " LowBatteryPollInterval=" + prefLowBatteryPollInterval + " ReducePollOnPumpAway=" + prefReducePollOnPumpAway );

            PowerManager.WakeLock wl = getWakeLock(TAG, 60000);

            long timePollStarted = System.currentTimeMillis();
//            long nextpoll = timePollStarted + POLL_GRACE_PERIOD_MS;
            long nextpoll = 0;

            realm = Realm.getDefaultInstance();
            pumpHistoryHandler = new PumpHistoryHandler();

            readDataStore();

            cnlClear = 0;
            cnl0x81 = 0;
            cnlChannelNegotiateError = 0;

            try {
                long pollInterval = prefPollInterval;

                if (!openUsbDevice()) {
                    Log.w(TAG, "Could not open usb device");
//                sendStatus(ICON_WARN + "Could not open usb device");
                    return;
                }

                long due = checkPollTime();
                if (due > 0) {
                    Log.d(TAG, "Please wait: Pump is expecting sensor communication. Poll due in " + ((due - System.currentTimeMillis()) / 1000L) + " seconds");
                    sendStatus("Please wait: Pump is expecting sensor communication. Poll due in " + ((due - System.currentTimeMillis()) / 1000L) + " seconds");
                    nextpoll = due;
                    return;
                }

                MedtronicCnlReader cnlReader = new MedtronicCnlReader(mHidDevice);

                realm.beginTransaction();

                commsActive = true;

                try {
                    sendStatus("Connecting to CNL [pid" + android.os.Process.myPid() + "]");
//                sendStatus("Connecting to Contour Next Link ");
                    Log.d(TAG, "Connecting to Contour Next Link");

                    shutdownProtect = true;
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
                        shutdownProtect = false;
                        cnlReader.openConnection();

                        cnlReader.requestReadInfo();

                        // always get LinkKey on startup to handle re-paired CNL-PUMP key changes
                        String key = null;
                        if (CommsSuccess > 0) {
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

                        byte radioChannel;
                        int retry = 3;
                        do {
                            radioChannel = cnlReader.negotiateChannel(activePump.getLastRadioChannel());
                        } while (radioChannel == 0 && --retry > 0);

//                        byte radioChannel = cnlReader.negotiateChannel(activePump.getLastRadioChannel());
                        if (radioChannel == 0) {
                            sendStatus(MasterService.ICON_WARN + "Could not communicate with the pump. Is it nearby?");
                            Log.i(TAG, "Could not communicate with the pump. Is it nearby?");
                            CommsConnectError++;
                            pollInterval = POLL_PERIOD_MS / (prefReducePollOnPumpAway ? 2L : 1L); // reduce polling interval to half until pump is available
                        } else if (cnlReader.getPumpSession().getRadioRSSIpercentage() < 5) {
                            sendStatus(String.format(Locale.getDefault(), "Connected on channel %d  RSSI: %d%%", (int) radioChannel, cnlReader.getPumpSession().getRadioRSSIpercentage()));
                            sendStatus(MasterService.ICON_WARN + "Warning: pump signal too weak. Is it nearby?");
                            Log.i(TAG, "Warning: pump signal too weak. Is it nearby?");
                            CommsConnectError++;
                            CommsSignalError++;
                            pollInterval = POLL_PERIOD_MS / (prefReducePollOnPumpAway ? 2L : 1L); // reduce polling interval to half until pump is available
                        } else {
                            if (CommsConnectError > 0) CommsConnectError--;
                            if (cnlReader.getPumpSession().getRadioRSSIpercentage() < 20)
                                CommsSignalError++;
                            else if (CommsSignalError > 0) CommsSignalError--;

                            if (retry != 3) {
                                sendStatus("*** connect retry: " + (3 - retry));
                            }

                            activePump.setLastRadioChannel(radioChannel);
                            sendStatus(String.format(Locale.getDefault(), "Connected on channel %d  RSSI: %d%%", (int) radioChannel, cnlReader.getPumpSession().getRadioRSSIpercentage()));
                            Log.d(TAG, String.format("Connected to Contour Next Link on channel %d.", (int) radioChannel));

                            // read pump status
                            PumpStatusEvent pumpRecord = realm.createObject(PumpStatusEvent.class);

                            String deviceName = String.format("medtronic-600://%s", cnlReader.getStickSerial());
                            activePump.setDeviceName(deviceName);

                            // TODO - this should not be necessary. We should reverse lookup the device name from PumpInfo
                            pumpRecord.setDeviceName(deviceName);

                            pumpEventTime = System.currentTimeMillis();
                            pumpClockDiff = cnlReader.getPumpTime().getTime() - pumpEventTime;

                            pumpRecord.setPumpTimeOffset(pumpClockDiff);
                            pumpRecord.setPumpDate(new Date(pumpEventTime + pumpClockDiff));
                            pumpRecord.setEventDate(new Date(pumpEventTime));
                            cnlReader.updatePumpStatus(pumpRecord);


                            validatePumpRecord(pumpRecord, activePump);
                            activePump.getPumpHistory().add(pumpRecord);

                            CommsSuccess++;
                            CommsError = 0;

                            if (pumpRecord.getBatteryPercentage() <= 25) {
                                PumpBatteryError++;
                                pollInterval = prefLowBatteryPollInterval;
                            } else {
                                PumpBatteryError = 0;
                            }

                            if (pumpRecord.isCgmActive()) {
                                PumpCgmNA = 0; // poll clash detection
                                PumpLostSensorError = 0;

                                if (pumpRecord.isCgmWarmUp())
                                    sendStatus(MasterService.ICON_CGM + "sensor is in warm-up phase");
                                else if (pumpRecord.getCalibrationDueMinutes() == 0)
                                    sendStatus(MasterService.ICON_CGM + "sensor calibration is due now!");
                                else if (pumpRecord.getSgv() == 0 && pumpRecord.isCgmCalibrating())
                                    sendStatus(MasterService.ICON_CGM + "sensor is calibrating");
                                else if (pumpRecord.getSgv() == 0)
                                    sendStatus(MasterService.ICON_CGM + "sensor error (pump graph gap)");
                                else {
                                    CommsSgvSuccess++;
                                    sendStatus("SGV: ¦" + pumpRecord.getSgv()
                                            + "¦  At: " + dateFormatter.format(pumpRecord.getCgmDate().getTime())
                                            + "  Pump: " + (pumpClockDiff > 0 ? "+" : "") + (pumpClockDiff / 1000L) + "sec");
                                    if (pumpRecord.isCgmCalibrating())
                                        sendStatus(MasterService.ICON_CGM + "sensor is calibrating");
                                    if (pumpRecord.isOldSgvWhenNewExpected()) {
                                        sendStatus(MasterService.ICON_CGM + "old SGV event received");
                                        // pump may have missed sensor transmission or be delayed in posting to status message
                                        // in most cases the next scheduled poll will have latest sgv, occasionally it is available this period after a delay
                                        pollInterval = 90000;
                                    }
                                }

                            } else {
                                PumpCgmNA++; // poll clash detection
                                if (CommsSgvSuccess > 0) {
                                    PumpLostSensorError++; // only count errors if cgm is being used
                                    sendStatus(MasterService.ICON_CGM + "cgm n/a (pump lost sensor)");
                                } else {
                                    sendStatus(MasterService.ICON_CGM + "cgm n/a");
                                }
                            }

                            statusNotifications(pumpRecord);

                            realm.commitTransaction();

                            // got history? will need to make charts update based on this!

                            //cnlReader.getHistoryInfo(timePollStarted - 1 * 60 * 60 * 1000, timePollStarted, pumpOFFSET, 2);
                            //cnlReader.getHistory(timePollStarted - 3 * 24 * 60 * 60 * 1000, timePollStarted, pumpOFFSET, 2);

                            //cnlReader.getHistory(timePollStarted - 1 * 60 * 60 * 1000, timePollStarted, pumpOFFSET, 2);

                            //cnlReader.getBasalPatterns();
                            //cnlReader.getBolusWizardCarbRatios();
                            //cnlReader.getBolusWizardSensitivity();
                            //cnlReader.getBolusWizardTargets();

                            pumpHistoryHandler.setPumpOFFSET(pumpOFFSET); // temp not final
                            pumpHistoryHandler.setPumpEvent(pumpEventTime); // temp not final

                            if (RequestPumpHistory) pumpHistoryHandler.setPullPump(true) ;
                            pumpHistoryHandler.history(pumpRecord, cnlReader);
                            RequestPumpHistory = false;

                            if (dataStore.isRequestProfile()) {
                                pumpHistoryHandler.profile(cnlReader);
                                realm.executeTransaction(new Realm.Transaction() {
                                    @Override
                                    public void execute(Realm realm) {
                                        dataStore.setRequestProfile(false);
                                    }
                                });
                            }

                        }

                    } catch (UnexpectedMessageException e) {
                        CommsError++;
                        pollInterval = 90000L; // retry once during this poll period, this allows for transient radio noise
                        Log.e(TAG, "Unexpected Message", e);
                        sendStatus(MasterService.ICON_WARN + "Communication Error: " + e.getMessage());
                    } catch (TimeoutException e) {
                        CommsError++;
                        pollInterval = 90000L; // retry once during this poll period, this allows for transient radio noise
                        Log.e(TAG, "Timeout communicating with the Contour Next Link.", e);
                        //sendStatus(MasterService.ICON_WARN + "Timeout communicating with the Contour Next Link / Pump.");
                        sendStatus(MasterService.ICON_WARN + "Timeout Error: " + e.getMessage());
                    } catch (ChecksumException e) {
                        CommsError++;
                        Log.e(TAG, "Checksum error getting message from the Contour Next Link.", e);
                        //sendStatus(MasterService.ICON_WARN + "Checksum error getting message from the Contour Next Link.");
                        sendStatus(MasterService.ICON_WARN + "Checksum Error: " + e.getMessage());
                    } catch (EncryptionException e) {
                        CommsError++;
                        Log.e(TAG, "Error decrypting messages from Contour Next Link.", e);
                        //sendStatus(MasterService.ICON_WARN + "Error decrypting messages from Contour Next Link.");
                        sendStatus(MasterService.ICON_WARN + "Decryption Error: " + e.getMessage());
                    } catch (NoSuchAlgorithmException e) {
                        CommsError++;
                        Log.e(TAG, "Could not determine CNL HMAC", e);
                        sendStatus(MasterService.ICON_WARN + "Error connecting to Contour Next Link: Hashing error.");
                    } finally {
                        try {
                            if (cnlChannelNegotiateError == 0)
                                cnlReader.closeConnection();
                            shutdownProtect = true;
                            cnlReader.endPassthroughMode();
                            shutdownProtect = false;
                            cnlReader.endControlMode();
                        } catch (NoSuchAlgorithmException e) {
                        }
                    }
                } catch (IOException e) {
                    CommsError++;
                    Log.e(TAG, "Error connecting to Contour Next Link.", e);
                    sendStatus(MasterService.ICON_WARN + "Error connecting to Contour Next Link.");
                    if (cnlReader.resetCNL())
                        sendStatus(MasterService.ICON_INFO + "CNL reset successful.");
                } catch (ChecksumException e) {
                    CommsError++;
                    Log.e(TAG, "Checksum error getting message from the Contour Next Link.", e);
//                    sendStatus(MasterService.ICON_WARN + "Checksum error getting message from the Contour Next Link.");
                    sendStatus(MasterService.ICON_WARN + "Checksum Error: " + e.getMessage());
                    if (cnlReader.resetCNL())
                        sendStatus(MasterService.ICON_INFO + "CNL reset successful.");
                } catch (EncryptionException e) {
                    CommsError++;
                    Log.e(TAG, "Error decrypting messages from Contour Next Link.", e);
                    sendStatus(MasterService.ICON_WARN + "Error decrypting messages from Contour Next Link.");
                    if (cnlReader.resetCNL())
                        sendStatus(MasterService.ICON_INFO + "CNL reset successful.");
                } catch (TimeoutException e) {
                    CommsError++;
                    Log.e(TAG, "Timeout communicating with the Contour Next Link.", e);
//                    sendStatus(MasterService.ICON_WARN + "Timeout communicating with the Contour Next Link.");
                    sendStatus(MasterService.ICON_WARN + "Timeout Error(2): " + e.getMessage());
                    if (cnlReader.resetCNL())
                        sendStatus(MasterService.ICON_INFO + "CNL reset successful.");
                } catch (UnexpectedMessageException e) {
                    CommsError++;
                    Log.e(TAG, "Could not close connection.", e);
                    sendStatus(MasterService.ICON_WARN + "Could not close connection: " + e.getMessage());
                    if (cnlReader.resetCNL())
                        sendStatus(MasterService.ICON_INFO + "CNL reset successful.");
                } finally {
                    commsActive = false;
                    if (realm.isInTransaction()) {
                        // If we didn't commit the transaction, we've run into an error. Let's roll it back
                        realm.cancelTransaction();
                    }

                    if (cnlClear > 0 || cnl0x81 > 0)
                        sendStatus("*** cnlClear=" + cnlClear + " cnl0x81=" + cnl0x81);

                    nextpoll = requestPollTime(timePollStarted, pollInterval);
//                    sendStatus("Next poll due at: " + dateFormatter.format(nextpoll));
                    sendStatus("Next poll due at: " + dateFormatter.format(nextpoll) + " [" + (System.currentTimeMillis() - timePollStarted) + "ms]");

                    RemoveOutdatedRecords();
                    //uploadPollResults();
                }

                UploaderApplication.killer++;

            } finally {
                statusWarnings();
                writeDataStore();

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

    private void statusNotifications(PumpStatusEvent pumpRecord) {
        if (pumpRecord.isValidBGL())
            sendStatus(MasterService.ICON_BGL + "Recent finger BG: ¦" + pumpRecord.getRecentBGL() + "¦");

        if (pumpRecord.isValidBolus()) {
            if (pumpRecord.isValidBolusSquare())
                sendStatus(MasterService.ICON_BOLUS + "Square bolus delivered: " + pumpRecord.getLastBolusAmount() + "u Started: " + dateFormatter.format(pumpRecord.getLastBolusDate()) + " Duration: " + pumpRecord.getLastBolusDuration() + " minutes");
            else if (pumpRecord.isValidBolusDual())
                sendStatus(MasterService.ICON_BOLUS + "Bolus (dual normal part): " + pumpRecord.getLastBolusAmount() + "u At: " + dateFormatter.format(pumpRecord.getLastBolusDate()));
            else
                sendStatus(MasterService.ICON_BOLUS + "Bolus: " + pumpRecord.getLastBolusAmount() + "u At: " + dateFormatter.format(pumpRecord.getLastBolusDate()));
        }

        if (pumpRecord.isValidTEMPBASAL()) {
            if (pumpRecord.getTempBasalMinutesRemaining() > 0 && pumpRecord.getTempBasalPercentage() > 0)
                sendStatus(MasterService.ICON_BASAL + "Temp basal: " + pumpRecord.getTempBasalPercentage() + "% Remaining: " + pumpRecord.getTempBasalMinutesRemaining() + " minutes");
            else if (pumpRecord.getTempBasalMinutesRemaining() > 0)
                sendStatus(MasterService.ICON_BASAL + "Temp basal: " + pumpRecord.getTempBasalRate() + "u Remaining: " + pumpRecord.getTempBasalMinutesRemaining() + " minutes");
            else
                sendStatus(MasterService.ICON_BASAL + "Temp basal: stopped before expected duration");
        }

        if (pumpRecord.isValidSUSPEND())
            sendStatus(MasterService.ICON_SUSPEND + "Pump suspended insulin delivery approx: " + dateFormatterNote.format(pumpRecord.getSuspendAfterDate()) + " - " + dateFormatterNote.format(pumpRecord.getSuspendBeforeDate()));
        if (pumpRecord.isValidSUSPENDOFF())
            sendStatus(MasterService.ICON_RESUME + "Pump resumed insulin delivery approx: " + dateFormatterNote.format(pumpRecord.getSuspendAfterDate()) + " - " + dateFormatterNote.format(pumpRecord.getSuspendBeforeDate()));

        if (pumpRecord.isValidSAGE())
            sendStatus(MasterService.ICON_CHANGE + "Sensor changed approx: " + dateFormatterNote.format(pumpRecord.getSageAfterDate()) + " - " + dateFormatterNote.format(pumpRecord.getSageBeforeDate()));
        if (pumpRecord.isValidCAGE())
            sendStatus(MasterService.ICON_CHANGE + "Reservoir changed approx: " + dateFormatterNote.format(pumpRecord.getCageAfterDate()) + " - " + dateFormatterNote.format(pumpRecord.getCageBeforeDate()));
        if (pumpRecord.isValidBATTERY())
            sendStatus(MasterService.ICON_CHANGE + "Pump battery changed approx: " + dateFormatterNote.format(pumpRecord.getBatteryAfterDate()) + " - " + dateFormatterNote.format(pumpRecord.getBatteryBeforeDate()));

        if (pumpRecord.isValidALERT())
            sendStatus(MasterService.ICON_BELL + "Active alert on pump At: " + dateFormatter.format(pumpRecord.getAlertDate()));
    }

    private void statusWarnings() {

        if (PumpBatteryError >= ERROR_PUMPBATTERY_AT) {
            PumpBatteryError = 0;
            sendStatus(MasterService.ICON_WARN + "Warning: pump battery low");
            if (prefLowBatteryPollInterval != prefPollInterval)
                sendStatus(MasterService.ICON_SETTING + "Low battery poll interval: " + (prefLowBatteryPollInterval / 60000) + " minutes");
        }

        if (Math.abs(pumpClockDiff) > ERROR_PUMPCLOCK_MS)
            PumpClockError++;
        if (PumpClockError >= ERROR_PUMPCLOCK_AT) {
            PumpClockError = 0;
            sendStatus(MasterService.ICON_WARN + "Warning: Time difference between Pump and Uploader excessive."
                    + " Pump is over " + (Math.abs(pumpClockDiff) / 60000L) + " minutes " + (pumpClockDiff > 0 ? "ahead" : "behind") + " of time used by uploader.");
            sendStatus(MasterService.ICON_HELP + "The uploader phone/device should have the current time provided by network. Pump clock drifts forward and needs to be set to correct time occasionally.");
        }

        if (CommsError >= ERROR_COMMS_AT) {
            sendStatus(MasterService.ICON_WARN + "Warning: multiple comms/timeout errors detected.");
            sendStatus(MasterService.ICON_HELP + "Try: disconnecting and reconnecting the Contour Next Link to phone / restarting phone / check pairing of CNL with Pump.");
        }

        if (PumpLostSensorError >= ERROR_PUMPLOSTSENSOR_AT) {
            PumpLostSensorError = 0;
            sendStatus(MasterService.ICON_WARN + "Warning: SGV is unavailable from pump often. The pump is missing transmissions from the sensor.");
            sendStatus(MasterService.ICON_HELP + "Keep pump on same side of body as sensor. Avoid using body sensor locations that can block radio signal.");
        }

        if (CommsConnectError >= ERROR_CONNECT_AT * (prefReducePollOnPumpAway ? 2 : 1)) {
            CommsConnectError = 0;
            sendStatus(MasterService.ICON_WARN + "Warning: connecting to pump is failing often.");
            sendStatus(MasterService.ICON_HELP + "Keep pump nearby to uploader phone/device. The body can block radio signals between pump and uploader.");
        }

        if (CommsSignalError >= ERROR_SIGNAL_AT) {
            CommsSignalError = 0;
            sendStatus(MasterService.ICON_WARN + "Warning: RSSI radio signal from pump is generally weak and may increase errors.");
            sendStatus(MasterService.ICON_HELP + "Keep pump nearby to uploader phone/device. The body can block radio signals between pump and uploader.");
        }
    }

    private void RemoveOutdatedRecords() {
        final RealmResults<PumpStatusEvent> results =
                realm.where(PumpStatusEvent.class)
                        .lessThan("eventDate", new Date(System.currentTimeMillis() - (48 * 60 * 60 * 1000)))
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

    // TODO - complete rework of this as we only need history pull triggers now
    // TODO - check on optimal use of Realm search+results as we make heavy use for validation

    private void validatePumpRecord(PumpStatusEvent pumpRecord, PumpInfo activePump) {

        RealmResults<PumpStatusEvent> pump_results = activePump.getPumpHistory()
                .where()
                .findAllSorted("eventDate", Sort.ASCENDING);
        if (pump_results.size() > 0) {
            // use user + low batt intervals!
            if (pumpRecord.getEventDate().getTime() - pump_results.last().getEventDate().getTime() > 15 * 60 * 1000)
                RequestPumpHistory = true;

            else if (pumpRecord.getActiveBasalPattern() != pump_results.last().getActiveBasalPattern())
                RequestPumpHistory = true;

            else if (pumpRecord.isSuspended() != pump_results.last().isSuspended())
                RequestPumpHistory = true;

            else if (pumpRecord.isTempBasalActive() != pump_results.last().isTempBasalActive())
                RequestPumpHistory = true;

            else if (pumpRecord.getLastBolusReference() != pump_results.last().getLastBolusReference())
                RequestPumpHistory = true;
            else if (pumpRecord.isBolusingSquare() != pump_results.last().isBolusingSquare())
                RequestPumpHistory = true;
            else if (pumpRecord.isBolusingDual() != pump_results.last().isBolusingDual())
                RequestPumpHistory = true;

            else if (pumpRecord.isCgmWarmUp() != pump_results.last().isCgmWarmUp())
                RequestPumpHistory = true;
            else if (pumpRecord.getReservoirAmount() > pump_results.last().getReservoirAmount())
                RequestPumpHistory = true;
            else if (pumpRecord.getBatteryPercentage() > pump_results.last().getBatteryPercentage())
                RequestPumpHistory = true;

            else if (pumpRecord.getRecentBGL() != 0 &&
                    pump_results.where()
                            .greaterThan("eventDate", new Date(System.currentTimeMillis() - (20 * 60 * 1000)))
                            .equalTo("recentBGL", pumpRecord.getRecentBGL())
                            .findAll()
                            .size() == 0)
                RequestPumpHistory = true;
        }

// for the trash heap baby!!!

        int index;

        // TODO - pump validation is unused but will allow for future record manipulation when adding data from pump history message (gap fill)

        // validate that this contains a new PUMP record
        pumpRecord.setValidPUMP(true);

        // TODO - cgm validation - handle sensor exceptions

        // validate that this contains a new CGM record
        if (pumpRecord.isCgmActive()) {
            pumpRecord.setValidCGM(true);
        }

        // validate that this contains a new SGV record
        if (pumpRecord.isCgmActive()) {
            if (pumpRecord.getPumpDate().getTime() - pumpRecord.getCgmPumpDate().getTime() > POLL_PERIOD_MS + (POLL_GRACE_PERIOD_MS / 2))
                pumpRecord.setOldSgvWhenNewExpected(true);
            else if (!pumpRecord.isCgmWarmUp() && pumpRecord.getSgv() > 0) {
                RealmResults<PumpStatusEvent> sgv_results = activePump.getPumpHistory()
                        .where()
                        .equalTo("cgmPumpDate", pumpRecord.getCgmPumpDate())
                        .equalTo("validSGV", true)
                        .findAll();
                if (sgv_results.size() == 0)
                    pumpRecord.setValidSGV(true);
            }
        }

        // validate that this contains a new BGL record
        if (pumpRecord.getRecentBGL() != 0) {
            RealmResults<PumpStatusEvent> bgl_results = activePump.getPumpHistory()
                    .where()
                    .greaterThan("eventDate", new Date(System.currentTimeMillis() - (20 * 60 * 1000)))
                    .equalTo("recentBGL", pumpRecord.getRecentBGL())
                    .findAll();
            if (bgl_results.size() == 0) {
                pumpRecord.setValidBGL(true);
//                RequestPumpHistory = true;
            }
        }

        // TODO - square/dual stopped and new bolus started between poll snapshots, check realm history to handle this?
        // TODO - multiple bolus between poll snapshots will only have the most recent, check IOB differences to handle this?

        // validate that this contains a new BOLUS record
        RealmResults<PumpStatusEvent> lastbolus_results = activePump.getPumpHistory()
                .where()
                .equalTo("lastBolusPumpDate", pumpRecord.getLastBolusPumpDate())
                .equalTo("lastBolusReference", pumpRecord.getLastBolusReference())
                .equalTo("validBolus", true)
                .findAll();

        if (lastbolus_results.size() == 0) {

            RealmResults<PumpStatusEvent> bolusing_results = activePump.getPumpHistory()
                    .where()
                    .equalTo("bolusingReference", pumpRecord.getLastBolusReference())
                    .greaterThan("bolusingMinutesRemaining", 10)   // if a manual normal bolus referred to here while square is being delivered it will show the remaining time for all bolusing
                    .findAllSorted("eventDate", Sort.ASCENDING);

            if (bolusing_results.size() > 0) {
                long start = pumpRecord.getLastBolusPumpDate().getTime();
                long start_bolusing = bolusing_results.first().getPumpDate().getTime();

                // if pump battery is changed during square/dual bolus period the last bolus time will be set to this time (pump asks user to resume/cancel bolus)
                // use bolusing start time when this is detected
                if (start - start_bolusing > 10 * 60000)
                    start = start_bolusing;

                long end = pumpRecord.getPumpDate().getTime();
                long duration = start_bolusing - start + (bolusing_results.first().getBolusingMinutesRemaining() * 60000);
                if (start + duration > end) // was square bolus stopped before expected duration?
                    duration = end - start;

                // check that this was a square bolus and not a normal bolus
                if (duration > 10 * 60000) {
                    pumpRecord.setValidBolus(true);
//                    RequestPumpHistory = true;
                    pumpRecord.setValidBolusSquare(true);
                    pumpRecord.setLastBolusDate(new Date(start));
                    pumpRecord.setLastBolusDuration((short) (duration / 60000));
                }
            }

            // check if bolus is current to session on this device
            // this is to avoid duplicates where multiple uploader devices are in use
/*
            RealmResults<PumpStatusEvent> session_results = activePump.getPumpHistory()
                    .where()
                    .greaterThan("eventDate", new Date(System.currentTimeMillis() - (20 * 60 * 1000)))
                    .lessThan("pumpDate", pumpRecord.getLastBolusPumpDate())
                    .findAllSorted("eventDate", Sort.DESCENDING);
            if (session_results.size() > 0) {
*/
            pumpRecord.setValidBolus(true);
//            RequestPumpHistory = true;
            if (pumpRecord.getBolusingReference() == pumpRecord.getLastBolusReference()
                    && pumpRecord.getBolusingMinutesRemaining() > 10) {
                pumpRecord.setValidBolusDual(true);
            }
//            }
        }

        // validate that this contains a new TEMP BASAL record
        // temp basal: rate / percentage can be set on pump for max duration of 24 hours / 1440 minutes
        RealmResults<PumpStatusEvent> tempbasal_results = activePump.getPumpHistory()
                .where()
                .greaterThan("eventDate", new Date(System.currentTimeMillis() - (24 * 60 * 60 * 1000)))
                .findAllSorted("eventDate", Sort.DESCENDING);
        if (pumpRecord.getTempBasalMinutesRemaining() > 0) {
            index = 0;
            if (tempbasal_results.size() > 1) {
                short minutes = pumpRecord.getTempBasalMinutesRemaining();
                for (index = 0; index < tempbasal_results.size(); index++) {
                    if (tempbasal_results.get(index).getTempBasalMinutesRemaining() < minutes ||
                            tempbasal_results.get(index).getTempBasalPercentage() != pumpRecord.getTempBasalPercentage() ||
                            tempbasal_results.get(index).getTempBasalRate() != pumpRecord.getTempBasalRate() ||
                            tempbasal_results.get(index).isValidTEMPBASAL())
                        break;
                    minutes = tempbasal_results.get(index).getTempBasalMinutesRemaining();
                }
            }
            if (tempbasal_results.size() > 0)
                if (!tempbasal_results.get(index).isValidTEMPBASAL() ||
                        tempbasal_results.get(index).getTempBasalPercentage() != pumpRecord.getTempBasalPercentage() ||
                        tempbasal_results.get(index).getTempBasalRate() != pumpRecord.getTempBasalRate()) {
                    pumpRecord.setValidTEMPBASAL(true);
//                    RequestPumpHistory = true;
                    pumpRecord.setTempBasalAfterDate(tempbasal_results.get(index).getEventDate());
                    pumpRecord.setTempBasalBeforeDate(pumpRecord.getEventDate());
                }
        } else {
            // check if stopped before expected duration
            if (tempbasal_results.size() > 0)
                if (pumpRecord.getPumpDate().getTime() - tempbasal_results.first().getPumpDate().getTime() - (tempbasal_results.first().getTempBasalMinutesRemaining() * 60 * 1000) < -60 * 1000) {
                    pumpRecord.setValidTEMPBASAL(true);
//                    RequestPumpHistory = true;
                    pumpRecord.setTempBasalAfterDate(tempbasal_results.first().getEventDate());
                    pumpRecord.setTempBasalBeforeDate(pumpRecord.getEventDate());
                }
        }

        // validate that this contains a new SUSPEND record
        RealmResults<PumpStatusEvent> suspend_results = activePump.getPumpHistory()
                .where()
                .greaterThan("eventDate", new Date(System.currentTimeMillis() - (2 * 60 * 60 * 1000)))
                .equalTo("validSUSPEND", true)
                .or()
                .equalTo("validSUSPENDOFF", true)
                .findAllSorted("eventDate", Sort.DESCENDING);

        if (suspend_results.size() > 0) {
            // new valid suspend - set temp basal for 0u 60m in NS
            if (pumpRecord.isSuspended() && suspend_results.first().isValidSUSPENDOFF()) {
                pumpRecord.setValidSUSPEND(true);
//                RequestPumpHistory = true;
            }
            // continuation valid suspend every 30m - set temp basal for 0u 60m in NS
            else if (pumpRecord.isSuspended() && suspend_results.first().isValidSUSPEND() &&
                    pumpRecord.getEventDate().getTime() - suspend_results.first().getEventDate().getTime() >= 30 * 60 * 1000) {
                pumpRecord.setValidSUSPEND(true);
                //pullPump = true;
            }
            // valid suspendoff - set temp stopped in NS
            else if (!pumpRecord.isSuspended() && suspend_results.first().isValidSUSPEND() &&
                    pumpRecord.getEventDate().getTime() - suspend_results.first().getEventDate().getTime() <= 60 * 60 * 1000) {
                pumpRecord.setValidSUSPENDOFF(true);
//                RequestPumpHistory = true;
                RealmResults<PumpStatusEvent> suspendended_results = activePump.getPumpHistory()
                        .where()
                        .greaterThan("eventDate", new Date(System.currentTimeMillis() - (2 * 60 * 60 * 1000)))
                        .findAllSorted("eventDate", Sort.DESCENDING);
                pumpRecord.setSuspendAfterDate(suspendended_results.first().getEventDate());
                pumpRecord.setSuspendBeforeDate(pumpRecord.getEventDate());
            }
        } else if (pumpRecord.isSuspended()) {
            pumpRecord.setValidSUSPEND(true);
//            RequestPumpHistory = true;
        }

        // absolute suspend start time approx to after-before range
        if (pumpRecord.isValidSUSPEND()) {
            RealmResults<PumpStatusEvent> suspendstarted_results = activePump.getPumpHistory()
                    .where()
                    .greaterThan("eventDate", new Date(System.currentTimeMillis() - (12 * 60 * 60 * 1000)))
                    .findAllSorted("eventDate", Sort.ASCENDING);
            index = suspendstarted_results.size();
            if (index > 0) {
                while (index > 0) {
                    index--;
                    if (!suspendstarted_results.get(index).isSuspended())
                        break;
                }
                pumpRecord.setSuspendAfterDate(suspendstarted_results.get(index).getEventDate());
            } else {
                pumpRecord.setSuspendAfterDate(pumpRecord.getEventDate());
            }
            if (++index < suspendstarted_results.size())
                pumpRecord.setSuspendBeforeDate(suspendstarted_results.get(index).getEventDate());
            else
                pumpRecord.setSuspendBeforeDate(pumpRecord.getEventDate());
        }

        // validate that this contains a new SAGE record
        if (pumpRecord.isCgmWarmUp()) {
            RealmResults<PumpStatusEvent> sage_results = activePump.getPumpHistory()
                    .where()
                    .greaterThan("eventDate", new Date(System.currentTimeMillis() - (130 * 60 * 1000)))
                    .equalTo("validSAGE", true)
                    .findAll();
            if (sage_results.size() == 0) {
                pumpRecord.setValidSAGE(true);
//                RequestPumpHistory = true;
                RealmResults<PumpStatusEvent> sagedate_results = activePump.getPumpHistory()
                        .where()
                        .greaterThan("eventDate", new Date(System.currentTimeMillis() - (6 * 60 * 60 * 1000)))
                        .findAllSorted("eventDate", Sort.DESCENDING);
                pumpRecord.setSageAfterDate(sagedate_results.first().getEventDate());
                pumpRecord.setSageBeforeDate(pumpRecord.getEventDate());
            }
        } else if (pumpRecord.isCgmActive() && pumpRecord.getTransmitterBattery() == 100) {
            // note: transmitter battery can fluctuate when on the edge of a state change
            RealmResults<PumpStatusEvent> sagebattery_results = activePump.getPumpHistory()
                    .where()
                    .greaterThan("eventDate", new Date(System.currentTimeMillis() - (6 * 60 * 60 * 1000)))
                    .equalTo("cgmActive", true)
                    .lessThan("transmitterBattery", 50)
                    .findAllSorted("eventDate", Sort.DESCENDING);
            if (sagebattery_results.size() > 0) {
                RealmResults<PumpStatusEvent> sage_valid_results = activePump.getPumpHistory()
                        .where()
                        .greaterThanOrEqualTo("eventDate", sagebattery_results.first().getEventDate())
                        .equalTo("validSAGE", true)
                        .findAll();
                if (sage_valid_results.size() == 0) {
                    pumpRecord.setValidSAGE(true);
//                    RequestPumpHistory = true;
                    pumpRecord.setSageAfterDate(sagebattery_results.first().getEventDate());
                    pumpRecord.setSageBeforeDate(pumpRecord.getEventDate());
                }
            }
        }

        // validate that this contains a new CAGE record
        RealmResults<PumpStatusEvent> cage_results = activePump.getPumpHistory()
                .where()
                .greaterThan("eventDate", new Date(System.currentTimeMillis() - (6 * 60 * 60 * 1000)))
                .lessThan("reservoirAmount", pumpRecord.getReservoirAmount())
                .findAllSorted("eventDate", Sort.DESCENDING);
        if (cage_results.size() > 0) {
            RealmResults<PumpStatusEvent> cage_valid_results = activePump.getPumpHistory()
                    .where()
                    .greaterThanOrEqualTo("eventDate", cage_results.first().getEventDate())
                    .equalTo("validCAGE", true)
                    .findAll();
            if (cage_valid_results.size() == 0) {
                pumpRecord.setValidCAGE(true);
//                RequestPumpHistory = true;
                pumpRecord.setCageAfterDate(cage_results.first().getEventDate());
                pumpRecord.setCageBeforeDate(pumpRecord.getEventDate());
            }
        }

        // validate that this contains a new BATTERY record
        RealmResults<PumpStatusEvent> battery_results = activePump.getPumpHistory()
                .where()
                .greaterThan("eventDate", new Date(System.currentTimeMillis() - (6 * 60 * 60 * 1000)))
                .lessThan("batteryPercentage", pumpRecord.getBatteryPercentage())
                .findAllSorted("eventDate", Sort.DESCENDING);
        if (battery_results.size() > 0) {
            RealmResults<PumpStatusEvent> battery_valid_results = activePump.getPumpHistory()
                    .where()
                    .greaterThanOrEqualTo("eventDate", battery_results.first().getEventDate())
                    .equalTo("validBATTERY", true)
                    .findAll();
            if (battery_valid_results.size() == 0) {
                pumpRecord.setValidBATTERY(true);
//                RequestPumpHistory = true;
                pumpRecord.setBatteryAfterDate(battery_results.first().getEventDate());
                pumpRecord.setBatteryBeforeDate(pumpRecord.getEventDate());
            }
        }

        // validate that this contains a new ALERT record
        if (pumpRecord.getAlert() > 0) {
            RealmResults<PumpStatusEvent> alert_results = activePump.getPumpHistory()
                    .where()
                    .greaterThan("eventDate", new Date(System.currentTimeMillis() - (6 * 60 * 60 * 1000)))
                    .findAllSorted("eventDate", Sort.DESCENDING);
            if (alert_results.size() > 0) {
                if (alert_results.first().getAlert() != pumpRecord.getAlert())
                    pumpRecord.setValidALERT(true);
            }
        }

    }

    // pollInterval: default = POLL_PERIOD_MS (time to pump cgm reading)
    //
    // Can be requested at a shorter or longer interval, used to request a retry before next expected cgm data or to extend poll times due to low pump battery.
    // Requests the next poll based on the actual time last cgm data was available on the pump and adding the interval
    // if this time is already stale then the next actual cgm time will be used
    // Any poll time request that falls within the pre-grace/grace period will be pushed to the next safe time slot

    private long requestPollTime(long lastPoll, long pollInterval) {
        boolean isWarmup = false;

        RealmResults<PumpStatusEvent> cgmresults = realm.where(PumpStatusEvent.class)
                .greaterThan("eventDate", new Date(System.currentTimeMillis() - (24 * 60 * 1000)))
                .equalTo("validCGM", true)
                .findAllSorted("cgmDate", Sort.DESCENDING);
        long timeLastCGM = 0;
        if (cgmresults.size() > 0) {
            timeLastCGM = cgmresults.first().getCgmDate().getTime();
            isWarmup = cgmresults.first().isCgmWarmUp();
        }

        long now = System.currentTimeMillis();
        long lastActualPollTime = lastPoll;
        if (timeLastCGM > 0)
            lastActualPollTime = timeLastCGM + POLL_GRACE_PERIOD_MS + (POLL_PERIOD_MS * ((now - timeLastCGM + POLL_GRACE_PERIOD_MS) / POLL_PERIOD_MS));
        long nextActualPollTime = lastActualPollTime + POLL_PERIOD_MS;
        long nextRequestedPollTime = lastActualPollTime + pollInterval;

        // check if requested poll is stale
        if (nextRequestedPollTime - now < 10 * 1000)
            nextRequestedPollTime = nextActualPollTime;

        // extended unavailable cgm may be due to clash with the current polling time
        // while we wait for a cgm event, polling is auto adjusted by offsetting the next poll based on miss count

        if (timeLastCGM == 0)
            nextRequestedPollTime += 15 * 1000; // push poll time forward to avoid potential clash when no previous poll time available to sync with
        else if (isWarmup)
            nextRequestedPollTime += 60 * 1000; // in warmup sensor-pump comms need larger grace period
        else if (PumpCgmNA >= POLL_ANTI_CLASH)
            nextRequestedPollTime += (((PumpCgmNA - POLL_ANTI_CLASH) % 3) + 2) * 30 * 1000; // adjust poll time in 30 second steps to avoid potential poll clash (adjustment: poll+30s / poll+60s / poll+90s)

        // check if requested poll time is too close to next actual poll time
        if (nextRequestedPollTime > nextActualPollTime - POLL_GRACE_PERIOD_MS - POLL_PRE_GRACE_PERIOD_MS
                && nextRequestedPollTime < nextActualPollTime) {
            nextRequestedPollTime = nextActualPollTime;
        }

        return nextRequestedPollTime;
    }

    private long checkPollTime() {
        long due = 0;

        RealmResults<PumpStatusEvent> cgmresults = realm.where(PumpStatusEvent.class)
                .greaterThan("eventDate", new Date(System.currentTimeMillis() - (24 * 60 * 1000)))
                .equalTo("validCGM", true)
                .findAllSorted("cgmDate", Sort.DESCENDING);

        if (cgmresults.size() > 0) {
            long now = System.currentTimeMillis();
            long timeLastCGM = cgmresults.first().getCgmDate().getTime();
            long timePollExpected = timeLastCGM + POLL_PERIOD_MS + POLL_GRACE_PERIOD_MS + (POLL_PERIOD_MS * ((now - 1000L - (timeLastCGM + POLL_GRACE_PERIOD_MS)) / POLL_PERIOD_MS));
            // avoid polling when too close to sensor-pump comms
            if (((timePollExpected - now) > 5000L) && ((timePollExpected - now) < (POLL_PRE_GRACE_PERIOD_MS + POLL_GRACE_PERIOD_MS)))
                due = timePollExpected;
        }

        return due;
    }

    /**
     * @return if device acquisition was successful
     */
    private boolean openUsbDevice() {
        if (!hasUsbHostFeature()) {
            sendStatus(MasterService.ICON_WARN + "It appears that this device doesn't support USB OTG.");
            Log.e(TAG, "Device does not support USB OTG");
            return false;
        }

        UsbDevice cnlStick = UsbHidDriver.getUsbDevice(mUsbManager, USB_VID, USB_PID);
        if (cnlStick == null) {
            sendStatus(MasterService.ICON_WARN + "USB connection error. Is the Contour Next Link plugged in?");
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
            sendStatus(MasterService.ICON_WARN + "Unable to open USB device");
            Log.e(TAG, "Unable to open serial device", e);
            return false;
        }

        return true;
    }

    private boolean hasUsbHostFeature() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST);
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