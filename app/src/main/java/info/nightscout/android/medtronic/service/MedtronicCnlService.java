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

import info.nightscout.android.PumpAlert;
import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.history.HistoryDebug;
import info.nightscout.android.R;
import info.nightscout.android.UploaderApplication;
import info.nightscout.android.medtronic.MedtronicCnlReader;
import info.nightscout.android.history.PumpHistoryHandler;
import info.nightscout.android.history.PumpHistoryParser;
import info.nightscout.android.medtronic.Stats;
import info.nightscout.android.medtronic.UserLogMessage;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.IntegrityException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;
import info.nightscout.android.medtronic.message.ContourNextLinkCommandMessage;
import info.nightscout.android.medtronic.message.ContourNextLinkMessage;
import info.nightscout.android.medtronic.message.MessageUtils;
import info.nightscout.android.model.medtronicNg.ContourNextLinkInfo;
import info.nightscout.android.model.medtronicNg.PumpHistorySystem;
import info.nightscout.android.model.medtronicNg.PumpInfo;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.model.store.DataStore;
import info.nightscout.android.model.store.StatPoll;
import info.nightscout.android.utils.FormatKit;
import info.nightscout.android.utils.HexDump;
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

    public final static String DEVICE_HEADER = "medtronic-600://";

    // Integrity check
    public final static long INTEGRITY_FAIL_MS = 900000L;

    // Poll intervals
    public final static long POLL_PERIOD_MS = 300000L;
    public final static long LOW_BATTERY_POLL_PERIOD_MS = 900000L;
    // Number of additional seconds to wait after the next expected CGM poll, so that we don't interfere with CGM radio comms.
    public final static long POLL_GRACE_PERIOD_MS = 30000L;
    // Number of seconds before the next expected CGM poll that we will allow uploader comms to start
    public final static long POLL_PRE_GRACE_PERIOD_MS = 15000L;
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

    // RSSI
    private final static int RSSI_SIGNAL_WEAK = 20;

    // show warning message after repeated errors
    private final static int ERROR_COMMS_AT = 3;
    private final static int ERROR_CONNECT_AT = 6;
    private final static int ERROR_SIGNAL_AT = 6;
    private final static int ERROR_PUMPLOSTSENSOR_AT = 6;
    private final static int ERROR_PUMPBATTERY_AT = 1;
    private final static int ERROR_PUMPCLOCK_AT = 3;
    private final static long ERROR_PUMPCLOCK_MS = 10 * 60 * 1000L;

    private Context mContext;
    private static UsbHidDriver mHidDevice;
    private UsbManager mUsbManager;
    private ReadPump readPump;
    private Realm realm;
    private Realm storeRealm;
    private DataStore dataStore;
    private PumpHistoryHandler pumpHistoryHandler;
    private StatPoll statPoll;

    private long pumpClockDifference;
    private long pollInterval;

    private boolean shutdownProtect = false;

    // DataStore local copy
    private int pumpCgmNA;
    private int commsSuccess;
    private int commsError;
    private int commsConnectError;
    private int commsSignalError;
    private int commsCgmSuccess;
    private int pumpLostSensorError;
    private int pumpClockError;
    private int pumpBatteryError;

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
            Log.w(TAG, "process uptime exceeded, killing process now. Uptime: " + uptime + " minutes");
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }

    @Override
    public void onTaskRemoved(Intent intent) {
        Log.w(TAG, "onTaskRemoved called");

        // Protection for older Android versions (v4 and v5)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M && readPump != null) {
            sendBroadcast(new Intent(MasterService.Constants.ACTION_CNL_COMMS_FINISHED).putExtra("nextpoll", System.currentTimeMillis() + 30000L));
            pullEmergencyBrake();
        }
    }

    @Override
    public void onLowMemory() {
        Log.w(TAG, "onLowMemory called");
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
            readPump.setPriority(Thread.NORM_PRIORITY);
            readPump.start();

            return START_STICKY;

        } else if (MasterService.Constants.ACTION_CNL_CHECKSTATE.equals(action) && readPump == null) {
            ready();

        } else if (MasterService.Constants.ACTION_CNL_SHUTDOWN.equals(action) && readPump != null) {
            // device is shutting down, pull the emergency brake!
            pullEmergencyBrake();
        }

        return START_NOT_STICKY;
    }

    private void ready() {
        sendBroadcast(new Intent(MasterService.Constants.ACTION_CNL_COMMS_READY));
        stopSelf();
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
        pumpCgmNA = dataStore.getPumpCgmNA();
        commsSuccess = dataStore.getCommsSuccess();
        commsError = dataStore.getCommsError();
        commsConnectError = dataStore.getCommsConnectError();
        commsSignalError = dataStore.getCommsSignalError();
        commsCgmSuccess = dataStore.getCommsCgmSuccess();
        pumpLostSensorError = dataStore.getPumpLostSensorError();
        pumpClockError = dataStore.getPumpClockError();
        pumpBatteryError = dataStore.getPumpBatteryError();
    }

    private void writeDataStore() {
        storeRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(@NonNull Realm realm) {
                dataStore.setPumpCgmNA(pumpCgmNA);
                dataStore.setCommsSuccess(commsSuccess);
                dataStore.setCommsError(commsError);
                dataStore.setCommsConnectError(commsConnectError);
                dataStore.setCommsSignalError(commsSignalError);
                dataStore.setCommsCgmSuccess(commsCgmSuccess);
                dataStore.setPumpLostSensorError(pumpLostSensorError);
                dataStore.setPumpClockError(pumpClockError);
                dataStore.setPumpBatteryError(pumpBatteryError);
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

            timePollStarted = System.currentTimeMillis();
            long nextpoll = 0;

            statPoll = (StatPoll) Stats.open().readRecord(StatPoll.class);

            try {
                // note: Realm use only in this thread!
                realm = Realm.getDefaultInstance();
                storeRealm = Realm.getInstance(UploaderApplication.getStoreConfiguration());
                dataStore = storeRealm.where(DataStore.class).findFirst();

                readDataStore();
                pumpHistoryHandler = new PumpHistoryHandler(mContext);

                statPoll.incPollCount();

                // *** debug use only ***
                //if (debugHistory(true)) return;

                pollInterval = dataStore.getPollInterval();

                if (!openUsbDevice()) {
                    Log.w(TAG, "Could not open usb device");
                    pumpHistoryHandler.systemEvent(PumpHistorySystem.STATUS.CNL_USB_ERROR)
                            .lastConnect()
                            .process();
                    return;
                }

                integrityCheck(timePollStarted);

                long due = checkPollTime();
                if (due > 0) {
                    if (dataStore.isSysEnableClashProtect()) {
                        UserLogMessage.send(mContext, String.format("{id;%s}: {id;%s} {qid;%s;%s}.",
                                R.string.ul_poll__please_wait,
                                R.string.ul_poll__pump_is_expecting_sensor_communication,
                                R.plurals.seconds,
                                (due - System.currentTimeMillis()) / 1000L));
                        nextpoll = due;
                        return;
                    } else {
                        UserLogMessage.send(mContext, String.format("{id;%s} {qid;%s;%s}.",
                                R.string.ul_poll__pump_is_expecting_sensor_communication,
                                R.plurals.seconds,
                                (due - System.currentTimeMillis()) / 1000L));
                        UserLogMessage.send(mContext, UserLogMessage.TYPE.OPTION, R.string.ul_poll__radio_clash_protection_is_disabled);
                    }
                }

                statPoll.incPollError(); // increment error now and decrement if successful

                final MedtronicCnlReader cnlReader = new MedtronicCnlReader(mHidDevice);
                if (dataStore.isSysEnableWait500ms()) cnlReader.setCnlCommandMessageSleepMS(500);

                try {
                    Log.d(TAG, "Connecting to Contour Next Link [pid" + android.os.Process.myPid() + "]");
                    UserLogMessage.send(mContext, R.string.ul_poll__connecting_to_contour_next_link);

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
                            UserLogMessage.send(mContext, UserLogMessage.TYPE.WARN, R.string.ul_poll__could_not_communicate_with_the_pump);
                            statPoll.incPollNoConnect();
                            commsConnectError++;
                            pollInterval = pollInterval / (dataStore.isDoublePollOnPumpAway() ? 2L : 1L); // reduce polling interval to half until pump is available
                            pumpHistoryHandler.systemEvent(PumpHistorySystem.STATUS.COMMS_PUMP_LOST)
                                    .lastConnect()
                                    .process();

                        } else if (cnlReader.getPumpSession().getRadioRSSIpercentage() < dataStore.getSysRssiAllowConnect()) {
                            Log.i(TAG, "Warning: pump signal too weak. Is it nearby?");
                            UserLogMessage.send(mContext, String.format("{id;%s} %s  {id;%s}: %s%%",
                                    R.string.ul_poll__connected_on_channel,
                                    radioChannel,
                                    R.string.ul_poll__rssi,
                                    cnlReader.getPumpSession().getRadioRSSIpercentage()));
                            UserLogMessage.send(mContext, UserLogMessage.TYPE.WARN, R.string.ul_poll__pump_signal_too_weak);
                            commsConnectError++;
                            commsSignalError++;
                            pollInterval = POLL_PERIOD_MS / (dataStore.isDoublePollOnPumpAway() ? 2L : 1L); // reduce polling interval to half until pump is available
                            pumpHistoryHandler.systemEvent(PumpHistorySystem.STATUS.COMMS_PUMP_LOST)
                                    .lastConnect()
                                    .process();

                        } else {
                            if (commsConnectError > 0) commsConnectError--;
                            if (cnlReader.getPumpSession().getRadioRSSIpercentage() < RSSI_SIGNAL_WEAK) {
                                commsSignalError++;
                                statPoll.incPollRSSIweak();
                            } else if (commsSignalError > 0) commsSignalError--;
                            statPoll.incPollConnect();
                            statPoll.setPollRSSI(statPoll.getPollRSSI() + cnlReader.getPumpSession().getRadioRSSIpercentage());

                            Log.d(TAG, String.format("Connected on channel %d  RSSI: %d%%", radioChannel, cnlReader.getPumpSession().getRadioRSSIpercentage()));
                            UserLogMessage.send(mContext, String.format("{id;%s} %s  {id;%s}: %s%%",
                                    R.string.ul_poll__connected_on_channel,
                                    radioChannel,
                                    R.string.ul_poll__rssi,
                                    cnlReader.getPumpSession().getRadioRSSIpercentage()));

                            // read pump status
                            final PumpStatusEvent pumpRecord = new PumpStatusEvent();

                            final String deviceName = DEVICE_HEADER + cnlReader.getStickSerial();

                            pumpRecord.setDeviceName(deviceName);

                            cnlReader.getPumpTime();
                            pumpClockDifference = cnlReader.getSessionClockDifference();

                            pumpRecord.setPumpMAC(pumpMAC);
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

                            commsSuccess++;
                            commsError = 0;
                            statPoll.incPollPumpStatus();

                            checkPumpBattery(pumpRecord);
                            checkCGM(pumpRecord);

                            // *** debug use only ***
                            //cnlReader.getHistoryLogcat(timePollStarted - 8 * 24 * 60 * 60000L, timePollStarted, 2);
                            //debugStatusMessage();
                            //debugActiveAlert();

                            pumpHistoryHandler.systemEvent(PumpHistorySystem.STATUS.COMMS_PUMP_CONNECTED)
                                    .dismiss(PumpHistorySystem.STATUS.CNL_UNPLUGGED)
                                    .lastConnect()
                                    .process();

                            pumpHistoryHandler.cgm(pumpRecord);

                            // history
                            if (dataStore.isSysEnablePumpHistory() && isHistoryNeeded()) {
                                storeRealm.executeTransaction(new Realm.Transaction() {
                                    @Override
                                    public void execute(@NonNull Realm realm) {
                                        dataStore.setRequestPumpHistory(true);
                                    }
                                });
                            }

                            // skip history processing for this poll when old SGV event received as we want to end comms asap
                            // due to the possibility of a late sensor-pump sgv send, the retry after 90 seconds will handle the history if needed
                            // skip if pump battery is low and interval time is higher then poll period, process history once per hour

                            if (!pumpRecord.isCgmOldWhenNewExpected() &&
                                    !(pumpBatteryError > 0
                                            && dataStore.getLowBatPollInterval() > POLL_PERIOD_MS
                                            && pumpHistoryHandler.pumpHistoryRecency() > dataStore.getLowBatPollInterval()
                                            && pumpHistoryHandler.pumpHistoryRecency() < 60 * 60000L)) {

                                if (pumpHistoryHandler.update(cnlReader))
                                    // poll sooner when the limiter is reached as more history data is required
                                    pollInterval = POLL_PERIOD_MS / 2;
                            }

                        }
                        statPoll.decPollError();

                    } catch (IntegrityException e) {
                        UserLogMessage.send(mContext, UserLogMessage.TYPE.WARN, R.string.ul_integrity__check_failed_time_mismatch);
                        pollInterval = System.currentTimeMillis() - timePollStarted + 10000L;
                        reset();

                    } catch (UnexpectedMessageException e) {
                        pollInterval = dataStore.isSysEnablePollOverride() ? dataStore.getSysPollErrorRetry() : POLL_ERROR_RETRY_MS;
                        Log.e(TAG, "Unexpected Message", e);

                        // Check if NAK error = DEVICE_HAS_ERROR(0x07)
                        // This error state will block all further comms until cleared on the pump
                        // Seen when pump has an alarm for "Insulin Flow Blocked" and others

                        ContourNextLinkMessage.NAK nak = ContourNextLinkMessage.NAK.NA;
                        if (e.getMessage().contains("NAK")) {
                            int nakcode = Integer.parseInt(e.getMessage().split("NAK")[1].split("[()]")[1].split(":")[1], 16);
                            nak = ContourNextLinkMessage.NAK.convert(nakcode);
                            Log.e(TAG, "Pump sent NAK code: " + nakcode + " name: " + nak.name());
                        }

                        if (nak == ContourNextLinkMessage.NAK.DEVICE_HAS_ERROR) {
                            // always check history after the pump user clears the blocking alert
                            storeRealm.executeTransaction(new Realm.Transaction() {
                                @Override
                                public void execute(@NonNull Realm realm) {
                                    dataStore.setRequestPumpHistory(true);
                                }
                            });
                            pumpHistoryHandler.systemEvent(PumpHistorySystem.STATUS.PUMP_DEVICE_ERROR)
                                    .lastConnect()
                                    .process();
                            UserLogMessage.send(mContext, UserLogMessage.TYPE.WARN, R.string.ul_error__pump_device_error);
                        } else {
                            UserLogMessage.sendN(mContext, UserLogMessage.TYPE.WARN, R.string.ul_error__communication_busy_noisy);
                            UserLogMessage.sendE(mContext, UserLogMessage.TYPE.WARN, String.format("{id;%s} %s", R.string.ul_error__communication, e.getMessage()));
                        }

                    } catch (TimeoutException e) {
                        commsError++;
                        pollInterval = dataStore.isSysEnablePollOverride() ? dataStore.getSysPollErrorRetry() : POLL_ERROR_RETRY_MS;
                        Log.e(TAG, "Timeout communicating with the Contour Next Link.", e);
                        UserLogMessage.sendN(mContext, UserLogMessage.TYPE.WARN, R.string.ul_error__timeout_pump);
                        UserLogMessage.sendE(mContext, UserLogMessage.TYPE.WARN, String.format("{id;%s} %s", R.string.ul_error__timeout, e.getMessage()));
                    } catch (ChecksumException e) {
                        commsError++;
                        Log.e(TAG, "Checksum error getting message from the Contour Next Link.", e);
                        UserLogMessage.sendN(mContext, UserLogMessage.TYPE.WARN, R.string.ul_error__checksum_cnl);
                        UserLogMessage.sendE(mContext, UserLogMessage.TYPE.WARN, String.format("{id;%s} %s", R.string.ul_error__checksum, e.getMessage()));
                    } catch (EncryptionException e) {
                        commsError++;
                        Log.e(TAG, "Error decrypting messages from Contour Next Link.", e);
                        UserLogMessage.sendN(mContext, UserLogMessage.TYPE.WARN, R.string.ul_error__decryption_cnl);
                        UserLogMessage.sendE(mContext, UserLogMessage.TYPE.WARN, String.format("{id;%s} %s", R.string.ul_error__decryption, e.getMessage()));
                    } catch (NoSuchAlgorithmException e) {
                        commsError++;
                        Log.e(TAG, "Could not determine CNL HMAC", e);
                        UserLogMessage.sendN(mContext, UserLogMessage.TYPE.WARN, R.string.ul_error__hashing);
                        UserLogMessage.sendE(mContext, UserLogMessage.TYPE.WARN, String.format("{id;%s} %s", R.string.ul_error__hashing, e.getMessage()));
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
                    commsError++;
                    Log.e(TAG, "Error connecting to Contour Next Link.", e);
                    UserLogMessage.sendN(mContext, UserLogMessage.TYPE.WARN, R.string.ul_error__connecting_cnl);
                    UserLogMessage.sendE(mContext, UserLogMessage.TYPE.WARN, String.format("{id;%s} %s", R.string.ul_error__connecting_cnl, e.getMessage()));
                    if (cnlReader.resetCNL()) UserLogMessage.send(mContext, UserLogMessage.TYPE.INFO, R.string.ul_error__cnl_reset_success);
                } catch (ChecksumException e) {
                    commsError++;
                    Log.e(TAG, "Checksum error getting message from the Contour Next Link.", e);
                    UserLogMessage.sendN(mContext, UserLogMessage.TYPE.WARN, R.string.ul_error__checksum_cnl);
                    UserLogMessage.sendE(mContext, UserLogMessage.TYPE.WARN, String.format("{id;%s} %s", R.string.ul_error__checksum, e.getMessage()));
                } catch (EncryptionException e) {
                    commsError++;
                    Log.e(TAG, "Error decrypting messages from Contour Next Link.", e);
                    UserLogMessage.sendN(mContext, UserLogMessage.TYPE.WARN, R.string.ul_error__decryption_cnl);
                    UserLogMessage.sendE(mContext, UserLogMessage.TYPE.WARN, String.format("{id;%s} %s", R.string.ul_error__decryption, e.getMessage()));
                } catch (TimeoutException e) {
                    commsError++;
                    Log.e(TAG, "Timeout communicating with the Contour Next Link.", e);
                    UserLogMessage.sendN(mContext, UserLogMessage.TYPE.WARN, R.string.ul_error__timeout_cnl);
                    UserLogMessage.sendE(mContext, UserLogMessage.TYPE.WARN, String.format("{id;%s} %s", R.string.ul_error__timeout, e.getMessage()));
                    if (cnlReader.resetCNL()) UserLogMessage.send(mContext, UserLogMessage.TYPE.INFO, R.string.ul_error__cnl_reset_success);
                } catch (UnexpectedMessageException e) {
                    commsError++;
                    Log.e(TAG, "Could not close connection.", e);
                    UserLogMessage.sendN(mContext, UserLogMessage.TYPE.WARN, R.string.ul_error__close_connection);
                    UserLogMessage.sendE(mContext, UserLogMessage.TYPE.WARN, String.format("{id;%s} %s", R.string.ul_error__close_connection, e.getMessage()));
                    if (cnlReader.resetCNL()) UserLogMessage.send(mContext, UserLogMessage.TYPE.INFO, R.string.ul_error__cnl_reset_success);
                } finally {
                    shutdownProtect = false;

                    nextpoll = requestPollTime(timePollStarted, pollInterval);
                    long timer = System.currentTimeMillis() - timePollStarted;

                    UserLogMessage.sendN(mContext, String.format("{id;%s} {time.poll;%s}", R.string.ul_poll__next_poll_due_at, nextpoll));
                    UserLogMessage.sendE(mContext, String.format("{id;%s} {time.poll.e;%s} [%sms]", R.string.ul_poll__next_poll_due_at, nextpoll, timer));

                    statPoll.timer(timer);

                    RemoveOutdatedRecords();
                    statusWarnings();
                }

            } catch (Exception e) {
                Log.e(TAG, "Unexpected Error! " + Log.getStackTraceString(e));
                UserLogMessage.sendN(mContext, UserLogMessage.TYPE.WARN, R.string.ul_poll__polling_service_could_not_complete);
                UserLogMessage.sendE(mContext, UserLogMessage.TYPE.WARN, String.format("{id;%s} %s", R.string.ul_poll__unexpected_error, e.getMessage()));
                nextpoll = System.currentTimeMillis() + 60000L;

            } finally {

                if (mHidDevice != null) {
                    Log.i(TAG, "Closing serial device...");
                    mHidDevice.close();
                    mHidDevice = null;
                }

                if (pumpHistoryHandler != null) pumpHistoryHandler.close();

                Stats.close();
                Stats.stale();

                if (dataStore != null) {
                    statsReport();
                    writeDataStore();
                }

                if (storeRealm != null && !storeRealm.isClosed()) storeRealm.close();
                if (realm != null && !realm.isClosed()) realm.close();

                sendBroadcast(new Intent(MasterService.Constants.ACTION_CNL_COMMS_FINISHED).putExtra("nextpoll", nextpoll));
                // allow some time for broadcast to be received before releasing wakelock
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                }
                releaseWakeLock(wl);

                stopSelf();
            }

            readPump = null;
        } // thread end
    }

    private void integrityCheck(long time) {
        // if device time has changed records may contain invalid dates
        // this can happen when time has been adjusted backwards pushing
        // record dates into the future

        final RealmResults<PumpStatusEvent> results = realm.where(PumpStatusEvent.class)
                .greaterThan("eventDate", new Date(time))
                .sort("eventDate", Sort.ASCENDING)
                .findAll();

        if (results.size() > 0) {
            UserLogMessage.send(mContext, UserLogMessage.TYPE.WARN, R.string.ul_integrity__check_failed_future_records);

            // how bad is it?
            // more then a defined ms shift does a full database reset

            if (results.last().getEventDate().getTime() - time < INTEGRITY_FAIL_MS) {
                // remove future pump status records
                realm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(@NonNull Realm realm) {
                        results.deleteAllFromRealm();
                    }
                });
                UserLogMessage.send(mContext, UserLogMessage.TYPE.INFO, R.string.ul_integrity__removed_future_records);

            } else {
                // full reset
                reset();
            }

        }
    }

    private void reset() {

        realm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(@NonNull Realm realm) {
                realm.where(PumpStatusEvent.class)
                        .findAll().deleteAllFromRealm();
            }
        });

        final boolean profile = pumpHistoryHandler.isProfileInHistory();

        pumpHistoryHandler.reset();

        storeRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(@NonNull Realm realm) {
                long now = System.currentTimeMillis();
                // stop Pushover resending messages
                dataStore.setCnlLimiterTimestamp(now);
                // rewrite all records to nightscout
                dataStore.setNightscoutAlwaysUpdateTimestamp(now);
                // rewrite pump profile
                if (profile && dataStore.isNsEnableProfileUpload())
                    dataStore.setRequestProfile(true);
            }
        });

        UserLogMessage.send(mContext, UserLogMessage.TYPE.INFO, R.string.ul_integrity__uploader_database_reset);
    }

    private void validatePumpRecord(PumpStatusEvent pumpRecord, PumpInfo activePump) {
        if (pumpRecord.isCgmActive()) {

            // pump can send the previous sgv at the post poll period minute mark
            // this can be a precursor to a lost sensor, sometimes there is a delay in the current sgv being available
            // we flag this and attempt another poll after a short delay
            if (pumpRecord.getEventRTC() - pumpRecord.getCgmRTC() > (POLL_PERIOD_MS + 10000) / 1000) {
                pumpRecord.setCgmOldWhenNewExpected(true);
                statPoll.incPollCgmOld();
            }

            // flag a new sgv as valid disregarding sgv's from multiple readings within the same poll period
            else if (!pumpRecord.isCgmWarmUp() && pumpRecord.getSgv() > 0 &&
                    activePump.getPumpHistory().where().equalTo("cgmRTC", pumpRecord.getCgmRTC()).findAll().size() == 0) {
                pumpRecord.setValidSGV(true);
            }
        }

        // no cgm reading contained in this status record
        else {
            statPoll.incPollCgmNA();

            // check if cgm is in use
            RealmResults<PumpStatusEvent> results = activePump.getPumpHistory()
                    .where()
                    .equalTo("cgmActive", true)
                    .sort("eventDate", Sort.DESCENDING)
                    .findAll();

            if (results.size() > 0) {
                long timespan = pumpRecord.getEventDate().getTime() - results.first().getCgmDate().getTime();
                pumpRecord.setCgmLastSeen(timespan);
                // flag as lost when within recent period
                if (timespan < 180 * 60000L)
                    pumpRecord.setCgmLostSensor(true);
                // record single stat per newly lost cgm sensor
                if (activePump.getPumpHistory().sort("eventDate", Sort.ASCENDING).last().isCgmActive()) {
                    statPoll.incPollCgmLost();
                }
            }
        }
    }

    private void statsReport() {
        String todayKey = Stats.sdfDateToKey.format(timePollStarted);
        Log.i(TAG, "STATS: " + Stats.report(todayKey));

        long yesterday = timePollStarted - 24 * 60 * 60000L;
        final String yesterdayKey = Stats.sdfDateToKey.format(yesterday);

        String last = dataStore.getLastStatReport();
        if (last == null || !last.equals(yesterdayKey)) {

            String report = Stats.report(yesterdayKey);
            if (report.length() > 0)
                UserLogMessage.sendE(mContext, UserLogMessage.TYPE.NOTE,
                        String.format("{weekday;%s} Stats: %s",
                                yesterday,
                                report
                        ));

            storeRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {
                    dataStore.setLastStatReport(yesterdayKey);
                }
            });
        }
    }

    private void checkPumpBattery(PumpStatusEvent pumpRecord) {
        if (pumpRecord.getBatteryPercentage() <= 25) {
            pumpBatteryError++;
            pollInterval = dataStore.getLowBatPollInterval();
        } else {
            pumpBatteryError = 0;
        }
    }

    private void checkCGM(PumpStatusEvent pumpRecord) {
        if (pumpRecord.isCgmActive()) {
            pumpCgmNA = 0; // poll clash detection
            pumpLostSensorError = 0;
            commsCgmSuccess++;

            if (pumpRecord.isCgmWarmUp())
                UserLogMessage.send(mContext, UserLogMessage.TYPE.CGM, R.string.ul_poll__sensor_is_in_warm_up_phase);
            else if (pumpRecord.getCalibrationDueMinutes() == 0)
                UserLogMessage.send(mContext, UserLogMessage.TYPE.CGM, R.string.ul_poll__sensor_calibration_is_due_now);
            else if (pumpRecord.getSgv() == 0 && pumpRecord.isCgmCalibrating())
                UserLogMessage.send(mContext, UserLogMessage.TYPE.CGM, R.string.ul_poll__sensor_is_calibrating);
            else if (pumpRecord.getSgv() == 0)
                UserLogMessage.send(mContext, UserLogMessage.TYPE.CGM,
                        String.format("{id;%s} ({id;%s})", R.string.ul_poll__sensor_error,
                                PumpHistoryParser.CGM_EXCEPTION.convert(pumpRecord.getCgmExceptionType()).stringId()));
            else {
                UserLogMessage.sendN(mContext, UserLogMessage.TYPE.SGV,
                        String.format("{id;%s} {sgv;%s} {id;%s} {time.sgv;%s} {id;%s} {diff;%s}",
                                R.string.ul_poll__reading_sgv__SGV,
                                pumpRecord.getSgv(),
                                R.string.ul_poll__reading_time__at,
                                pumpRecord.getCgmDate().getTime(),
                                R.string.ul_poll__reading_pump_clock_difference__Pump,
                                pumpClockDifference / 1000L
                        ));
                UserLogMessage.sendE(mContext, UserLogMessage.TYPE.SGV,
                        String.format("{id;%s} {sgv;%s} {id;%s} {time.sgv.e;%s} {id;%s} {diff;%s}",
                                R.string.ul_poll__reading_sgv__SGV,
                                pumpRecord.getSgv(),
                                R.string.ul_poll__reading_time__at,
                                pumpRecord.getCgmDate().getTime(),
                                R.string.ul_poll__reading_pump_clock_difference__Pump,
                                pumpClockDifference / 1000L
                        ));
                if (pumpRecord.isCgmCalibrating())
                    UserLogMessage.send(mContext, UserLogMessage.TYPE.CGM, R.string.ul_poll__sensor_is_calibrating);
            }

            if (pumpRecord.isCgmOldWhenNewExpected()) {
                UserLogMessage.send(mContext, UserLogMessage.TYPE.CGM, R.string.ul_poll__old_cgm_event_received);
                if (!pumpRecord.isCgmWarmUp()) {
                    // pump may have missed sensor transmission or be delayed in posting to status message
                    // in most cases the next scheduled poll will have latest sgv, occasionally it is available this period after a delay
                    pollInterval = dataStore.isSysEnablePollOverride() ? dataStore.getSysPollOldSgvRetry() : POLL_OLDSGV_RETRY_MS;
                }
            }

        } else {
            pumpCgmNA++; // poll clash detection
            if (pumpRecord.isCgmLostSensor()) {
                pumpLostSensorError++; // only count errors if cgm is being used
                UserLogMessage.send(mContext, UserLogMessage.TYPE.CGM, R.string.ul_poll__no_cgm_pump_lost_sensor);
            } else {
                UserLogMessage.send(mContext, UserLogMessage.TYPE.CGM, R.string.ul_poll__no_cgm);
            }
        }
    }

    private void statusWarnings() {

        if (pumpBatteryError >= ERROR_PUMPBATTERY_AT) {
            pumpBatteryError = 0;
            if (dataStore.getLowBatPollInterval() > POLL_PERIOD_MS) {
                UserLogMessage.send(mContext, UserLogMessage.TYPE.WARN,
                        String.format("{id;%s}. {id;%s}",
                                R.string.ul_poll__warn_pump_battery_low,
                                R.string.ul_poll__info_pump_low_battery_mode_change));

                UserLogMessage.send(mContext, UserLogMessage.TYPE.OPTION,
                        String.format("{id;%s} {qid;%s;%s}",
                                R.string.ul_poll__info_low_battery_interval,
                                R.plurals.minutes,
                                dataStore.getLowBatPollInterval() / 60000));
            } else {
                UserLogMessage.send(mContext, UserLogMessage.TYPE.WARN, R.string.ul_poll__warn_pump_battery_low);
            }
        }

        if (Math.abs(pumpClockDifference) > ERROR_PUMPCLOCK_MS)
            pumpClockError++;
        if (pumpClockError >= ERROR_PUMPCLOCK_AT) {
            pumpClockError = 0;
            UserLogMessage.send(mContext, UserLogMessage.TYPE.WARN,
                    String.format("{id;%s} {qid;%s;%s} {id;%s}",
                            R.string.ul_poll__warn_pump_time_difference,
                            R.plurals.minutes,
                            Math.abs(pumpClockDifference) / 60000L,
                            pumpClockDifference > 0
                                    ? R.string.ul_poll__warn_pump_time_difference_ahead
                                    : R.string.ul_poll__warn_pump_time_difference_behind));
            UserLogMessage.send(mContext, UserLogMessage.TYPE.HELP, R.string.ul_poll__help_pump_time_difference);
        }

        if (commsError >= ERROR_COMMS_AT) {
            UserLogMessage.send(mContext, UserLogMessage.TYPE.WARN, R.string.ul_poll__warn_multiple_comms_errors);
            UserLogMessage.send(mContext, UserLogMessage.TYPE.HELP, R.string.ul_poll__help_multiple_comms_errors);
        }

        if (pumpLostSensorError >= ERROR_PUMPLOSTSENSOR_AT) {
            pumpLostSensorError = 0;
            UserLogMessage.send(mContext, UserLogMessage.TYPE.WARN, R.string.ul_poll__warn_missing_transmissions);
            UserLogMessage.send(mContext, UserLogMessage.TYPE.HELP, R.string.ul_poll__help_missing_transmissions);
        }

        if (commsConnectError >= ERROR_CONNECT_AT * (dataStore.isDoublePollOnPumpAway() ? 2 : 1)) {
            commsConnectError = 0;
            UserLogMessage.send(mContext, UserLogMessage.TYPE.WARN, R.string.ul_poll__warn_connection_fail);
            UserLogMessage.send(mContext, UserLogMessage.TYPE.HELP, R.string.ul_poll__help_connection_fail);
        }

        if (commsSignalError >= ERROR_SIGNAL_AT) {
            commsSignalError = 0;
            UserLogMessage.send(mContext, UserLogMessage.TYPE.WARN, R.string.ul_poll__warn_rssi_signal);
            UserLogMessage.send(mContext, UserLogMessage.TYPE.HELP, R.string.ul_poll__help_rssi_signal);
        }
    }

    private void RemoveOutdatedRecords() {
        try {

            realm.executeTransaction(new Realm.Transaction() {
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

        } catch (Exception e) {
            Log.w(TAG, "RemoveOutdatedRecords Realm task could not complete");
        }
    }

    private boolean isHistoryNeeded() {
        boolean historyNeeded = false;
        String logTAG = "historyNeeded: ";

        int recency = Math.round((float) pumpHistoryHandler.pumpHistoryRecency() / 60000L);
        if (recency == -1 || recency > 24 * 60) {
            Log.d(TAG, logTAG + "no recent data");
            UserLogMessage.send(mContext, UserLogMessage.TYPE.HISTORY,
                    String.format("{id;%s}: {id;%s}",
                            R.string.ul_history__history,
                            R.string.ul_history__no_recent_data));
            historyNeeded = true;
            statPoll.incHistoryReqRecency();
        } else if (recency >= 3 * 60) {
            Log.d(TAG, logTAG + "recency " + (recency < 120 ? recency + " minutes" : ">" + recency / 60 + " hours"));
            UserLogMessage.send(mContext, UserLogMessage.TYPE.HISTORY,
                    String.format("{id;%s}: {id;%s} %s{qid;%s;%s}",
                            R.string.ul_history__history,
                            R.string.ul_history__recency,
                            recency < 120 ? "" : ">",
                            recency < 120 ? R.plurals.minutes : R.plurals.hours,
                            recency < 120 ? recency : recency / 60
                    ));
            historyNeeded = true;
            statPoll.incHistoryReqRecency();
        } else if (dataStore.getSysPumpHistoryFrequency() > 0 && (recency >= dataStore.getSysPumpHistoryFrequency() && pumpHistoryHandler.isLoopActivePotential())) {
            Log.d(TAG, logTAG + "auto mode");
            UserLogMessage.send(mContext, UserLogMessage.TYPE.HISTORY,
                    String.format("{id;%s}: {id;%s}", R.string.ul_history__history, R.string.ul_history__auto_mode));
            historyNeeded = true;
            statPoll.incHistoryReqAutoMode();
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
                UserLogMessage.send(mContext, UserLogMessage.TYPE.HISTORY,
                        String.format("{id;%s}: {id;%s} %s{qid;%s;%s}",
                                R.string.ul_history__history,
                                R.string.ul_history__stale_status,
                                ageMinutes < 120 ? "" : ">",
                                ageMinutes < 120 ? R.plurals.minutes : R.plurals.hours,
                                ageMinutes < 120 ? ageMinutes : ageMinutes / 60
                        ));
                historyNeeded = true;
                statPoll.incHistoryReqStale();

            } else {

                // basal pattern changed?
                if (basalPatNow != 0 && basalPatPre != 0 && basalPatNow != basalPatPre) {
                    Log.d(TAG, logTAG + "basal pattern changed");
                    UserLogMessage.send(mContext, UserLogMessage.TYPE.HISTORY,
                            String.format("{id;%s}: {id;%s}", R.string.ul_history__history, R.string.ul_history__basal_pattern_changed));
                    historyNeeded = true;
                    statPoll.incHistoryReqTreatment();
                }

                // suspend/resume?
                if (results.first().isSuspended() && !results.get(1).isSuspended()) {
                    Log.d(TAG, logTAG + "basal suspend");
                    UserLogMessage.send(mContext, UserLogMessage.TYPE.HISTORY,
                            String.format("{id;%s}: {id;%s}", R.string.ul_history__history, R.string.ul_history__basal_suspend));
                    historyNeeded = true;
                    statPoll.incHistoryReqTreatment();
                } else if (!results.first().isSuspended() && results.get(1).isSuspended()) {
                    Log.d(TAG, logTAG + "basal resume");
                    UserLogMessage.send(mContext, UserLogMessage.TYPE.HISTORY,
                            String.format("{id;%s}: {id;%s}", R.string.ul_history__history, R.string.ul_history__basal_resume));
                    historyNeeded = true;
                    statPoll.incHistoryReqTreatment();
                }

                // new temp basal?
                if (results.first().isTempBasalActive() && !results.get(1).isTempBasalActive()) {
                    Log.d(TAG, logTAG + "temp basal");
                    UserLogMessage.send(mContext, UserLogMessage.TYPE.HISTORY,
                            String.format("{id;%s}: {id;%s}", R.string.ul_history__history, R.string.ul_history__temp_basal));
                    historyNeeded = true;
                    statPoll.incHistoryReqTreatment();
                }

                // was temp ended before expected duration?
                if (!results.first().isTempBasalActive() && results.get(1).isTempBasalActive()
                        && Math.abs(tempMinsPre - ageMinutes) > 4) {
                    Log.d(TAG, logTAG + "temp ended");
                    UserLogMessage.send(mContext, UserLogMessage.TYPE.HISTORY,
                            String.format("{id;%s}: {id;%s}", R.string.ul_history__history, R.string.ul_history__temp_ended));
                    historyNeeded = true;
                    statPoll.incHistoryReqTreatment();
                }

                // was a new temp started while one was in progress?
                if (results.first().isTempBasalActive() && results.get(1).isTempBasalActive()
                        && (Math.abs(tempMinsDiff - ageMinutes) > 4
                        || results.first().getActiveTempBasalPattern() != results.get(1).getActiveTempBasalPattern()
                        || results.first().getTempBasalPercentage() != results.get(1).getTempBasalPercentage()
                        || results.first().getTempBasalRate() != results.get(1).getTempBasalRate()
                        || Math.abs(tempMinsDiff - ageMinutes) > 4)
                ) {
                    Log.d(TAG, logTAG + "temp changed");
                    UserLogMessage.send(mContext, UserLogMessage.TYPE.HISTORY,
                            String.format("{id;%s}: {id;%s}", R.string.ul_history__history, R.string.ul_history__temp_extended));
                    historyNeeded = true;
                    statPoll.incHistoryReqTreatment();
                }

                // bolus part delivered? normal (ref change) / square (ref change) / dual (ref can change due to normal bolus mid delivery of square part)
                if (!results.first().isBolusingNormal() && bolusRefNow != bolusRefPre) {

                    // was dual ended before expected duration?
                    if (!results.first().isBolusingDual() && results.get(1).isBolusingDual()) {
                        if (Math.abs(bolusMinsPre - ageMinutes) > 4) {
                            Log.d(TAG, logTAG + "dual ended");
                            UserLogMessage.send(mContext, UserLogMessage.TYPE.HISTORY,
                                    String.format("{id;%s}: {id;%s}", R.string.ul_history__history, R.string.ul_history__dual_ended));
                            historyNeeded = true;
                            statPoll.incHistoryReqTreatment();
                        }
                    }
                    // was square ended before expected duration?
                    else if (!results.first().isBolusingSquare() && results.get(1).isBolusingSquare() && !results.get(1).isBolusingNormal()) {
                        if (Math.abs(bolusMinsPre - ageMinutes) > 4) {
                            Log.d(TAG, logTAG + "square ended");
                            UserLogMessage.send(mContext, UserLogMessage.TYPE.HISTORY,
                                    String.format("{id;%s}: {id;%s}", R.string.ul_history__history, R.string.ul_history__square_ended));
                            historyNeeded = true;
                            statPoll.incHistoryReqTreatment();
                        }
                    }
                    // dual bolus normal part delivered?
                    else if (!results.first().isBolusingSquare() && !results.get(1).isBolusingDual() && results.first().isBolusingDual()) {
                        Log.d(TAG, logTAG + "dual bolus");
                        UserLogMessage.send(mContext, UserLogMessage.TYPE.HISTORY,
                                String.format("{id;%s}: {id;%s}", R.string.ul_history__history, R.string.ul_history__dual_bolus));
                        historyNeeded = true;
                        statPoll.incHistoryReqTreatment();
                    }
                    // normal bolus delivered
                    else {
                        Log.d(TAG, logTAG + "normal bolus");
                        UserLogMessage.send(mContext, UserLogMessage.TYPE.HISTORY,
                                String.format("{id;%s}: {id;%s}", R.string.ul_history__history, R.string.ul_history__normal_bolus));
                        historyNeeded = true;
                        statPoll.incHistoryReqTreatment();
                    }
                }
                // bolus ended? or ended before expected duration?
                else if (!results.first().isBolusingDual() && results.get(1).isBolusingDual()
                        && Math.abs(bolusMinsPre - ageMinutes) > 4) {
                    Log.d(TAG, logTAG + "bolus ended");
                    UserLogMessage.send(mContext, UserLogMessage.TYPE.HISTORY,
                            String.format("{id;%s}: {id;%s}", R.string.ul_history__history, R.string.ul_history__bolus_ended));
                    historyNeeded = true;
                    statPoll.incHistoryReqTreatment();
                }

                // square bolus started?
                if (!results.first().isBolusingNormal() && results.first().isBolusingSquare() && !results.get(1).isBolusingSquare()) {
                    Log.d(TAG, logTAG + "square bolus");
                    UserLogMessage.send(mContext, UserLogMessage.TYPE.HISTORY,
                            String.format("{id;%s}: {id;%s}", R.string.ul_history__history, R.string.ul_history__square_bolus));
                    historyNeeded = true;
                    statPoll.incHistoryReqTreatment();
                }

                // reservoir/battery changes need to pull history after pump has resumed
                // to ensure that we don't miss the resume entry in the history
                if (results.first().getActiveBasalPattern() != 0) {

                    if (results.first().getReservoirAmount() > results.get(1).getReservoirAmount()) {
                        Log.d(TAG, logTAG + "reservoir changed");
                        UserLogMessage.send(mContext, UserLogMessage.TYPE.HISTORY,
                                String.format("{id;%s}: {id;%s}", R.string.ul_history__history, R.string.ul_history__reservoir_changed));
                        historyNeeded = true;
                        statPoll.incHistoryReqConsumable();
                    }

                    if (results.first().getBatteryPercentage() > results.get(1).getBatteryPercentage()) {
                        Log.d(TAG, logTAG + "battery changed");
                        UserLogMessage.send(mContext, UserLogMessage.TYPE.HISTORY,
                                String.format("{id;%s}: {id;%s}", R.string.ul_history__history, R.string.ul_history__battery_changed));
                        historyNeeded = true;
                        statPoll.incHistoryReqConsumable();
                    }

                    if (!historyNeeded && results.get(1).getActiveBasalPattern() == 0) {
                        Log.d(TAG, logTAG + "pattern resume");
                        UserLogMessage.send(mContext, UserLogMessage.TYPE.HISTORY,
                                String.format("{id;%s}: {id;%s}", R.string.ul_history__history, R.string.ul_history__pattern_resume));
                        historyNeeded = true;
                        statPoll.incHistoryReqConsumable();
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
                        UserLogMessage.send(mContext, UserLogMessage.TYPE.HISTORY,
                                String.format("{id;%s}: {id;%s}", R.string.ul_history__history, R.string.ul_history__cannula_fill));
                        historyNeeded = true;
                        statPoll.incHistoryReqConsumable();
                    }
                }

                boolean sensor = (dataStore.isNsEnableTreatments() & dataStore.isNsEnableSensorChange())
                        | (dataStore.isPushoverEnable() & dataStore.isPushoverEnableConsumables());

                boolean fingerbg = (dataStore.isNsEnableTreatments() & dataStore.isNsEnableFingerBG())
                        | (dataStore.isPushoverEnable() & dataStore.isPushoverEnableBG());

                boolean calibration = (dataStore.isNsEnableTreatments() & dataStore.isNsEnableFingerBG() & dataStore.isNsEnableCalibrationInfo())
                        | (dataStore.isPushoverEnable() & dataStore.isPushoverEnableCalibration())
                        | (dataStore.isSysEnableEstimateSGV());

                boolean dailytotals = (dataStore.isNsEnableTreatments() & dataStore.isNsEnableDailyTotals())
                        | (dataStore.isPushoverEnable() & dataStore.isPushoverEnableDailyTotals());

                boolean alarms = (dataStore.isNsEnableTreatments() & dataStore.isNsEnableAlarms())
                        | (dataStore.isPushoverEnable());

                boolean cleared = dataStore.isNsAlarmCleared() | dataStore.isPushoverEnableClearedAcknowledged();

                // sensor changed?
                if (sensor) {
                    if (results.first().isCgmWarmUp()
                            && (cgmresults.size() > 1 && !cgmresults.get(1).isCgmWarmUp())) {
                        Log.d(TAG, logTAG + "sensor changed");
                        UserLogMessage.send(mContext, UserLogMessage.TYPE.HISTORY,
                                String.format("{id;%s}: {id;%s}", R.string.ul_history__history, R.string.ul_history__sensor_changed));
                        historyNeeded = true;
                        statPoll.incHistoryReqConsumable();
                    }
                }

                // recentBGL is in the status message for up to 20 minutes, check for this or if there was a new reading with a different bgl value
                if (fingerbg) {
                    if (results.first().getRecentBGL() != 0 &&
                            results.where()
                                    .greaterThan("eventDate", new Date(System.currentTimeMillis() - (20 * 60000L)))
                                    .equalTo("recentBGL", results.first().getRecentBGL())
                                    .findAll()
                                    .size() == 1) {
                        Log.d(TAG, logTAG + "recent finger bg");
                        UserLogMessage.send(mContext, UserLogMessage.TYPE.HISTORY,
                                String.format("{id;%s}: {id;%s}", R.string.ul_history__history, R.string.ul_history__recent_finger_bg));
                        historyNeeded = true;
                        statPoll.incHistoryReqBG();
                    }
                }

                // calibration factor info available?
                if (calibration) {
                    if (cgmresults.size() > 1 && !results.first().isCgmWarmUp()
                            && results.first().getCalibrationDueMinutes() > 0
                            && results.first().getCalibrationDueMinutes() > cgmresults.get(1).getCalibrationDueMinutes()) {
                        Log.d(TAG, logTAG + "calibration info");
                        UserLogMessage.send(mContext, UserLogMessage.TYPE.HISTORY,
                                String.format("{id;%s}: {id;%s}", R.string.ul_history__history, R.string.ul_history__calibration_info));
                        historyNeeded = true;
                        statPoll.incHistoryReqCalibration();
                    }
                }

                // daily totals?
                if (dailytotals) {
                    SimpleDateFormat sdfDay = new SimpleDateFormat("dd", Locale.getDefault());
                    long dayNow = results.first().getEventDate().getTime() + pumpClockDifference;
                    long dayPre = results.get(1).getEventDate().getTime() + pumpClockDifference;
                    if (!sdfDay.format(dayNow).equals(sdfDay.format(dayPre))) {
                        Log.d(TAG, logTAG + "daily totals");
                        UserLogMessage.send(mContext, UserLogMessage.TYPE.HISTORY,
                                String.format("{id;%s}: {id;%s}", R.string.ul_history__history, R.string.ul_history__daily_totals));
                        historyNeeded = true;
                    }
                }

                if (alarms) {
                    // active alert
                    if (results.first().getAlert() != 0) {
                        if (results.first().getAlertRTC() != results.get(1).getAlertRTC()) {
                            Log.d(TAG, logTAG + "active alert");
                            UserLogMessage.send(mContext, UserLogMessage.TYPE.HISTORY,
                                    String.format("{id;%s}: {id;%s}", R.string.ul_history__history, R.string.ul_history__active_alert));
                            historyNeeded = true;
                            statPoll.incHistoryReqAlert();
                        } else if (recency >= 15) {
                            // recheck at interval to catch stacked alerts as pump will only show the oldest uncleared active alert
                            Log.d(TAG, logTAG + "alert recheck");
                            UserLogMessage.send(mContext, UserLogMessage.TYPE.HISTORY,
                                    String.format("{id;%s}: {id;%s}", R.string.ul_history__history, R.string.ul_history__alert_recheck));
                            historyNeeded = true;
                            statPoll.incHistoryReqAlertRecheck();
                        }

                    }
                    // cleared alert
                    else if (cleared && results.get(1).getAlert() != 0) {
                        Log.d(TAG, logTAG + "cleared alert");
                        UserLogMessage.send(mContext, UserLogMessage.TYPE.HISTORY,
                                String.format("{id;%s}: {id;%s}", R.string.ul_history__history, R.string.ul_history__cleared_alert));
                        historyNeeded = true;
                        statPoll.incHistoryReqAlertCleared();
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
                if (pumpCgmNA >= POLL_ANTI_CLASH)
                    pollOffset += (((pumpCgmNA - POLL_ANTI_CLASH) % 3)) * 15 * 1000L;
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
            UserLogMessage.send(mContext, UserLogMessage.TYPE.WARN, getString(R.string.ul_usb__no_support));
            return false;
        }

        if (mUsbManager == null) {
            Log.e(TAG, "USB connection error. mUsbManager == null");
            UserLogMessage.send(mContext, UserLogMessage.TYPE.WARN, getString(R.string.ul_usb__no_connection));
            return false;
        }

        UsbDevice cnlStick = UsbHidDriver.getUsbDevice(mUsbManager, USB_VID, USB_PID);
        if (cnlStick == null) {
            Log.w(TAG, "USB connection error. Is the CNL plugged in?");
            UserLogMessage.send(mContext, UserLogMessage.TYPE.WARN, getString(R.string.ul_usb__no_connection));
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
            UserLogMessage.send(mContext, UserLogMessage.TYPE.WARN, getString(R.string.ul_usb__no_open));
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

    private void debugActiveAlert() {

        RealmResults<PumpStatusEvent> results = realm
                .where(PumpStatusEvent.class)
                .sort("eventDate", Sort.DESCENDING)
                .findAll();

        if (results.size() > 1
                && results.first().getAlert() != 0
                && results.first().getAlertRTC() != results.get(1).getAlertRTC()) {

            int faultNumber = results.first().getAlert();
            PumpAlert pumpAlert = new PumpAlert().faultNumber(faultNumber).build();
            if (!pumpAlert.isAlertKnown()) {

                int eventRTC = results.first().getAlertRTC();
                int eventOFFSET = results.first().getAlertOFFSET();
                Date pumpdate = MessageUtils.decodeDateTime(eventRTC & 0xFFFFFFFFL, eventOFFSET);

                String s = String.format("[ActiveAlert]<br>pumpDate: %s<br>faultNumber: #%s",
                        FormatKit.getInstance().formatAsYMDHMS(pumpdate.getTime()),
                        faultNumber
                );

                pumpHistoryHandler.systemEvent(PumpHistorySystem.STATUS.DEBUG_NIGHTSCOUT)
                        .data(s)
                        .process();
            }
        }
    }

    private boolean debugHistory(boolean debug) {
        if (debug) {
            if (pumpHistoryHandler.records() != 0) {
                UserLogMessage.send(mContext, "Getting history from file");
                new HistoryDebug(mContext, pumpHistoryHandler).run();
                UserLogMessage.send(mContext, "Processing done");
            } else {
                UserLogMessage.send(mContext, "History file already processed");
            }
        }
        return debug;
    }

    private void debugStatusMessage() {
        DateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.US);

        long now = System.currentTimeMillis();

        Date lastDate = pumpHistoryHandler.debugNoteLastDate();
        if (lastDate != null && now - lastDate.getTime() < 30 * 60000L) return;

        RealmResults<PumpStatusEvent> results = realm
                .where(PumpStatusEvent.class)
                .greaterThan("eventDate", new Date(now - 33 * 60000L))
                .sort("eventDate", Sort.DESCENDING)
                .findAll();

        if (results.size() < 1) return;

        StringBuilder note = new StringBuilder("DEBUG:");
        byte[] payload;

        for (PumpStatusEvent record : results) {
            note.append(" [").append(sdf.format(record.getEventDate())).append("] ");

            byte status = record.getPumpStatus();

            note.append(" ST:").append(String.format(Locale.US, "%8s", Integer.toBinaryString(status)).replace(' ', '0')).append(" BP:").append(record.getActiveBasalPattern()).append("/").append(record.getActiveTempBasalPattern()).append(" LB:").append(record.getLastBolusReference() & 0xFF).append("/").append(record.getBolusingReference() & 0xFF).append(" BR:").append(record.getBasalRate()).append("/").append(record.getBasalUnitsDeliveredToday()).append(" AI:").append(record.getActiveInsulin());

            payload = record.getPayload();
            if (payload != null && payload.length >= 0x60) {
                if ((payload[0x08] | payload[0x09] | payload[0x0A] | payload[0x0B]) > 0) {
                    note.append(" 0x08: ").append(HexDump.toHexString(payload, 0x08, 4));
                }

                if ((payload[0x55] | payload[0x56] | payload[0x57]) > 0) {
                    note.append(" 0x55: ").append(HexDump.toHexString(payload, 0x55, 3));
                }

                if ((payload[0x0F] | payload[0x19]) > 0) {
                    note.append(" 0x0F: ").append(HexDump.toHexString(payload[0x0F])).append(" 0x19: ").append(HexDump.toHexString(payload[0x19]));
                }

                if (payload.length > 0x60) {
                    note.append(" 0x60: ").append(HexDump.toHexString(payload, 0x60, payload.length - 0x60));
                }
            }
        }

        pumpHistoryHandler.debugNote(note.toString());
    }
}
