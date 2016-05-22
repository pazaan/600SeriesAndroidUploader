package info.nightscout.android.dexcom;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask.Status;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import info.nightscout.android.R;
import info.nightscout.android.dexcom.USB.SerialInputOutputManager;
import info.nightscout.android.USB.USBPower;
import info.nightscout.android.dexcom.USB.UsbSerialDriver;
import info.nightscout.android.dexcom.USB.UsbSerialProber;
import info.nightscout.android.upload.UploadHelper;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@SuppressLint("NewApi")
public class DexcomG4Service extends Service {

    public UsbManager mUsbManager;
    private static final String TAG = DexcomG4Service.class.getSimpleName();
    private Context mContext;
    private NotificationManager NM;
    private final int FIVE_MINS_MS = 300000;
    private final int THREE_MINS_MS = 180000;
    private final int UPLOAD_OFFSET_MS = 3000;
    private long nextUploadTimer = THREE_MINS_MS;
    private boolean initialRead = true;
    private UsbSerialDriver mSerialDevice;
    private UploadHelper uploader;
    private Handler mHandler = new Handler();
    private SerialInputOutputManager mSerialIoManager;
    private WifiManager wifiManager;

    @Override
    public IBinder onBind(Intent arg0) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate called");
        mContext = this.getBaseContext();
        wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        // connectToG4();
        mHandler.removeCallbacks(readAndUpload);
        mHandler.post(readAndUpload);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy called");
        mHandler.removeCallbacks(readAndUpload);
        stopIoManager();

        if (NM != null) {
            NM.cancelAll();
            NM = null;
        }

