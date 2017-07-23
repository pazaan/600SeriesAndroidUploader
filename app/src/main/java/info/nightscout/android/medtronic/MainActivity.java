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
import android.text.format.DateUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TextView.BufferType;
import android.widget.Toast;

import com.github.javiersantos.appupdater.AppUpdater;
import com.github.javiersantos.appupdater.enums.UpdateFrom;
import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import info.nightscout.android.R;
import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.eula.Eula;
import info.nightscout.android.eula.Eula.OnEulaAgreedTo;
import info.nightscout.android.medtronic.service.MedtronicCnlAlarmManager;
import info.nightscout.android.medtronic.service.MedtronicCnlIntentService;
import info.nightscout.android.model.medtronicNg.PumpInfo;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.settings.SettingsActivity;
import info.nightscout.android.utils.ConfigurationStore;
import info.nightscout.android.utils.DataStore;
import io.realm.Realm;
import io.realm.RealmChangeListener;
import io.realm.RealmResults;
import io.realm.Sort;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

public class MainActivity extends AppCompatActivity implements OnSharedPreferenceChangeListener, OnEulaAgreedTo {
    private static final String TAG = MainActivity.class.getSimpleName();
    public static final int USB_DISCONNECT_NOFICATION_ID = 1;
    public static final float MMOLXLFACTOR = 18.016f;

    private DataStore dataStore = DataStore.getInstance();
    private ConfigurationStore configurationStore = ConfigurationStore.getInstance();

    private int chartZoom = 3;
    private boolean hasZoomedChart = false;

    private boolean mEnableCgmService = true;
    private SharedPreferences prefs = null;
    private PumpInfo mActivePump;
    private TextView mTextViewLog; // This will eventually move to a status page.
    private ScrollView mScrollView;
    private GraphView mChart;
    private Handler mUiRefreshHandler = new Handler();
    private Runnable mUiRefreshRunnable = new RefreshDisplayRunnable();
    private Realm mRealm;
    private StatusMessageReceiver statusMessageReceiver = new StatusMessageReceiver();
    private UsbReceiver usbReceiver = new UsbReceiver();
    private BatteryReceiver batteryReceiver = new BatteryReceiver();

    private DateFormat dateFormatter = new SimpleDateFormat("HH:mm:ss", Locale.US);

