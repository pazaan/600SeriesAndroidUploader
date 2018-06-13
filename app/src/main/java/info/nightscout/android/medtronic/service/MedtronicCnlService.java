package info.nightscout.android.medtronic.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import info.nightscout.android.history.HistoryDebug;
import info.nightscout.android.R;
import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.UploaderApplication;
import info.nightscout.android.medtronic.MedtronicCnlReader;
import info.nightscout.android.history.PumpHistoryHandler;
import info.nightscout.android.history.PumpHistoryParser;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;
import info.nightscout.android.medtronic.message.ContourNextLinkCommandMessage;
import info.nightscout.android.medtronic.message.ContourNextLinkMessage;
import info.nightscout.android.medtronic.message.MessageUtils;
import info.nightscout.android.model.medtronicNg.ContourNextLinkInfo;
import info.nightscout.android.model.medtronicNg.PumpHistoryBG;
import info.nightscout.android.model.medtronicNg.PumpHistoryMisc;
import info.nightscout.android.model.medtronicNg.PumpHistorySystem;
import info.nightscout.android.model.medtronicNg.PumpInfo;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.model.store.DataStore;
import info.nightscout.android.utils.HexDump;
import info.nightscout.android.utils.FormatKit;
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
    private long pollInterval;

    private boolean shutdownProtect = false;

    // debug use: temporary for error investigation
    public static long cnlClearTimer = 0;
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
        long uptime = UploaderApplication.getUptime() / 60000L;
        if (uptime > 60) {
            Log.d(TAG, "process uptime exceeded, killing process now. Uptime: " + uptime + " minutes");
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }

    @Override
    public void onTaskRemoved(Intent intent) {
        Log.i(TAG, "onTaskRemoved called");

        // Protection for older Android versions (v4 and v5)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M && readPump != null) {
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
            Log.w(TAG, "comms in progress, pull the emergency brake!");

            long now = System.currentTimeMillis();
            while (shutdownProtect && (System.currentTimeMillis() - now) < 1000L) {
                Log.d(TAG, "shutdownProtect");
                readPump.interrupt();
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ignored) {
                }
            }

            if (mHidDevice != null) {
                try {
                    new ContourNextLinkCommandMessage(ContourNextLinkCommandMessage.ASCII.EOT)
                            .sendNoResponse(mHidDevice);
                    Thread.sleep(10);
                } catch (IOException | InterruptedException | EncryptionException | ChecksumException | UnexpectedMessageException | TimeoutException ignored) {
                }
            }

            if (mHidDevice != null) mHidDevice.close();
        }

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
            public void execute(@NonNull Realm realm) {
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

    private long timePollStarted;

    private class ReadPump extends Thread {
        public void run() {
            Log.d(TAG, "readPump called");

            PowerManager.WakeLock wl = getWakeLock(mContext, TAG, 60000);

            sendBroadcast(new Intent(MasterService.Constants.ACTION_CNL_COMMS_ACTIVE));

            //long timePollStarted = System.currentTimeMillis();
            timePollStarted = System.currentTimeMillis();
            long nextpoll = 0;

            // debug use:
            cnlClearTimer = 0;
            cnlClear = 0;
            cnl0x81 = 0;

            FormatKit.getInstance(mContext);

            try {
                // note: Realm use only in this thread!
                realm = Realm.getDefaultInstance();
                storeRealm = Realm.getInstance(UploaderApplication.getStoreConfiguration());
                dataStore = storeRealm.where(DataStore.class).findFirst();

                readDataStore();
                pumpHistoryHandler = new PumpHistoryHandler(mContext);

                //if (true) return;

                //pumpHistoryHandler.reupload(PumpHistoryBG.class, "NS");
                //pumpHistoryHandler.reupload(PumpHistoryMisc.class, "NS");

                //if (debugHistory(true)) return;

                pollInterval = dataStore.getPollInterval();

                if (!openUsbDevice()) {
                    Log.w(TAG, "Could not open usb device");
//                userLogMessage(ICON_WARN + "Could not open usb device");

                    pumpHistoryHandler.systemStatus(PumpHistorySystem.STATUS.CNL_USB_ERROR);

                    return;
                }

                long due = checkPollTime();
                if (due > 0) {
                    if (dataStore.isSysEnableClashProtect()) {
                        userLogMessage(String.format(Locale.getDefault(),
                                getString(R.string.please_wait) + ": " + getString(R.string.pump_is_expecting_sensor_communication),
                                (due - System.currentTimeMillis()) / 1000L));
                        nextpoll = due;
                        return;
                    } else {
                        userLogMessage(String.format(Locale.getDefault(),
                                getString(R.string.pump_is_expecting_sensor_communication),
                                (due - System.currentTimeMillis()) / 1000L));
                        userLogMessage(ICON_SETTING + getString(R.string.radio_clash_protection_is_disabled));
                    }
                }

                final MedtronicCnlReader cnlReader = new MedtronicCnlReader(mHidDevice);
                if (dataStore.isSysEnableWait500ms()) cnlReader.setCnlCommandMessageSleepMS(500);

                try {
                    Log.d(TAG, "Connecting to Contour Next Link [pid" + android.os.Process.myPid() + "]");
                    userLogMessage(getString(R.string.connecting_to_contour_next_link));

                    shutdownProtect = true;
                    cnlReader.requestDeviceInfo();

                    // Is the device already configured?
                    if (realm.where(ContourNextLinkInfo.class).equalTo("serialNumber", cnlReader.getStickSerial()).findFirst() == null) {
                        realm.executeTransaction(new Realm.Transaction() {
                            @Override
                            public void execute(@NonNull Realm realm) {
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

                        cnlReader.requestLinkKey();
                        realm.executeTransaction(new Realm.Transaction() {
                            @Override
                            public void execute(@NonNull Realm realm) {
                                info.setKey(MessageUtils.byteArrayToHexString(cnlReader.getPumpSession().getKey()));
                            }
                        });

                        final long pumpMAC = cnlReader.getPumpSession().getPumpMAC();
                        Log.i(TAG, "PumpInfo MAC: " + (pumpMAC & 0xFFFFFF));

                        // Is activePump already configured?
                        if (realm.where(PumpInfo.class).equalTo("pumpMac", pumpMAC).findFirst() == null) {
                            realm.executeTransaction(new Realm.Transaction() {
                                @Override
                                public void execute(@NonNull Realm realm) {
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
                            Log.i(TAG, "Could not communicate with the pump. Is it nearby?");
                            userLogMessage(ICON_WARN + getString(R.string.could_not_communicate_with_the_pump));
                            CommsConnectError++;
                            pollInterval = pollInterval / (dataStore.isDoublePollOnPumpAway() ? 2L : 1L); // reduce polling interval to half until pump is available

                            pumpHistoryHandler.systemStatus(PumpHistorySystem.STATUS.COMMS_PUMP_LOST);

                            /*
                        } else if (cnlReader.getPumpSession().getRadioRSSIpercentage() < 5) {
                            userLogMessage(String.format(Locale.getDefault(), "Connected on channel %d  RSSI: %d%%", (int) radioChannel, cnlReader.getPumpSession().getRadioRSSIpercentage()));
                            userLogMessage(ICON_WARN + "Warning: pump signal too weak. Is it nearby?");
                            Log.i(TAG, "Warning: pump signal too weak. Is it nearby?");
                            CommsConnectError++;
                            CommsSignalError++;
                            pollInterval = POLL_PERIOD_MS / (dataStore.isDoublePollOnPumpAway() ? 2L : 1L); // reduce polling interval to half until pump is available
*/
                        } else {
                            if (CommsConnectError > 0) CommsConnectError--;
                            if (cnlReader.getPumpSession().getRadioRSSIpercentage() < 20)
                                CommsSignalError++;
                            else if (CommsSignalError > 0) CommsSignalError--;

                            Log.d(TAG, String.format("Connected on channel %d  RSSI: %d%%", radioChannel, cnlReader.getPumpSession().getRadioRSSIpercentage()));
                            userLogMessage(String.format(Locale.getDefault(), getString(R.string.connected_on_channel_rssi),
                                    radioChannel, cnlReader.getPumpSession().getRadioRSSIpercentage()));

                            // read pump status
                            final PumpStatusEvent pumpRecord = new PumpStatusEvent();

                            final String deviceName = String.format("medtronic-600://%s", cnlReader.getStickSerial());

                            // TODO - this should not be necessary. We should reverse lookup the device name from PumpInfo
                            pumpRecord.setDeviceName(deviceName);

                            cnlReader.getPumpTime();
                            pumpClockDifference = cnlReader.getSessionClockDifference();

                            pumpRecord.setEventDate(cnlReader.getSessionDate());
                            pumpRecord.setEventRTC(cnlReader.getSessionRTC());
                            pumpRecord.setEventOFFSET(cnlReader.getSessionOFFSET());
                            pumpRecord.setClockDifference(pumpClockDifference);
                            cnlReader.updatePumpStatus(pumpRecord);

                            validatePumpRecord(pumpRecord, activePump);

                            // write completed record to storage
                            realm.executeTransaction(new Realm.Transaction() {
                                @Override
                                public void execute(@NonNull Realm realm) {
                                    activePump.setLastRadioChannel(radioChannel);
                                    activePump.setDeviceName(deviceName);
                                    activePump.getPumpHistory().add(pumpRecord);
                                }
                            });

                            CommsSuccess++;
                            CommsError = 0;

                            checkPumpBattery(pumpRecord);
                            checkCGM(pumpRecord);

                            // debug use:
                            //debugStatusMessage();

                            // history

                            //pumpHistoryHandler.reupload(PumpHistoryAlarm.class, "NS");
                            //pumpHistoryHandler.reupload(PumpHistoryMisc.class, "NS");
                            //cnlReader.getHistoryLogcat(timePollStarted - 8 * 24 * 60 * 60000L, timePollStarted, 2);

                            pumpHistoryHandler.systemStatus(PumpHistorySystem.STATUS.COMMS_PUMP_CONNECTED);

                            pumpHistoryHandler.cgm(pumpRecord);

                            if (dataStore.isSysEnablePumpHistory() && isHistoryNeeded()) {
//                            if (dataStore.isSysEnablePumpHistory() && isHistoryNeeded() || true) {
                                storeRealm.executeTransaction(new Realm.Transaction() {
                                    @Override
                                    public void execute(@NonNull Realm realm) {
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
                                        public void execute(@NonNull Realm realm) {
                                            dataStore.setRequestProfile(false);
                                        }
                                    });
                                }

                                pumpHistoryHandler.checkGramsPerExchangeChanged();

                                pumpHistoryHandler.update(cnlReader);
                            }
                        }

                    } catch (UnexpectedMessageException e) {
                        //CommsError++;
                        pollInterval = dataStore.isSysEnablePollOverride() ? dataStore.getSysPollErrorRetry() : POLL_ERROR_RETRY_MS;
                        Log.e(TAG, "Unexpected Message", e);

                        // Check if NAK error = DEVICE_HAS_ERROR(0x07)
                        // This error state will block all further comms until cleared on the pump
                        // Seen when pump has an alarm for "Insulin Flow Blocked" faultCode=7

                        ContourNextLinkMessage.NAK nak = ContourNextLinkMessage.NAK.NA;
                        if (e.getMessage().contains("NAK")) {
                            int nakcode = Integer.parseInt(e.getMessage().split("NAK")[1].split("[()]")[1].split(":")[1], 16);
                            nak = ContourNextLinkMessage.NAK.convert(nakcode);
                            Log.e(TAG, "Pump sent NAK code: " + nakcode + " name: " + nak.name());
                        }

                        if (nak == ContourNextLinkMessage.NAK.DEVICE_HAS_ERROR) {
                            pumpHistoryHandler.systemStatus(PumpHistorySystem.STATUS.PUMP_DEVICE_ERROR);
                            userLogMessage(ICON_WARN + "Pump has a device error. No data can be read until this error has been cleared on the Pump.");
                        } else if (dataStore.isDbgEnableExtendedErrors()) {
                            userLogMessage(ICON_WARN + getString(R.string.error_communication) + " " + e.getMessage());
                        } else {
                            userLogMessage(ICON_WARN + getString(R.string.error_communication_busy_noisy));
                        }
                    } catch (TimeoutException e) {
                        //CommsError++;
                        pollInterval = dataStore.isSysEnablePollOverride() ? dataStore.getSysPollErrorRetry() : POLL_ERROR_RETRY_MS;
                        Log.e(TAG, "Timeout communicating with the Contour Next Link.", e);
                        if (dataStore.isDbgEnableExtendedErrors())
                            userLogMessage(ICON_WARN + getString(R.string.error_timeout) + " " + e.getMessage());
                        else
                            userLogMessage(ICON_WARN + getString(R.string.error_timeout_pump));
                    } catch (ChecksumException e) {
                        CommsError++;
                        Log.e(TAG, "Checksum error getting message from the Contour Next Link.", e);
                        if (dataStore.isDbgEnableExtendedErrors())
                            userLogMessage(ICON_WARN + getString(R.string.error_checksum) + " " + e.getMessage());
                        else
                            userLogMessage(ICON_WARN + getString(R.string.error_checksum_cnl));
                    } catch (EncryptionException e) {
                        CommsError++;
                        Log.e(TAG, "Error decrypting messages from Contour Next Link.", e);
                        if (dataStore.isDbgEnableExtendedErrors())
                            userLogMessage(ICON_WARN + getString(R.string.error_decryption) + " " + e.getMessage());
                        else
                            userLogMessage(ICON_WARN + getString(R.string.error_decryption_cnl));
                    } catch (NoSuchAlgorithmException e) {
                        CommsError++;
                        Log.e(TAG, "Could not determine CNL HMAC", e);
                        userLogMessage(ICON_WARN + getString(R.string.error_hashing));
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
                        userLogMessage(ICON_WARN + getString(R.string.error_connecting_cnl) + " " + e.getMessage());
                    else
                        userLogMessage(ICON_WARN + getString(R.string.error_connecting_cnl));
                    if (cnlReader.resetCNL()) userLogMessage(ICON_INFO + getString(R.string.error_cnl_reset_success));
                } catch (ChecksumException e) {
                    CommsError++;
                    Log.e(TAG, "Checksum error getting message from the Contour Next Link.", e);
                    if (dataStore.isDbgEnableExtendedErrors())
                        userLogMessage(ICON_WARN + getString(R.string.error_checksum) + " " + e.getMessage());
                    else
                        userLogMessage(ICON_WARN + getString(R.string.error_checksum_cnl));
                } catch (EncryptionException e) {
                    CommsError++;
                    Log.e(TAG, "Error decrypting messages from Contour Next Link.", e);
                    if (dataStore.isDbgEnableExtendedErrors())
                        userLogMessage(ICON_WARN + getString(R.string.error_decryption) + " " + e.getMessage());
                    else
                        userLogMessage(ICON_WARN + getString(R.string.error_decryption_cnl));
                } catch (TimeoutException e) {
                    CommsError++;
                    Log.e(TAG, "Timeout communicating with the Contour Next Link.", e);
                    if (dataStore.isDbgEnableExtendedErrors())
                        userLogMessage(ICON_WARN + getString(R.string.error_timeout) + " " + e.getMessage());
                    else
                        userLogMessage(ICON_WARN + getString(R.string.error_timeout_cnl));
                    if (cnlReader.resetCNL()) userLogMessage(ICON_INFO + getString(R.string.error_cnl_reset_success));
                } catch (UnexpectedMessageException e) {
                    CommsError++;
                    Log.e(TAG, "Could not close connection.", e);
                    if (dataStore.isDbgEnableExtendedErrors())
                        userLogMessage(ICON_WARN + getString(R.string.error_close_connection) + " " + e.getMessage());
                    else
                        userLogMessage(ICON_WARN + getString(R.string.error_close_connection));
                    if (cnlReader.resetCNL()) userLogMessage(ICON_INFO + getString(R.string.error_cnl_reset_success));
                } finally {
                    shutdownProtect = false;

                    // debug use:
                    //if (cnlClear > 0 || cnl0x81 > 0) userLogMessage("*** cnlClear=" + cnlClear + " [" + cnlClearTimer + "ms] cnl0x81=" + cnl0x81);

                    nextpoll = requestPollTime(timePollStarted, pollInterval);
                    if (dataStore.isDbgEnableExtendedErrors())
                        userLogMessage(String.format(Locale.getDefault(), getString(R.string.next_poll_due_at) + " [%dms]",
                                dateFormatter.format(nextpoll), System.currentTimeMillis() - timePollStarted));
                    else
                        userLogMessage(String.format(Locale.getDefault(), getString(R.string.next_poll_due_at), dateFormatter.format(nextpoll)));

                    RemoveOutdatedRecords();

                    statusWarnings();
                }

            } catch (Exception e) {
                Log.e(TAG, "Unexpected Error! " + Log.getStackTraceString(e));
                try {
                    if (dataStore.isDbgEnableExtendedErrors())
                        userLogMessage(ICON_WARN + getString(R.string.unexpected_error) + "\n" + Log.getStackTraceString(e));
                } catch (Exception ignored) {}
                int retry = 60;
                userLogMessage(String.format(Locale.getDefault(), getString(R.string.polling_service_could_not_complete), retry));
                nextpoll = System.currentTimeMillis() + (retry * 1000L);

            } finally {

                checkConnection();

                if (dataStore != null) writeDataStore();

                if (pumpHistoryHandler != null) pumpHistoryHandler.close();

                if (storeRealm != null && !storeRealm.isClosed()) storeRealm.close();
                if (realm != null && !realm.isClosed()) realm.close();

                if (mHidDevice != null) {
                    Log.i(TAG, "Closing serial device...");
                    mHidDevice.close();
                    mHidDevice = null;
                }

                sendBroadcast(new Intent(MasterService.Constants.ACTION_CNL_COMMS_FINISHED).putExtra("nextpoll", nextpoll));

                // allow some time for broadcast to be received before releasing wakelock
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                }
                releaseWakeLock(wl);

                stopSelf();
            }

            readPump = null;
        } // thread end
    }

    private void checkConnection() {

        RealmResults<PumpStatusEvent> results = realm
                .where(PumpStatusEvent.class)
                .sort("eventDate", Sort.DESCENDING)
                .findAll();
        if (results.size() >= 2) {

            long last1 = results.first().getEventDate().getTime();
            long last2 = results.get(1).getEventDate().getTime();

            long disconnected = timePollStarted - last1;
            long timespan = last1 - last2;

            if (disconnected > 0) {
                // disconnected

            } else {
                // connected

            }

        }

    }

    private void checkPumpBattery(PumpStatusEvent pumpRecord) {
        if (pumpRecord.getBatteryPercentage() <= 25) {
            PumpBatteryError++;
            pollInterval = dataStore.getLowBatPollInterval();
        } else {
            PumpBatteryError = 0;
        }
    }

    private void checkCGM(PumpStatusEvent pumpRecord) {
        if (pumpRecord.isCgmActive()) {
            PumpCgmNA = 0; // poll clash detection
            PumpLostSensorError = 0;

            Log.d(TAG, "&&& CgmWarmUp=" + pumpRecord.isCgmWarmUp() +
                    " CalibrationDueMinutes=" + pumpRecord.getCalibrationDueMinutes() +
                    " CgmCalibrating=" + pumpRecord.isCgmCalibrating() +
                    " ExceptionType=" + pumpRecord.getCgmExceptionType() +
                    " CgmStatus=" + pumpRecord.getCgmStatus()
            );

            if (pumpRecord.isCgmWarmUp())
                userLogMessage(ICON_CGM + "sensor is in warm-up phase");
            else if (pumpRecord.getCalibrationDueMinutes() == 0)
                userLogMessage(ICON_CGM + "sensor calibration is due now!");
            else if (pumpRecord.getSgv() == 0 && pumpRecord.isCgmCalibrating())
                userLogMessage(ICON_CGM + "sensor is calibrating");
            else if (pumpRecord.getSgv() == 0)

                switch (PumpHistoryParser.CGM_EXCEPTION.convert(pumpRecord.getCgmExceptionType())) {
                    case SENSOR_INIT:
                        userLogMessage(ICON_CGM + "sensor error (init)");
                        break;
                    case SENSOR_CAL_NEEDED:
                        userLogMessage(ICON_CGM + "sensor error (cal needed)");
                        break;
                    case SENSOR_ERROR:
                        userLogMessage(ICON_CGM + "sensor error (sgv not available)");
                        break;
                    case SENSOR_CHANGE_SENSOR_ERROR:
                        userLogMessage(ICON_CGM + "sensor error (change sensor)");
                        break;
                    case SENSOR_END_OF_LIFE:
                        userLogMessage(ICON_CGM + "sensor error (end of life)");
                        break;
                    case SENSOR_NOT_READY:
                        userLogMessage(ICON_CGM + "sensor error (not ready)");
                        break;
                    case SENSOR_READING_HIGH:
                        userLogMessage(ICON_CGM + "sensor error (reading high)");
                        break;
                    case SENSOR_READING_LOW:
                        userLogMessage(ICON_CGM + "sensor error (reading low)");
                        break;
                    case SENSOR_CAL_PENDING:
                        userLogMessage(ICON_CGM + "sensor error (cal pending)");
                        break;
                    case SENSOR_CAL_ERROR:
                        userLogMessage(ICON_CGM + "sensor error (cal error)");
                        break;
                    case SENSOR_TIME_UNKNOWN:
                        userLogMessage(ICON_CGM + "sensor error (time unknown)");
                        break;
                    default:
                        userLogMessage(ICON_CGM + "sensor error (n/a)");
                }

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
        if (dataStore.isNightscoutUpload() && dataStore.isNsEnableProfileUpload() && CommsSuccess > 0) {
            if (!pumpHistoryHandler.isProfileUploaded()) {
                userLogMessage(ICON_INFO + getString(R.string.info_no_profile_uploaded));
                userLogMessage(ICON_HELP + getString(R.string.help_profile_upload_main_menu));
            } else if (dataStore.isNameBasalPatternChanged() &&
                    (dataStore.isNsEnableProfileSingle() || dataStore.isNsEnableProfileOffset())) {
                userLogMessage(ICON_INFO + getString(R.string.info_basal_pattern_names));
                userLogMessage(ICON_HELP + getString(R.string.help_profile_upload_main_menu));
            }
        }

        if (PumpBatteryError >= ERROR_PUMPBATTERY_AT) {
            PumpBatteryError = 0;
            if (dataStore.getLowBatPollInterval() > POLL_PERIOD_MS) {
                userLogMessage(ICON_WARN + String.format(Locale.getDefault(), "%s %s",
                        getString(R.string.warn_pump_battery_low),
                        getString(R.string.info_pump_low_battery_mode_change)));
                userLogMessage(ICON_SETTING + String.format(Locale.getDefault(), "%s %d %s",
                        getString(R.string.info_low_battery_interval),
                        dataStore.getLowBatPollInterval() / 60000,
                        getString(R.string.time_minutes)));
            } else {
                userLogMessage(ICON_WARN + getString(R.string.warn_pump_battery_low));
            }
        }

        if (Math.abs(pumpClockDifference) > ERROR_PUMPCLOCK_MS)
            PumpClockError++;
        if (PumpClockError >= ERROR_PUMPCLOCK_AT) {
            PumpClockError = 0;
            userLogMessage(ICON_WARN + String.format(Locale.getDefault(), getString(R.string.warn_pump_time_difference),
                    (int) (Math.abs(pumpClockDifference) / 60000L),
                    getString(R.string.time_minutes),
                    pumpClockDifference > 0 ? getString(R.string.time_ahead) : getString(R.string.time_behind)));
            userLogMessage(ICON_HELP + getString(R.string.help_pump_time_difference));
        }

        if (CommsError >= ERROR_COMMS_AT) {
            userLogMessage(ICON_WARN + getString(R.string.warn_multiple_comms_errors));
            userLogMessage(ICON_HELP + getString(R.string.help_multiple_comms_errors));
        }

        if (PumpLostSensorError >= ERROR_PUMPLOSTSENSOR_AT) {
            PumpLostSensorError = 0;
            userLogMessage(ICON_WARN + getString(R.string.warn_missing_transmissions));
            userLogMessage(ICON_HELP + getString(R.string.help_missing_transmissions));
        }

        if (CommsConnectError >= ERROR_CONNECT_AT * (dataStore.isDoublePollOnPumpAway() ? 2 : 1)) {
            CommsConnectError = 0;
            userLogMessage(ICON_WARN + getString(R.string.warn_connection_fail));
            userLogMessage(ICON_HELP + getString(R.string.help_connection_fail));
        }

        if (CommsSignalError >= ERROR_SIGNAL_AT) {
            CommsSignalError = 0;
            userLogMessage(ICON_WARN + getString(R.string.warn_rssi_signal));
            userLogMessage(ICON_HELP + getString(R.string.help_rssi_signal));
        }
    }

    private void RemoveOutdatedRecords() {
        realm.executeTransactionAsync(new Realm.Transaction() {
            @Override
            public void execute(@NonNull Realm realm) {

                RealmResults<PumpStatusEvent> results =
                        realm.where(PumpStatusEvent.class)
                                .lessThan("eventDate", new Date(System.currentTimeMillis() - (48 * 60 * 60000L)))
                                .findAll();

                if (results.size() > 0) {
                    Log.d(TAG, "Deleting " + results.size() + " records from realm");
                    results.deleteAllFromRealm();
                }

            }
        });
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
        String logTAG = "*H* ";
        String userlogTAG = ICON_REFRESH + getString(R.string.history_text) + ": ";

        int recency = Math.round((float) pumpHistoryHandler.pumpHistoryRecency() / 60000L);
        if (recency == -1 || recency > 24 * 60) {
            Log.d(TAG, logTAG + "no recent data");
            userLogMessage(userlogTAG + getString(R.string.history_no_recent_data));
            historyNeeded = true;
        } else if (recency >= 6 * 60) {
            Log.d(TAG, logTAG + "recency " + (recency < 120 ? recency + " minutes" : ">" + recency / 60 + " hours"));
            userLogMessage(userlogTAG + getString(R.string.history_recency) + " " + (recency < 120 ? recency + " " + getString(R.string.time_minutes) : ">" + recency / 60 + " " + getString(R.string.time_hours)));
            historyNeeded = true;
        } else if (dataStore.getSysPumpHistoryFrequency() > 0 && (recency >= dataStore.getSysPumpHistoryFrequency() && pumpHistoryHandler.isLoopActivePotential())) {
            Log.d(TAG, logTAG + "auto mode");
            userLogMessage(userlogTAG + getString(R.string.history_auto_mode));
            historyNeeded = true;
        }

        RealmResults<PumpStatusEvent> results = realm
                .where(PumpStatusEvent.class)
                .sort("eventDate", Sort.DESCENDING)
                .findAll();

        RealmResults<PumpStatusEvent> cgmresults  = results.where()
                .equalTo("cgmActive", true)
                .sort("eventDate", Sort.DESCENDING)
                .findAll();

        if (results.size() > 1) {

            // time between status data points
            long ageMS = results.first().getEventDate().getTime() - results.get(1).getEventDate().getTime();
            int ageMinutes = Math.round((float) ageMS / 60000L);

            byte statusNow = results.first().getPumpStatus();
            byte statusPre = results.get(1).getPumpStatus();

            Log.d(TAG, String.format(logTAG + "recency=%s age=%s [%sms] statusNow=%s statusPre=%s",
                    recency, ageMinutes, ageMS,
                    String.format("%8s", Integer.toBinaryString(statusNow)).replace(' ', '0'),
                    String.format("%8s", Integer.toBinaryString(statusPre)).replace(' ', '0')));

            byte bolusRefNow = results.first().getLastBolusReference();
            byte bolusRefPre = results.get(1).getLastBolusReference();
            int bolusMinsNow = results.first().getBolusingMinutesRemaining();
            int bolusMinsPre = results.get(1).getBolusingMinutesRemaining();
            int bolusMinsDiff = bolusMinsPre - bolusMinsNow;

            Log.d(TAG, String.format(logTAG + "refNow=%s refPre=%s bolusMinsNow=%s bolusMinsPre=%s bolusMinsDiff=%s",
                    bolusRefNow, bolusRefPre, bolusMinsNow, bolusMinsPre, bolusMinsDiff));

            byte basalPatNow = results.first().getActiveBasalPattern();
            byte basalPatPre = results.get(1).getActiveBasalPattern();
            int tempMinsNow = results.first().getTempBasalMinutesRemaining();
            int tempMinsPre = results.get(1).getTempBasalMinutesRemaining();
            int tempMinsDiff = tempMinsPre - tempMinsNow;

            Log.d(TAG, String.format(logTAG + "basalPatNow=%s basalPatPre=%s tempMinsNow=%s tempMinsPre=%s tempMinsDiff=%s",
                    basalPatNow, basalPatPre, tempMinsNow, tempMinsPre, tempMinsDiff));

            // note: floats used for div 10000 int conversion, round to 4 places for actual value (%.4f)
            float reservoirChange = results.first().getReservoirAmount() - results.get(1).getReservoirAmount();
            float basalChange = results.first().getBasalUnitsDeliveredToday() - results.get(1).getBasalUnitsDeliveredToday();
            float sumChange = reservoirChange + basalChange;

            Log.d(TAG, String.format(logTAG + "reservoirChange=%.4f basalChange=%.4f sumChange=%.4f",
                    reservoirChange, basalChange, sumChange));

            Log.d(TAG, String.format(logTAG + "silence status=%d minutes=%d high=%s highlow=%s all=%s",
                    results.first().getAlertSilenceStatus(),
                    results.first().getAlertSilenceMinutesRemaining(),
                    results.first().isAlertSilenceHigh(),
                    results.first().isAlertSilenceHighLow(),
                    results.first().isAlertSilenceAll()));

            Log.d(TAG, String.format(logTAG + "PLGM status=%d high=%s low=%s beforehigh=%s beforelow=%s suspend=%s suspendlow=%s",
                    results.first().getPlgmStatus(),
                    results.first().isPlgmAlertOnHigh(),
                    results.first().isPlgmAlertOnLow(),
                    results.first().isPlgmAlertBeforeHigh(),
                    results.first().isPlgmAlertBeforeLow(),
                    results.first().isPlgmAlertSuspend(),
                    results.first().isPlgmAlertSuspendLow()));

            Log.d(TAG, String.format(logTAG + "alert code=%d date=%s rtc=%s offset=%s",
                    results.first().getAlert(),
                    results.first().getAlertDate(),
                    HexDump.toHexString(results.first().getAlertRTC()),
                    HexDump.toHexString(results.first().getAlertOFFSET())));

            if (ageMinutes > 15) {
                Log.d(TAG, logTAG + "stale status " + (ageMinutes < 120 ? ageMinutes + " minutes" : ">" + ageMinutes / 60 + " hours"));
                userLogMessage(userlogTAG + getString(R.string.history_stale_status) + " " + (ageMinutes < 120 ? ageMinutes + " " + getString(R.string.time_minutes) : ">" + ageMinutes / 60 + " " + getString(R.string.time_hours)));
                historyNeeded = true;

            } else {

                // basal pattern changed?
                if (basalPatNow != 0 && basalPatPre != 0 && basalPatNow != basalPatPre) {
                    Log.d(TAG, "*H* basal pattern changed");
                    userLogMessage(userlogTAG + getString(R.string.history_basal_pattern_changed));
                    historyNeeded = true;
                }

                // suspend/resume?
                if (results.first().isSuspended() && !results.get(1).isSuspended()) {
                    Log.d(TAG, logTAG + "basal suspend");
                    userLogMessage(userlogTAG + getString(R.string.history_basal_suspend));
                    historyNeeded = true;
                } else if (!results.first().isSuspended() && results.get(1).isSuspended()) {
                    Log.d(TAG, logTAG + "basal resume");
                    userLogMessage(userlogTAG + getString(R.string.history_basal_resume));
                    historyNeeded = true;
                }

                // new temp basal?
                if (results.first().isTempBasalActive() && !results.get(1).isTempBasalActive()) {
                    Log.d(TAG, logTAG + "temp basal");
                    userLogMessage(userlogTAG + getString(R.string.history_temp_basal));
                    historyNeeded = true;
                }

                // was temp ended before expected duration?
                if (!results.first().isTempBasalActive() && results.get(1).isTempBasalActive()
                        && Math.abs(tempMinsPre - ageMinutes) > 4) {
                    Log.d(TAG, logTAG + "temp ended");
                    userLogMessage(userlogTAG + getString(R.string.history_temp_ended));
                    historyNeeded = true;
                }

                // was a new temp started while one was in progress?
                if (results.first().isTempBasalActive() && results.get(1).isTempBasalActive()
                        && Math.abs(tempMinsDiff - ageMinutes) > 4) {
                    Log.d(TAG, logTAG + "temp extended");
                    userLogMessage(userlogTAG + getString(R.string.history_temp_extended));
                    historyNeeded = true;
                }

                // bolus part delivered? normal (ref change) / square (ref change) / dual (ref can change due to normal bolus mid delivery of square part)
                if (!results.first().isBolusingNormal() && bolusRefNow != bolusRefPre) {

                    // was dual ended before expected duration?
                    if (!results.first().isBolusingDual() && results.get(1).isBolusingDual()) {
                        if (Math.abs(bolusMinsPre - ageMinutes) > 4) {
                            Log.d(TAG, logTAG + "dual ended");
                            userLogMessage(userlogTAG + getString(R.string.history_dual_ended));
                            historyNeeded = true;
                        }
                    }
                    // was square ended before expected duration?
                    else if (!results.first().isBolusingSquare() && results.get(1).isBolusingSquare() && !results.get(1).isBolusingNormal()) {
                        if (Math.abs(bolusMinsPre - ageMinutes) > 4) {
                            Log.d(TAG, logTAG + "square ended");
                            userLogMessage(userlogTAG + getString(R.string.history_square_ended));
                            historyNeeded = true;
                        }
                    }
                    // dual bolus normal part delivered?
                    else if (!results.first().isBolusingSquare() && !results.get(1).isBolusingDual() && results.first().isBolusingDual()) {
                        Log.d(TAG, logTAG + "dual bolus");
                        userLogMessage(userlogTAG + getString(R.string.history_dual_bolus));
                        historyNeeded = true;
                    }
                    // normal bolus delivered
                    else {
                        Log.d(TAG, logTAG + "normal bolus");
                        userLogMessage(userlogTAG + getString(R.string.history_normal_bolus));
                        historyNeeded = true;
                    }
                }
                // bolus ended? or ended before expected duration?
                else if (!results.first().isBolusingDual() && results.get(1).isBolusingDual()
                        && Math.abs(bolusMinsPre - ageMinutes) > 4) {
                    Log.d(TAG, logTAG + "bolus ended");
                    userLogMessage(userlogTAG + getString(R.string.history_bolus_ended));
                    historyNeeded = true;
                }

                // square bolus started?
                if (!results.first().isBolusingNormal() && results.first().isBolusingSquare() && !results.get(1).isBolusingSquare()) {
                    Log.d(TAG, logTAG + "square bolus");
                    userLogMessage(userlogTAG + getString(R.string.history_square_bolus));
                    historyNeeded = true;
                }

                // recentBGL is in the status message for up to 20 minutes, check for this or if there was a new reading with a different bgl value
                if (results.first().getRecentBGL() != 0 &&
                        results.where()
                                .greaterThan("eventDate", new Date(System.currentTimeMillis() - (20 * 60000L)))
                                .equalTo("recentBGL", results.first().getRecentBGL())
                                .findAll()
                                .size() == 1) {
                    Log.d(TAG, logTAG + "recent finger bg");
                    userLogMessage(userlogTAG + getString(R.string.history_recent_finger_bg));
                    historyNeeded = true;
                }
/*
                // calibration factor info available?
                if (dataStore.isNsEnableCalibrationInfo() && dataStore.isNsEnableCalibrationInfoNow()
                        && (results.first().isCgmCalibrationComplete() && !results.get(1).isCgmCalibrationComplete())) {
                    Log.d(TAG, logTAG + "calibration info");
                    userLogMessage(userlogTAG + getString(R.string.history_calibration_info));
                    historyNeeded = true;
                }
*/
                // calibration factor info available?
                if (dataStore.isNsEnableCalibrationInfo()
                        && (cgmresults.size() > 1 && results.first().getCalibrationDueMinutes() > 0
                        && results.first().getCalibrationDueMinutes() > cgmresults.get(1).getCalibrationDueMinutes())) {
                    Log.d(TAG, logTAG + "calibration info");
                    userLogMessage(userlogTAG + getString(R.string.history_calibration_info));
                    historyNeeded = true;
                }

                // reservoir/battery changes need to pull history after pump has resumed
                // to ensure that we don't miss the resume entry in the history
                if (results.first().getActiveBasalPattern() != 0) {

                    if (results.first().getReservoirAmount() > results.get(1).getReservoirAmount()) {
                        Log.d(TAG, logTAG + "reservoir changed");
                        userLogMessage(userlogTAG + getString(R.string.history_reservoir_changed));
                        historyNeeded = true;
                    }

                    if (results.first().getBatteryPercentage() > results.get(1).getBatteryPercentage()) {
                        Log.d(TAG, logTAG + "battery changed");
                        userLogMessage(userlogTAG + getString(R.string.history_battery_changed));
                        historyNeeded = true;
                    }

                    if (!historyNeeded && results.get(1).getActiveBasalPattern() == 0) {
                        Log.d(TAG, logTAG + "pattern resume");
                        userLogMessage(userlogTAG + getString(R.string.history_pattern_resume));
                        historyNeeded = true;
                    }

                    // cannula fill?
                    // if nothing else is happening and pump is just delivering basal check reservoir amounts to deduce a cannula fill event
                    if (ageMinutes < 15 && !historyNeeded
                            && !results.first().isBolusingNormal() && !results.get(1).isBolusingNormal()
                            && !results.first().isBolusingSquare() && !results.get(1).isBolusingSquare()
                            && !results.first().isBolusingDual() && !results.get(1).isBolusingDual()
                            && bolusRefNow == bolusRefPre
                            && basalChange >= 0 && sumChange < -0.1) {
                        Log.d(TAG, logTAG + "cannula fill");
                        userLogMessage(userlogTAG + getString(R.string.history_cannula_fill));
                        historyNeeded = true;
                    }
                }

                // sensor changed?
                if (results.first().isCgmWarmUp()
                        && (cgmresults.size() > 1 && !cgmresults.get(1).isCgmWarmUp())) {
                    Log.d(TAG, logTAG + "sensor changed");
                    userLogMessage(userlogTAG + getString(R.string.history_sensor_changed));
                    historyNeeded = true;
                }

                // daily totals?
                SimpleDateFormat sdfDay = new SimpleDateFormat("dd", Locale.getDefault());
                long dayNow = results.first().getEventDate().getTime() + pumpClockDifference;
                long dayPre = results.get(1).getEventDate().getTime() + pumpClockDifference;
                if (!sdfDay.format(dayNow).equals(sdfDay.format(dayPre))) {
                    Log.d(TAG, logTAG + "daily totals");
                    userLogMessage(userlogTAG + getString(R.string.history_daily_totals));
                    historyNeeded = true;
                }

                // active alerts?
                if (results.first().getAlert() != 0) {
                    if (results.first().getAlertRTC() != results.get(1).getAlertRTC()) {
                        Log.d(TAG, logTAG + "active alert");
                        userLogMessage(userlogTAG + getString(R.string.history_active_alert));
                        historyNeeded = true;
                    } else if (recency >= 15) {
                        // recheck at interval to catch stacked alerts as pump will only show the oldest uncleared active alert
                        Log.d(TAG, logTAG + "alert recheck");
                        userLogMessage(userlogTAG + getString(R.string.history_alert_recheck));
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
            Log.e(TAG, "Device does not support USB OTG");
            userLogMessage(ICON_WARN + getString(R.string.error_usb_no_support));
            return false;
        }

        if (mUsbManager == null) {
            Log.e(TAG, "USB connection error. mUsbManager == null");
            userLogMessage(ICON_WARN + getString(R.string.error_usb_no_connection));
            return false;
        }

        UsbDevice cnlStick = UsbHidDriver.getUsbDevice(mUsbManager, USB_VID, USB_PID);
        if (cnlStick == null) {
            Log.w(TAG, "USB connection error. Is the CNL plugged in?");
            userLogMessage(ICON_WARN + getString(R.string.error_usb_no_connection));
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
            Log.e(TAG, "Unable to open serial device", e);
            userLogMessage(ICON_WARN + getString(R.string.error_usb_no_open));
            return false;
        }

        return true;
    }

    private boolean hasUsbHostFeature() {

        //getUsbInfo();

        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST);
    }

    private void getUsbInfo() {

        UsbManager mManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = mManager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();

        while (deviceIterator.hasNext()) {
            UsbDevice device = deviceIterator.next();
            Log.i(TAG, "Model: " + device.getDeviceName());
            Log.i(TAG, "ID: " + device.getDeviceId());
            Log.i(TAG, "Class: " + device.getDeviceClass());
            Log.i(TAG, "Protocol: " + device.getDeviceProtocol());
            Log.i(TAG, "Vendor ID " + device.getVendorId());
            Log.i(TAG, "Product ID: " + device.getProductId());
            Log.i(TAG, "Interface count: " + device.getInterfaceCount());
            Log.i(TAG, "---------------------------------------");
            // Get interface details
            for (int index = 0; index < device.getInterfaceCount(); index++) {
                UsbInterface mUsbInterface = device.getInterface(index);
                Log.i(TAG, "  *****     *****");
                Log.i(TAG, "  Interface index: " + index);
                Log.i(TAG, "  Interface ID: " + mUsbInterface.getId());
                Log.i(TAG, "  Inteface class: " + mUsbInterface.getInterfaceClass());
                Log.i(TAG, "  Interface protocol: " + mUsbInterface.getInterfaceProtocol());
                Log.i(TAG, "  Endpoint count: " + mUsbInterface.getEndpointCount());
                // Get endpoint details
                for (int epi = 0; epi < mUsbInterface.getEndpointCount(); epi++) {
                    UsbEndpoint mEndpoint = mUsbInterface.getEndpoint(epi);
                    Log.i(TAG, "    ++++   ++++   ++++");
                    Log.i(TAG, "    Endpoint index: " + epi);
                    Log.i(TAG, "    Attributes: " + mEndpoint.getAttributes());
                    Log.i(TAG, "    Direction: " + mEndpoint.getDirection());
                    Log.i(TAG, "    Number: " + mEndpoint.getEndpointNumber());
                    Log.i(TAG, "    Interval: " + mEndpoint.getInterval());
                    Log.i(TAG, "    Packet size: " + mEndpoint.getMaxPacketSize());
                    Log.i(TAG, "    Type: " + mEndpoint.getType());
                }
            }
        }
        Log.i(TAG, " No more devices connected.");
    }

    private boolean debugHistory(boolean debug) {
        if (debug) {
            if (pumpHistoryHandler.records() == 0) {
                userLogMessage("Getting history from file");
                new HistoryDebug(mContext, pumpHistoryHandler).run();
                userLogMessage("Processing done");
            } else {
                userLogMessage("History file already processed");
            }
        }
        return debug;
    }

    private void debugStatusMessage() {
        long now = System.currentTimeMillis();

        Date lastDate = pumpHistoryHandler.debugNoteLastDate();
        if (lastDate != null && now - lastDate.getTime() < 30 * 60000L) return;

        RealmResults<PumpStatusEvent> results = realm
                .where(PumpStatusEvent.class)
                .greaterThan("eventDate", new Date(now - 33 * 60000L))
                .sort("eventDate", Sort.DESCENDING)
                .findAll();

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
