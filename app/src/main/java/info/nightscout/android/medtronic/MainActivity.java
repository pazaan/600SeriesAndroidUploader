package info.nightscout.android.medtronic;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.NotificationCompat;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.TextView.BufferType;

import com.github.mikephil.charting.data.realm.implementation.RealmLineData;
import com.github.mikephil.charting.data.realm.implementation.RealmLineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.materialdrawer.AccountHeaderBuilder;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import info.nightscout.android.R;
import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.eula.Eula;
import info.nightscout.android.eula.Eula.OnEulaAgreedTo;
import info.nightscout.android.medtronic.service.MedtronicCnlIntentService;
import info.nightscout.android.model.CgmStatusEvent;
import info.nightscout.android.model.medtronicNg.ContourNextLinkInfo;
import info.nightscout.android.settings.SettingsActivity;
import info.nightscout.android.upload.MedtronicNG.PumpStatusRecord;
import info.nightscout.android.upload.nightscout.NightscoutUploadIntentService;
import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

/* Main activity for the MainActivity program */
public class MainActivity extends AppCompatActivity implements OnSharedPreferenceChangeListener, OnEulaAgreedTo {
    private static final String TAG = MainActivity.class.getSimpleName();

    public static int batLevel = 0;
    public static PumpStatusRecord pumpStatusRecord = new PumpStatusRecord();
    boolean mEnableCgmService = true;
    SharedPreferences prefs = null;
    private TextView mTextViewLog; // This will eventually move to a status page.
    private Intent mCnlIntentService;
    private Intent mNightscoutUploadService;
    private Handler mUiRefreshHandler = new Handler();
    private Runnable mUiRefreshRunnable = new RefreshDisplayRunnable();
    private Realm mRealm;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate called");
        super.onCreate(savedInstanceState);

        mRealm = Realm.getDefaultInstance();
        mCnlIntentService = new Intent(this, MedtronicCnlIntentService.class);
        mNightscoutUploadService = new Intent(this, NightscoutUploadIntentService.class);

        setContentView(R.layout.activity_main);

        PreferenceManager.getDefaultSharedPreferences(getBaseContext()).registerOnSharedPreferenceChangeListener(this);
        prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        if (!prefs.getBoolean(getString(R.string.preference_eula_accepted), false)) {
            stopCgmService();
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(
                new StatusMessageReceiver(),
                new IntentFilter(MedtronicCnlIntentService.Constants.ACTION_STATUS_MESSAGE));
        LocalBroadcastManager.getInstance(this).registerReceiver(
                new RefreshDataReceiver(),
                new IntentFilter(MedtronicCnlIntentService.Constants.ACTION_REFRESH_DATA));

        mEnableCgmService = Eula.show(this, prefs);

        IntentFilter batteryIntentFilter = new IntentFilter();
        batteryIntentFilter.addAction(Intent.ACTION_BATTERY_LOW);
        batteryIntentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
        batteryIntentFilter.addAction(Intent.ACTION_BATTERY_OKAY);
        registerReceiver(new BatteryReceiver(), batteryIntentFilter);

        UsbReceiver usbReceiver = new UsbReceiver();
        IntentFilter usbIntentFilter = new IntentFilter();
        usbIntentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        usbIntentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        usbIntentFilter.addAction(MedtronicCnlIntentService.Constants.ACTION_USB_PERMISSION);
        registerReceiver(usbReceiver, usbIntentFilter);
        LocalBroadcastManager.getInstance(this).registerReceiver(
                usbReceiver,
                new IntentFilter(MedtronicCnlIntentService.Constants.ACTION_NO_USB_PERMISSION));
        LocalBroadcastManager.getInstance(this).registerReceiver(
                usbReceiver,
                new IntentFilter(MedtronicCnlIntentService.Constants.ACTION_USB_REGISTER));

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

        if (toolbar != null) {
            setSupportActionBar(toolbar);
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            getSupportActionBar().setElevation(0);
            getSupportActionBar().setTitle("Nightscout");
        }

        final PrimaryDrawerItem itemSettings = new PrimaryDrawerItem()
                .withName("Settings")
                .withIcon(GoogleMaterial.Icon.gmd_settings)
                .withSelectable(false);
        final PrimaryDrawerItem itemRegisterUsb = new PrimaryDrawerItem()
                .withName("Register Contour Next Link")
                .withIcon(GoogleMaterial.Icon.gmd_usb)
                .withSelectable(false);
        final PrimaryDrawerItem itemStopCollecting = new PrimaryDrawerItem()
                .withName("Stop collecting data")
                .withIcon(GoogleMaterial.Icon.gmd_stop)
                .withSelectable(false);
        final PrimaryDrawerItem itemGetNow = new PrimaryDrawerItem()
                .withName("Read data now")
                .withIcon(GoogleMaterial.Icon.gmd_play_arrow)
                .withSelectable(false);
        final PrimaryDrawerItem itemClearLog = new PrimaryDrawerItem()
                .withName("Clear Log")
                .withIcon(GoogleMaterial.Icon.gmd_clear_all)
                .withSelectable(false);

        new DrawerBuilder()
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
                        itemSettings,
                        itemRegisterUsb,
                        itemStopCollecting,
                        itemGetNow,
                        itemClearLog
                )
                .withOnDrawerItemClickListener(new Drawer.OnDrawerItemClickListener() {
                    @Override
                    public boolean onItemClick(View view, int position, IDrawerItem drawerItem) {
                        if (drawerItem.equals(itemSettings)) {
                            openSettings();
                        } else if (drawerItem.equals(itemRegisterUsb)) {
                            openUsbRegistration();
                        } else if (drawerItem.equals(itemStopCollecting)) {
                            mEnableCgmService = false;
                            stopCgmService();
                            finish();
                        } else if (drawerItem.equals(itemGetNow)) {
                            startCgmService();
                        } else if (drawerItem.equals(itemClearLog)) {
                            mTextViewLog.setText("", BufferType.EDITABLE);
                        }

                        return false;
                    }
                })
                .build();

