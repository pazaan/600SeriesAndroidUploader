package info.nightscout.android.medtronic;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TextView.BufferType;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.answers.Answers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.Locale;

import info.nightscout.android.R;
import info.nightscout.android.eula.Eula;
import info.nightscout.android.eula.Eula.OnEulaAgreedTo;
import info.nightscout.android.medtronic.service.MedtronicCNLService;
import info.nightscout.android.service.ServiceManager;
import info.nightscout.android.settings.SettingsActivity;
import info.nightscout.android.upload.MedtronicNG.CGMRecord;
import info.nightscout.android.upload.MedtronicNG.PumpStatusRecord;
import io.fabric.sdk.android.Fabric;

/* Main activity for the Medtronic640gActivity program */
public class Medtronic640gActivity extends Activity implements OnSharedPreferenceChangeListener, OnEulaAgreedTo {
    //CGMs supported
    public static final int CNL_24 = 2;
    private static final String TAG = Medtronic640gActivity.class.getSimpleName();
    private static final boolean ISDEBUG = true;
    public static int batLevel = 0;
    public static PumpStatusRecord pumpStatusRecord = new PumpStatusRecord();
    BatteryReceiver mArrow;
    Intent batteryReceiver;
    boolean keepServiceAlive = true;
    Boolean mHandlerActive = false;
    final Object mHandlerActiveLock = new Object();
    ActivityManager manager = null;
    SharedPreferences settings = null;
    SharedPreferences prefs = null;
    private Logger log = (Logger) LoggerFactory.getLogger(Medtronic640gActivity.class.getName());
    private int cgmSelected = CNL_24;
    private Handler mHandler = new Medtronic640gActivityHandler();
    private TextView mTitleTextView;
    private TextView mDumpTextView;
    private Button b1;
    private TextView display;
    private ServiceManager cgmService; // > service

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

        if (prefs.getBoolean(getString(R.string.preferences_enable_crashlytics), true)) {
            Fabric.with(this, new Crashlytics());
        }
        if (prefs.getBoolean(getString(R.string.preferences_enable_answers), true)) {
            Fabric.with(this, new Answers());
        }

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
            mHandlerActive = true;
        }

        b1.setText(R.string.button_text_stop_uploading_data);
        lnr.addView(b1);
        lnr2.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        lnr3.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        Button b2 = new Button(this);
        b2.setText(R.string.button_text_clear_log);
        b2.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 1.0f));
        Button b4 = new Button(this);
        b4.setText(R.string.button_text_get_now);
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
            }
        });

        b4.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                display.setKeyListener(null);
                if (cgmService != null) {
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
                        b1.setText(R.string.button_text_start_uploading_data);
                        finish();
                    } else {
                        startCGMServices();

                        mHandlerActive = true;
                        b1.setText(R.string.button_text_stop_uploading_data);
                    }
                }

            }
        });
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        startCGMServices();
        if (cgmService != null) {
            Log.d(TAG, "onPostCreate: Starting the service");
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);

        return true;
    }

    private boolean checkOnline(String title, String message) {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();

        boolean isOnline = (netInfo != null && netInfo.isConnectedOrConnecting());

        if (!isOnline) {
            new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // continue with delete
                            dialog.dismiss();
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
        }

        return isOnline;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.menu_settings:
                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                startActivity(settingsIntent);
                break;
            case R.id.registerCNL:
                if (checkOnline("Please connect to the Internet", "You must be online to register your USB stick.")) {
                    Intent loginIntent = new Intent(this, GetHmacAndKeyActivity.class);
                    startActivity(loginIntent);
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
            default:
                Log.d("DexcomActivity", "Starting Medtronic CNL service");
                cgmService = new ServiceManager(this, MedtronicCNLService.class, mHandler);
                break;
        }
    }

    private void stopCGMServices() {
        switch (cgmSelected) {
            default:
                if (cgmService != null) {
                    cgmService.stop();
                }
                break;
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

            stopCGMServices();
            if (keepServiceAlive) {
                startCGMServices();
            }
            mHandlerActive = false;
            SharedPreferences.Editor editor = getBaseContext().getSharedPreferences(MedtronicConstants.PREFS_NAME, 0).edit();
            editor.putLong("lastDestroy", System.currentTimeMillis());
            editor.apply();
            super.onDestroy();
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
                    b1.setText(R.string.button_text_start_uploading_data);
                    stopCGMServices();
                } else {
                    startCGMServices();
                    //mHandler.post(updateDataView);
                    mHandlerActive = true;
                }
            }
        } catch (Exception e) {
            StringBuilder sb1 = new StringBuilder("");
            sb1.append("EXCEPTION!!!!!! ").append(e.getMessage()).append(" ").append(e.getCause());
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

    private String renderTrendHtml(CGMRecord.TREND trend) {
        switch (trend) {
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

    public class Medtronic640gActivityHandler extends Handler {
        public static final int MSG_ERROR = 1;
        public static final int MSG_STATUS = 2;
        public static final int MSG_DATA = 3;

        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "Got message from Service.");
            switch (cgmSelected) {
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

                            DecimalFormat df;
                            if (prefs.getBoolean("mmolDecimals", false))
                                df = new DecimalFormat("#.00");
                            else
                                df = new DecimalFormat("#.0");
                            String sgvString;
                            String unitsString = "mg/dL";
                            if (prefs.getBoolean("mmolxl", false)) {

                                float fBgValue = (float) record.sgv;
                                sgvString = df.format(fBgValue / 18.016f);
                                unitsString = "mmol/L";
                                log.info("mmolxl true --> " + sgvString);

                            } else {
                                sgvString = String.valueOf(record.sgv);
                                log.info("mmolxl false --> " + sgvString);
                            }

                            //mTitleTextView.setTextColor(Color.YELLOW);
                            mTitleTextView.setText(Html.fromHtml(
                                    String.format("<big><b>%s</b></big> <small>%s %s</small>",
                                            sgvString, unitsString, renderTrendHtml(record.getTrend()))));

                            mDumpTextView.setTextColor(Color.WHITE);
                            mDumpTextView.setText(Html.fromHtml(
                                    String.format( Locale.getDefault(), "<b>SG at:</b> %s<br/><b>Pump Time:</b> %s<br/><b>Active Insulin: </b>%.3f<br/><b>Rate of Change: </b>%s",
                                            DateUtils.formatDateTime(getBaseContext(), record.sgvDate.getTime(), DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME),
                                            DateUtils.formatDateTime(getBaseContext(), pumpStatusRecord.pumpDate.getTime(), DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME),
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
                batLevel = arg1.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            }
        }
    }
}
