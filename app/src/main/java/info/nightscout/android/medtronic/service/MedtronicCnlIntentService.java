package info.nightscout.android.medtronic.service;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.R;
import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.MainActivity;
import info.nightscout.android.medtronic.MedtronicCNLReader;
import info.nightscout.android.medtronic.message.ChecksumException;
import info.nightscout.android.medtronic.message.EncryptionException;
import info.nightscout.android.medtronic.message.MessageUtils;
import info.nightscout.android.medtronic.message.UnexpectedMessageException;
import info.nightscout.android.model.medtronicNg.ContourNextLinkInfo;
import info.nightscout.android.upload.MedtronicNG.CGMRecord;
import info.nightscout.android.upload.MedtronicNG.PumpStatusRecord;
import info.nightscout.android.upload.UploadHelper;
import io.realm.Realm;

public class MedtronicCnlIntentService extends IntentService {
    public final static int USB_VID = 0x1a79;
    public final static int USB_PID = 0x6210;
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

    public final class Constants {
        public static final String ACTION_STATUS_MESSAGE = "info.nightscout.android.medtronic.service.STATUS_MESSAGE";
        public static final String ACTION_CGM_DATA = "info.nightscout.android.medtronic.service.CGM_DATA";
        public static final String EXTENDED_DATA = "info.nightscout.android.medtronic.service.DATA";
    }

