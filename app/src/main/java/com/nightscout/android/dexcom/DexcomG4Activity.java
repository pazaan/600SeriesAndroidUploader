package com.nightscout.android.dexcom;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.text.Html;
import android.text.format.DateUtils;
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
import com.nightscout.android.medtronic.MedtronicConstants;
import com.nightscout.android.medtronic.service.MedtronicCNLService;
import com.nightscout.android.service.ServiceManager;
import com.nightscout.android.settings.SettingsActivity;
import com.nightscout.android.upload.MedtronicNG.CGMRecord;
import com.nightscout.android.upload.MedtronicNG.PumpStatusRecord;
import com.nightscout.android.upload.Record;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.text.DecimalFormat;

/* Main activity for the DexcomG4Activity program */
public class DexcomG4Activity extends Activity implements OnSharedPreferenceChangeListener, OnEulaAgreedTo {
    private Logger log = (Logger) LoggerFactory.getLogger(DexcomG4Activity.class.getName());
    //CGMs supported
    public static final int DEXCOMG4 = 0;
    public static final int MEDTRONIC_CGM = 1;
    public static final int CNL_24 = 2;

    private static final String TAG = DexcomG4Activity.class.getSimpleName();
    private int cgmSelected = CNL_24;
    private int calibrationSelected = MedtronicConstants.CALIBRATION_GLUCOMETER;

    private Handler mHandler = new DexcomG4ActivityHandler();

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
    public static PumpStatusRecord pumpStatusRecord = new PumpStatusRecord();
    BatteryReceiver mArrow;
    IBinder bService = null;
    Intent batteryReceiver;
    Messenger mService = null;
    boolean mIsBound;
    boolean keepServiceAlive = true;
    Boolean mHandlerActive = false;
    Object mHandlerActiveLock = new Object();
    Boolean usbAllowedPermission = false;
    ActivityManager manager = null;
    final Context ctx = this;
    SharedPreferences settings = null;
    SharedPreferences prefs = null;
    private static final boolean ISDEBUG = true;

    public class DexcomG4ActivityHandler extends Handler {
        public static final int MSG_ERROR = 1;
        public static final int MSG_STATUS = 2;
        public static final int MSG_DATA = 3;

        @Override
        public void handleMessage(Message msg) {
            Log.d( TAG, "Got message from Service." );
            switch ( cgmSelected ) {
                case CNL_24:
                    switch (msg.what) {
                        case MSG_ERROR:
                            display.setText(msg.obj.toString(), BufferType.EDITABLE);
                            break;
                        case MSG_STATUS:
                            display.setText(msg.obj.toString(), BufferType.EDITABLE);
                            break;
                        case MSG_DATA:
                            CGMRecord record = (CGMRecord) msg.obj;

                            DecimalFormat df = null;
                            if (prefs.getBoolean("mmolDecimals", false))
                                df = new DecimalFormat("#.##");
                            else
                                df = new DecimalFormat("#.#");
                            String bglString = "---";
                            String unitsString = "mg/dL";
                            if (prefs.getBoolean("mmolxl", false)) {
                                try {
                                    float fBgValue = Float.valueOf(record.sensorBGL);
                                    bglString = df.format(fBgValue / 18.016f);
                                    unitsString = "mmol/L";
                                    log.info("mmolxl true --> " + bglString);
                                } catch (Exception e) {

                                }
                            } else {
                                bglString = String.valueOf(record.sensorBGL);
                                log.info("mmolxl false --> " + bglString);
                            }

                            //mTitleTextView.setTextColor(Color.YELLOW);
                            mTitleTextView.setText(Html.fromHtml(
                                    String.format( "<big><b>%s</b></big> <small>%s %s</small>",
                                            bglString, unitsString, renderTrendHtml(record.getTrend()))));

                            mDumpTextView.setTextColor(Color.WHITE);
                            mDumpTextView.setText(Html.fromHtml(
                                String.format( "<b>BGL at:</b> %s<br/><b>Pump Time:</b> %s<br/><b>Active Insulin: </b>%.3f<br/><b>Rate of Change: </b>%s",
                                    DateUtils.formatDateTime(getBaseContext(),record.sensorBGLDate.getTime(),DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME),
                                    DateUtils.formatDateTime(getBaseContext(),pumpStatusRecord.pumpDate.getTime(),DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME),
                                    pumpStatusRecord.activeInsulin,
                                    record.direction
                                )
                            ));

                            break;
                    }
                default:
                    super.handleMessage(msg);
            }
        }
    }

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
        super.onCreate(savedInstanceState);