        if (mSerialDevice != null) {
            try {
                Log.i(TAG, "Closing serial device...");
                mSerialDevice.close();
            } catch (IOException e) {
                Log.e(TAG, "Unable to close serial device.", e);
            }
            mSerialDevice = null;
        }
    }

    //get the data upload it
    //if you don't have root, the On, Off commands won't do a thing - shouldn't break anything either
    private Runnable readAndUpload = new Runnable() {
        public void run() {

            try {
                uploader = new UploadHelper(getBaseContext());
                boolean connected = isConnected();
                if (connected && isOnline()) {

                    USBOn();
                    doReadAndUpload();
                    USBOff();
                } else {
                    USBOn();
                    USBOff();

                    if (!connected)
                        displayMessage("CGM connection error");
                    else
                        displayMessage("NET connection error");
                }

            } catch (Exception e) {
                // ignore... for now - simply prevent service and activity from
                // losing its shit.
                USBOn();
                USBOff();
                Log.e(TAG, "Unable to read from dexcom or upload", e);
            }
            mHandler.removeCallbacks(readAndUpload);
            mHandler.postDelayed(readAndUpload, nextUploadTimer);
        }
    };

    private void acquireSerialDevice() {
        mSerialDevice = UsbSerialProber.acquire(mUsbManager);
        if (mSerialDevice == null) {

            Log.i(TAG, "Unable to get the serial device, forcing USB PowerOn, and trying to get an updated USB Manager");

            try {
                USBPower.PowerOn();
            } catch (Exception e) {
                Log.w(TAG, "acquireSerialDevice: Unable to PowerOn", e);
            }

            try {
                Thread.sleep(2500);
            } catch (InterruptedException e) {
                Log.w(TAG, "Interrupted during sleep after Power On", e);
            }

            mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

            try {
                Thread.sleep(2500);
            } catch (InterruptedException e) {
                Log.w(TAG, "Interrupted during sleep after getting updated USB Manager", e);
            }

            mSerialDevice = UsbSerialProber.acquire(mUsbManager);
        }
    }


    protected void doReadAndUpload() {

        acquireSerialDevice();

        if (mSerialDevice != null) {
            startIoManager();
            try {
                mSerialDevice.open();
            } catch (Exception e) {
                Log.e(TAG, "Unable to open serial device", e);
            }

            //Go get the data
            DexcomReader dexcomReader = new DexcomReader(mSerialDevice);

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

            if (initialRead && prefs.getBoolean("InitialTwoDayUpload", true)) {
                // for first time on, read at least 2 days of data.  Each Dexcom read of EGV records
                // is limited to 4 pages which is equivalent to 12 hours of contiguous data, so
                // read 20 pages which is ~ 2.5 days.
                List<EGVRecord> data = new ArrayList<EGVRecord>();
                for(int i = 5; i >= 1; i--) {
                    dexcomReader.readFromReceiver(getBaseContext(), i);
                    Collections.addAll(data, dexcomReader.mRD);
                }
                EGVRecord[] dataRecords = new EGVRecord[data.size()];
                dataRecords = data.toArray(dataRecords);
                uploader.execute(dataRecords);
            } else {
                // just read most recent pages (consider only reading 1 page since only need latest value).
                dexcomReader.readFromReceiver(getBaseContext(), 1);
                uploader.execute(dexcomReader.mRD[dexcomReader.mRD.length - 1]);
            }

            initialRead = false;

            nextUploadTimer = getNextUploadTimer(dexcomReader);

            if (prefs.getBoolean("EnableWifiHack", false)) {
                doWifiHack();
            }
        }
    }

    private void doWifiHack() {
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            //Interesting case: location with lousy wifi
            //toggle it off to use cellular
            //toggle back on for next try
            public void run() {
                Status dataUp = uploader.getStatus();
                if (dataUp == Status.RUNNING) {
                    uploader.cancel(true);

                    if (wifiManager.isWifiEnabled()) {
                        wifiManager.setWifiEnabled(false);
                        try {
                            Thread.sleep(2500);
                        } catch (InterruptedException e) {
                            Log.e(TAG, "Sleep after setWifiEnabled(false) interrupted", e);
                        }
                        wifiManager.setWifiEnabled(true);
                        try {
                            Thread.sleep(2500);
                        } catch (InterruptedException e) {
                            Log.e(TAG, "Sleep after setWifiEnabled(true) interrupted", e);
                        }
                    }
                }

            }
        }, 22500);
    }

    private void USBOff() {
        if (mSerialDevice != null) {
            try {
                mSerialDevice.close();
            } catch (IOException e) {
                Log.e(TAG, "Unable to close serial device", e);
            }

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Sleep after close interrupted", e);
            }

            USBPower.PowerOff();
            Log.i(TAG, "USB OFF");
        } else {
            Log.i(TAG, "USBOff: Receiver Not Found");
            // displayMessage("Receiver Not Found");
            // android.os.Process.killProcess(android.os.Process.myPid());
        }

    }

    private void USBOn() {

        acquireSerialDevice();

        if (mSerialDevice != null) {
            try {
                mSerialDevice.close();
            } catch (IOException e) {
                Log.e(TAG, "Unable to close serial device", e);
            }

            USBPower.PowerOn();

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Sleep after close and PowerOn interrupted", e);
            }

            Log.i(TAG, "USB ON");
        } else {
            Log.i(TAG, "USBOn: Receiver Not Found");
            // displayMessage("Receiver Not Found");
            // android.os.Process.killProcess(android.os.Process.myPid());
        }

    }

    private boolean isConnected() {
        acquireSerialDevice();
        return mSerialDevice != null;
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

    private void displayMessage(String message) {
        Toast toast = Toast.makeText(getBaseContext(), message, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.CENTER, 0, 0);
        LinearLayout toastLayout = (LinearLayout) toast.getView();
        TextView toastTV = (TextView) toastLayout.getChildAt(0);
        if (toastTV != null) {
            toastTV.setTextSize(20);
            toastTV.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        }
        toast.show();

    }

    private void stopIoManager() {
        if (mSerialIoManager != null) {
            Log.i(TAG, "Stopping io manager...");
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
    }

    private void startIoManager() {
        if (mSerialDevice != null) {
            Log.i(TAG, "Starting io manager...");
            mSerialIoManager = new SerialInputOutputManager(mSerialDevice);
            // mExecutor.submit(mSerialIoManager);
        }
    }

    // Get the devices display time and compare with its last upload time to determine
    // when to poll for next reading, since readings are on 5 minute intervals
    @SuppressLint("NewApi")
	private long getNextUploadTimer(DexcomReader dexcomReader) {

        SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy hh:mm:ss a");
        Date dexcomTime = dexcomReader.getDisplayTime();
        Date androidTime = Calendar.getInstance().getTime();
        long dt = dexcomTime.getTime() - androidTime.getTime();

        if (Math.abs(dt) > FIVE_MINS_MS * 2) {
            // Use ContextText and bigText in case < 16 API
            NM = (NotificationManager)mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            Notification n = new Notification.Builder(mContext)
                    .setDefaults(Notification.DEFAULT_ALL)
                    .setContentTitle("Devices time mismatch!")
                    .setStyle(new Notification.BigTextStyle()
                            .bigText("Receiver: " + formatter.format(dexcomTime) +
                                    "\nAndroid:  " + formatter.format(androidTime)))
                    .setContentText("Check that devices times match")
                    .setTicker("Devices time mismatch!")
                    .setSmallIcon(R.drawable.ic_action_warning)
                    .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher))
                    .getNotification();
            NM.notify(R.string.app_name, n);
        }

        Date lastRecord;
        long timeSinceLastRecord = -1;
        try {
            lastRecord = formatter.parse(dexcomReader.mRD[dexcomReader.mRD.length - 1].displayTime);
            timeSinceLastRecord = dexcomTime.getTime() - lastRecord.getTime();
            Log.d(TAG, "The time since last record is: " + timeSinceLastRecord / 1000 + " secs");
        } catch (ParseException e) {
            Log.d(TAG, "Error parsing last record displayTime.");
            e.printStackTrace();
        }

        if (timeSinceLastRecord < 0) {
            displayMessage("Dexcom's time is less than current record time, possible time change.");
            nextUploadTimer = THREE_MINS_MS;
        }  else if (timeSinceLastRecord > FIVE_MINS_MS) {
            nextUploadTimer = THREE_MINS_MS;
            // TODO: consider making UI display "???" for SG records since likely to be out of range
        } else {
            nextUploadTimer = FIVE_MINS_MS - timeSinceLastRecord;
            nextUploadTimer += UPLOAD_OFFSET_MS;
            Log.d(TAG, "Setting next upload time to: " + nextUploadTimer / 1000 + " secs");
        }

        return  nextUploadTimer;
    }
}
