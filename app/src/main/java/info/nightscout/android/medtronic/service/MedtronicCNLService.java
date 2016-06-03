package info.nightscout.android.medtronic.service;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.R;
import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.Medtronic640gActivity;
import info.nightscout.android.medtronic.MedtronicCNLReader;
import info.nightscout.android.medtronic.data.CNLConfigDbHelper;
import info.nightscout.android.medtronic.message.ChecksumException;
import info.nightscout.android.medtronic.message.EncryptionException;
import info.nightscout.android.medtronic.message.MessageUtils;
import info.nightscout.android.medtronic.message.UnexpectedMessageException;
import info.nightscout.android.service.AbstractService;
import info.nightscout.android.upload.MedtronicNG.CGMRecord;
import info.nightscout.android.upload.MedtronicNG.PumpStatusRecord;
import info.nightscout.android.upload.UploadHelper;

public class MedtronicCNLService extends AbstractService {
    public final static int USB_VID = 0x1a79;
    public final static int USB_PID = 0x6210;

    private UsbHidDriver mHidDevice;
    private Timer mTimer = new Timer();
    private static final String TAG = MedtronicCNLService.class.getSimpleName();
    private Context mContext;
    private NotificationManagerCompat nm;
    private final static long POLL_PERIOD_MS = 300000L;
    private final static long POLL_DELAY_MS = 30000L;
    // If the polling is within this many milliseconds (either side), then we don't reset the timer
    private final static long POLL_MARGIN_MS = 10000L;
    private UsbManager mUsbManager;
    private Handler handler;

    @Override
    public void onCreate() {
        this.handler = new Handler();
        super.onCreate();
    }

    @Override
    public void onStartService() {
        Log.i(TAG, "onStartService called");
        mContext = this.getBaseContext();
        mUsbManager = (UsbManager) this.getSystemService(Context.USB_SERVICE);

        // Add a small start delay - for some reason, having no start delay causes initial
        // binding/rendering issues
        startPollingLoop(250L);
    }