    protected void sendStatus(String message) {
        Intent localIntent =
                new Intent(MedtronicCnlIntentService.Constants.ACTION_STATUS_MESSAGE)
                        .putExtra(MedtronicCnlIntentService.Constants.EXTENDED_DATA, message);
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    /**
     * calculate the next poll timestamp based on last svg event
     *
     * @param pumpStatusData
     * @return timestamp
     */
    public static long getNextPoll(PumpStatusEvent pumpStatusData) {
        long nextPoll = pumpStatusData.getSgvDate().getTime(),
                now = System.currentTimeMillis(),
                pollInterval = ConfigurationStore.getInstance().getPollInterval();

        // align to next poll slot
        if (nextPoll + 2 * 60 * 60 * 1000 < now) { // last event more than 2h old -> could be a calibration
            nextPoll = System.currentTimeMillis() + 1000;
        } else {
            // align to poll interval
            nextPoll += (((now - nextPoll) / pollInterval)) * pollInterval
                    + MedtronicCnlIntentService.POLL_GRACE_PERIOD_MS;
            if (pumpStatusData.getBatteryPercentage() > 25) {
                // poll every 5 min
                nextPoll += pollInterval;
            } else {
                // if pump battery seems to be empty reduce polling to save battery (every 15 min)
                //TODO add message & document it
                nextPoll += ConfigurationStore.getInstance().getLowBatteryPollInterval();
            }
        }

        return nextPoll;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate called");
        super.onCreate(savedInstanceState);

        mRealm = Realm.getDefaultInstance();

        RealmResults<PumpStatusEvent> data = mRealm.where(PumpStatusEvent.class)
                .findAllSorted("eventDate", Sort.DESCENDING);
        if (data.size() > 0)
            dataStore.setLastPumpStatus(data.first());

        setContentView(R.layout.activity_main);

        PreferenceManager.getDefaultSharedPreferences(getBaseContext()).registerOnSharedPreferenceChangeListener(this);
        prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        if (!prefs.getBoolean(getString(R.string.preference_eula_accepted), false)) {
            stopCgmService();
        }

        // setup preferences
        configurationStore.setPollInterval(Long.parseLong(prefs.getString("pollInterval", Long.toString(MedtronicCnlIntentService.POLL_PERIOD_MS))));
        configurationStore.setLowBatteryPollInterval(Long.parseLong(prefs.getString("lowBatPollInterval", Long.toString(MedtronicCnlIntentService.LOW_BATTERY_POLL_PERIOD_MS))));
        configurationStore.setReducePollOnPumpAway(prefs.getBoolean("doublePollOnPumpAway", false));

        chartZoom = Integer.parseInt(prefs.getString("chartZoom", "3"));
        configurationStore.setMmolxl(prefs.getBoolean("mmolxl", false));
        configurationStore.setMmolxlDecimals(prefs.getBoolean("mmolDecimals", false));

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
        registerReceiver(batteryReceiver, batteryIntentFilter);

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
                .withName("Registered devices")
                .withIcon(GoogleMaterial.Icon.gmd_usb)
                .withSelectable(false);
        final PrimaryDrawerItem itemStopCollecting = new PrimaryDrawerItem()
                .withName("Stop collecting data")
                .withIcon(GoogleMaterial.Icon.gmd_power_settings_new)
                .withSelectable(false);
        final PrimaryDrawerItem itemGetNow = new PrimaryDrawerItem()
                .withName("Read data now")
                .withIcon(GoogleMaterial.Icon.gmd_refresh)
                .withSelectable(false);
        final PrimaryDrawerItem itemUpdateProfile = new PrimaryDrawerItem()
                .withName("Update pump profile")
                .withIcon(GoogleMaterial.Icon.gmd_insert_chart)
                .withSelectable(false);
        final PrimaryDrawerItem itemClearLog = new PrimaryDrawerItem()
                .withName("Clear log")
                .withIcon(GoogleMaterial.Icon.gmd_clear_all)
                .withSelectable(false);
        final PrimaryDrawerItem itemCheckForUpdate = new PrimaryDrawerItem()
                .withName("Check for App update")
                .withIcon(GoogleMaterial.Icon.gmd_update)
                .withSelectable(false);

        assert toolbar != null;
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
                        //itemUpdateProfile, // TODO - re-add when we to add Basal Profile Upload
                        itemRegisterUsb,
                        itemCheckForUpdate,
                        itemClearLog,
                        itemGetNow,
                        itemStopCollecting
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
                            // It was triggered by user so start reading of data now and not based on last poll.
                            sendStatus("Requesting poll now...");
                            startCgmService(System.currentTimeMillis() + 1000);
                        } else if (drawerItem.equals(itemClearLog)) {
                            clearLogText();
                        } else if (drawerItem.equals(itemCheckForUpdate)) {
                            checkForUpdateNow();
                        }

                        return false;
                    }
                })
                .build();

        mTextViewLog = (TextView) findViewById(R.id.textview_log);
        mScrollView = (ScrollView) findViewById(R.id.scrollView);
        mScrollView.setSmoothScrollingEnabled(true);

        mChart = (GraphView) findViewById(R.id.chart);

        // disable scrolling at the moment
        mChart.getViewport().setScalable(false);
        mChart.getViewport().setScrollable(false);
        mChart.getViewport().setYAxisBoundsManual(true);
        mChart.getViewport().setMinY(80);
        mChart.getViewport().setMaxY(120);
        mChart.getViewport().setXAxisBoundsManual(true);
        final long now = System.currentTimeMillis(),
                left = now - chartZoom * 60 * 60 * 1000;

        mChart.getViewport().setMaxX(now);
        mChart.getViewport().setMinX(left);