    protected void sendStatus(String message) {
        Intent localIntent =
                new Intent(Constants.ACTION_STATUS_MESSAGE)
                        .putExtra(Constants.EXTENDED_DATA, message);
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    protected void sendCgmRecord(Serializable cgmRecord) {
        Intent localIntent =
                new Intent(Constants.ACTION_CGM_DATA)
                        .putExtra(Constants.EXTENDED_DATA, cgmRecord);
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
        Log.i(TAG, "onHandleIntent called");

        if (!hasUsbHostFeature()) {
            sendStatus("It appears that this device doesn't support USB OTG.");
            return;
        }

        UploadHelper mUploader = new UploadHelper(getBaseContext());
        mHidDevice = UsbHidDriver.acquire(mUsbManager, USB_VID, USB_PID);
        Realm realm = Realm.getDefaultInstance();

        // Load the initial data to the display
        CGMRecord cgmRecord = loadData();
        PumpStatusRecord pumpRecord = MainActivity.pumpStatusRecord;

        sendCgmRecord(cgmRecord);

        if (mHidDevice == null) {
            String title = "USB connection error";
            String msg = "Is the Bayer Contour NextLink plugged in?";
            //showNotification(title, msg);
            sendStatus(title + "\n" + msg);
        } else {
            try {
                mHidDevice.open();
            } catch (Exception e) {
                Log.e(TAG, "Unable to open serial device", e);
                return;
            }

            // Go get the data
            MedtronicCNLReader cnlReader = new MedtronicCNLReader(mHidDevice);

            try {
                sendStatus("Connecting to the Contour Next Link...");
                cnlReader.requestDeviceInfo();

                // Is the device already configured?
                ContourNextLinkInfo info = realm
                        .where(ContourNextLinkInfo.class).equalTo("serialNumber", cnlReader.getStickSerial())
                        .findFirst();

                if (info == null) {
                    info = new ContourNextLinkInfo();
                    info.setSerialNumber(cnlReader.getStickSerial());

                    realm.beginTransaction();
                    info = realm.copyToRealm(info);
                    realm.commitTransaction();
                }

                String hmac = info.getHmac();
                String key = info.getKey();

                String deviceName = String.format("medtronic-640g://%s", cnlReader.getStickSerial());
                cgmRecord.setDeviceName(deviceName);
                pumpRecord.setDeviceName(deviceName);

                if (hmac == null || key == null) {
                    sendStatus(String.format("Before you can use the Contour Next Link, you need to register it with the app. Select '%s' from the menu.", getString(R.string.register_contour_next_link)));
                    return;
                }

                cnlReader.getPumpSession().setHMAC(MessageUtils.hexStringToByteArray(hmac));
                cnlReader.getPumpSession().setKey(MessageUtils.hexStringToByteArray(key));

                cnlReader.enterControlMode();
                try {
                    cnlReader.enterPassthroughMode();
                    cnlReader.openConnection();
                    cnlReader.requestReadInfo();
                    byte radioChannel = cnlReader.negotiateChannel();
                    if (radioChannel == 0) {
                        sendStatus("Could not communicate with the 640g. Are you near the pump?");
                        Log.i(TAG, "Could not communicate with the 640g. Are you near the pump?");
                    } else {
                        sendStatus(String.format(Locale.getDefault(), "Connected to Contour Next Link on channel %d.", (int) radioChannel));
                        Log.d(TAG, String.format("Connected to Contour Next Link on channel %d.", (int) radioChannel));
                        cnlReader.beginEHSMSession();

                        pumpRecord.pumpDate = cnlReader.getPumpTime();
                        cnlReader.getPumpStatus(cgmRecord);

                        writeData(cgmRecord);
                        sendCgmRecord(cgmRecord);
                        cnlReader.endEHSMSession();
                    }
                    cnlReader.closeConnection();
                } catch (UnexpectedMessageException e) {
                    Log.e(TAG, "Unexpected Message", e);
                    sendStatus("Communication Error: " + e.getMessage());

                } finally {
                    cnlReader.endPassthroughMode();
                    cnlReader.endControlMode();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error getting SGVs", e);
                sendStatus("Error connecting to Contour Next Link.");
            } catch (ChecksumException e) {
                Log.e(TAG, "Checksum error", e);
                sendStatus("Checksum error getting message from the Contour Next Link.");
            } catch (EncryptionException e) {
                Log.e(TAG, "Encryption exception", e);
                sendStatus("Error decrypting messages from Contour Next Link.");
            } catch (TimeoutException e) {
                Log.e(TAG, "Timeout communicating with Contour", e);
                sendStatus("Timeout communicating with the Contour Next Link.");
            } catch (UnexpectedMessageException e) {
                Log.e(TAG, "Unexpected Message", e);
                sendStatus("Could not close connection: " + e.getMessage());
            }

            // TODO - add retries.
            if (!isOnline()) {
                String title = "Cannot upload data";
                String msg = "Please check that you're connected to the Internet";
                //showNotification(title, msg);
                sendStatus(title + "\n" + msg);
            } else {
                // FIXME - DO THE UPLOAD!
                //mUploader.execute(cgmRecord);
            }

            realm.close();
        }
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

    private boolean hasUsbHostFeature() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST);
    }

    // FIXME - replace this with writing to the SQLite DB.
    private void writeData(CGMRecord cgmRecord) {
        //Write most recent data
        try {
            Context context = getBaseContext();
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(new File(context.getFilesDir(), "save.bin"))); //Select where you wish to save the file...
            oos.writeObject(cgmRecord); // write the class as an 'object'
            oos.flush(); // flush the stream to insure all of the information was written to 'save.bin'
            oos.close();// close the stream
        } catch (Exception e) {
            Log.e(TAG, "write to OutputStream failed", e);
        }
    }

    /*
    // FIXME - when we want to enable notifications, start with this. We'll need to fix the icon to match
    // the Android standards (linter will fail anyway)
    private void showNotification(String title, String message) {
        NotificationManagerCompat nm = NotificationManagerCompat.from(mContext);

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);
        nm.notify(R.string.app_name, new NotificationCompat.Builder(mContext)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentTitle(title)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(message))
                .setContentText(message)
                .setTicker(message)
                //.setSmallIcon(R.drawable.ic_action_warning)
                .setSmallIcon(R.drawable.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher))
                .setContentIntent(contentIntent)
                .build());
    }
    */

    private CGMRecord loadData() {
        ObjectInputStream ois = null;
        try {
            Context context = getBaseContext();
            ois = new ObjectInputStream(new FileInputStream(new File(context.getFilesDir(), "save.bin")));
            Object o = ois.readObject();
            ois.close();
            return (CGMRecord) o;
        } catch (Exception ex) {
            Log.w(TAG, " unable to load Medtronic640g data");
            try {
                if (ois != null)
                    ois.close();
            } catch (Exception e) {
                Log.e(TAG, " Error closing ObjectInputStream");
            }
        }
        return new CGMRecord();
    }
}