    private void startPollingLoop(long delay) {
        mTimer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                handler.post(new Runnable() {
                    public void run() {
                        doReadAndUpload();
                    }
                });
            }
        }, delay, POLL_PERIOD_MS);
    }

    @Override
    public void onStopService() {
        Log.d(TAG, "onStopService called");

        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }

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

    @Override
    public void onReceiveMessage(Message msg) {
    }

    protected void doReadAndUpload() {
        UploadHelper mUploader = new UploadHelper(getBaseContext());
        mHidDevice = UsbHidDriver.acquire(mUsbManager, USB_VID, USB_PID);

        // Load the initial data to the display
        CGMRecord cgmRecord = loadData();
        PumpStatusRecord pumpRecord = Medtronic640gActivity.pumpStatusRecord;

        send(Message.obtain(null, Medtronic640gActivity.Medtronic640gActivityHandler.MSG_DATA, cgmRecord));

        if (mHidDevice == null) {
            String title = "USB connection error";
            String msg = "Is the Bayer Contour NextLink plugged in?";
            //showNotification(title, msg);
            send(Message.obtain(null, Medtronic640gActivity.Medtronic640gActivityHandler.MSG_ERROR, title + "\n" + msg));
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
                send(Message.obtain(null, Medtronic640gActivity.Medtronic640gActivityHandler.MSG_STATUS, "Connecting to the Contour Next Link..."));
                cnlReader.requestDeviceInfo();

                // Is the device already configured?
                CNLConfigDbHelper configDbHelper = new CNLConfigDbHelper(mContext);
                configDbHelper.insertStickSerial(cnlReader.getStickSerial());
                String hmac = configDbHelper.getHmac(cnlReader.getStickSerial());
                String key = configDbHelper.getKey(cnlReader.getStickSerial());
                String deviceName = String.format("medtronic-640g://%s", cnlReader.getStickSerial());
                cgmRecord.setDeviceName(deviceName);
                Medtronic640gActivity.pumpStatusRecord.setDeviceName(deviceName);

                if (hmac.equals("") || key.equals("")) {
                    send(Message.obtain(null, Medtronic640gActivity.Medtronic640gActivityHandler.MSG_ERROR, String.format("Before you can use the Contour Next Link, you need to register it with the app. Select '%s' from the menu.", getString(R.string.register_contour_next_link))));
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
                        send(Message.obtain(null, Medtronic640gActivity.Medtronic640gActivityHandler.MSG_ERROR, "Could not communicate with the 640g. Are you near the pump?"));
                        Log.i(TAG, "Could not communicate with the 640g. Are you near the pump?");
                    } else {
                        send(Message.obtain(null, Medtronic640gActivity.Medtronic640gActivityHandler.MSG_STATUS, String.format( Locale.getDefault(), "Connected to Contour Next Link on channel %d.", (int) radioChannel)));
                        Log.d(TAG, String.format("Connected to Contour Next Link on channel %d.", (int) radioChannel));
                        cnlReader.beginEHSMSession();

                        cnlReader.getPumpTime(pumpRecord);
                        cnlReader.getPumpStatus(cgmRecord);

                        long pumpToUploaderTimeOffset = (new java.util.Date()).getTime() - Medtronic640gActivity.pumpStatusRecord.pumpDate.getTime();

                        writeData(cgmRecord);
                        send(Message.obtain(null, Medtronic640gActivity.Medtronic640gActivityHandler.MSG_DATA, cgmRecord));
                        cnlReader.endEHSMSession();
                    }
                    cnlReader.closeConnection();
                } catch (UnexpectedMessageException e) {
                    Log.e(TAG, "Unexpected Message", e);
                    send(Message.obtain(null, Medtronic640gActivity.Medtronic640gActivityHandler.MSG_ERROR, "Communication Error: " + e.getMessage()));
                } finally {
                    cnlReader.endPassthroughMode();
                    cnlReader.endControlMode();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error getting SGVs", e);
                send(Message.obtain(null, Medtronic640gActivity.Medtronic640gActivityHandler.MSG_ERROR, "Error connecting to Contour Next Link."));
            } catch (ChecksumException e) {
                Log.e(TAG, "Checksum error", e);
                send(Message.obtain(null, Medtronic640gActivity.Medtronic640gActivityHandler.MSG_ERROR, "Checksum error getting message from the Contour Next Link."));
            } catch (EncryptionException e) {
                Log.e(TAG, "Encryption exception", e);
                send(Message.obtain(null, Medtronic640gActivity.Medtronic640gActivityHandler.MSG_ERROR, "Error decrypting messages from Contour Next Link."));
            } catch (TimeoutException e) {
                Log.e(TAG, "Timeout communicating with Contour", e);
                send(Message.obtain(null, Medtronic640gActivity.Medtronic640gActivityHandler.MSG_ERROR, "Timeout communicating with the Contour Next Link."));
            } catch (UnexpectedMessageException e) {
                Log.e(TAG, "Unexpected Message", e);
                send(Message.obtain(null, Medtronic640gActivity.Medtronic640gActivityHandler.MSG_ERROR, "Could not close connection: " + e.getMessage()));
            }

            // TODO - add retries.
            if (!isOnline()) {
                String title = "Cannot upload data";
                String msg = "Please check that you're connected to the Internet";
                //showNotification(title, msg);
                send(Message.obtain(null, Medtronic640gActivity.Medtronic640gActivityHandler.MSG_ERROR, title + "\n" + msg));
            } else {
                mUploader.execute(cgmRecord);
            }
        }
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

    /*
    // FIXME - when we want to enable notifications, start with this. We'll need to fix the icon to match
    // the Android standards (linter will fail anyway)
    private void showNotification(String title, String message) {
        NotificationManagerCompat nm = NotificationManagerCompat.from(mContext);

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, Medtronic640gActivity.class), 0);
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

    // FIXME - replace this with writing to the SQLite DB.
    private void writeData(CGMRecord mostRecentData) {
        //Write most recent data
        try {
            Context context = getBaseContext();
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(new File(context.getFilesDir(), "save.bin"))); //Select where you wish to save the file...
            oos.writeObject(mostRecentData); // write the class as an 'object'
            oos.flush(); // flush the stream to insure all of the information was written to 'save.bin'
            oos.close();// close the stream
        } catch (Exception e) {
            Log.e(TAG, "write to OutputStream failed", e);
        }
    }

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
