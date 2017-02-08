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

import info.nightscout.android.R;
import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.MainActivity;
import info.nightscout.android.medtronic.MedtronicCnlReader;
import info.nightscout.android.medtronic.message.ChecksumException;
import info.nightscout.android.medtronic.message.EncryptionException;
import info.nightscout.android.medtronic.message.MessageUtils;
import info.nightscout.android.medtronic.message.UnexpectedMessageException;
import info.nightscout.android.model.medtronicNg.ContourNextLinkInfo;
import info.nightscout.android.model.medtronicNg.PumpInfo;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.upload.nightscout.NightscoutUploadReceiver;
import info.nightscout.android.xdrip_plus.XDripPlusUploadReceiver;
import io.realm.Realm;
import io.realm.RealmResults;

public class MedtronicCnlIntentService extends IntentService {
    public final static int USB_VID = 0x1a79;
    public final static int USB_PID = 0x6210;
    public final static long USB_WARMUP_TIME_MS = 5000L;
    public final static long POLL_PERIOD_MS = 300000L;
    // Number of additional seconds to wait after the next expected CGM poll, so that we don't interfere with CGM radio comms.
    public final static long POLL_GRACE_PERIOD_MS = 30000L;
    private static final String TAG = MedtronicCnlIntentService.class.getSimpleName();
    private UsbHidDriver mHidDevice;
    private Context mContext;
    private NotificationManagerCompat nm;
    private UsbManager mUsbManager;

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

        MedtronicCnlReader cnlReader = new MedtronicCnlReader(mHidDevice);

        Realm realm = Realm.getDefaultInstance();
        realm.beginTransaction();

        try {
            sendStatus("Connecting to the Contour Next Link...");
            Log.d(TAG, "Connecting to the Contour Next Link.");
            cnlReader.requestDeviceInfo();

            // Is the device already configured?
            ContourNextLinkInfo info = realm
                    .where(ContourNextLinkInfo.class)
                    .equalTo("serialNumber", cnlReader.getStickSerial())
                    .findFirst();

            if (info == null) {
                // TODO - use realm.createObject()?
                info = new ContourNextLinkInfo();
                info.setSerialNumber(cnlReader.getStickSerial());

                info = realm.copyToRealm(info);
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
                MainActivity.setActivePumpMac(pumpMAC);
                PumpInfo activePump = realm
                        .where(PumpInfo.class)
                        .equalTo("pumpMac", pumpMAC)
                        .findFirst();

                if (activePump == null) {
                    activePump = realm.createObject(PumpInfo.class);
                    activePump.setPumpMac(pumpMAC);
                }

                byte radioChannel = cnlReader.negotiateChannel(activePump.getLastRadioChannel());
                if (radioChannel == 0) {
                    sendStatus("Could not communicate with the 640g. Are you near the pump?");
                    Log.i(TAG, "Could not communicate with the 640g. Are you near the pump?");
                } else {
                    activePump.setLastRadioChannel(radioChannel);
                    sendStatus(String.format(Locale.getDefault(), "Connected to Contour Next Link on channel %d.", (int) radioChannel));
                    Log.d(TAG, String.format("Connected to Contour Next Link on channel %d.", (int) radioChannel));
                    cnlReader.beginEHSMSession();

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
                    cnlReader.getPumpStatus(pumpRecord, pumpOffset);
                    activePump.getPumpHistory().add(pumpRecord);

                    cnlReader.endEHSMSession();

                    boolean cancelTransaction = true;
                    if (pumpRecord.getSgv() != 0) {
                        // Check that the record doesn't already exist before committing
                        RealmResults<PumpStatusEvent> checkExistingRecords = activePump.getPumpHistory()
                                .where()
                                .equalTo("eventDate", pumpRecord.getEventDate())
                                .equalTo("sgv", pumpRecord.getSgv())
                                .findAll();

                        // There should be the 1 record we've already added in this transaction.
                        if (checkExistingRecords.size() <= 1) {
                            realm.commitTransaction();
                            cancelTransaction = false;
                        }

                        // Tell the Main Activity we have new data
                        sendMessage(Constants.ACTION_REFRESH_DATA);
                    }

                    if (cancelTransaction) {
                        realm.cancelTransaction();
                    }
                }
            } catch (UnexpectedMessageException e) {
                Log.e(TAG, "Unexpected Message", e);
                sendStatus("Communication Error: " + e.getMessage());
            } catch (NoSuchAlgorithmException e) {
                Log.e(TAG, "Could not determine CNL HMAC", e);
                sendStatus("Error connecting to Contour Next Link: Hashing error.");
            } finally {
                //TODO : 05.11.2016 has the close to be here?
                cnlReader.closeConnection();
                cnlReader.endPassthroughMode();
                cnlReader.endControlMode();
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

        public static final String EXTENDED_DATA = "info.nightscout.android.medtronic.service.DATA";
    }
}
