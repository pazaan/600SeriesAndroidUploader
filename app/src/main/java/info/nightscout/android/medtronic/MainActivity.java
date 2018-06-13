package info.nightscout.android.medtronic;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.menu.MenuView;
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
//import com.jaredrummler.android.device.DeviceName;
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

import info.nightscout.android.R;
import info.nightscout.android.UploaderApplication;
import info.nightscout.android.eula.Eula;
import info.nightscout.android.eula.Eula.OnEulaAgreedTo;
import info.nightscout.android.medtronic.service.MasterService;
import info.nightscout.android.medtronic.service.MedtronicCnlService;
import info.nightscout.android.model.medtronicNg.PumpHistoryCGM;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.settings.SettingsActivity;
import info.nightscout.android.model.store.DataStore;
import info.nightscout.android.model.store.UserLog;
import info.nightscout.android.utils.FormatKit;
import io.realm.OrderedCollectionChangeSet;
import io.realm.OrderedRealmCollectionChangeListener;
import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

import static info.nightscout.android.medtronic.UserLogMessage.Icons.ICON_HEART;
import static info.nightscout.android.medtronic.UserLogMessage.Icons.ICON_INFO;
import static info.nightscout.android.medtronic.UserLogMessage.Icons.ICON_SETTING;
import static org.apache.commons.lang3.math.NumberUtils.toInt;

public class MainActivity extends AppCompatActivity implements OnSharedPreferenceChangeListener, OnEulaAgreedTo {
    private static final String TAG = MainActivity.class.getSimpleName();

    public static final float MMOLXLFACTOR = 18.016f;

    private UserLogMessage userLogMessage = UserLogMessage.getInstance();

    private int chartZoom = 3;
    private boolean hasZoomedChart = false;

    private boolean mEnableCgmService;// = true;
    private SharedPreferences prefs;// = null;

    private TextView mTextViewLog; // This will eventually move to a status page.
    private TextView mTextViewLogButtonTop;
    private TextView mTextViewLogButtonTopRecent;
    private TextView mTextViewLogButtonBottom;
    private TextView mTextViewLogButtonBottomRecent;

    private ScrollView mScrollView;
    private GraphView mChart;
    private Handler mUiRefreshHandler = new Handler();
    private Runnable mUiRefreshRunnable = new RefreshDisplayRunnable();

    private Realm mRealm;
    private Realm storeRealm;
    private Realm userLogRealm;
    private Realm historyRealm;

    private DataStore dataStore;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate called");
        super.onCreate(savedInstanceState);

        try {
            if (Realm.compactRealm(Realm.getDefaultConfiguration()))
                Log.i(TAG, "compactRealm: default successful");
            if (Realm.compactRealm(UploaderApplication.getStoreConfiguration()))
                Log.i(TAG, "compactRealm: store successful");
            if (Realm.compactRealm(UploaderApplication.getUserLogConfiguration()))
                Log.i(TAG, "compactRealm: userlog successful");
            if (Realm.compactRealm(UploaderApplication.getHistoryConfiguration()))
                Log.i(TAG, "compactRealm: history successful");
        } catch (Exception e) {
            Log.e(TAG, "Error trying to compact realm" + Log.getStackTraceString(e));
        }

