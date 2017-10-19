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
import android.support.v7.app.AppCompatActivity;
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

import info.nightscout.android.R;
import info.nightscout.android.UploaderApplication;
import info.nightscout.android.eula.Eula;
import info.nightscout.android.eula.Eula.OnEulaAgreedTo;
import info.nightscout.android.medtronic.service.MasterService;
import info.nightscout.android.medtronic.service.MedtronicCnlService;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.settings.SettingsActivity;
import info.nightscout.android.utils.ConfigurationStore;
import info.nightscout.android.utils.StatusMessage;
import info.nightscout.android.utils.StatusStore;
import io.realm.OrderedCollectionChangeSet;
import io.realm.OrderedRealmCollectionChangeListener;
import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

public class MainActivity extends AppCompatActivity implements OnSharedPreferenceChangeListener, OnEulaAgreedTo {
    private static final String TAG = MainActivity.class.getSimpleName();

    public static final float MMOLXLFACTOR = 18.016f;

    private ConfigurationStore configurationStore = ConfigurationStore.getInstance();
    private StatusMessage statusMessage = StatusMessage.getInstance();

    private int chartZoom = 3;
    private boolean hasZoomedChart = false;

    private boolean mEnableCgmService = true;
    private SharedPreferences prefs = null;

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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate called");
        super.onCreate(savedInstanceState);

        mRealm = Realm.getDefaultInstance();
        storeRealm = Realm.getInstance(UploaderApplication.getStoreConfiguration());

        setContentView(R.layout.activity_main);

        PreferenceManager.getDefaultSharedPreferences(getBaseContext()).registerOnSharedPreferenceChangeListener(this);
        prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        if (!prefs.getBoolean(getString(R.string.preference_eula_accepted), false)) {
            stopCgmService();
        }

        // setup preferences
        configurationStore.setPollInterval(Long.parseLong(prefs.getString("pollInterval", Long.toString(MedtronicCnlService.POLL_PERIOD_MS))));
        configurationStore.setLowBatteryPollInterval(Long.parseLong(prefs.getString("lowBatPollInterval", Long.toString(MedtronicCnlService.LOW_BATTERY_POLL_PERIOD_MS))));
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

        mEnableCgmService = Eula.show(this, prefs);

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
                            statusFinish();
                            mEnableCgmService = false;
                            stopCgmService();
                            finish();
                        } else if (drawerItem.equals(itemGetNow)) {
                            // It was triggered by user so start reading of data now and not based on last poll.
                            statusMessage.add("Requesting poll now...");
                            sendBroadcast(new Intent(MasterService.Constants.ACTION_READ_NOW));
                        } else if (drawerItem.equals(itemClearLog)) {
                            startService(new Intent(UploaderApplication.getAppContext(), MedtronicCnlService.class)
                                    .setAction(MasterService.Constants.ACTION_CNL_SHUTDOWN));
//                            statusMessage.clear();
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
        mTextViewLogButtonTop = (TextView) findViewById(R.id.button_log_top);
        mTextViewLogButtonTop.setVisibility(View.GONE);
        mTextViewLogButtonTop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeStatusViewOlder();
            }
        });
        mTextViewLogButtonTopRecent = (TextView) findViewById(R.id.button_log_top_recent);
        mTextViewLogButtonTopRecent.setVisibility(View.GONE);
        mTextViewLogButtonTopRecent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeStatusViewRecent();
            }
        });

        mTextViewLogButtonBottom = (TextView) findViewById(R.id.button_log_bottom);
        mTextViewLogButtonBottom.setVisibility(View.GONE);
        mTextViewLogButtonBottom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeStatusViewNewer();
            }
        });
        mTextViewLogButtonBottomRecent = (TextView) findViewById(R.id.button_log_bottom_recent);
        mTextViewLogButtonBottomRecent.setVisibility(View.GONE);
        mTextViewLogButtonBottomRecent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeStatusViewRecent();
            }
        });

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
                            return strFormatSGV(value);
                        }
                    }
                }
        );

    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart called");
        super.onStart();
        checkForUpdateBackground(5);
        startStatusView();
        startDisplay();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume called");
        super.onResume();
        // Focus status log to most recent on returning to app