// due to bug in GraphView v4.2.1 using setNumHorizontalLabels reverted to using v4.0.1 and setOnXAxisBoundsChangedListener is n/a in this version
/*
        mChart.getViewport().setOnXAxisBoundsChangedListener(new Viewport.OnXAxisBoundsChangedListener() {
            @Override
            public void onXAxisBoundsChanged(double minX, double maxX, Reason reason) {
                double rightX = mChart.getSeries().get(0).getHighestValueX();
                hasZoomedChart = (rightX != maxX || rightX - chartZoom * 60 * 60 * 1000 != minX);
            }
        });
*/
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

// due to bug in GraphView v4.2.1 using setNumHorizontalLabels reverted to using v4.0.1 and setHumanRounding is n/a in this version
//        mChart.getGridLabelRenderer().setHumanRounding(false);

        mChart.getGridLabelRenderer().setLabelFormatter(
                new DefaultLabelFormatter() {
                    DateFormat mFormat = new SimpleDateFormat("HH:mm", Locale.US);  // 24 hour format forced to fix label overlap

                    @Override
                    public String formatLabel(double value, boolean isValueX) {
                        if (isValueX) {
                            return mFormat.format(new Date((long) value));
                        } else {
                            return MainActivity.strFormatSGV(value);
                        }
                    }
                }
        );
    }

    @Override
    protected void onStart() {
        super.onStart();
        checkForUpdateBackground(5);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        startDisplayRefreshLoop();
        statusStartup();
        startCgmService();
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
                // TODO - remove when we want to re-add the status menu item
                //Intent intent = new Intent(this, StatusActivity.class);
                //startActivity(intent);
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

    private void clearLogText() {
        statusMessageReceiver.clearMessages();
    }

    private void checkForUpdateNow() {
        new AppUpdater(this)
                .setUpdateFrom(UpdateFrom.JSON)
                .setUpdateJSON("https://raw.githubusercontent.com/pazaan/600SeriesAndroidUploader/master/app/update.json")
                .showAppUpdated(true) // Show a dialog, even if there isn't an update
                .start();
    }

    private void checkForUpdateBackground(int checkEvery) {
        new AppUpdater(this)
                .setUpdateFrom(UpdateFrom.JSON)
                .setUpdateJSON("https://raw.githubusercontent.com/pazaan/600SeriesAndroidUploader/master/app/update.json")
                .showEvery(checkEvery) // Only check for an update every `checkEvery` invocations
                .start();
    }

    private void statusStartup() {
        sendStatus(MedtronicCnlIntentService.ICON_HEART + "Nightscout 600 Series Uploader");
        sendStatus(MedtronicCnlIntentService.ICON_SETTING + "Poll interval: " + (configurationStore.getPollInterval() / 60000) +" minutes");
        sendStatus(MedtronicCnlIntentService.ICON_SETTING + "Low battery poll interval: " + (configurationStore.getLowBatteryPollInterval() / 60000) +" minutes");
    }

    private void refreshDisplay() {
        cancelDisplayRefreshLoop();
        mUiRefreshHandler.post(mUiRefreshRunnable);;
    }
    private void refreshDisplay(int delay) {
        cancelDisplayRefreshLoop();
        mUiRefreshHandler.postDelayed(mUiRefreshRunnable, delay);
    }

    private void startDisplayRefreshLoop() {
        refreshDisplay(50);
    }

    private void cancelDisplayRefreshLoop() {
        mUiRefreshHandler.removeCallbacks(mUiRefreshRunnable);
    }

    private void startCgmService() {
        startCgmServiceDelayed(0);
    }

    private void startCgmServiceDelayed(long delay) {
        if (!mRealm.isClosed()) {
            RealmResults<PumpStatusEvent> results = mRealm.where(PumpStatusEvent.class)
                    .findAllSorted("eventDate", Sort.DESCENDING);
            if (results.size() > 0) {
                long nextPoll = getNextPoll(results.first());
                long pollInterval = results.first().getBatteryPercentage() > 25 ? ConfigurationStore.getInstance().getPollInterval() : ConfigurationStore.getInstance().getLowBatteryPollInterval();
                if ((nextPoll - MedtronicCnlIntentService.POLL_GRACE_PERIOD_MS - results.first().getSgvDate().getTime()) <= pollInterval) {
                    startCgmService(nextPoll + delay);
                    sendStatus("Next poll due at: " + dateFormatter.format(nextPoll + delay));
                    return;
                }
            }
        }
        startCgmService(System.currentTimeMillis() + (delay == 0 ? 1000 : delay));
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

    private void stopCgmService() {
        Log.i(TAG, "stopCgmService called");
        MedtronicCnlAlarmManager.cancelAlarm();
    }

    private void showDisconnectionNotification(String title, String message) {
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
        mNotificationManager.notify(USB_DISCONNECT_NOFICATION_ID, mBuilder.build());
    }

    private void clearDisconnectionNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(MainActivity.USB_DISCONNECT_NOFICATION_ID);
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "onResume called");
        super.onResume();
        // Focus status log to most recent on returning to app
        mScrollView.post(new Runnable() {
            public void run() {
                mScrollView.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy called");
        super.onDestroy();

        unregisterReceiver(usbReceiver);
        unregisterReceiver(batteryReceiver);

        PreferenceManager.getDefaultSharedPreferences(getBaseContext()).unregisterOnSharedPreferenceChangeListener(this);
        cancelDisplayRefreshLoop();

        if (!mRealm.isClosed()) {
            mRealm.close();
        }
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
            configurationStore.setMmolxl(sharedPreferences.getBoolean("mmolxl", false));
            configurationStore.setMmolxlDecimals(sharedPreferences.getBoolean("mmolDecimals", false));
            refreshDisplay();
        } else if (key.equals("pollInterval")) {
            configurationStore.setPollInterval(Long.parseLong(sharedPreferences.getString("pollInterval",
                    Long.toString(MedtronicCnlIntentService.POLL_PERIOD_MS))));
        } else if (key.equals("lowBatPollInterval")) {
            configurationStore.setLowBatteryPollInterval(Long.parseLong(sharedPreferences.getString("lowBatPollInterval",
                    Long.toString(MedtronicCnlIntentService.LOW_BATTERY_POLL_PERIOD_MS))));
        } else if (key.equals("doublePollOnPumpAway")) {
            configurationStore.setReducePollOnPumpAway(sharedPreferences.getBoolean("doublePollOnPumpAway", false));
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

    private PumpInfo getActivePump() {
        long activePumpMac = dataStore.getActivePumpMac();
        if (activePumpMac != 0L && (mActivePump == null || !mActivePump.isValid() || mActivePump.getPumpMac() != activePumpMac)) {
            if (mActivePump != null) {
                // remove listener on old pump
                mRealm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(Realm sRealm) {
                        mActivePump.removeAllChangeListeners();
                    }
                });
                mActivePump = null;
            }

            PumpInfo pump = mRealm
                    .where(PumpInfo.class)
                    .equalTo("pumpMac", activePumpMac)
                    .findFirst();

            if (pump != null && pump.isValid()) {

                // first change listener start can miss fresh data and not update until next poll, force a refresh now
                RemoveOutdatedRecords();
                refreshDisplay(1000);

                mActivePump = pump;
                mActivePump.addChangeListener(new RealmChangeListener<PumpInfo>() {
                    long lastQueryTS = 0;

                    @Override
                    public void onChange(PumpInfo pump) {
                        // prevent double updating after deleting old events below
                        if (pump.getLastQueryTS() == lastQueryTS || !pump.isValid()) {
                            return;
                        }

                        lastQueryTS = pump.getLastQueryTS();

                        RemoveOutdatedRecords();
                        refreshDisplay(1000);

                        // TODO - handle isOffline in NightscoutUploadIntentService?
                    }
                });
            }
        }

        return mActivePump;
    }

    private void RemoveOutdatedRecords() {
        // Delete invalid or old records from Realm
        // TODO - show an error message if the valid records haven't been uploaded
        final RealmResults<PumpStatusEvent> results =
                mRealm.where(PumpStatusEvent.class)
                        .equalTo("sgv", 0)
                        .or()
                        .lessThan("sgvDate", new Date(System.currentTimeMillis() - (24 * 60 * 60 * 1000)))
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
    }

    public static String strFormatSGV(double sgvValue) {
        ConfigurationStore configurationStore = ConfigurationStore.getInstance();

        NumberFormat sgvFormatter;
        if (configurationStore.isMmolxl()) {
            if (configurationStore.isMmolxlDecimals()) {
                sgvFormatter = new DecimalFormat("0.00");
            } else {
                sgvFormatter = new DecimalFormat("0.0");
            }
            return sgvFormatter.format(sgvValue / MMOLXLFACTOR);
        } else {
            sgvFormatter = new DecimalFormat("0");
            return sgvFormatter.format(sgvValue);
        }
    }

    public static String renderTrendSymbol(PumpStatusEvent.CGM_TREND trend) {
        // TODO - symbols used for trend arrow may vary per device, find a more robust solution
        switch (trend) {
            case DOUBLE_UP:
                return "\u21c8";
            case SINGLE_UP:
                return "\u2191";
            case FOURTY_FIVE_UP:
                return "\u2197";
            case FLAT:
                return "\u2192";
            case FOURTY_FIVE_DOWN:
                return "\u2198";
            case SINGLE_DOWN:
                return "\u2193";
            case DOUBLE_DOWN:
                return "\u21ca";
            default:
                return "\u2014";
        }
    }

    private class StatusMessageReceiver extends BroadcastReceiver {
        private class StatusMessage {
            private long timestamp;
            private String message;

            StatusMessage(String message) {
                this(System.currentTimeMillis(), message);
            }

            StatusMessage(long timestamp, String message) {
                this.timestamp = timestamp;
                this.message = message;
            }

            public String toString() {
                return DateFormat.getTimeInstance(DateFormat.MEDIUM).format(timestamp) + ": " + message;
            }
        }

        private final Queue<StatusMessage> messages = new ArrayBlockingQueue<>(400);

        @Override
        public void onReceive(Context context, Intent intent) {
            String message = intent.getStringExtra(MedtronicCnlIntentService.Constants.EXTENDED_DATA);
            Log.i(TAG, "Message Receiver: " + message);

            synchronized (messages) {
                while (messages.size() > 398) {
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

            // auto scroll status log
            if ((mScrollView.getChildAt(0).getBottom() < mScrollView.getHeight()) || ((mScrollView.getChildAt(0).getBottom() - mScrollView.getScrollY() - mScrollView.getHeight()) < (mScrollView.getHeight() / 3))) {
                mScrollView.post(new Runnable() {
                    public void run() {
                        mScrollView.fullScroll(View.FOCUS_DOWN);
                    }
                });
            }
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
            long nextRun = 60000L;

            TextView textViewBg = (TextView) findViewById(R.id.textview_bg);
            TextView textViewBgTime = (TextView) findViewById(R.id.textview_bg_time);
            TextView textViewUnits = (TextView) findViewById(R.id.textview_units);
            if (configurationStore.isMmolxl()) {
                textViewUnits.setText(R.string.text_unit_mmolxl);
            } else {
                textViewUnits.setText(R.string.text_unit_mgxdl);
            }
            TextView textViewTrend = (TextView) findViewById(R.id.textview_trend);
            TextView textViewIOB = (TextView) findViewById(R.id.textview_iob);

            // Get the most recently written CGM record for the active pump.
            PumpStatusEvent pumpStatusData = null;

            if (dataStore.getLastPumpStatus().getEventDate().getTime() > 0) {
                pumpStatusData = dataStore.getLastPumpStatus();
            }

            updateChart(mRealm.where(PumpStatusEvent.class)
                    .notEqualTo("sgv", 0)
                    .greaterThan("sgvDate", new Date(System.currentTimeMillis() - 1000 * 60 * 60 * 24))
                    .findAllSorted("sgvDate", Sort.ASCENDING));

            if (pumpStatusData != null) {
                String sgvString;
                if (pumpStatusData.isCgmActive()) {
                    sgvString = MainActivity.strFormatSGV(pumpStatusData.getSgv());
                    if (configurationStore.isMmolxl()) {
                        Log.d(TAG, sgvString + " mmol/L");
                    } else {
                        Log.d(TAG, sgvString + " mg/dL");
                    }
                } else {
                    sgvString = "\u2014"; // &mdash;
                }

                nextRun = 60000L - (System.currentTimeMillis() - pumpStatusData.getSgvDate().getTime()) % 60000L;
                textViewBg.setText(sgvString);
                textViewBgTime.setText(DateUtils.getRelativeTimeSpanString(pumpStatusData.getSgvDate().getTime()));

                textViewTrend.setText(MainActivity.renderTrendSymbol(pumpStatusData.getCgmTrend()));
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

            // Run myself again in 60 (or less) seconds;
            mUiRefreshHandler.postDelayed(this, nextRun);
        }

        private void updateChart(RealmResults<PumpStatusEvent> results) {

            mChart.getGridLabelRenderer().setNumHorizontalLabels(6);

            int size = results.size();
            if (size == 0) {
                final long now = System.currentTimeMillis(),
                        left = now - chartZoom * 60 * 60 * 1000;

                mChart.getViewport().setXAxisBoundsManual(true);
                mChart.getViewport().setMaxX(now);
                mChart.getViewport().setMinX(left);

                mChart.getViewport().setYAxisBoundsManual(true);
                mChart.getViewport().setMinY(80);
                mChart.getViewport().setMaxY(120);

                mChart.postInvalidate();
                return;
            }

            DataPoint[] entries = new DataPoint[size];

            int pos = 0;
            for (PumpStatusEvent pumpStatus : results) {
                // turn your data into Entry objects
                entries[pos++] = new DataPoint(pumpStatus.getSgvDate(), (double) pumpStatus.getSgv());
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
                        sb.append(MainActivity.strFormatSGV(sgv));
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
                        float dotSize = 3.0f;
                        if (chartZoom == 3) dotSize = 2.0f;
                        else if (chartZoom == 6) dotSize = 2.0f;
                        else if (chartZoom == 12) dotSize = 1.65f;
                        else if (chartZoom == 24) dotSize = 1.25f;
                        canvas.drawCircle(x, y, dipToPixels(getApplicationContext(), dotSize), paint);
                    }
                });

                mChart.getViewport().setYAxisBoundsManual(false);
                mChart.addSeries(sgvSerie);
            } else {
                if (entries.length > 0) {
                    ((PointsGraphSeries) mChart.getSeries().get(0)).resetData(entries);
                }
            }

            // TODO - chart viewport needs rework as currently using a workaround to handle updating

            // set viewport to latest SGV
            long lastSGVTimestamp = (long) mChart.getSeries().get(0).getHighestValueX();

            long min_x = (((lastSGVTimestamp + 150000 - (chartZoom * 60 * 60 * 1000)) / 60000) * 60000);
            long max_x = lastSGVTimestamp + 90000;

            if (!hasZoomedChart) {
                mChart.getViewport().setMinX(min_x);
                mChart.getViewport().setMaxX(max_x);
            }
            if (entries.length > 0) {
                ((PointsGraphSeries) mChart.getSeries().get(0)).resetData(entries);
            }

        }
    }

    private static float dipToPixels(Context context, float dipValue) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue, metrics);
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
            // TODO move this somewhere else ... wherever it belongs
            // realm might be closed ... sometimes occurs when USB is disconnected and replugged ...
            if (mRealm.isClosed()) mRealm = Realm.getDefaultInstance();
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
                if (mEnableCgmService) {
                    clearDisconnectionNotification();
                }
                dataStore.clearAllCommsErrors();
                sendStatus(MedtronicCnlIntentService.ICON_INFO + "Contour Next Link plugged in.");
                if (hasUsbPermission()) {
                    // Give the USB a little time to warm up first
                    startCgmServiceDelayed(MedtronicCnlIntentService.USB_WARMUP_TIME_MS);
                } else {
                    Log.d(TAG, "No permission for USB. Waiting.");
                    waitForUsbPermission();
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                Log.d(TAG, "USB unplugged");
                if (mEnableCgmService) {
                    showDisconnectionNotification("USB Error", "Contour Next Link unplugged.");
                    sendStatus(MedtronicCnlIntentService.ICON_WARN + "USB error. Contour Next Link unplugged.");
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
                dataStore.setUploaderBatteryLevel(arg1.getIntExtra(BatteryManager.EXTRA_LEVEL, 0));
            }
        }
    }

}
