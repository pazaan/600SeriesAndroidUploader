package info.nightscout.android.medtronic;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.TextView.BufferType;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.answers.Answers;
import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.materialdrawer.AccountHeaderBuilder;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.Locale;

import info.nightscout.android.R;
import info.nightscout.android.eula.Eula;
import info.nightscout.android.eula.Eula.OnEulaAgreedTo;
import info.nightscout.android.medtronic.service.MedtronicCnlIntentService;
import info.nightscout.android.settings.SettingsActivity;
import info.nightscout.android.upload.MedtronicNG.CGMRecord;
import info.nightscout.android.upload.MedtronicNG.PumpStatusRecord;
import io.fabric.sdk.android.Fabric;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

/* Main activity for the MainActivity program */
public class MainActivity extends AppCompatActivity implements OnSharedPreferenceChangeListener, OnEulaAgreedTo {
    private static final String TAG = MainActivity.class.getSimpleName();
    public static int batLevel = 0;
    public static PumpStatusRecord pumpStatusRecord = new PumpStatusRecord();
    final Object mHandlerActiveLock = new Object();
    boolean keepServiceAlive = true;
    Boolean mHandlerActive = false;
    ActivityManager manager = null;
    SharedPreferences prefs = null;
    private Logger log = (Logger) LoggerFactory.getLogger(MainActivity.class.getName());
    private Toolbar toolbar;
    private TextView mTextViewBg;
    private TextView mTextViewBgTime;
    private TextView mTextViewLog;
    private TextView mTextViewUnits;
    private TextView mTextViewTrend;
    private TextView mTextViewIOB;
    private Button buttonStopService;
    private Intent mCnlIntentService;

    //Look for and launch the service, mTextViewLog status to user
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate called");
        super.onCreate(savedInstanceState);

        mCnlIntentService = new Intent(this, MedtronicCnlIntentService.class);

        setContentView(R.layout.activity_main);

        PreferenceManager.getDefaultSharedPreferences(getBaseContext()).registerOnSharedPreferenceChangeListener(this);
        prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        if (prefs.getBoolean(getString(R.string.preferences_enable_crashlytics), true)) {
            Fabric.with(this, new Crashlytics());
        }
        if (prefs.getBoolean(getString(R.string.preferences_enable_answers), true)) {
            Fabric.with(this, new Answers());
        }

        if (!prefs.getBoolean("IUNDERSTAND", false)) {
            stopCgmService();
        } else {
            mHandlerActive = true;
        }

        // Registers the DownloadStateReceiver and its intent filters
        LocalBroadcastManager.getInstance(this).registerReceiver(
                new StatusMessageReceiver(),
                new IntentFilter(MedtronicCnlIntentService.Constants.ACTION_STATUS_MESSAGE));
        LocalBroadcastManager.getInstance(this).registerReceiver(
                new CgmRecordReceiver(),
                new IntentFilter(MedtronicCnlIntentService.Constants.ACTION_CGM_DATA));

        keepServiceAlive = Eula.show(this, prefs);

        IntentFilter batteryIntentFilter = new IntentFilter();
        batteryIntentFilter.addAction(Intent.ACTION_BATTERY_LOW);
        batteryIntentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
        batteryIntentFilter.addAction(Intent.ACTION_BATTERY_OKAY);
        registerReceiver(new BatteryReceiver(), batteryIntentFilter);

        IntentFilter usbIntentFilter = new IntentFilter();
        usbIntentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        usbIntentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(new UsbReceiver(), usbIntentFilter);

        manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        toolbar = (Toolbar) findViewById(R.id.toolbar);

        if (toolbar != null) {
            setSupportActionBar(toolbar);
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            getSupportActionBar().setElevation(0);
            getSupportActionBar().setTitle("Nightscout");
        }


        final PrimaryDrawerItem itemHome = new PrimaryDrawerItem().withName("Home").withIcon(GoogleMaterial.Icon.gmd_home).withSelectable(false);
        final PrimaryDrawerItem itemSettings = new PrimaryDrawerItem().withName("Settings").withIcon(GoogleMaterial.Icon.gmd_settings).withSelectable(false);
        final PrimaryDrawerItem itemRegisterUsb = new PrimaryDrawerItem().withName("Register Contour Next Link").withIcon(GoogleMaterial.Icon.gmd_usb).withSelectable(false);

        Drawer drawer = new DrawerBuilder()
                .withActivity(this)
                .withAccountHeader(new AccountHeaderBuilder()
                        .withActivity(this)
                        .withHeaderBackground(R.drawable.drawer_header)
                        .build()
                )
                .withTranslucentStatusBar(false)
                .withToolbar(toolbar)
                .withActionBarDrawerToggle(true)
                .withSelectedItem(-1)
                .addDrawerItems(
                        itemHome,
                        itemSettings,
                        itemRegisterUsb
                )
                .withOnDrawerItemClickListener(new Drawer.OnDrawerItemClickListener() {
                    @Override
                    public boolean onItemClick(View view, int position, IDrawerItem drawerItem) {
                        if (drawerItem.equals(itemSettings)) {
                            openSettings();
                        } else if (drawerItem.equals(itemRegisterUsb)) {
                            openUsbRegistration();
                        }
                        return false;
                    }
                })
                .build();