        storeRealm = Realm.getInstance(UploaderApplication.getStoreConfiguration());
        dataStore = storeRealm.where(DataStore.class).findFirst();
        if (dataStore == null) {
            storeRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {
                    dataStore = realm.createObject(DataStore.class);
                }
            });
        }

        // limit date for NS backfill sync period, set to this init date to stop overwrite of older NS data (pref option to override)
        if (dataStore.getNightscoutLimitDate() == null) {
            storeRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {
                    dataStore.setNightscoutLimitDate(new Date(System.currentTimeMillis()));
                }
            });
        }

        prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        prefs.registerOnSharedPreferenceChangeListener(this);
        copyPrefsToDataStore(prefs);

        setContentView(R.layout.activity_main);

        // Disable battery optimization to avoid missing values on 6.0+
        // taken from https://github.com/NightscoutFoundation/xDrip/blob/master/app/src/main/java/com/eveningoutpost/dexdrip/Home.java#L277L298
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            final String packageName = getPackageName();
            final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);

            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
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

        mEnableCgmService = Eula.show(this, prefs)
                && prefs.getBoolean(getString(R.string.preference_eula_accepted), false);

        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            getSupportActionBar().setElevation(0);
            getSupportActionBar().setTitle("Nightscout");
        }

        final PrimaryDrawerItem itemSettings = new PrimaryDrawerItem()
                .withName(R.string.main_menu_settings)
                .withIcon(GoogleMaterial.Icon.gmd_settings)
                .withSelectable(false);
        final PrimaryDrawerItem itemRegisterUsb = new PrimaryDrawerItem()
                .withName(R.string.main_menu_registered_devices)
                .withIcon(GoogleMaterial.Icon.gmd_usb)
                .withSelectable(false);
        final PrimaryDrawerItem itemStopCollecting = new PrimaryDrawerItem()
                .withName(R.string.main_menu_stop_collecting_data)
                .withIcon(GoogleMaterial.Icon.gmd_power_settings_new)
                .withSelectable(false);
        final PrimaryDrawerItem itemGetNow = new PrimaryDrawerItem()
                .withName(R.string.main_menu_read_data_now)
                .withIcon(GoogleMaterial.Icon.gmd_refresh)
                .withSelectable(false);
        final PrimaryDrawerItem itemUpdateProfile = new PrimaryDrawerItem()
                .withName(R.string.main_menu_update_pump_profile)
                .withIcon(GoogleMaterial.Icon.gmd_insert_chart)
                .withSelectable(false);
        final PrimaryDrawerItem itemClearLog = new PrimaryDrawerItem()
                .withName(R.string.main_menu_clear_log)
                .withIcon(GoogleMaterial.Icon.gmd_clear_all)
                .withSelectable(false);
        final PrimaryDrawerItem itemCheckForUpdate = new PrimaryDrawerItem()
                .withName(R.string.main_menu_check_for_app_update)
                .withIcon(GoogleMaterial.Icon.gmd_update)
                .withSelectable(false);

        assert toolbar != null;
        DrawerBuilder drawerBuilder = new DrawerBuilder();
        drawerBuilder
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
                        itemCheckForUpdate,
                        itemUpdateProfile,
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
                            finish();
                        } else if (drawerItem.equals(itemGetNow)) {
                            // It was triggered by user so start reading of data now and not based on last poll.
                            if (mEnableCgmService) {
                                userLogMessage.add(getString(R.string.main_requesting_poll_now));
                                sendBroadcast(new Intent(MasterService.Constants.ACTION_READ_NOW));
                            } else {
                                userLogMessage.add(getString(R.string.main_cgm_service_disabled));
                            }
                        } else if (drawerItem.equals(itemUpdateProfile)) {
                            if (mEnableCgmService) {
                                if (dataStore.isNsEnableProfileUpload()) {
                                    userLogMessage.add(getString(R.string.main_requesting_pump_profile));
                                    sendBroadcast(new Intent(MasterService.Constants.ACTION_READ_PROFILE));
                                } else {
                                    userLogMessage.add(getString(R.string.main_pump_profile_disabled));
                                }
                            } else {
                                userLogMessage.add(getString(R.string.main_cgm_service_disabled));
                            }
                        } else if (drawerItem.equals(itemClearLog)) {
                            userLogMessage.clear();
                        } else if (drawerItem.equals(itemCheckForUpdate)) {
                            checkForUpdateNow();
                        }

                        return false;
                    }
                })
                .build();

        mTextViewLog = findViewById(R.id.textview_log);
        mScrollView = findViewById(R.id.scrollView);
        mScrollView.setSmoothScrollingEnabled(true);
        mTextViewLogButtonTop = findViewById(R.id.button_log_top);
        mTextViewLogButtonTop.setVisibility(View.GONE);
        mTextViewLogButtonTop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeUserLogViewOlder();
            }
        });
        mTextViewLogButtonTopRecent = findViewById(R.id.button_log_top_recent);
        mTextViewLogButtonTopRecent.setVisibility(View.GONE);
        mTextViewLogButtonTopRecent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeUserLogViewRecent();
            }
        });

        mTextViewLogButtonBottom = findViewById(R.id.button_log_bottom);
        mTextViewLogButtonBottom.setVisibility(View.GONE);
        mTextViewLogButtonBottom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeUserLogViewNewer();
            }
        });
        mTextViewLogButtonBottomRecent = findViewById(R.id.button_log_bottom_recent);
        mTextViewLogButtonBottomRecent.setVisibility(View.GONE);
        mTextViewLogButtonBottomRecent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeUserLogViewRecent();
            }
        });

        chartZoom = Integer.parseInt(prefs.getString("chartZoom", "3"));

        mChart = findViewById(R.id.chart);

        // disable scrolling at the moment
        mChart.getViewport().setScalable(false);
        mChart.getViewport().setScrollable(false);
        mChart.getViewport().setYAxisBoundsManual(true);
        mChart.getViewport().setMinY(80);
        mChart.getViewport().setMaxY(120);
        mChart.getViewport().setXAxisBoundsManual(true);
        final long now = System.currentTimeMillis(),
                left = now - chartZoom * 60 * 60 * 1000L;

        mChart.getViewport().setMaxX(now);
        mChart.getViewport().setMinX(left);

        mChart.getGridLabelRenderer().setTextSize(35f);
        mChart.getGridLabelRenderer().reloadStyles();

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
                    mChart.getViewport().setMinX(rightX - chartZoom * 60 * 60 * 1000L);
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
                    //DateFormat mFormat = new SimpleDateFormat("HH:mm", Locale.US);  // 24 hour format forced to fix label overlap
                    DateFormat mFormat = new SimpleDateFormat("h:mm", Locale.getDefault());

                    @Override
                    public String formatLabel(double value, boolean isValueX) {
                        if (isValueX) {
                            //return FormatKit.getInstance().formatAsClock((long) value);
                            return mFormat.format(new Date((long) value));
                        } else {
                            return strFormatSGV(value);
                        }
                    }
                }
        );

        /*
        DeviceName.with(this).request(new DeviceName.Callback() {

            @Override public void onFinished(DeviceName.DeviceInfo info, Exception error) {
                String manufacturer = info.manufacturer;  // "Samsung"
                String name = info.marketName;            // "Galaxy S8+"
                String model = info.model;                // "SM-G955W"
                String codename = info.codename;          // "dream2qltecan"
                String deviceName = info.getName();       // "Galaxy S8+"

                Log.i(TAG, "manufacturer = " + info.manufacturer);
                Log.i(TAG, "name = " + info.marketName);
                Log.i(TAG, "model = " + info.model);
                Log.i(TAG, "codename = " + info.codename);
                Log.i(TAG, "deviceName = " + info.getName());
            }
        });
        */

        FormatKit.getInstance(this);
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart called");
        super.onStart();
        if (userLogRealm == null) userLogRealm = Realm.getInstance(UploaderApplication.getUserLogConfiguration());
        if (historyRealm == null) historyRealm = Realm.getInstance(UploaderApplication.getHistoryConfiguration());
        if (mRealm == null) mRealm = Realm.getDefaultInstance();

        checkForUpdateBackground(5);

        startDisplay();
        startUserLogView();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume called");
        super.onResume();
    }

    protected void onPause() {
        Log.d(TAG, "onPause called");
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop called");
        super.onStop();

        stopUserLogView();
        stopDisplay();

        if (userLogRealm != null && !userLogRealm.isClosed()) userLogRealm.close();
        if (historyRealm != null && !historyRealm.isClosed()) historyRealm.close();
        if (mRealm != null && !mRealm.isClosed()) mRealm.close();
        userLogRealm = null;
        historyRealm = null;
        mRealm = null;
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy called");
        super.onDestroy();

        if (!mEnableCgmService) stopMasterService();

        statusDestroy();
        PreferenceManager.getDefaultSharedPreferences(getBaseContext()).unregisterOnSharedPreferenceChangeListener(this);

        FormatKit.close();

        if (storeRealm !=null && !storeRealm.isClosed()) storeRealm.close();
        if (userLogRealm != null && !userLogRealm.isClosed()) userLogRealm.close();
        if (historyRealm != null && !historyRealm.isClosed()) historyRealm.close();
        if (mRealm != null && !mRealm.isClosed()) mRealm.close();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onPostCreate called");
        super.onPostCreate(savedInstanceState);
        statusStartup();
        startMasterService();
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        Log.d(TAG, "attachBaseContext called");
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d(TAG, "onCreateOptionsMenu called");
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
        if (!prefs.getBoolean("EnableCgmService", false)) {
            userLogMessage.add(ICON_HEART + getString(R.string.main_hello));
            userLogMessage.add(ICON_SETTING + String.format(Locale.getDefault(), "%s %s",
                    getString(R.string.main_uploading),
                    dataStore.isNightscoutUpload() ? getString(R.string.text_enabled) : getString(R.string.text_disabled)));
            userLogMessage.add(ICON_SETTING + String.format(Locale.getDefault(), "%s %s",
                    getString(R.string.main_treatments),
                    dataStore.isNightscoutUpload() ? getString(R.string.text_enabled) : getString(R.string.text_disabled)));
            userLogMessage.add(ICON_SETTING + String.format(Locale.getDefault(), "%s %d %s",
                    getString(R.string.main_poll_interval),
                    dataStore.getLowBatPollInterval() / 60000,
                    getString(R.string.time_min)));
            userLogMessage.add(ICON_SETTING + String.format(Locale.getDefault(), "%s %d %s",
                    getString(R.string.main_low_battery_poll_interval),
                    dataStore.getLowBatPollInterval() / 60000,
                    getString(R.string.time_min)));
            int historyFrequency = dataStore.getSysPumpHistoryFrequency();
            if (historyFrequency > 0) {
                userLogMessage.add(ICON_SETTING + String.format(Locale.getDefault(), "%s %d %s",
                        getString(R.string.main_auto_mode_update),
                        historyFrequency,
                        getString(R.string.time_min)));
            } else {
                userLogMessage.add(ICON_SETTING + getString(R.string.main_auto_mode_update) + " " + getString(R.string.main_events_only));
            }
        }
    }

    private void statusDestroy() {
        if (!prefs.getBoolean("EnableCgmService", false)) {
            userLogMessage.add(ICON_HEART + getString(R.string.main_goodbye));
            userLogMessage.add("---------------------------------------------------");
        }
    }

    private void startMasterService() {
        Log.i(TAG, "startMasterService called");
        if (mEnableCgmService) {
            prefs.edit().putBoolean("EnableCgmService", true).commit();
            startService(new Intent(this, MasterService.class));
        } else {
            prefs.edit().putBoolean("EnableCgmService", false).commit();
            Log.i(TAG, "startMasterService: CgmService is disabled");
        }
    }

    private void stopMasterService() {
        Log.i(TAG, "stopMasterService called");
        userLogMessage.add(ICON_INFO + getString(R.string.main_shutting_down_cgm_service));
        prefs.edit().putBoolean("EnableCgmService", false).commit();
        sendBroadcast(new Intent(MasterService.Constants.ACTION_STOP_SERVICE));
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Log.d(TAG, "onSharedPreferenceChanged called");
        if (key.equals(getString(R.string.preference_eula_accepted))) {
            if (!sharedPreferences.getBoolean(getString(R.string.preference_eula_accepted), false)) {
                mEnableCgmService = false;
                stopMasterService();
            } else {
                mEnableCgmService = true;
                startMasterService();
            }
        } else if (key.equals("chartZoom")) {
            chartZoom = Integer.parseInt(sharedPreferences.getString("chartZoom", "3"));
            hasZoomedChart = false;
        } else {
            copyPrefsToDataStore(sharedPreferences);
            if (mEnableCgmService)
                sendBroadcast(new Intent(MasterService.Constants.ACTION_URCHIN_UPDATE));
        }
    }

    public void copyPrefsToDataStore(final SharedPreferences sharedPreferences) {

        // prefs that are in constant use, safe across threads and processes

        final Context context = this.getBaseContext();

        storeRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(@NonNull Realm realm) {
                dataStore.copyPrefs(context, sharedPreferences);
            }
        });

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

    private String strFormatSGV(double sgvValue) {
        NumberFormat sgvFormatter;
        if (dataStore.isMmolxl()) {
            if (dataStore.isMmolxlDecimals()) {
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

    private RealmResults displayResults;
    private long timeLastSGV;
    private int pumpBattery;

    private void startDisplay() {
        Log.d(TAG, "startDisplay");

        displayResults = mRealm.where(PumpStatusEvent.class)
                .sort("eventDate", Sort.ASCENDING)
                .findAllAsync();

        displayResults.addChangeListener(new OrderedRealmCollectionChangeListener<RealmResults>() {
            @Override
            public void onChange(@NonNull RealmResults realmResults, OrderedCollectionChangeSet changeSet) {
                if (changeSet != null) {
                    Log.d(TAG, "display listener triggered");
                    if (changeSet.getInsertions().length > 0) {
                        refreshDisplay();
                    }
                }
            }
        });

        refreshDisplay();

        startDisplayCgm();
    }

    private void stopDisplay() {
        Log.d(TAG, "stopDisplay");
        displayResults.removeAllChangeListeners();
        displayResults = null;

        mUiRefreshHandler.removeCallbacks(mUiRefreshRunnable);

        stopDisplayCgm();
    }

    private void refreshDisplay() {
        Log.d(TAG, "refreshDisplay");

        mUiRefreshHandler.removeCallbacks(mUiRefreshRunnable);

        timeLastSGV = 0;
        pumpBattery = -1;

        TextView textViewBg = findViewById(R.id.textview_bg);
        TextView textViewUnits = findViewById(R.id.textview_units);
        if (dataStore.isMmolxl()) {
            textViewUnits.setText(R.string.text_unit_mmol);
        } else {
            textViewUnits.setText(R.string.text_unit_mgdl);
        }
        TextView textViewTrend = findViewById(R.id.textview_trend);
        TextView textViewIOB = findViewById(R.id.textview_iob);

        String sgvString = "\u2014"; // &mdash;
        String trendString = "{ion_ios_minus_empty}";
        int trendRotation = 0;
        float iob = 0;

        // most recent sgv status
        RealmResults<PumpStatusEvent> sgv_results =
                mRealm.where(PumpStatusEvent.class)
                        .equalTo("validSGV", true)
                        .sort("cgmDate", Sort.ASCENDING)
                        .findAll();

        if (sgv_results.size() > 0) {
            timeLastSGV = sgv_results.last().getCgmDate().getTime();
            sgvString = strFormatSGV(sgv_results.last().getSgv());

            switch (sgv_results.last().getCgmTrend()) {
                case DOUBLE_UP:
                    trendString = "{ion_ios_arrow_thin_up}{ion_ios_arrow_thin_up}";
                    break;
                case SINGLE_UP:
                    trendString = "{ion_ios_arrow_thin_up}";
                    break;
                case FOURTY_FIVE_UP:
                    trendRotation = -45;
                    trendString = "{ion_ios_arrow_thin_right}";
                    break;
                case FLAT:
                    trendString = "{ion_ios_arrow_thin_right}";
                    break;
                case FOURTY_FIVE_DOWN:
                    trendRotation = 45;
                    trendString = "{ion_ios_arrow_thin_right}";
                    break;
                case SINGLE_DOWN:
                    trendString = "{ion_ios_arrow_thin_down}";
                    break;
                case DOUBLE_DOWN:
                    trendString = "{ion_ios_arrow_thin_down}{ion_ios_arrow_thin_down}";
                    break;
                default:
                    trendString = "{ion_ios_minus_empty}";
                    break;
            }
        }

        // most recent pump status
        RealmResults<PumpStatusEvent> pump_results =
                mRealm.where(PumpStatusEvent.class)
                        .greaterThan("eventDate", new Date(System.currentTimeMillis() - 60 * 60000L))
                        .sort("eventDate", Sort.ASCENDING)
                        .findAll();

        if (pump_results.size() > 0) {
            iob = pump_results.last().getActiveInsulin();
            pumpBattery = pump_results.last().getBatteryPercentage();
        }

        textViewBg.setText(sgvString);
        textViewIOB.setText(String.format(Locale.getDefault(), "%.2f", iob));
        textViewTrend.setText(trendString);
        textViewTrend.setRotation(trendRotation);

        mUiRefreshHandler.post(mUiRefreshRunnable);
    }

    private class RefreshDisplayRunnable implements Runnable {
        @Override
        public void run() {
            Log.d(TAG, "refreshDisplayRunnable");
            long nextRun = 60000L;

            TextView textViewBgTime = findViewById(R.id.textview_bg_time);
            String timeString = getString(R.string.main_never);

            if (timeLastSGV > 0) {
                nextRun = 60000L - (System.currentTimeMillis() - timeLastSGV) % 60000L;
                timeString = (DateUtils.getRelativeTimeSpanString(timeLastSGV)).toString();
            }

            textViewBgTime.setText(timeString);

            MenuView.ItemView batIcon = findViewById(R.id.status_battery);
            if (batIcon != null) {
                switch (pumpBattery) {
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
            } else {
                // run again in 100ms if batIcon resource is not available yet
                nextRun = 100L;
            }

            // Run myself again in 60 (or less) seconds;
            mUiRefreshHandler.postDelayed(this, nextRun);
        }
    }

    private RealmResults displayCgmResults;

    private void startDisplayCgm() {
        stopDisplayCgm();

        Log.d(TAG, "startDisplayCgm");

        displayCgmResults = historyRealm.where(PumpHistoryCGM.class)
                .notEqualTo("sgv", 0)
                .sort("eventDate", Sort.ASCENDING)
                .findAllAsync();

        displayCgmResults.addChangeListener(new OrderedRealmCollectionChangeListener<RealmResults>() {
            @Override
            public void onChange(@NonNull RealmResults realmResults, OrderedCollectionChangeSet changeSet) {
                if (changeSet == null) {
                    // initial refresh after start
                    refreshDisplayCgm();
                } else if (changeSet.getInsertions().length > 0) {
                    // new items added
                    refreshDisplayCgm();
                }
            }
        });
    }

    private void stopDisplayCgm() {
        Log.d(TAG, "stopDisplayCgm");
        if (displayCgmResults != null) {
            displayCgmResults.removeAllChangeListeners();
            displayCgmResults = null;
        }
    }

    private void refreshDisplayCgm() {
        Log.d(TAG, "refreshDisplayCgm");

        RealmResults<PumpHistoryCGM> results = displayCgmResults;

        if (results.size() > 0) {
            long timeLastSGV = results.last().getEventDate().getTime();
            results = results.where()
                    .greaterThan("eventDate", new Date(timeLastSGV - 24 * 60 * 60 * 1000L))
                    .sort("eventDate", Sort.ASCENDING)
                    .findAll();
        }

        updateChart(results);
    }

    private void updateChart(RealmResults<PumpHistoryCGM> results) {

        mChart.getGridLabelRenderer().setNumHorizontalLabels(6);

        // empty chart when no data available
        int size = results.size();
        if (size == 0) {
            final long now = System.currentTimeMillis(),
                    left = now - chartZoom * 60 * 60 * 1000L;

            mChart.getViewport().setXAxisBoundsManual(true);
            mChart.getViewport().setMaxX(now);
            mChart.getViewport().setMinX(left);

            mChart.getViewport().setYAxisBoundsManual(true);
            mChart.getViewport().setMinY(80);
            mChart.getViewport().setMaxY(120);

            mChart.postInvalidate();
            return;
        }

        long timeLastSGV = results.last().getEventDate().getTime();

        // calc X & Y chart bounds with readable stepping for mmol & mg/dl
        // X needs offsetting as graphview will not always show points near edges
        long minX = (((timeLastSGV + 150000L - (chartZoom * 60 * 60 * 1000L)) / 60000L) * 60000L);
        long maxX = timeLastSGV + 90000L;

        RealmResults<PumpHistoryCGM> minmaxY = results.where()
                .greaterThan("eventDate",  new Date(minX))
                .sort("sgv", Sort.ASCENDING)
                .findAll();
/*
        long rangeY, minRangeY;
        long minY = minmaxY.first().getSgv();
        long maxY = minmaxY.last().getSgv();

        if (prefs.getBoolean("mmolxl", false)) {
            minY = (long) Math.floor((minY / MMOLXLFACTOR) * 2);
            maxY = (long) Math.ceil((maxY / MMOLXLFACTOR) * 2);
            rangeY = maxY - minY;
            minRangeY = ((rangeY / 4 ) + 1) * 4;
            minY = minY - (long) Math.floor((minRangeY - rangeY) / 2);
            maxY = minY + minRangeY;
            minY = (long) (minY * MMOLXLFACTOR / 2);
            maxY = (long) (maxY * MMOLXLFACTOR / 2);
        } else {
            minY = (long) Math.floor(minY / 10) * 10;
            maxY = (long) Math.ceil((maxY + 5) / 10) * 10;
            rangeY = maxY - minY;
            minRangeY = ((rangeY / 20 ) + 1) * 20;
            minY = minY - (long) Math.floor((minRangeY - rangeY) / 2);
            maxY = minY + minRangeY;
        }
*/
        long rangeY, minRangeY;
        double minY = minmaxY.first().getSgv();
        double maxY = minmaxY.last().getSgv();

        if (prefs.getBoolean("mmolxl", false)) {
            minY = Math.floor((minY / MMOLXLFACTOR) * 2);
            maxY = Math.ceil((maxY / MMOLXLFACTOR) * 2);
            rangeY = (long) (maxY - minY);
            minRangeY = ((rangeY / 4 ) + 1) * 4;
            minY = minY - Math.floor((minRangeY - rangeY) / 2);
            maxY = minY + minRangeY;
            minY = Math.floor(minY * MMOLXLFACTOR / 2);
            maxY = Math.floor(maxY * MMOLXLFACTOR / 2);
        } else {
            minY = Math.floor(minY / 10) * 10;
            maxY = Math.ceil(maxY / 10) * 10;
            rangeY = (long) (maxY - minY);
            minRangeY = ((rangeY / 20 ) + 1) * 20;
            minY = minY - Math.floor((minRangeY - rangeY) / 2);
            maxY = minY + minRangeY;
        }

        mChart.getViewport().setYAxisBoundsManual(true);
        mChart.getViewport().setMinY(minY);
        mChart.getViewport().setMaxY(maxY);
        mChart.getViewport().setXAxisBoundsManual(true);
        mChart.getViewport().setMinX(minX);
        mChart.getViewport().setMaxX(maxX);

        // create chart
        DataPoint[] entries = new DataPoint[size];

        int pos = 0;
        for (PumpHistoryCGM event : results) {
            // turn your data into Entry objects
            entries[pos++] = new DataPoint(event.getEventDate(), (double) event.getSgv());
        }

        if (mChart.getSeries().size() == 0) {
//                long now = System.currentTimeMillis();
//                entries = new DataPoint[1000];
//                int j = 0;
//                for(long i = now - 24*60*60*1000; i < now - 30*60*1000; i+= 5*60*1000) {
//                    entries[j++] = new DataPoint(i, (float) (Math.random()*200 + 89));
//                }
//                entries = Arrays.copyOfRange(entries, 0, j);

            PointsGraphSeries sgvSeries = new PointsGraphSeries(entries);

            sgvSeries.setOnDataPointTapListener(new OnDataPointTapListener() {
                DateFormat mFormat = DateFormat.getTimeInstance(DateFormat.MEDIUM);

                @Override
                public void onTap(Series series, DataPointInterface dataPoint) {
                    double sgv = dataPoint.getY();

                    StringBuilder sb = new StringBuilder(mFormat.format(new Date((long) dataPoint.getX())) + ": ");
                    sb.append(strFormatSGV(sgv));
                    Toast.makeText(getBaseContext(), sb.toString(), Toast.LENGTH_SHORT).show();
                }
            });

            sgvSeries.setCustomShape(new PointsGraphSeries.CustomShape() {
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

                    float dotSize;
                    switch (chartZoom) {
                        case 1:
                            dotSize = 3.0f;
                            break;
                        case 3:
                            dotSize = 2.0f;
                            break;
                        case 6:
                            dotSize = 2.0f;
                            break;
                        case 12:
                            dotSize = 1.65f;
                            break;
                        case 24:
                            dotSize = 1.25f;
                            break;
                        default:
                            dotSize = 3.0f;
                    }

                    canvas.drawCircle(x, y, dipToPixels(getApplicationContext(), dotSize), paint);
                }
            });

            mChart.addSeries(sgvSeries);
        } else {
            if (entries.length > 0) {
                ((PointsGraphSeries) mChart.getSeries().get(0)).resetData(entries);
            }
        }

    }

    private static float dipToPixels(Context context, float dipValue) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue, metrics);
    }


    private final int PAGE_SIZE = 300;
    private final int FIRSTPAGE_SIZE = 75; //100;
    private int viewPosition = 0;
    private RealmResults userLogResults;

    private void stopUserLogView() {
        Log.d(TAG, "stopUserLogView");
        if (userLogResults != null) userLogResults.removeAllChangeListeners();
        userLogResults = null;
    }

    private void startUserLogView() {
        Log.d(TAG, "startUserLogView");
        viewPosition = 0;

        userLogResults = userLogRealm.where(UserLog.class)
                .sort("timestamp", Sort.DESCENDING)
                .findAllAsync();

        userLogResults.addChangeListener(new OrderedRealmCollectionChangeListener<RealmResults>() {
            @Override
            public void onChange(@NonNull RealmResults realmResults, OrderedCollectionChangeSet changeSet) {

                if (changeSet == null) {
                    changeUserLogViewRecent();
                    return;
                }

                if (userLogResults.size() > 0) {
                    if (viewPosition > 0)
                        viewPosition++; // move the view pointer when not on first page to keep aligned
                } else {
                    Log.d(TAG, "UserLogView listener reset!!!");
                    changeUserLogViewRecent();
                    return;
                }

                buildUserLogView();

                if (viewPosition == 0) {
                    // auto scroll status log
                    if ((mScrollView.getChildAt(0).getBottom() < mScrollView.getHeight()) || ((mScrollView.getChildAt(0).getBottom() - mScrollView.getScrollY() - mScrollView.getHeight()) < (mScrollView.getHeight() / 3))) {
                        mScrollView.post(new Runnable() {
                            public void run() {
                                mScrollView.fullScroll(View.FOCUS_DOWN);
                            }
                        });
                    }
                }
            }
        });
    }

    private void buildUserLogView() {
        int remain = userLogResults.size() - viewPosition;
        int segment = remain;
        if (viewPosition == 0 && segment > FIRSTPAGE_SIZE) segment = FIRSTPAGE_SIZE;
        else if (segment > PAGE_SIZE) segment = PAGE_SIZE;

        RealmResults<UserLog> ul = userLogResults;
        StringBuilder sb = new StringBuilder();
        if (segment > 0) {
            for (int index = viewPosition; index < viewPosition + segment; index++)
                sb.insert(0, formatMessage(ul.get(index)) + (sb.length() > 0 ? "\n" : ""));
        }
        mTextViewLog.setText(sb.toString(), BufferType.EDITABLE);

        if (viewPosition > 0) {
            mTextViewLogButtonBottom.setVisibility(View.VISIBLE);
            mTextViewLogButtonBottomRecent.setVisibility(View.VISIBLE);
        } else {
            mTextViewLogButtonBottom.setVisibility(View.GONE);
            mTextViewLogButtonBottomRecent.setVisibility(View.GONE);
        }
        if (remain > segment) {
            mTextViewLogButtonTop.setVisibility(View.VISIBLE);
        } else {
            mTextViewLogButtonTop.setVisibility(View.GONE);
        }
        if (viewPosition > 0 || mScrollView.getChildAt(0).getBottom() > mScrollView.getHeight() + 100) {
            mTextViewLogButtonTopRecent.setVisibility(View.VISIBLE);
        } else {
            mTextViewLogButtonTopRecent.setVisibility(View.GONE);
        }
    }

    private String formatMessage(UserLog userLog) {
/*
        SimpleDateFormat sdf = new SimpleDateFormat("E", Locale.getDefault());
        String clock = sdf.format(userLog.getTimestamp());
        if (android.text.format.DateFormat.is24HourFormat(this)) {
            sdf.applyLocalizedPattern(" H:mm:ss");
            clock += sdf.format(userLog.getTimestamp());
        } else {
            sdf.applyLocalizedPattern(" h:mm:ss a");
            clock += sdf.format(userLog.getTimestamp()).toLowerCase().replace(".", "").replace(",", "");
        }
*/
        SimpleDateFormat sdf = new SimpleDateFormat("E HH:mm:ss");
        String clock = sdf.format(userLog.getTimestamp());

        String split[] = userLog.getMessage().split("¦");
        if (split.length == 2)
            return clock + ": " + split[0] + strFormatSGV(toInt(split[1]));
        else if (split.length == 3)
            return clock + ": " + split[0] + strFormatSGV(toInt(split[1])) + split[2];
        else
            return clock + ": " + split[0];
    }

    private void changeUserLogViewOlder() {
        if (viewPosition == 0) viewPosition += FIRSTPAGE_SIZE;
        else viewPosition += PAGE_SIZE;
        buildUserLogView();
        mScrollView.post(new Runnable() {
            public void run() {
                mScrollView.fullScroll(View.FOCUS_DOWN);
                if (viewPosition > 0 || mScrollView.getChildAt(0).getBottom() > mScrollView.getHeight() + 100) {
                    mTextViewLogButtonTopRecent.setVisibility(View.VISIBLE);
                } else {
                    mTextViewLogButtonTopRecent.setVisibility(View.GONE);
                }
            }
        });
    }

    private void changeUserLogViewNewer() {
        viewPosition -= PAGE_SIZE;
        if (viewPosition < 0) viewPosition = 0;
        buildUserLogView();
        mScrollView.post(new Runnable() {
            public void run() {
                mScrollView.fullScroll(View.FOCUS_UP);
                if (viewPosition > 0 || mScrollView.getChildAt(0).getBottom() > mScrollView.getHeight() + 100) {
                    mTextViewLogButtonTopRecent.setVisibility(View.VISIBLE);
                } else {
                    mTextViewLogButtonTopRecent.setVisibility(View.GONE);
                }
            }
        });
    }

    private void changeUserLogViewRecent() {
        viewPosition = 0;
        buildUserLogView();
        mScrollView.post(new Runnable() {
            public void run() {
                mScrollView.fullScroll(View.FOCUS_DOWN);
                if (viewPosition > 0 || mScrollView.getChildAt(0).getBottom() > mScrollView.getHeight() + 100) {
                    mTextViewLogButtonTopRecent.setVisibility(View.VISIBLE);
                } else {
                    mTextViewLogButtonTopRecent.setVisibility(View.GONE);
                }
            }
        });
    }
}