        mTextViewLog = (TextView) findViewById(R.id.textview_log);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        startCgmService();
        startDisplayRefreshLoop();
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_menu_status:
                Intent intent = new Intent(this, StatusActivity.class);
                startActivity(intent);
                break;
        }
        return true;
    }

    private boolean hasDetectedCnl() {
        if (mRealm.where(ContourNextLinkInfo.class).count() == 0) {
            new AlertDialog.Builder(this, R.style.AppTheme)
                    .setTitle("Contour Next Link not detected")
                    .setMessage("To register a Contour Next Link you must first plug it in.")
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
            return false;
        }
        return true;
    }

    private boolean hasUsbPermission() {
        UsbManager usbManager = (UsbManager) this.getSystemService(Context.USB_SERVICE);
        UsbDevice cnlDevice = UsbHidDriver.getUsbDevice(usbManager, MedtronicCnlIntentService.USB_VID, MedtronicCnlIntentService.USB_PID);

        return !(usbManager != null && cnlDevice != null && !usbManager.hasPermission(cnlDevice));

    }

    private void waitForUsbPermission() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent permissionIntent = new Intent(MedtronicCnlIntentService.Constants.ACTION_USB_PERMISSION);
        permissionIntent.putExtra(UsbManager.EXTRA_PERMISSION_GRANTED, hasUsbPermission());
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, permissionIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1000L, pendingIntent);
    }

    private void requestUsbPermission() {
        if (!hasUsbPermission()) {
            UsbManager usbManager = (UsbManager) this.getSystemService(Context.USB_SERVICE);
            UsbDevice cnlDevice = UsbHidDriver.getUsbDevice(usbManager, MedtronicCnlIntentService.USB_VID, MedtronicCnlIntentService.USB_PID);
            PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(MedtronicCnlIntentService.Constants.ACTION_USB_PERMISSION), 0);
            usbManager.requestPermission(cnlDevice, permissionIntent);
        }
    }

    private void refreshDisplay() {
        cancelDisplayRefreshLoop();
        startDisplayRefreshLoop();
    }

    private void startDisplayRefreshLoop() {
        mUiRefreshHandler.post(mUiRefreshRunnable);
    }

    private void cancelDisplayRefreshLoop() {
        mUiRefreshHandler.removeCallbacks(mUiRefreshRunnable);
    }

    private void startCgmService() {
        startCgmService(0L);
    }

    private void startCgmService(long runAtTime) {
        Log.i(TAG, "startCgmService called");

        if (!mEnableCgmService) {
            return;
        }

        if (runAtTime == 0L) {
            startService(mCnlIntentService);
        } else {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            PendingIntent pending = PendingIntent.getService(this, 0, mCnlIntentService, 0);

            alarmManager.set(AlarmManager.RTC_WAKEUP, runAtTime, pending);
        }
    }

    private void startCgmServicePolling(long initialPoll) {
        Log.i(TAG, "startCgmServicePolling called");

        if (!mEnableCgmService) {
            return;
        }

        // Cancel any existing polling.
        stopCgmService();

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        PendingIntent pending = PendingIntent.getService(this, 0, mCnlIntentService, 0);

        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP,
                initialPoll, MedtronicCnlIntentService.POLL_PERIOD_MS, pending);
    }

    private void uploadCgmData() {
        startService(mNightscoutUploadService);
    }

    private void stopCgmService() {
        Log.i(TAG, "stopCgmService called");

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        PendingIntent pending = PendingIntent.getService(this, 0, mCnlIntentService, 0);

        alarmManager.cancel(pending);
    }

    private void showDisconnectionNotification(String title, String message) {
        int notifyId = 1;

        NotificationCompat.Builder mBuilder =
                (NotificationCompat.Builder) new NotificationCompat.Builder(this)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setSmallIcon(R.drawable.ic_launcher) // FIXME - this icon doesn't follow the standards (ie, it has black in it)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setTicker(message)
                        .setVibrate(new long[]{1000, 1000, 1000, 1000, 1000});
        // Creates an explicit intent for an Activity in your app
        Intent resultIntent = new Intent(this, MainActivity.class);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        // Adds the back stack for the Intent (but not the Intent itself)
        stackBuilder.addParentStack(MainActivity.class);
        // Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(
                        0,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
        mBuilder.setContentIntent(resultPendingIntent);
        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        // notifyId allows you to update the notification later on.
        mNotificationManager.notify(notifyId, mBuilder.build());
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy called");
        PreferenceManager.getDefaultSharedPreferences(getBaseContext()).unregisterOnSharedPreferenceChangeListener(this);
        cancelDisplayRefreshLoop();

        if (!mEnableCgmService) {
            stopCgmService();
        }

        mRealm.close();
        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                          String key) {
        if (key.equals(getString(R.string.preference_eula_accepted))) {
            if (!sharedPreferences.getBoolean(getString(R.string.preference_eula_accepted), false)) {
                mEnableCgmService = false;
                stopCgmService();
            } else {
                mEnableCgmService = true;
                startCgmService();
            }
        } else if (key.equals("mmolxl")) {
            refreshDisplay();
        }
    }

    @Override
    public void onEulaAgreedTo() {
        mEnableCgmService = true;
    }

    @Override
    public void onEulaRefusedTo() {
        mEnableCgmService = false;
    }

    public void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    public void openUsbRegistration() {
        if (hasDetectedCnl()) {
            Intent loginIntent = new Intent(this, GetHmacAndKeyActivity.class);
            startActivity(loginIntent);
        }
    }

    private String renderTrendHtml(CgmStatusEvent.TREND trend) {
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

            Log.i(TAG, "Message Receiver: " + message);
            mTextViewLog.setText(mTextViewLog.getText() + "\n" + message, BufferType.EDITABLE);
        }
    }

    private class RefreshDisplayRunnable implements Runnable {
        @Override
        public void run() {
            // UI elements - TODO do these need to be members?
            TextView textViewBg = (TextView) findViewById(R.id.textview_bg);
            TextView textViewBgTime = (TextView) findViewById(R.id.textview_bg_time);
            TextView textViewUnits = (TextView) findViewById(R.id.textview_units);
            if (prefs.getBoolean("mmolxl", false)) {
                textViewUnits.setText(R.string.text_unit_mmolxl);
            } else {
                textViewUnits.setText(R.string.text_unit_mgxdl);
            }
            TextView textViewTrend = (TextView) findViewById(R.id.textview_trend);
            TextView textViewIOB = (TextView) findViewById(R.id.textview_iob);
            //LineChart chart = (LineChart) findViewById(R.id.chart);

            // Get the most recently written CGM record.
            RealmResults<CgmStatusEvent> results =
                    mRealm.where(CgmStatusEvent.class)
                            .findAllSorted("eventDate", Sort.ASCENDING);

            CgmStatusEvent cgmRecord = null;

            if (results.size() > 0) {
                cgmRecord = results.last();
            }

            if (cgmRecord == null) {
                return;
            }

            DecimalFormat df;
            if (prefs.getBoolean("mmolDecimals", false))
                df = new DecimalFormat("0.00");
            else
                df = new DecimalFormat("0.0");

            String sgvString, units;
            if (prefs.getBoolean("mmolxl", false)) {
                float fBgValue = (float) cgmRecord.getSgv();
                sgvString = df.format(fBgValue / 18.016f);
                units = "mmol/L";
                Log.d(TAG, "mmolxl true --> " + sgvString);

            } else {
                sgvString = String.valueOf(cgmRecord.getSgv());
                units = "mg/dL";
                Log.d(TAG, "mmolxl false --> " + sgvString);
            }

            textViewBg.setText(sgvString);
            textViewUnits.setText(units);
            textViewBgTime.setText(DateUtils.getRelativeTimeSpanString(cgmRecord.getEventDate().getTime()));
            textViewTrend.setText(Html.fromHtml(renderTrendHtml(cgmRecord.getTrend())));
            textViewIOB.setText(String.format(Locale.getDefault(), "%.2f", pumpStatusRecord.activeInsulin));

            // TODO - waiting for MPAndroidCharts 3.0.0. This will fix:
            // Date support
            // Realm v1.0.0 support
            // updateChart(results);

            // Run myself again in 60 seconds;
            mUiRefreshHandler.postDelayed(this, 60000L);
        }

        private void updateChart(RealmResults<CgmStatusEvent> results) {
            RealmLineDataSet<CgmStatusEvent> lineDataSet = new RealmLineDataSet<>(results, "sgv", "eventDate");

            lineDataSet.setDrawCircleHole(false);
            lineDataSet.setColor(ColorTemplate.rgb("#FF5722"));
            lineDataSet.setCircleColor(ColorTemplate.rgb("#FF5722"));
            lineDataSet.setLineWidth(1.8f);
            lineDataSet.setCircleSize(3.6f);

            ArrayList<ILineDataSet> dataSets = new ArrayList<ILineDataSet>();
            dataSets.add(lineDataSet);

            RealmLineData lineData = new RealmLineData(results, "eventDate", dataSets);

            // set data
            //chart.setMinimumHeight(200);
            //chart.setData(lineData);
        }
    }

    private class RefreshDataReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            CgmStatusEvent record = mRealm.where(CgmStatusEvent.class)
                    .findAll()
                    .last();

            long nextPoll = record.getEventDate().getTime() + MedtronicCnlIntentService.POLL_GRACE_PERIOD_MS + MedtronicCnlIntentService.POLL_PERIOD_MS;
            startCgmServicePolling(nextPoll);
            Log.d(TAG, "Next Poll at " + new Date(nextPoll).toString());

            // Delete invalid or old records from Realm
            // TODO - show an error message if the valid records haven't been uploaded
            final RealmResults<CgmStatusEvent> results =
                    mRealm.where(CgmStatusEvent.class)
                            .equalTo("sgv", 0)
                            .or()
                            .lessThan("eventDate", new Date(System.currentTimeMillis() - (24 * 60 * 60 * 1000)))
                            .findAll();

            if (results.size() > 0) {
                mRealm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(Realm realm) {
                        // Delete all matches
                        Log.d(TAG, "Deleting " + results.size() + " records from realm");
                        results.deleteAllFromRealm();
                    }
                });
            }

            // TODO - handle isOffline in NightscoutUploadIntentService?
            uploadCgmData();

            refreshDisplay();
        }
    }

    private class UsbReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (MedtronicCnlIntentService.Constants.ACTION_USB_PERMISSION.equals(action)) {
                boolean permissionGranted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                if (permissionGranted) {
                    Log.d(TAG, "Got permission to access USB");
                    startCgmService();
                } else {
                    Log.d(TAG, "Still no permission for USB. Waiting...");
                    waitForUsbPermission();
                }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                Log.d(TAG, "USB plugged in");

                if (hasUsbPermission()) {
                    // Give the USB a little time to warm up first
                    startCgmService(System.currentTimeMillis() + MedtronicCnlIntentService.USB_WARMUP_TIME_MS);
                } else {
                    Log.d(TAG, "No permission for USB. Waiting.");
                    waitForUsbPermission();
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                Log.d(TAG, "USB unplugged");
                if (mEnableCgmService) {
                    showDisconnectionNotification("USB Error", "Contour Next Link unplugged.");
                }
            } else if (MedtronicCnlIntentService.Constants.ACTION_NO_USB_PERMISSION.equals(action)) {
                Log.d(TAG, "No permission to read the USB device.");
                requestUsbPermission();
            } else if (MedtronicCnlIntentService.Constants.ACTION_USB_REGISTER.equals(action)) {
                openUsbRegistration();
            }
        }
    }

    private class BatteryReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context arg0, Intent arg1) {
            if (arg1.getAction().equalsIgnoreCase(Intent.ACTION_BATTERY_LOW)
                    || arg1.getAction().equalsIgnoreCase(Intent.ACTION_BATTERY_CHANGED)
                    || arg1.getAction().equalsIgnoreCase(Intent.ACTION_BATTERY_OKAY)) {
                batLevel = arg1.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            }
        }
    }
}