        // UI elements - TODO do these need to be members?
        mTextViewBg = (TextView) findViewById(R.id.textview_bg);
        mTextViewBgTime = (TextView) findViewById(R.id.textview_bg_time);
        mTextViewLog = (TextView) findViewById(R.id.textview_log);
        mTextViewUnits = (TextView) findViewById(R.id.textview_units);
        if (prefs.getBoolean("mmolxl", false)) {
            mTextViewUnits.setText(R.string.text_unit_mmolxl);
        } else {
            mTextViewUnits.setText(R.string.text_unit_mgxdl);
        }
        mTextViewTrend = (TextView) findViewById(R.id.textview_trend);
        mTextViewIOB = (TextView) findViewById(R.id.textview_iob);

        buttonStopService = (Button) findViewById(R.id.button_stop_service);

        Button buttonClearLog = (Button) findViewById(R.id.button_clear_log);
        Button buttonStartService = (Button) findViewById(R.id.button_start_service);

        buttonClearLog.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mTextViewLog.setText("", BufferType.EDITABLE);
            }
        });

        buttonStartService.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // Get an immediate reading.
                startCgmService();
            }
        });

        buttonStopService.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                synchronized (mHandlerActiveLock) {
                    mHandlerActive = false;
                    keepServiceAlive = false;
                    stopCgmService();
                    finish();
                }
            }
        });
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        startCgmService();
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
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //MenuInflater inflater = getMenuInflater();
        //inflater.inflate(R.menu.menu, menu);

        return true;
    }

    private boolean checkOnline(String title, String message) {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();

        boolean isOnline = (netInfo != null && netInfo.isConnectedOrConnecting());

        if (!isOnline) {
            new AlertDialog.Builder(this, R.style.AppTheme)
                    .setTitle(title)
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
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

    private void startCgmService() {
        Log.i(TAG, "startCgmService called");
        startService(mCnlIntentService);
    }

    private void startCgmServicePolling(long initialPoll) {
        Log.i(TAG, "startCgmServicePolling called");
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        PendingIntent pending = PendingIntent.getService(this, 0, mCnlIntentService, 0);

        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP,
                initialPoll, MedtronicCnlIntentService.POLL_PERIOD_MS, pending);
    }

    private void stopCgmService() {
        Log.i(TAG, "stopCgmService called");

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        PendingIntent pending = PendingIntent.getService(this, 0, mCnlIntentService, 0);

        alarmManager.cancel(pending);
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy called");
        log.info("onDestroy called");
        PreferenceManager.getDefaultSharedPreferences(getBaseContext()).unregisterOnSharedPreferenceChangeListener(this);
        synchronized (mHandlerActiveLock) {
            if (!keepServiceAlive) {
                stopCgmService();
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
        if (key.equals(getString(R.string.preference_eula_accepted))) {
            if (!sharedPreferences.getBoolean(getString(R.string.preference_eula_accepted), false)) {
                synchronized (mHandlerActiveLock) {
                    mHandlerActive = false;
                }
                stopCgmService();
            } else {
                startCgmService();
                mHandlerActive = true;
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

    public void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    public void openUsbRegistration() {
        if (checkOnline("Please connect to the Internet", "You must be online to register your USB stick.")) {
            Intent loginIntent = new Intent(this, GetHmacAndKeyActivity.class);
            startActivity(loginIntent);
        }
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

    private class StatusMessageReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String message = intent.getStringExtra(MedtronicCnlIntentService.Constants.EXTENDED_DATA);

            mTextViewLog.setText(mTextViewLog.getText() + "\n" + message, BufferType.EDITABLE);
        }
    }

    private class CgmRecordReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            CGMRecord cgmRecord = (CGMRecord) intent.getSerializableExtra(MedtronicCnlIntentService.Constants.EXTENDED_DATA);

            // FIXME - replace initial polling time with the next expected polling period
            // i.e. Next 5 minute CGM polling increment to occur after now, + POLL_GRACE_PERIOD_MS
            startCgmServicePolling(System.currentTimeMillis() + MedtronicCnlIntentService.POLL_PERIOD_MS);

            DecimalFormat df;
            if (prefs.getBoolean("mmolDecimals", false))
                df = new DecimalFormat("0.00");
            else
                df = new DecimalFormat("0.0");

            String sgvString, units;
            if (prefs.getBoolean("mmolxl", false)) {
                float fBgValue = (float) cgmRecord.sgv;
                sgvString = df.format(fBgValue / 18.016f);
                units = "mmol/L";
                log.info("mmolxl true --> " + sgvString);

            } else {
                sgvString = String.valueOf(cgmRecord.sgv);
                units = "mg/dL";
                log.info("mmolxl false --> " + sgvString);
            }

            mTextViewBg.setText(sgvString);
            mTextViewUnits.setText(units);
            mTextViewBgTime.setText(DateUtils.formatDateTime(getBaseContext(), cgmRecord.sgvDate.getTime(), DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME));
            mTextViewTrend.setText(Html.fromHtml(renderTrendHtml(cgmRecord.getTrend())));
            mTextViewIOB.setText(String.format(Locale.getDefault(), "%.2f", pumpStatusRecord.activeInsulin));
        }
    }

    private class UsbReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                mTextViewLog.setText(mTextViewLog.getText() + "\nUSB plugged in", BufferType.EDITABLE);
                startCgmService();
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                mTextViewLog.setText(mTextViewLog.getText() + "\nUSB unplugged", BufferType.EDITABLE);
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