//        changeStatusViewRecent();
    }

    protected void onPasue() {
        Log.d(TAG, "onPause called");
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop called");
        super.onStop();
        stopStatusView();
        stopDisplay();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy called");
        super.onDestroy();

        statusDestroy();

        PreferenceManager.getDefaultSharedPreferences(getBaseContext()).unregisterOnSharedPreferenceChangeListener(this);

        if (!storeRealm.isClosed()) {
            storeRealm.close();
        }
        if (!mRealm.isClosed()) {
            mRealm.close();
        }
        if (!mEnableCgmService) {
//            stopCgmService();
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onPostCreate called");
        super.onPostCreate(savedInstanceState);
        statusStartup();
        startCgmService();
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
            statusMessage.resetCounter();
            statusMessage.add(MasterService.ICON_HEART + "Nightscout 600 Series Uploader");
            statusMessage.add(MasterService.ICON_SETTING + "Poll interval: " + (configurationStore.getPollInterval() / 60000) + " minutes");
            statusMessage.add(MasterService.ICON_SETTING + "Low battery poll interval: " + (configurationStore.getLowBatteryPollInterval() / 60000) + " minutes");
        } else {
            statusMessage.add(MasterService.ICON_HEART + "Welcome back :)");
        }
    }

    private void statusFinish() {
        statusMessage.add(MasterService.ICON_INFO + "Shutting down CGM Service");
    }

    private void statusDestroy() {
        statusMessage.add(MasterService.ICON_INFO + "Shutting down UI");
        if (!prefs.getBoolean("EnableCgmService", false)) {
            statusMessage.add(MasterService.ICON_HEART + "Goodbye :)");
            statusMessage.add("-----------------------------------------------------");
        }
    }

    private void startCgmService() {
        Log.i(TAG, "startCgmService called");
        prefs.edit().putBoolean("EnableCgmService", true).commit();
        startService(new Intent(this, MasterService.class));
    }

    private void stopCgmService() {
        Log.i(TAG, "stopCgmService called");
        prefs.edit().putBoolean("EnableCgmService", false).commit();
        sendBroadcast(new Intent(MasterService.Constants.ACTION_STOP_SERVICE));
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
                    Long.toString(MedtronicCnlService.POLL_PERIOD_MS))));
        } else if (key.equals("lowBatPollInterval")) {
            configurationStore.setLowBatteryPollInterval(Long.parseLong(sharedPreferences.getString("lowBatPollInterval",
                    Long.toString(MedtronicCnlService.LOW_BATTERY_POLL_PERIOD_MS))));
        } else if (key.equals("doublePollOnPumpAway")) {
            configurationStore.setReducePollOnPumpAway(sharedPreferences.getBoolean("doublePollOnPumpAway", false));
        } else if (key.equals("chartZoom")) {
            chartZoom = Integer.parseInt(sharedPreferences.getString("chartZoom", "3"));
            hasZoomedChart = false;
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




    private RealmResults displayResults;
    private long timeLastSGV;
    private int battery = 0;

    private void startDisplay() {
        Log.d(TAG, "startDisplay");

        displayResults = mRealm.where(PumpStatusEvent.class)
                .findAllSortedAsync("eventDate", Sort.ASCENDING);

        displayResults.addChangeListener(new OrderedRealmCollectionChangeListener<RealmResults>() {
            @Override
            public void onChange(RealmResults realmResults, OrderedCollectionChangeSet changeSet) {
                if (changeSet != null) {
                    Log.d(TAG, "display listener triggered");
                    if (changeSet.getInsertions().length > 0) {
                        refreshDisplay();
                    }
                }
            }
        });

        refreshDisplay();
    }

    private void stopDisplay() {
        Log.d(TAG, "stopDisplay");
        displayResults.removeAllChangeListeners();
        displayResults = null;

        mUiRefreshHandler.removeCallbacks(mUiRefreshRunnable);
    }

    private void refreshDisplay() {
        Log.d(TAG, "refreshDisplay");

        mUiRefreshHandler.removeCallbacks(mUiRefreshRunnable);

        timeLastSGV = 0;

        TextView textViewBg = (TextView) findViewById(R.id.textview_bg);
        TextView textViewUnits = (TextView) findViewById(R.id.textview_units);
        if (configurationStore.isMmolxl()) {
            textViewUnits.setText(R.string.text_unit_mmolxl);
        } else {
            textViewUnits.setText(R.string.text_unit_mgxdl);
        }
        TextView textViewTrend = (TextView) findViewById(R.id.textview_trend);
        TextView textViewIOB = (TextView) findViewById(R.id.textview_iob);

        String sgvString = "\u2014"; // &mdash;
        String trendString = "{ion_ios_minus_empty}";
        int trendRotation = 0;
        float iob = 0;
        int battery = 0;

        // most recent sgv status
        RealmResults<PumpStatusEvent> sgv_results =
                mRealm.where(PumpStatusEvent.class)
                        .equalTo("validSGV", true)
                        .findAllSorted("cgmDate", Sort.ASCENDING);

        if (sgv_results.size() > 0) {
            timeLastSGV = sgv_results.last().getCgmDate().getTime();
            sgvString = MainActivity.strFormatSGV(sgv_results.last().getSgv());

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

            updateChart(sgv_results.where()
                    .greaterThan("cgmDate",  new Date(timeLastSGV - 1000 * 60 * 60 * 24))
                    .findAllSorted("cgmDate", Sort.ASCENDING));
        } else {
            updateChart(sgv_results); // render empty chart
        }

        // most recent pump status
        RealmResults<PumpStatusEvent> pump_results =
                mRealm.where(PumpStatusEvent.class)
                        .findAllSorted("eventDate", Sort.ASCENDING);

        if (pump_results.size() > 0) {
            iob = pump_results.last().getActiveInsulin();
            battery = pump_results.last().getBatteryPercentage();
        }

        textViewBg.setText(sgvString);
        textViewIOB.setText(String.format(Locale.getDefault(), "%.2f", iob));
        textViewTrend.setText(trendString);
        textViewTrend.setRotation(trendRotation);

        ActionMenuItemView batIcon = ((ActionMenuItemView) findViewById(R.id.status_battery));
        if (batIcon != null) {
            switch (battery) {
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

        mUiRefreshHandler.post(mUiRefreshRunnable);
    }

    private class RefreshDisplayRunnable implements Runnable {
        @Override
        public void run() {
            Log.d(TAG, "refreshDisplayRunnable");
            long nextRun = 60000L;

            TextView textViewBgTime = (TextView) findViewById(R.id.textview_bg_time);
            String timeString = "never";

            if (timeLastSGV > 0) {
                nextRun = 60000L - (System.currentTimeMillis() - timeLastSGV) % 60000L;
                timeString = (DateUtils.getRelativeTimeSpanString(timeLastSGV)).toString();
            }

            textViewBgTime.setText(timeString);

            // Run myself again in 60 (or less) seconds;
            mUiRefreshHandler.postDelayed(this, nextRun);
        }
    }

    private void updateChart(RealmResults<PumpStatusEvent> results) {

        mChart.getGridLabelRenderer().setNumHorizontalLabels(6);

        // empty chart when no data available
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

        // calc X & Y chart bounds with readable stepping for mmol & ml/dl
        // X needs offsetting as graphview will not always show points near edges
        long minX = (((timeLastSGV + 150000 - (chartZoom * 60 * 60 * 1000)) / 60000) * 60000);
        long maxX = timeLastSGV + 90000;

        RealmResults<PumpStatusEvent> minmaxY = results.where()
                .greaterThan("cgmDate",  new Date(minX))
                .findAllSorted("sgv", Sort.ASCENDING);

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
            maxY = (long) Math.ceil(maxY / 10) * 10;
            rangeY = maxY - minY;
            minRangeY = ((rangeY / 20 ) + 1) * 20;
            minY = minY - (long) Math.floor((minRangeY - rangeY) / 2);
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
        for (PumpStatusEvent pumpStatus : results) {
            // turn your data into Entry objects
            entries[pos++] = new DataPoint(pumpStatus.getCgmDate(), (double) pumpStatus.getSgv());
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
                    float dotSize = 3.0f;
                    if (chartZoom == 3) dotSize = 2.0f;
                    else if (chartZoom == 6) dotSize = 2.0f;
                    else if (chartZoom == 12) dotSize = 1.65f;
                    else if (chartZoom == 24) dotSize = 1.25f;
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



    private static final int PAGE_SIZE = 300;
    private static final int FIRSTPAGE_SIZE = 100;
    private int viewPosition = 0;
    private RealmResults statusResults;

    private void stopStatusView() {
        Log.d(TAG, "stopStatusView");
        statusResults.removeAllChangeListeners();
        statusResults = null;
    }

    private void startStatusView() {
        Log.d(TAG, "startStatusView");
        viewPosition = 0;

        statusResults = storeRealm.where(StatusStore.class)
//                .findAllSortedAsync("timestamp", Sort.DESCENDING);
                .findAllSorted("timestamp", Sort.DESCENDING);

        statusResults.addChangeListener(new OrderedRealmCollectionChangeListener<RealmResults>() {
            @Override
            public void onChange(RealmResults realmResults, OrderedCollectionChangeSet changeSet) {
                if (changeSet != null) {
                    Log.d(TAG, "status listener triggered");
                    if (statusResults.size() > 0) {
                        if (viewPosition > 0)
                            viewPosition++; // move the view pointer when not on first page to keep aligned
                    } else {
                        Log.d(TAG, "status listener reset!!!");
                        viewPosition = 0;
                    }
                }

                buildStatusView();

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

        changeStatusViewRecent();

    }

    private void buildStatusView() {
//        Log.d(TAG, "* counter=" + statusMessage.getCounter() + " SP=" + viewPositionSecondPage + " VP=" + viewPosition);

        int remain = statusResults.size() - viewPosition;
        int segment = remain;
        if (viewPosition == 0 && segment > FIRSTPAGE_SIZE) segment = FIRSTPAGE_SIZE;
        else if (segment > PAGE_SIZE) segment = PAGE_SIZE;

        StringBuilder sb = new StringBuilder();
        if (segment > 0) {
            for (int index = viewPosition; index < viewPosition + segment; index++)
                sb.insert(0, statusResults.get(index) + (sb.length() > 0 ? "\n" : ""));
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

    private void changeStatusViewOlder() {
        if (viewPosition == 0) viewPosition += FIRSTPAGE_SIZE;
        else viewPosition += PAGE_SIZE;
        buildStatusView();
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

    private void changeStatusViewNewer() {
        viewPosition -= PAGE_SIZE;
        if (viewPosition < 0) viewPosition = 0;
        buildStatusView();
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

    private void changeStatusViewRecent() {
        viewPosition = 0;
        buildStatusView();
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

    /*
        private static final int PAGE_SIZE = 300;
    private static final int FIRSTPAGE_SIZE = 100;
    private int viewPosition = 0;
    private int viewPositionSecondPage = 0;
    private RealmResults statusResults;

    private void stopStatusView() {
        Log.d(TAG, "stopStatusView");
        statusResults.removeAllChangeListeners();
        statusResults = null;
    }

    private void startStatusView() {
        Log.d(TAG, "startStatusView");
        viewPosition = 0;
        viewPositionSecondPage = statusMessage.getCounter();
        if (viewPositionSecondPage > FIRSTPAGE_SIZE)
            viewPositionSecondPage = FIRSTPAGE_SIZE;
//        viewPositionSecondPage = 100;

        statusResults = storeRealm.where(StatusStore.class)
//                .findAllSortedAsync("timestamp", Sort.DESCENDING);
                .findAllSorted("timestamp", Sort.DESCENDING);

        statusResults.addChangeListener(new OrderedRealmCollectionChangeListener<RealmResults>() {
            @Override
            public void onChange(RealmResults realmResults, OrderedCollectionChangeSet changeSet) {
                if (changeSet != null) {
                    Log.d(TAG, "status listener triggered");
                    if (statusResults.size() > 0) {
                        viewPositionSecondPage = statusMessage.getCounter();
//                        viewPositionSecondPage = 100;
                        if (viewPositionSecondPage > FIRSTPAGE_SIZE)
                            viewPositionSecondPage = FIRSTPAGE_SIZE;
//                        if (viewPositionSecondPage < FIRSTPAGE_SIZE)
//                            viewPositionSecondPage++; // older session log begins on next page
                        if (viewPosition > 0)
                            viewPosition++; // move the view pointer when not on first page to keep aligned
                    } else {
                        Log.d(TAG, "status listener reset!!!");
                        viewPosition = 0;
                        viewPositionSecondPage = 0;
                    }
                }

                buildStatusView();

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

        changeStatusViewRecent();

    }

    private void buildStatusView() {
//        Log.d(TAG, "* counter=" + statusMessage.getCounter() + " SP=" + viewPositionSecondPage + " VP=" + viewPosition);

        int remain = statusResults.size() - viewPosition;
        int segment = remain;
        if (viewPosition == 0 && viewPositionSecondPage < PAGE_SIZE) segment = viewPositionSecondPage;
        if (segment > PAGE_SIZE) segment = PAGE_SIZE;

        StringBuilder sb = new StringBuilder();
        if (segment > 0) {
            for (int index = viewPosition; index < viewPosition + segment; index++)
                sb.insert(0, statusResults.get(index) + (sb.length() > 0 ? "\n" : ""));
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

    private void changeStatusViewOlder() {
        if (viewPosition == 0 && viewPositionSecondPage < PAGE_SIZE) {
            viewPosition = viewPositionSecondPage;
        } else {
            viewPosition += PAGE_SIZE;
        }
        buildStatusView();
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

    private void changeStatusViewNewer() {
        viewPosition -= PAGE_SIZE;
        if (viewPosition < 0) viewPosition = 0;
        buildStatusView();
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

    private void changeStatusViewRecent() {
        viewPosition = 0;
        buildStatusView();
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

     */
}
