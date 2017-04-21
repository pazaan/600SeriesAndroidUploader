package info.nightscout.android.medtronic;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.NotificationCompat;
import android.support.v7.view.menu.ActionMenuItemView;
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
import android.widget.Toast;

import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.DataPointInterface;
import com.jjoe64.graphview.series.OnDataPointTapListener;
import com.jjoe64.graphview.series.PointsGraphSeries;
import com.jjoe64.graphview.series.Series;
import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.materialdrawer.AccountHeaderBuilder;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import info.nightscout.android.R;
import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.eula.Eula;
import info.nightscout.android.eula.Eula.OnEulaAgreedTo;
import info.nightscout.android.medtronic.service.MedtronicCnlAlarmManager;
import info.nightscout.android.medtronic.service.MedtronicCnlAlarmReceiver;
import info.nightscout.android.medtronic.service.MedtronicCnlIntentService;
import info.nightscout.android.model.medtronicNg.PumpInfo;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.settings.SettingsActivity;
import info.nightscout.android.upload.nightscout.NightscoutUploadIntentService;
import io.realm.Realm;
import io.realm.RealmChangeListener;
import io.realm.RealmResults;
import io.realm.Sort;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

public class MainActivity extends AppCompatActivity implements OnSharedPreferenceChangeListener, OnEulaAgreedTo {
    private static final String TAG = MainActivity.class.getSimpleName();
    public static final float MMOLXLFACTOR = 18.016f;

    public static int batLevel = 0;
    public static boolean reducePollOnPumpAway = false;
    public static long pollInterval = MedtronicCnlIntentService.POLL_PERIOD_MS;
    public static long lowBatteryPollInterval = MedtronicCnlIntentService.LOW_BATTERY_POLL_PERIOD_MS;

    private static long activePumpMac;
    private int chartZoom = 3;
    private boolean hasZoomedChart = false;

    private NumberFormat sgvFormatter;
    private boolean mmolxl;
    private boolean mmolxlDecimals;

    boolean mEnableCgmService = true;
    SharedPreferences prefs = null;
    private PumpInfo mActivePump;
    private TextView mTextViewLog; // This will eventually move to a status page.
    private GraphView mChart;
    private Intent mNightscoutUploadService;
    private Handler mUiRefreshHandler = new Handler();
    private Runnable mUiRefreshRunnable = new RefreshDisplayRunnable();
    private Realm mRealm;
    private StatusMessageReceiver statusMessageReceiver = new StatusMessageReceiver();
    private MedtronicCnlAlarmReceiver medtronicCnlAlarmReceiver = new MedtronicCnlAlarmReceiver();


