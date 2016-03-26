package com.nightscout.android.dexcom;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.text.DecimalFormat;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TextView.BufferType;

import com.nightscout.android.R;
import com.nightscout.android.eula.Eula;
import com.nightscout.android.eula.Eula.OnEulaAgreedTo;
import com.nightscout.android.medtronic.MedtronicCGMService;
import com.nightscout.android.medtronic.service.MedtronicCNLService;
import com.nightscout.android.medtronic.MedtronicConstants;
import com.nightscout.android.medtronic.service.TestService;
import com.nightscout.android.service.ServiceManager;
import com.nightscout.android.settings.SettingsActivity;
import com.nightscout.android.upload.MedtronicSensorRecord;
import com.nightscout.android.upload.Record;

/* Main activity for the DexcomG4Activity program */
public class DexcomG4Activity extends Activity implements OnSharedPreferenceChangeListener, OnEulaAgreedTo {
    private Logger log = (Logger) LoggerFactory.getLogger(DexcomG4Activity.class.getName());
    //CGMs supported
    public static final int DEXCOMG4 = 0;
    public static final int MEDTRONIC_CGM = 1;
    public static final int CNL_24 = 2;

    private static final String TAG = DexcomG4Activity.class.getSimpleName();
    private int cgmSelected = DEXCOMG4;
    private int calibrationSelected = MedtronicConstants.CALIBRATION_GLUCOMETER;

    private Handler mHandler = new Handler();

    private int maxRetries = 20;
    private int retryCount = 0;
    EditText input;