        if (android.os.Build.VERSION.SDK_INT > 9) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }

        this.settings = getBaseContext().getSharedPreferences(
                MedtronicConstants.PREFS_NAME, 0);
        PreferenceManager.getDefaultSharedPreferences(getBaseContext()).registerOnSharedPreferenceChangeListener(this);
        prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        keepServiceAlive = Eula.show(this, prefs);

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
            //mHandler.post(updateDataView);
            mHandlerActive = true;
        }

        b1.setText("Stop Uploading CGM Data");
        lnr.addView(b1);
        lnr2.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        lnr3.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        Button b2 = new Button(this);
        b2.setText("Clear Log");
        b2.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 1.0f));
        b4 = new Button(this);
        b4.setText("Get Now");
        b4.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 1.0f));
        lnr3.addView(b4);

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
                display.setKeyListener(null);
                if( cgmService != null ) {
                    if (!cgmService.isRunning()) {
                        cgmService.start();
                    } else {
                        cgmService.stop();
                        cgmService.start();
                    }
                }
            }
        });

        b1.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                synchronized (mHandlerActiveLock) {
                    if (b1.getText() == "Stop Uploading CGM Data") {
                        mHandlerActive = false;
                        //mHandler.removeCallbacks(updateDataView);
                        keepServiceAlive = false;
                        stopCGMServices();
                        b1.setText("Start Uploading CGM Data");
                        //mTitleTextView.setTextColor(Color.RED);
                        //mTitleTextView.setText("CGM Service Stopped");
                        finish();
                    } else {
                        mHandlerActive = false;
                        //mHandler.removeCallbacks(updateDataView);
                        //mHandler.post(updateDataView);
                        if (!usbAllowedPermission)
                            if (mService == null && bService != null) {
                                mService = new Messenger(bService);
                            }
                        if (mService != null) {
                            try {
                                Message msg = Message.obtain(null, MedtronicConstants.MSG_MEDTRONIC_CGM_REQUEST_PERMISSION, 0, 0);
                                //msg.replyTo = mMessenger;
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
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        startCGMServices();
        if( cgmService != null ) {
            Log.d( TAG, "onPostCreate: Starting the service");
            cgmService.start();
        }
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

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.menu_settings:
                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                startActivity(settingsIntent);
                break;
            case R.id.registerCNL:
                Intent loginIntent = new Intent(this, LoginActivity.class);
                startActivity(loginIntent);
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
                Log.d("DexcomActivity", "Starting Medtronic CNL service");
                cgmService = new ServiceManager(this, MedtronicCNLService.class, mHandler );
                //cgmService.start();
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
                if( cgmService != null ) {
                    cgmService.stop();
                }
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
            //mHandler.removeCallbacks(updateDataView);
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
        stopCGMServices();
    }

    void doBindService() {
        if ((service != null && isMyServiceRunning()) || mIsBound)
            stopCGMServices();
        service = new Intent(this, MedtronicCGMService.class);
        //bindService(service, mConnection, Context.BIND_AUTO_CREATE);
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
                    //msg.replyTo = mMessenger;
                    mService.send(msg);
                } catch (RemoteException e) {
                    // There is nothing special we need to do if the service has crashed.
                }
            }
            // Detach our existing connection.
            //unbindService(mConnection);
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
            //If i do not
            if (key.equals("IUNDERSTAND")) {
                if (!sharedPreferences.getBoolean("IUNDERSTAND", false)) {
                    synchronized (mHandlerActiveLock) {
                        //mHandler.removeCallbacks(updateDataView);
                        mHandlerActive = false;
                    }
                    b1.setText("Start Uploading CGM Data");
                    mTitleTextView.setTextColor(Color.RED);
                    mTitleTextView.setText("CGM Service Stopped");
                    stopCGMServices();
                } else {
                    startCGMServices();
                    //mHandler.post(updateDataView);
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

    private String renderTrendHtml(CGMRecord.TREND trend)
    {
        switch( trend )
        {
            case DOUBLE_UP:
                return "&#x21c8;";
            case SINGLE_UP:
                return "&#x2191;";
            case FOURTY_FIVE_UP:
                return "&#x2197;";
            case FLAT:
                return "&#x2192;";
            case FOURTY_FIVE_DOWN:
                return "&#x2198;";
            case SINGLE_DOWN:
                return "&#x2193;";
            case DOUBLE_DOWN:
                return "&#x21ca;";
            default:
                return "&mdash;";
        }
    }
}