    public static long getNextPoll(PumpStatusEvent pumpStatusData) {
        long nextPoll = pumpStatusData.getEventDate().getTime() + pumpStatusData.getPumpTimeOffset()
                + MedtronicCnlIntentService.POLL_GRACE_PERIOD_MS;

        if (pumpStatusData.getBatteryPercentage() > 25) {
            // poll every 5 min
            nextPoll += MainActivity.pollInterval;
        } else {
            // if pump battery seems to be empty reduce polling to save battery (every 15 min)
            //TODO add message & document it
            nextPoll += MainActivity.lowBatteryPollInterval;
        }

        return nextPoll;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate called");
        super.onCreate(savedInstanceState);

        mRealm = Realm.getDefaultInstance();
        mNightscoutUploadService = new Intent(this, NightscoutUploadIntentService.class);

        setContentView(R.layout.activity_main);

        PreferenceManager.getDefaultSharedPreferences(getBaseContext()).registerOnSharedPreferenceChangeListener(this);
        prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        if (!prefs.getBoolean(getString(R.string.preference_eula_accepted), false)) {
            stopCgmService();
        }

        // setup preferences
        MainActivity.pollInterval = Long.parseLong(prefs.getString("pollInterval", Long.toString(MedtronicCnlIntentService.POLL_PERIOD_MS)));
        MainActivity.lowBatteryPollInterval = Long.parseLong(prefs.getString("lowBatPollInterval", Long.toString(MedtronicCnlIntentService.LOW_BATTERY_POLL_PERIOD_MS)));
        MainActivity.reducePollOnPumpAway = prefs.getBoolean("doublePollOnPumpAway", false);
        chartZoom = Integer.parseInt(prefs.getString("chartZoom", "3"));
        mmolxl = prefs.getBoolean("mmolxl", false);
        mmolxlDecimals = prefs.getBoolean("mmolDecimals", false);

        if (mmolxl) {
            if (mmolxlDecimals)
                sgvFormatter = new DecimalFormat("0.00");
            else
                sgvFormatter = new DecimalFormat("0.0");
        } else {
            sgvFormatter = new DecimalFormat("0");
        }

        // Disable battery optimization to avoid missing values on 6.0+
        // taken from https://github.com/NightscoutFoundation/xDrip/blob/master/app/src/main/java/com/eveningoutpost/dexdrip/Home.java#L277L298

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            final String packageName = getPackageName();
            final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);

            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                Log.d(TAG, "Requesting ignore battery optimization");
                try {
                    // ignoring battery optimizations required for constant connection
                    // to peripheral device - eg CGM transmitter.
                    final Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + packageName));
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    Log.d(TAG, "Device does not appear to support battery optimization whitelisting!");
                }
            }
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(
                statusMessageReceiver,
                new IntentFilter(MedtronicCnlIntentService.Constants.ACTION_STATUS_MESSAGE));
        LocalBroadcastManager.getInstance(this).registerReceiver(
                new UpdatePumpReceiver(),
                new IntentFilter(MedtronicCnlIntentService.Constants.ACTION_UPDATE_PUMP));

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
                .withName("Registered Devices")
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
                            clearLogText();
                        }

                        return false;
                    }
                })
                .build();

        mTextViewLog = (TextView) findViewById(R.id.textview_log);

        mChart = (GraphView) findViewById(R.id.chart);

        // disable scrolling at the moment
        mChart.getViewport().setScalable(false);
        mChart.getViewport().setScrollable(false);
        mChart.getViewport().setXAxisBoundsManual(true);
        final long now = System.currentTimeMillis(),
                left = now - chartZoom * 60 * 60 * 1000;

        mChart.getViewport().setMaxX(now);
        mChart.getViewport().setMinX(left);

        mChart.getViewport().setOnXAxisBoundsChangedListener(new Viewport.OnXAxisBoundsChangedListener() {
            @Override
            public void onXAxisBoundsChanged(double minX, double maxX, Reason reason) {
                double rightX = mChart.getSeries().get(0).getHighestValueX();
                hasZoomedChart = (rightX != maxX || rightX - chartZoom * 60 * 60 * 1000 != minX);
            }
        });

        mChart.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (!mChart.getSeries().isEmpty() && !mChart.getSeries().get(0).isEmpty()) {
                    double rightX = mChart.getSeries().get(0).getHighestValueX();
                    mChart.getViewport().setMaxX(rightX);
                    mChart.getViewport().setMinX(rightX - chartZoom * 60 * 60 * 1000);
                }
                hasZoomedChart = false;
                return true;
            }
        });
        mChart.getGridLabelRenderer().setNumHorizontalLabels(6);
        mChart.getGridLabelRenderer().setHumanRounding(false);

        mChart.getGridLabelRenderer().setLabelFormatter(new DefaultLabelFormatter() {
            DateFormat mFormat = DateFormat.getTimeInstance(DateFormat.SHORT);
            @Override
            public String formatLabel(double value, boolean isValueX) {
                if (isValueX) {
                    return mFormat.format(new Date((long) value));
                } else {
                    if (mmolxl) {
                        return sgvFormatter.format(value / MMOLXLFACTOR);
                    } else {
                        return sgvFormatter.format(value);
                    }
                }
            }}
        );
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

        // setup self handling alarm receiver
        MedtronicCnlAlarmManager.setContext(getBaseContext());
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

    private void clearLogText() {
        statusMessageReceiver.clearMessages();
        //mTextViewLog.setText("", BufferType.EDITABLE);
    }

    private void startDisplayRefreshLoop() {
        mUiRefreshHandler.post(mUiRefreshRunnable);
    }

    private void cancelDisplayRefreshLoop() {
        mUiRefreshHandler.removeCallbacks(mUiRefreshRunnable);
    }

    private void startCgmService() {
        startCgmService(System.currentTimeMillis() + 1000);
    }

    private void startCgmService(long initialPoll) {
        Log.i(TAG, "startCgmService called");

        if (!mEnableCgmService) {
            return;
        }

        // Cancel any existing polling.
        stopCgmService();
        MedtronicCnlAlarmManager.setAlarm(initialPoll);
    }

    private void uploadCgmData() {
        startService(mNightscoutUploadService);
    }

    private void stopCgmService() {
        Log.i(TAG, "stopCgmService called");
        MedtronicCnlAlarmManager.cancelAlarm();
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
        super.onDestroy();

        PreferenceManager.getDefaultSharedPreferences(getBaseContext()).unregisterOnSharedPreferenceChangeListener(this);
        cancelDisplayRefreshLoop();

        mRealm.close();

        if (!mEnableCgmService) {
            stopCgmService();
        }
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
        } else if (key.equals("mmolxl") || key.equals("mmolDecimals")) {
            mmolxl = sharedPreferences.getBoolean("mmolxl", false);
            mmolxlDecimals = sharedPreferences.getBoolean("mmolDecimals", false);
            if (mmolxl) {
                if (mmolxlDecimals)
                    sgvFormatter = new DecimalFormat("0.00");
                else
                    sgvFormatter = new DecimalFormat("0.0");
            } else {
                sgvFormatter = new DecimalFormat("0");
            }
            refreshDisplay();
        } else if (key.equals("pollInterval")) {
            MainActivity.pollInterval = Long.parseLong(sharedPreferences.getString("pollInterval",
                    Long.toString(MedtronicCnlIntentService.POLL_PERIOD_MS)));
        } else if (key.equals("lowBatPollInterval")) {
            MainActivity.lowBatteryPollInterval = Long.parseLong(sharedPreferences.getString("lowBatPollInterval",
                    Long.toString(MedtronicCnlIntentService.LOW_BATTERY_POLL_PERIOD_MS)));
        } else if (key.equals("doublePollOnPumpAway")) {
            MainActivity.reducePollOnPumpAway = sharedPreferences.getBoolean("doublePollOnPumpAway", false);
        } else if (key.equals("chartZoom")) {
            chartZoom = Integer.parseInt(sharedPreferences.getString("chartZoom", "3"));
            hasZoomedChart = false;
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
        Intent manageCNLIntent = new Intent(this, ManageCNLActivity.class);
        startActivity(manageCNLIntent);
    }

    private String renderTrendHtml(PumpStatusEvent.CGM_TREND trend) {
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

    public static void setActivePumpMac(long pumpMac) {
        activePumpMac = pumpMac;
    }

    private PumpInfo getActivePump() {
        if (activePumpMac != 0L && (mActivePump == null || !mActivePump.isValid() || mActivePump.getPumpMac() != activePumpMac)) {
            if (mActivePump != null) {
                // remove listener on old pump
                mActivePump.removeChangeListeners();
                mActivePump = null;
            }

            PumpInfo pump = mRealm
                    .where(PumpInfo.class)
                    .equalTo("pumpMac", MainActivity.activePumpMac)
                    .findFirst();

            if (pump != null && pump.isValid()) {
                mActivePump = pump;
                mActivePump.addChangeListener(new RealmChangeListener<PumpInfo>() {
                    long lastQueryTS = 0;
                    @Override
                    public void onChange(PumpInfo pump) {
                        // prevent double updating after deleting old events below
                        if (pump.getLastQueryTS() == lastQueryTS || !pump.isValid()) {
                            return;
                        }

                        PumpStatusEvent pumpStatusData = pump.getPumpHistory().last();;

                        lastQueryTS = pump.getLastQueryTS();

                        startCgmService(MainActivity.getNextPoll(pumpStatusData));

                        // Delete invalid or old records from Realm
                        // TODO - show an error message if the valid records haven't been uploaded
                        final RealmResults<PumpStatusEvent> results =
                                mRealm.where(PumpStatusEvent.class)
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
                });
            }
        }

        return mActivePump;
    }

    private class StatusMessageReceiver extends BroadcastReceiver {
        private class StatusMessage {
            private long timestamp;
            private String message;

            public StatusMessage(String message) {
                this(System.currentTimeMillis(), message);
            }

            public StatusMessage(long timestamp, String message) {
                this.timestamp = timestamp;
                this.message = message;
            }

            public long getTimestamp() {
                return timestamp;
            }

            public void setTimestamp(long timestamp) {
                this.timestamp = timestamp;
            }

            public String getMessage() {
                return message;
            }

            public void setMessage(String message) {
                this.message = message;
            }

            public String toString() {
                return DateFormat.getTimeInstance(DateFormat.MEDIUM).format(timestamp) + ": " + message;
            }
        }

        private Queue<StatusMessage> messages = new ArrayBlockingQueue<>(10);

        @Override
        public void onReceive(Context context, Intent intent) {
            String message = intent.getStringExtra(MedtronicCnlIntentService.Constants.EXTENDED_DATA);
            Log.i(TAG, "Message Receiver: " + message);

            synchronized (messages) {
                while (messages.size() > 8) {
                    messages.poll();
                }
                messages.add(new StatusMessage(message));
            }

            StringBuilder sb = new StringBuilder();
            for (StatusMessage msg : messages) {
                if (sb.length() > 0)
                    sb.append("\n");
                sb.append(msg);
            }

            mTextViewLog.setText(sb.toString(), BufferType.EDITABLE);
        }

        public void clearMessages() {
            synchronized (messages) {
                messages.clear();
            }

            mTextViewLog.setText("", BufferType.EDITABLE);
        }
    }

    private class RefreshDisplayRunnable implements Runnable {
        @Override
        public void run() {
            // UI elements - TODO do these need to be members?
            TextView textViewBg = (TextView) findViewById(R.id.textview_bg);
            TextView textViewBgTime = (TextView) findViewById(R.id.textview_bg_time);
            TextView textViewUnits = (TextView) findViewById(R.id.textview_units);
            if (mmolxl) {
                textViewUnits.setText(R.string.text_unit_mmolxl);
            } else {
                textViewUnits.setText(R.string.text_unit_mgxdl);
            }
            TextView textViewTrend = (TextView) findViewById(R.id.textview_trend);
            TextView textViewIOB = (TextView) findViewById(R.id.textview_iob);

            // Get the most recently written CGM record for the active pump.
            PumpStatusEvent pumpStatusData = null;

            PumpInfo pump = getActivePump();

            if (pump != null && pump.isValid()) {
                Log.d(TAG, "history display refresh size: " + pump.getPumpHistory().size());
                Log.d(TAG, "history display refresh date: " + pump.getPumpHistory().last().getEventDate());
                pumpStatusData = pump.getPumpHistory().last();
            }

            // FIXME - grab the last item from the activePump's getPumpHistory
            updateChart(mRealm.where(PumpStatusEvent.class)
                    .greaterThan("eventDate", new Date(System.currentTimeMillis() - 1000*60*60*24))
                    .findAllSorted("eventDate", Sort.ASCENDING));

            if (pumpStatusData != null) {

                String sgvString;
                if (mmolxl) {
                    float fBgValue = (float) pumpStatusData.getSgv();
                    sgvString = sgvFormatter.format(fBgValue / MMOLXLFACTOR);
                    Log.d(TAG, sgvString + " mmol/L");
                } else {
                    sgvString = String.valueOf(pumpStatusData.getSgv());
                    Log.d(TAG, sgvString + " mg/dL");
                }

                textViewBg.setText(sgvString);
                textViewBgTime.setText(DateUtils.getRelativeTimeSpanString(pumpStatusData.getEventDate().getTime()));
                textViewTrend.setText(Html.fromHtml(renderTrendHtml(pumpStatusData.getCgmTrend())));
                textViewIOB.setText(String.format(Locale.getDefault(), "%.2f", pumpStatusData.getActiveInsulin()));

                ActionMenuItemView batIcon = ((ActionMenuItemView) findViewById(R.id.status_battery));
                if (batIcon != null) {
                    switch (pumpStatusData.getBatteryPercentage()) {
                        case 0:
                            batIcon.setTitle("0%");
                            batIcon.setIcon(getResources().getDrawable(R.drawable.battery_0));
                            break;
                        case 25:
                            batIcon.setTitle("25%");
                            batIcon.setIcon(getResources().getDrawable(R.drawable.battery_25));
                            break;
                        case 50:
                            batIcon.setTitle("50%");
                            batIcon.setIcon(getResources().getDrawable(R.drawable.battery_50));
                            break;
                        case 75:
                            batIcon.setTitle("75%");
                            batIcon.setIcon(getResources().getDrawable(R.drawable.battery_75));
                            break;
                        case 100:
                            batIcon.setTitle("100%");
                            batIcon.setIcon(getResources().getDrawable(R.drawable.battery_100));
                            break;
                        default:
                            batIcon.setTitle(getResources().getString(R.string.menu_name_status));
                            batIcon.setIcon(getResources().getDrawable(R.drawable.battery_unknown));
                    }
                }

            }

            // Run myself again in 60 seconds;
            mUiRefreshHandler.postDelayed(this, 60000L);
        }

        private void updateChart(RealmResults<PumpStatusEvent> results) {
            int size = results.size();
            if (size == 0) {
                final long now = System.currentTimeMillis(),
                        left = now - chartZoom * 60 * 60 * 1000;

                mChart.getViewport().setXAxisBoundsManual(true);
                mChart.getViewport().setMaxX(now);
                mChart.getViewport().setMinX(left);

                mChart.getViewport().setYAxisBoundsManual(true);
                if (mmolxl) {
                    mChart.getViewport().setMinY(80 / MMOLXLFACTOR);
                    mChart.getViewport().setMaxY(120 / MMOLXLFACTOR);
                } else {
                    mChart.getViewport().setMinY(80);
                    mChart.getViewport().setMaxY(120);
                }
                mChart.postInvalidate();
                return;
            }

            DataPoint[] entries = new DataPoint[size];
            final long left = System.currentTimeMillis() - chartZoom * 60 * 60 * 1000;

            int pos = 0;
            for (PumpStatusEvent pumpStatus: results) {
                // turn your data into Entry objects
                int sgv = pumpStatus.getSgv();

                if (mmolxl) {
                    entries[pos++] = new DataPoint(pumpStatus.getEventDate(), pumpStatus.getSgv() / MMOLXLFACTOR);
                } else {
                    entries[pos++] = new DataPoint(pumpStatus.getEventDate(), pumpStatus.getSgv());
                }
            }

            if (mChart.getSeries().size() == 0) {
//                long now = System.currentTimeMillis();
//                entries = new DataPoint[1000];
//                int j = 0;
//                for(long i = now - 24*60*60*1000; i < now - 30*60*1000; i+= 5*60*1000) {
//                    entries[j++] = new DataPoint(i, (float) (Math.random()*200 + 89));
//                }
//                entries = Arrays.copyOfRange(entries, 0, j);

                PointsGraphSeries sgvSerie = new PointsGraphSeries(entries);
//                sgvSerie.setSize(3.6f);
//                sgvSerie.setColor(Color.LTGRAY);


                sgvSerie.setOnDataPointTapListener(new OnDataPointTapListener() {
                    DateFormat mFormat = DateFormat.getTimeInstance(DateFormat.MEDIUM);

                    @Override
                    public void onTap(Series series, DataPointInterface dataPoint) {
                        double sgv = dataPoint.getY();

                        StringBuilder sb = new StringBuilder(mFormat.format(new Date((long) dataPoint.getX())) + ": ");
                        if (mmolxl) {
                            sb.append(sgvFormatter.format(sgv / MMOLXLFACTOR));
                        } else {
                            sb.append(sgvFormatter.format(sgv));
                        }
                        Toast.makeText(getBaseContext(), sb.toString(), Toast.LENGTH_SHORT).show();
                    }
                });

                sgvSerie.setCustomShape(new PointsGraphSeries.CustomShape() {
                    @Override
                    public void draw(Canvas canvas, Paint paint, float x, float y, DataPointInterface dataPoint) {
                        double sgv = dataPoint.getY();
                        if (sgv < 80)
                            paint.setColor(Color.RED);
                        else if (sgv <= 180)
                            paint.setColor(Color.GREEN);
                        else if (sgv <= 260)
                            paint.setColor(Color.YELLOW);
                        else
                            paint.setColor(Color.RED);
                        canvas.drawCircle(x, y, 3.6f, paint);
                    }
                });

                mChart.getViewport().setYAxisBoundsManual(false);
                mChart.addSeries(sgvSerie);
            } else {
                if (entries.length > 0) {
                    ((PointsGraphSeries) mChart.getSeries().get(0)).resetData(entries);
                }
            }

            // set vieport to latest SGV
            long lastSGVTimestamp = (long) mChart.getSeries().get(0).getHighestValueX();
            if (!hasZoomedChart) {
                mChart.getViewport().setMaxX(lastSGVTimestamp);
                mChart.getViewport().setMinX(lastSGVTimestamp - chartZoom * 60 * 60 * 1000);
            }
        }
    }

    /**
     * has to be done in MainActivity thread
     */
    private class UpdatePumpReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            // If the MainActivity has already been destroyed (meaning the Realm instance has been closed)
            // then don't worry about processing this broadcast
            if (mRealm.isClosed()) {
                return;
            }
            //init local pump listener
            getActivePump();
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