    private TextView mTitleTextView;
    private TextView mDumpTextView;
    private Button b1;
    private Button b4;
    private TextView display;
    private Menu menu = null;
    private Intent service = null;
    private ServiceManager cgmService; // > service
    private int msgsDisplayed = 0;
    public static int batLevel = 0;
    BatteryReceiver mArrow;
    IBinder bService = null;
    Intent batteryReceiver;
    Messenger mService = null;
    boolean mIsBound;
    boolean keepServiceAlive = true;
    Boolean mHandlerActive = false;
    Object mHandlerActiveLock = new Object();
    Boolean usbAllowedPermission = false;
    final Messenger mMessenger = new Messenger(new IncomingHandler());
    ActivityManager manager = null;
    final Context ctx = this;
    SharedPreferences settings = null;
    SharedPreferences prefs = null;
    private static final boolean ISDEBUG = true;

    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MedtronicConstants.MSG_MEDTRONIC_CGM_MESSAGE_RECEIVED:
                /*if (msgsDisplayed > 8){
            		display.setText("", BufferType.EDITABLE);
            		msgsDisplayed = 0;
            	}*/
                    Log.i("MessageReceived", msg.getData().getString("data") + "\n");
                    if (ISDEBUG) {
                        display.setText(display.getText() + "Medtronic CGM Message: " + msg.getData().getString("data") + "\n", BufferType.EDITABLE);
                        msgsDisplayed++;
                    }
                    mHandler.removeCallbacks(updateDataView);
                    mHandler.post(updateDataView);
                    break;
                case MedtronicConstants.MSG_MEDTRONIC_CGM_CLEAR_DISPLAY:
                    if (ISDEBUG) {
                        display.setText("", BufferType.EDITABLE);
                        msgsDisplayed = 0;
                    }
                    break;
                case MedtronicConstants.MSG_MEDTRONIC_CGM_NO_PERMISSION:
                    usbAllowedPermission = false;
                    mHandler.removeCallbacks(updateDataView);
                    mHandler.post(updateDataView);
                    break;
                case MedtronicConstants.MSG_MEDTRONIC_GLUCMEASURE_DETECTED:
                    showUseCalibConfirmation(msg.getData().getFloat("data"), msg.getData().getBoolean("calibrating"), msg.getData().getBoolean("isCalFactorFromPump"));
                    break;
                case MedtronicConstants.MSG_MEDTRONIC_CGM_USB_GRANTED:
                    usbAllowedPermission = true;
                    mHandler.removeCallbacks(updateDataView);
                    mHandler.post(updateDataView);
                    break;
                case MedtronicConstants.MSG_MEDTRONIC_CGM_ERROR_RECEIVED:
                    Log.e("MessageReceived", msg.getData().getString("data") + "\n");
                    if (ISDEBUG) {
                        if (msgsDisplayed > 8) {
                            display.setText("", BufferType.EDITABLE);
                            msgsDisplayed = 0;
                        }
                        String sText = display.getText().toString();
                        String sError = msg.getData().getString("data");
                        if (!(sText.indexOf(sError) >= 0)) {
                            display.setText(display.getText() + "Medtronic CGM Message: " + sError + "\n", BufferType.EDITABLE);
                            msgsDisplayed++;
                        }
                    }
                    break;
                case MedtronicConstants.MSG_MEDTRONIC_CALIBRATION_DONE:
                    Log.e("MessageReceived", MedtronicConstants.MSG_MEDTRONIC_CALIBRATION_DONE + "\n");
                    mHandler.removeCallbacks(updateDataView);
                    mHandler.post(updateDataView);
                    break;
                case MedtronicConstants.MSG_REFRESH_DB_CONNECTION:
                    if (mService != null) {
                        try {
                            Message message = Message.obtain(null, MedtronicConstants.MSG_REFRESH_DB_CONNECTION);
                            msg.replyTo = mMessenger;
                            mService.send(message);
                        } catch (RemoteException e) {
                            StringBuffer sb1 = new StringBuffer("");
                            sb1.append("EXCEPTION!!!!!! " + e.getMessage() + " " + e.getCause());
                            for (StackTraceElement st : e.getStackTrace()) {
                                sb1.append(st.toString()).append("\n");
                            }
                            Log.e("ApprovGlucValReceived", "Error approving gluc. value \n " + sb1.toString());
                            if (ISDEBUG) {
                                display.setText(display.getText() + "Error approving gluc. value\n", BufferType.EDITABLE);
                            }
                        }
                    }
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    private void showUseCalibConfirmation(final float num, final boolean calibrate, final boolean isCalFactorFromPump) {
        AlertDialog.Builder alert = new AlertDialog.Builder(ctx);
        alert.setTitle("Calibration Detected!!!");
        alert.setMessage("Do you want to use the received glucometer value for calibration?");

        alert.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                if (mService != null) {
                    try {
                        Message msg = Message.obtain(null, MedtronicConstants.MSG_MEDTRONIC_GLUCMEASURE_APPROVED);
                        Bundle b = new Bundle();
                        b.putBoolean("approved", true);
                        b.putFloat("data", num);
                        b.putBoolean("calibrating", calibrate);
                        b.putBoolean("isCalFactorFromPump", isCalFactorFromPump);
                        msg.setData(b);
                        msg.replyTo = mMessenger;
                        mService.send(msg);
                    } catch (RemoteException e) {
                        StringBuffer sb1 = new StringBuffer("");
                        sb1.append("EXCEPTION!!!!!! " + e.getMessage() + " " + e.getCause());
                        for (StackTraceElement st : e.getStackTrace()) {
                            sb1.append(st.toString()).append("\n");
                        }
                        Log.e("ApprovGlucValReceived", "Error approving gluc. value \n " + sb1.toString());
                        if (ISDEBUG) {
                            display.setText(display.getText() + "Error approving gluc. value\n", BufferType.EDITABLE);
                        }
                    }
                }
            }
        });

        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                if (mService != null) {
                    try {
                        Message msg = Message.obtain(null, MedtronicConstants.MSG_MEDTRONIC_GLUCMEASURE_APPROVED);
                        Bundle b = new Bundle();

                        b.putBoolean("approved", false);

                        msg.setData(b);
                        msg.replyTo = mMessenger;
                        mService.send(msg);
                    } catch (RemoteException e) {
                        StringBuffer sb1 = new StringBuffer("");
                        sb1.append("EXCEPTION!!!!!! " + e.getMessage() + " " + e.getCause());
                        for (StackTraceElement st : e.getStackTrace()) {
                            sb1.append(st.toString()).append("\n");
                        }
                        Log.e("ApprovGlucValReceived", "Error approving gluc. value \n " + sb1.toString());
                        if (ISDEBUG) {
                            display.setText(display.getText() + "Error approving gluc. value\n", BufferType.EDITABLE);
                        }
                    }
                }
            }
        });

        alert.show();
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            bService = service;
            mService = new Messenger(service);
            try {
                Message msg = Message.obtain(null, MedtronicConstants.MSG_REGISTER_CLIENT);
                msg.replyTo = mMessenger;
                mService.send(msg);
            } catch (RemoteException e) {

                StringBuffer sb1 = new StringBuffer("");
                sb1.append("EXCEPTION!!!!!! " + e.getMessage() + " " + e.getCause());
                for (StackTraceElement st : e.getStackTrace()) {
                    sb1.append(st.toString()).append("\n");
                }
                Log.e("CGM_ONSERVICECONNECT", "Error Registering Client Service Connection\n" + sb1.toString());
                if (ISDEBUG) {
                    display.setText(display.getText() + "Error Registering Client Service Connection\n", BufferType.EDITABLE);
                }
                // In this case the service has crashed before we could even do anything with it
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been unexpectedly disconnected - process crashed.
            mService = null;
            bService = null;
            Log.i("CGM_ONSERVICEDISCONNECT", "Service Disconnected\n");
            if (ISDEBUG) {
                display.setText(display.getText() + "Service Disconnected\n", BufferType.EDITABLE);
            }
        }
    };


    //All I'm really doing here is creating a simple activity to launch and maintain the service
    private Runnable updateDataView = new Runnable() {
        public void run() {
            synchronized (mHandlerActiveLock) {
                if (!mHandlerActive)
                    return;
                if (!isMyServiceRunning()) {
                    if (retryCount < maxRetries) {
                        stopCGMServices();
                        startCGMServices();
                        mTitleTextView.setTextColor(Color.YELLOW);
                        mTitleTextView.setText("Connecting...");
                        Log.i(TAG, "Starting service " + retryCount + "/" + maxRetries);
                        ++retryCount;
                    } else {
                        mHandler.removeCallbacks(updateDataView);
                        Log.i(TAG, "Unable to restart service, trying to recreate the activity");
                        //recreate();
                    }
                } else {
                    retryCount = 0;
                    if (usbAllowedPermission) {
                        mTitleTextView.setTextColor(Color.GREEN);
                        mTitleTextView.setText("CGM Service Started");
                        b1.setText("Stop Uploading CGM Data");
                        Record auxRecord = DexcomG4Activity.this.loadClassFile(new File(getBaseContext().getFilesDir(), "save.bin"));
                        long calDate = -1;
                        try {
                            if (settings.contains("lastCalibrationDate")) {
                                calDate = settings.getLong("lastCalibrationDate", -1);
                            }
                            SharedPreferences prefs = PreferenceManager
                                    .getDefaultSharedPreferences(getBaseContext());


                            DecimalFormat df = null;
                            if (prefs.getBoolean("mmolDecimals", false))
                                df = new DecimalFormat("#.##");
                            else
                                df = new DecimalFormat("#.#");
                            if (cgmSelected == MEDTRONIC_CGM && auxRecord instanceof MedtronicSensorRecord && auxRecord != null) {

                                MedtronicSensorRecord record = (MedtronicSensorRecord) auxRecord;

                                if (prefs.getBoolean("mmolxl", false)) {
                                    Float fBgValue = null;
                                    try {
                                        fBgValue = (float) Integer.parseInt(record.bGValue);
                                        log.info("mmolxl true --> " + record.bGValue);
                                        record.bGValue = df.format(fBgValue / 18f);
                                        log.info("mmolxl/18 true --> " + record.bGValue);
                                    } catch (Exception e) {

                                    }
                                } else
                                    log.info("mmolxl false --> " + record.bGValue);
                                boolean isCalibrating = record.isCalibrating;
                                String calib = "---";
                                if (isCalibrating) {
                                    calib = MedtronicConstants.CALIBRATING_STR;
                                } else {
                                    calib = MedtronicConstants.getCalibrationStrValue(record.calibrationStatus);
                                }
                                calib += "\nlast cal. ";
                                String tail = " min. ago";
                                int lastCal = 0;
                                if (calDate > 0) {
                                    lastCal = (int) ((System.currentTimeMillis() - calDate) / 60000);
                                    if (lastCal >= 60) {
                                        lastCal = lastCal / 60;
                                        tail = " hour(s) ago";
                                    }
                                }
                                calib += "" + lastCal + tail;
                                if (prefs.getBoolean("isWarmingUp", false)) {
                                    calib = "";
                                    record.bGValue = "W_Up";
                                    record.trendArrow = "---";
                                }
                                mDumpTextView.setTextColor(Color.WHITE);
                                if (record.displayDateTime == 0) {
                                    mDumpTextView.setText("\n" + record.displayTime + "\n" + record.bGValue + "  " + record.trendArrow + "\n" + calib + "\n");
                                } else
                                    mDumpTextView.setText("\n" + (System.currentTimeMillis() - record.displayDateTime) / 60000 + " min. ago\n" + record.bGValue + "  " + record.trendArrow + "\n" + calib + "\n");

                            } else if (auxRecord instanceof EGVRecord) {
                                EGVRecord record = (EGVRecord) auxRecord;
                                if (prefs.getBoolean("mmolxl", false)) {
                                    Float fBgValue = null;
                                    try {
                                        fBgValue = (float) Integer.parseInt(record.bGValue);
                                        log.info("mmolxl true --> " + record.bGValue);
                                        record.bGValue = df.format(fBgValue / 18f);
                                        log.info("mmolxl/18 true --> " + record.bGValue);
                                    } catch (Exception e) {

                                    }
                                } else
                                    log.info("mmolxl false --> " + record.bGValue);
                                mDumpTextView.setTextColor(Color.WHITE);
                                mDumpTextView.setText("\n" + record.displayTime + "\n" + record.bGValue + "  " + record.trendArrow + "\n");
                            } else {
                                mDumpTextView.setTextColor(Color.WHITE);
                                if (auxRecord == null || auxRecord.displayTime == null)
                                    mDumpTextView.setText("\n---\n---\n---\n");
                                else
                                    mDumpTextView.setText("\n" + auxRecord.displayTime + "\n---\n---\n");
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                    } else {
                        b1.setText("Start Uploading CGM Data");
                        mTitleTextView.setTextColor(Color.RED);
                        mTitleTextView.setText("CGM Service Stopped");
                    }

                }

                mHandler.removeCallbacks(updateDataView);
                mHandler.postDelayed(updateDataView, 60000);
            }
        }
    };


    private class BatteryReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context arg0, Intent arg1) {
            if (arg1.getAction().equalsIgnoreCase(Intent.ACTION_BATTERY_LOW)
                    || arg1.getAction().equalsIgnoreCase(Intent.ACTION_BATTERY_CHANGED)
                    || arg1.getAction().equalsIgnoreCase(Intent.ACTION_BATTERY_OKAY)) {
                Log.i("BatteryReceived", "BatteryReceived");
                batLevel = arg1.getIntExtra("level", 0);
            }
        }
    }

    //Look for and launch the service, display status to user
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate called");
        keepServiceAlive = Eula.show(this);
        super.onCreate(savedInstanceState);

        if (android.os.Build.VERSION.SDK_INT > 9) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }

        this.settings = getBaseContext().getSharedPreferences(
                MedtronicConstants.PREFS_NAME, 0);
        PreferenceManager.getDefaultSharedPreferences(getBaseContext()).registerOnSharedPreferenceChangeListener(this);
        prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        if (prefs.contains("monitor_type")) {
            String type = prefs.getString("monitor_type", "1");
            if ("3".equalsIgnoreCase(type)) {
                cgmSelected = CNL_24;
            } else if ("2".equalsIgnoreCase(type)) {
                cgmSelected = MEDTRONIC_CGM;
                if (prefs.contains("calibrationType")) {
                    type = prefs.getString("calibrationType", "3");
                    if ("3".equalsIgnoreCase(type))
                        calibrationSelected = MedtronicConstants.CALIBRATION_MANUAL;
                    else if ("2".equalsIgnoreCase(type)) {
                        calibrationSelected = MedtronicConstants.CALIBRATION_SENSOR;
                    } else
                        calibrationSelected = MedtronicConstants.CALIBRATION_GLUCOMETER;
                }
            } else {
                cgmSelected = DEXCOMG4;
            }
        }
        mArrow = new BatteryReceiver();
        IntentFilter mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(Intent.ACTION_BATTERY_LOW);
        mIntentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
        mIntentFilter.addAction(Intent.ACTION_BATTERY_OKAY);
        batteryReceiver = registerReceiver(mArrow, mIntentFilter);
        setContentView(R.layout.adb);
        manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        mTitleTextView = (TextView) findViewById(R.id.demoTitle);
        mDumpTextView = (TextView) findViewById(R.id.demoText);

        LinearLayout lnr = (LinearLayout) findViewById(R.id.container);
        LinearLayout lnr2 = new LinearLayout(this);
        LinearLayout lnr3 = new LinearLayout(this);
        lnr3.setOrientation(LinearLayout.HORIZONTAL);
        b1 = new Button(this);

        if (!prefs.getBoolean("IUNDERSTAND", false)) {
            stopCGMServices();
        } else {
            if (isMyServiceRunning() && cgmSelected == MEDTRONIC_CGM) {
                doBindService();
            }
            mHandler.post(updateDataView);
            mHandlerActive = true;
        }


        mTitleTextView.setTextColor(Color.YELLOW);
        mTitleTextView.setText("CGM Service Pending");

        b1.setText("Stop Uploading CGM Data");
        lnr.addView(b1);
        lnr2.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        lnr3.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        Button b2 = new Button(this);
        b2.setText("Clear Log");
        b2.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 1.0f));
        b4 = new Button(this);
        b4.setText("Test CNL");
        b4.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 1.0f));
        lnr3.addView(b4);
        if (menu != null) {
            if (calibrationSelected == MedtronicConstants.CALIBRATION_MANUAL) {
                menu.getItem(3).setVisible(false);
                menu.getItem(4).setVisible(true);
            } else {
                menu.getItem(3).setVisible(false);
                menu.getItem(4).setVisible(false);
            }
        }
        if (ISDEBUG) {
            lnr3.addView(b2);
        }
        lnr.addView(lnr3);
        lnr.addView(lnr2);
        display = new TextView(this);
        if (ISDEBUG) {
            display.setText("", BufferType.EDITABLE);
            display.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            display.setKeyListener(null);
            display.setBackgroundColor(Color.BLACK);
            display.setTextColor(Color.WHITE);
            display.setMovementMethod(new ScrollingMovementMethod());
            display.setMaxLines(10);

            lnr2.addView(display);
        }
        b2.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                display.setText("", BufferType.EDITABLE);
                display.setKeyListener(null);
                msgsDisplayed = 0;
            }
        });

        b4.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                display.setText("Yolo swag", BufferType.EDITABLE);
                display.setKeyListener(null);
                if( !cgmService.isRunning() ) {
                    cgmService.start();
                } else {
                    cgmService.stop();
                }
            }
        });

        b1.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                synchronized (mHandlerActiveLock) {
                    if (b1.getText() == "Stop Uploading CGM Data") {
                        mHandlerActive = false;
                        mHandler.removeCallbacks(updateDataView);
                        keepServiceAlive = false;
                        stopCGMServices();
                        b1.setText("Start Uploading CGM Data");
                        mTitleTextView.setTextColor(Color.RED);
                        mTitleTextView.setText("CGM Service Stopped");
                        finish();
                    } else {
                        mHandlerActive = false;
                        mHandler.removeCallbacks(updateDataView);
                        mHandler.post(updateDataView);
                        if (!usbAllowedPermission)
                            if (mService == null && bService != null) {
                                mService = new Messenger(bService);
                            }
                        if (mService != null) {
                            try {
                                Message msg = Message.obtain(null, MedtronicConstants.MSG_MEDTRONIC_CGM_REQUEST_PERMISSION, 0, 0);
                                msg.replyTo = mMessenger;
                                mService.send(msg);
                            } catch (RemoteException e) {
                                mService = null;
                            }
                        }
                        mHandlerActive = true;
                        b1.setText("Stop Uploading CGM Data");
                    }
                }

            }
        });

    }

    @Override
    protected void onPause() {
        log.info("ON PAUSE!");
        super.onPause();

    }

    @Override
    protected void onResume() {
        log.info("ON RESUME!");
        super.onResume();
        // Refresh the status
        try {
            Record auxRecord = DexcomG4Activity.this.loadClassFile(new File(
                    getBaseContext().getFilesDir(), "save.bin"));
            long calDate = -1;
            if (settings.contains("lastCalibrationDate")) {
                calDate = settings.getLong("lastCalibrationDate", -1);
            }
            SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(getBaseContext());

            DecimalFormat df = null;
            if (prefs.getBoolean("mmolDecimals", false))
                df = new DecimalFormat("#.##");
            else
                df = new DecimalFormat("#.#");
            if (cgmSelected == MEDTRONIC_CGM
                    && auxRecord instanceof MedtronicSensorRecord
                    && auxRecord != null) {

                MedtronicSensorRecord record = (MedtronicSensorRecord) auxRecord;

                if (prefs.getBoolean("mmolxl", false)) {
                    Float fBgValue = null;
                    try {
                        fBgValue = (float) Integer.parseInt(record.bGValue);
                        log.info("mmolxl true --> " + record.bGValue);
                        record.bGValue = df.format(fBgValue / 18f);
                        log.info("mmolxl/18 true --> " + record.bGValue);
                    } catch (Exception e) {

                    }
                } else
                    log.info("mmolxl false --> " + record.bGValue);
                boolean isCalibrating = record.isCalibrating;
                String calib = "---";
                if (isCalibrating) {
                    calib = MedtronicConstants.CALIBRATING_STR;
                } else {
                    calib = MedtronicConstants
                            .getCalibrationStrValue(record.calibrationStatus);
                }
                calib += "\nlast cal. ";
                String tail = " min. ago";
                int lastCal = 0;
                if (calDate > 0) {
                    lastCal = (int) ((System.currentTimeMillis() - calDate) / 60000);
                    if (lastCal >= 60) {
                        lastCal = lastCal / 60;
                        tail = " hour(s) ago";
                    }
                }
                calib += "" + lastCal + tail;
                if (prefs.getBoolean("isWarmingUp", false)) {
                    calib = "";
                    record.bGValue = "W_Up";
                    record.trendArrow = "---";
                }
                mDumpTextView.setTextColor(Color.WHITE);
                if (record.displayDateTime == 0) {
                    mDumpTextView.setText("\n" + record.displayTime + "\n"
                            + record.bGValue + "  " + record.trendArrow + "\n"
                            + calib + "\n");
                } else
                    mDumpTextView
                            .setText("\n"
                                    + (System.currentTimeMillis() - record.displayDateTime)
                                    / 60000 + " min. ago\n" + record.bGValue
                                    + "  " + record.trendArrow + "\n" + calib
                                    + "\n");

            } else if (auxRecord instanceof EGVRecord) {
                EGVRecord record = (EGVRecord) auxRecord;
                if (prefs.getBoolean("mmolxl", false)) {
                    Float fBgValue = null;
                    try {
                        fBgValue = (float) Integer.parseInt(record.bGValue);
                        log.info("mmolxl true --> " + record.bGValue);
                        record.bGValue = df.format(fBgValue / 18f);
                        log.info("mmolxl/18 true --> " + record.bGValue);
                    } catch (Exception e) {

                    }
                } else
                    log.info("mmolxl false --> " + record.bGValue);
                mDumpTextView.setTextColor(Color.WHITE);
                mDumpTextView.setText("\n" + record.displayTime + "\n"
                        + record.bGValue + "  " + record.trendArrow + "\n");
            } else {
                mDumpTextView.setTextColor(Color.WHITE);
                if (auxRecord == null || auxRecord.displayTime == null)
                    mDumpTextView.setText("\n---\n---\n---\n");
                else
                    mDumpTextView.setText("\n" + auxRecord.displayTime
                            + "\n---\n---\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    //Check to see if service is running
    private boolean isMyServiceRunning() {

        for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (isServiceAlive(service.service.getClassName()))
                return true;
        }
        return false;
    }

    //Deserialize the EGVRecord (most recent) value
    public Record loadClassFile(File f) {
        ObjectInputStream ois = null;
        try {
            ois = new ObjectInputStream(new FileInputStream(f));
            Object o = ois.readObject();
            ois.close();
            return (Record) o;
        } catch (Exception ex) {
            Log.w(TAG, " unable to loadEGVRecord");
            try {
                if (ois != null)
                    ois.close();
            } catch (Exception e) {
                Log.e(TAG, " Error closing ObjectInputStream");
            }
        }
        return new Record();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        this.menu = menu;
        inflater.inflate(R.menu.menu, menu);
        if (cgmSelected == MEDTRONIC_CGM) {
            SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(getBaseContext());
            if (prefs.contains("calibrationType")) {
                String type = prefs.getString("calibrationType", "3");
                if ("3".equalsIgnoreCase(type))
                    menu.getItem(2).setVisible(false);
                else if ("2".equalsIgnoreCase(type)) {
                    menu.getItem(2).setVisible(true);
                } else
                    menu.getItem(2).setVisible(false);
            }
            menu.getItem(1).setVisible(true);
            if (calibrationSelected == MedtronicConstants.CALIBRATION_MANUAL) {
                menu.getItem(3).setVisible(false);
                menu.getItem(4).setVisible(true);
            } else {
                menu.getItem(3).setVisible(false);
                menu.getItem(4).setVisible(false);
            }
        } else {
            menu.getItem(2).setVisible(false);
            menu.getItem(1).setVisible(false);
            menu.getItem(3).setVisible(false);
            menu.getItem(4).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.menu_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                break;
            case R.id.refreshCalFactor:
                if (mService == null && bService != null) {
                    mService = new Messenger(bService);
                }
                if (mService != null) {
                    try {
                        Message msg = Message.obtain(null, MedtronicConstants.MSG_MEDTRONIC_SEND_GET_SENSORCAL_FACTOR);
                        msg.replyTo = mMessenger;
                        mService.send(msg);
                    } catch (RemoteException e) {
                        StringBuffer sb1 = new StringBuffer("");
                        sb1.append("EXCEPTION!!!!!! " + e.getMessage() + " " + e.getCause());
                        for (StackTraceElement st : e.getStackTrace()) {
                            sb1.append(st.toString()).append("\n");
                        }
                        //log.error("medtronicManualCalibration", "Error sending Manual Calibration\n "+sb1.toString());
                        if (ISDEBUG) {
                            display.setText(display.getText() + "Error sending get sensor Calibration factor\n", BufferType.EDITABLE);
                        }
                        // In this case the service has crashed before we could even do anything with it
                    }
                }

                break;
            case R.id.readPumpInfo:
                if (mService == null && bService != null) {
                    mService = new Messenger(bService);
                }
                if (mService != null) {
                    try {
                        Message msg = Message.obtain(null, MedtronicConstants.MSG_MEDTRONIC_SEND_GET_PUMP_INFO);
                        msg.replyTo = mMessenger;
                        mService.send(msg);
                    } catch (RemoteException e) {
                        StringBuffer sb1 = new StringBuffer("");
                        sb1.append("EXCEPTION!!!!!! " + e.getMessage() + " " + e.getCause());
                        for (StackTraceElement st : e.getStackTrace()) {
                            sb1.append(st.toString()).append("\n");
                        }
                        //log.error("medtronicManualCalibration", "Error sending Manual Calibration\n "+sb1.toString());
                        if (ISDEBUG) {
                            display.setText(display.getText() + "Error sending get pump info\n", BufferType.EDITABLE);
                        }
                        // In this case the service has crashed before we could even do anything with it
                    }
                }
                break;
            case R.id.calibMan: {

                log.debug("Manual Calibration");
                AlertDialog.Builder alert = new AlertDialog.Builder(ctx);
                alert.setTitle("Manual Calibration");
                alert.setMessage("Insert your glucose value in mg/dl (only natural numbers)");

                if (prefs.getBoolean("mmolxl", false)) {
                    alert.setMessage("Insert your glucose value in mmol/l (only 2 decimals)");
                    log.debug("mmol/l");
                }


                // Set an EditText view to get user input
                input = new EditText(ctx);
                input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                alert.setView(input);

                alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        String value = input.getText().toString();
                        log.debug("Manual Calibration send " + value);
                        if (mService == null && bService != null) {
                            mService = new Messenger(bService);
                        }
                        if (mService != null) {
                            try {
                                Message msg = Message.obtain(null, MedtronicConstants.MSG_MEDTRONIC_SEND_MANUAL_CALIB_VALUE);
                                Bundle b = new Bundle();
                                b.putString("sgv", value);
                                prefs.edit().putString("manual_sgv", value).commit();
                                msg.setData(b);
                                msg.replyTo = mMessenger;
                                mService.send(msg);
                            } catch (RemoteException e) {
                                StringBuffer sb1 = new StringBuffer("");
                                sb1.append("EXCEPTION!!!!!! " + e.getMessage() + " " + e.getCause());
                                for (StackTraceElement st : e.getStackTrace()) {
                                    sb1.append(st.toString()).append("\n");
                                }
                                Log.e("medtronicManualCal", "Error sending Manual Calibration\n " + sb1.toString());
                                if (ISDEBUG) {
                                    display.setText(display.getText() + "Error sending Manual Calibration\n", BufferType.EDITABLE);
                                }
                                // In this case the service has crashed before we could even do anything with it
                            }
                        }
                    }
                });

                alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // Canceled.
                    }
                });

                alert.show();
            }
            break;
            case R.id.instantCalib: {
                log.debug("Instant Calibration ");
                AlertDialog.Builder alert2 = new AlertDialog.Builder(ctx);

                alert2.setTitle("Instant Calibration");
                alert2.setMessage("Insert pump value in mg/dl (only natural numbers)");
                prefs = PreferenceManager
                        .getDefaultSharedPreferences(getBaseContext());
                if (prefs.getBoolean("mmolxl", false)) {
                    alert2.setMessage("Insert pump value in mmol/l (only 2 decimals)");
                    log.debug("Instant Calibration mmol/l");
                }
                // Set an EditText view to get user input
                input = new EditText(ctx);
                input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

                float num = settings.getFloat("lastGlucometerValue", (float) -1f);
                if (num <= 0)
                    num = 0;

                if (prefs.getBoolean("mmolxl", false)) {
                    DecimalFormat df = new DecimalFormat("#.#");
                    input.setText(df.format((num / 18.0)));
                } else
                    input.setText("" + (int) num);
                alert2.setView(input);

                alert2.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        String value = input.getText().toString();
                        log.debug("Instant Calibration send " + value);
                        if (mService != null) {
                            try {
                                Message msg = Message.obtain(null, MedtronicConstants.MSG_MEDTRONIC_SEND_INSTANT_CALIB_VALUE);
                                Bundle b = new Bundle();
                                b.putString("sgv", value);
                                prefs.edit().putString("instant_sgv", value).commit();
                                msg.setData(b);
                                msg.replyTo = mMessenger;
                                mService.send(msg);
                            } catch (RemoteException e) {
                                StringBuffer sb1 = new StringBuffer("");
                                sb1.append("EXCEPTION!!!!!! " + e.getMessage() + " " + e.getCause());
                                for (StackTraceElement st : e.getStackTrace()) {
                                    sb1.append(st.toString()).append("\n");
                                }
                                Log.e("medtronicManualCal", "Error sending Instant Calibration\n " + sb1.toString());
                                if (ISDEBUG) {
                                    display.setText(display.getText() + "Error sending Instant Calibration\n", BufferType.EDITABLE);
                                }

                            }
                        }
                    }
                });

                alert2.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // Canceled.
                    }
                });

                alert2.show();
            }
            break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void startCGMServices() {
        Log.d("DexcomActivity", "Starting service for CGM: " + cgmSelected);
        switch (cgmSelected) {
            case MEDTRONIC_CGM:
                if (service != null || isMyServiceRunning())
                    stopCGMServices();
                doBindService();
                return;
            case CNL_24:
                display.setText("Kicking lyrics right through your brain.", BufferType.EDITABLE);
                Log.d("DexcomActivity", "Starting Medtronic CNL service");
                this.cgmService = new ServiceManager(this, MedtronicCNLService.class, new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        Log.d( TAG, "Got message from Service." );
                        switch (msg.what) {
                            case MedtronicCNLService.MSG_STATUS:
                                display.setText( msg.obj.toString(), BufferType.EDITABLE );
                                break;
                            case TestService.MSG_COUNTER:
                                // Receive counter value from service 1
                                display.setText("Counter @ Service1:" + msg.arg1, BufferType.EDITABLE);
                                break;
                            case TestService.MSG_STATUS:
                                display.setText( msg.obj.toString(), BufferType.EDITABLE );
                                break;
                            default:
                                super.handleMessage(msg);
                        }
                    }
                });
                break;
            default:
                startService(new Intent(DexcomG4Activity.this, DexcomG4Service.class));
        }
        return;
    }

    private void stopCGMServices() {
        switch (cgmSelected) {
            case MEDTRONIC_CGM:
                if (service != null) {
                    doUnbindService();
                    killService();
                }
                return;
            case CNL_24:
                stopService(new Intent(DexcomG4Activity.this, MedtronicCNLService.class));
                break;
            default:
                stopService(new Intent(DexcomG4Activity.this, DexcomG4Service.class));
        }
        return;
    }

    private boolean isServiceAlive(String name) {
        switch (cgmSelected) {
            case MEDTRONIC_CGM:
                return MedtronicCGMService.class.getName().equals(name);
            case CNL_24:
                return MedtronicCNLService.class.getName().equals(name);
            default:
                return DexcomG4Service.class.getName().equals(name);
        }
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy called");
        log.info("onDestroy called");
        PreferenceManager.getDefaultSharedPreferences(getBaseContext()).unregisterOnSharedPreferenceChangeListener(this);
        unregisterReceiver(mArrow);
        synchronized (mHandlerActiveLock) {
            mHandler.removeCallbacks(updateDataView);
            doUnbindService();
            if (keepServiceAlive) {
                killService();
                service = new Intent(this, MedtronicCGMService.class);
                startService(service);
            }
            mHandlerActive = false;
            SharedPreferences.Editor editor = getBaseContext().getSharedPreferences(MedtronicConstants.PREFS_NAME, 0).edit();
            editor.putLong("lastDestroy", System.currentTimeMillis());
            editor.commit();
            super.onDestroy();
        }
    }

    void doBindService() {
        if ((service != null && isMyServiceRunning()) || mIsBound)
            stopCGMServices();
        service = new Intent(this, MedtronicCGMService.class);
        bindService(service, mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    void doUnbindService() {
        if (mIsBound) {
            // If we have received the service, and hence registered with it, then now is the time to unregister.
            if (mService == null && bService != null) {
                mService = new Messenger(bService);
            }
            if (mService != null) {
                try {
                    Message msg = Message.obtain(null, MedtronicConstants.MSG_UNREGISTER_CLIENT);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                } catch (RemoteException e) {
                    // There is nothing special we need to do if the service has crashed.
                }
            }
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
        }
    }

    protected void killService() {
        if (service != null) {
            stopService(service);
            service = null;
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                          String key) {
        try {
            if (sharedPreferences.contains("monitor_type")) {
                String type = sharedPreferences.getString("monitor_type", "1");
                if ("3".equalsIgnoreCase(type)) {
                    cgmSelected = CNL_24;
                } else if ("2".equalsIgnoreCase(type)) {
                    cgmSelected = MEDTRONIC_CGM;
                    if (sharedPreferences.contains("calibrationType")) {
                        type = sharedPreferences.getString("calibrationType", "3");
                        if ("3".equalsIgnoreCase(type))
                            calibrationSelected = MedtronicConstants.CALIBRATION_MANUAL;
                        else if ("2".equalsIgnoreCase(type)) {
                            calibrationSelected = MedtronicConstants.CALIBRATION_SENSOR;
                        } else
                            calibrationSelected = MedtronicConstants.CALIBRATION_GLUCOMETER;
                    }
                } else {
                    cgmSelected = DEXCOMG4;
                }
	         	/*if (calibrationSelected == MedtronicConstants.CALIBRATION_MANUAL)
	         		b4.setVisibility(View.VISIBLE);
	             else
	             	b4.setVisibility(View.GONE);*/
                if (menu != null) {
                    if (calibrationSelected == MedtronicConstants.CALIBRATION_MANUAL) {
                        menu.getItem(3).setVisible(false);
                        menu.getItem(4).setVisible(true);
                    } else {
                        menu.getItem(3).setVisible(false);
                        menu.getItem(4).setVisible(false);
                    }
                }
            }
            if (cgmSelected == MEDTRONIC_CGM) {
                SharedPreferences prefs = PreferenceManager
                        .getDefaultSharedPreferences(getBaseContext());
                if (prefs.contains("calibrationType")) {
                    String type = prefs.getString("calibrationType", "3");
                    if ("3".equalsIgnoreCase(type)) {
                        menu.getItem(2).setVisible(false);
                        if (menu != null) {
                            menu.getItem(3).setVisible(false);
                            menu.getItem(4).setVisible(true);
                        }
                    } else if ("2".equalsIgnoreCase(type)) {
                        menu.getItem(2).setVisible(true);
                        if (menu != null) {
                            menu.getItem(3).setVisible(false);
                            menu.getItem(4).setVisible(false);
                        }
                    } else {
                        menu.getItem(2).setVisible(false);
                        if (menu != null) {
                            menu.getItem(3).setVisible(false);
                            menu.getItem(4).setVisible(false);
                        }
                    }
                }
                menu.getItem(1).setVisible(true);
            } else {
                menu.getItem(2).setVisible(false);
                menu.getItem(1).setVisible(false);
            }

            if (key.equals("monitor_type")) {
                stopService(new Intent(DexcomG4Activity.this, DexcomG4Service.class));
                SharedPreferences settings = getSharedPreferences(MedtronicConstants.PREFS_NAME, 0);
                SharedPreferences.Editor editor = settings.edit();
                String type = prefs.getString("monitor_type", "1");
                if ("1".equalsIgnoreCase(type)) {
                    editor.remove("lastGlucometerMessage");
                    editor.remove("previousValue");
                    editor.remove("expectedSensorSortNumber");
                    editor.remove("knownDevices");
                    editor.remove("isCalibrating");
                    editor.remove("lastGlucometerMessage");
                    editor.remove("previousValue");
                    editor.remove("lastGlucometerValue");
                    editor.remove("lastGlucometerDate");
                    editor.remove("expectedSensorSortNumberForCalibration0");
                    editor.remove("expectedSensorSortNumberForCalibration1");
                    editor.remove("lastPumpAwake");
                    editor.commit();
                }
                if (!sharedPreferences.getBoolean("IUNDERSTAND", false)) {
                    synchronized (mHandlerActiveLock) {
                        mHandler.removeCallbacks(updateDataView);
                        mHandlerActive = false;
                    }
                    b1.setText("Start Uploading CGM Data");
                    mTitleTextView.setTextColor(Color.RED);
                    mTitleTextView.setText("CGM Service Stopped");
                    stopCGMServices();
                } else {
                    startCGMServices();
                    mHandler.post(updateDataView);
                    mHandlerActive = true;
                }
            }
            //If i do not
            if (key.equals("IUNDERSTAND")) {
                if (!sharedPreferences.getBoolean("IUNDERSTAND", false)) {
                    synchronized (mHandlerActiveLock) {
                        mHandler.removeCallbacks(updateDataView);
                        mHandlerActive = false;
                    }
                    b1.setText("Start Uploading CGM Data");
                    mTitleTextView.setTextColor(Color.RED);
                    mTitleTextView.setText("CGM Service Stopped");
                    stopCGMServices();
                } else {
                    startCGMServices();
                    mHandler.post(updateDataView);
                    mHandlerActive = true;
                }
            }
        } catch (Exception e) {
            StringBuffer sb1 = new StringBuffer("");
            sb1.append("EXCEPTION!!!!!! " + e.getMessage() + " " + e.getCause());
            for (StackTraceElement st : e.getStackTrace()) {
                sb1.append(st.toString()).append("\n");
            }
            Log.e("CGM_onSharedPrefChanged", sb1.toString());
            if (ISDEBUG) {
                display.append(sb1.toString());
            }
        }
    }

    @Override
    public void onEulaAgreedTo() {
        keepServiceAlive = true;
    }

    @Override
    public void onEulaRefusedTo() {
        keepServiceAlive = false;

    }
}
