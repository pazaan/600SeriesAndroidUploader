package info.nightscout.android.medtronic;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.menu.MenuView;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.format.DateUtils;
import android.text.style.AlignmentSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.github.javiersantos.appupdater.AppUpdater;
import com.github.javiersantos.appupdater.enums.UpdateFrom;
import com.jjoe64.graphview.DefaultLabelFormatter;
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

import java.io.Serializable;
import java.util.Date;
import java.util.Locale;

import co.moonmonkeylabs.realmrecyclerview.RealmRecyclerView;
import info.nightscout.android.R;
import info.nightscout.android.UploaderApplication;
import info.nightscout.android.eula.Eula;
import info.nightscout.android.eula.Eula.OnEulaAgreedTo;
import info.nightscout.android.history.PumpHistoryParser;
import info.nightscout.android.medtronic.service.MasterService;
import info.nightscout.android.model.medtronicNg.PumpHistoryCGM;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.settings.SettingsActivity;
import info.nightscout.android.model.store.DataStore;
import info.nightscout.android.model.store.UserLog;
import info.nightscout.android.utils.FormatKit;
import info.nightscout.android.utils.RealmKit;
import io.realm.OrderedCollectionChangeSet;
import io.realm.OrderedRealmCollectionChangeListener;
import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

public class MainActivity extends AppCompatActivity implements OnSharedPreferenceChangeListener, OnEulaAgreedTo {
    private static final String TAG = MainActivity.class.getSimpleName();

    public static final float MMOLXLFACTOR = 18.016f;

    private Context mContext;

    private boolean mEnableCgmService;
    private boolean settingsChanged;
    private SharedPreferences mPrefs;

    private ChartSgv mChart;
    private int chartZoom;
    private long chartTimeOffset;

    private long chartChangeTimestamp;

    private boolean landscape;

    private RealmResults<PumpHistoryCGM> displayChartResults;
    private RealmResults<PumpStatusEvent> displayPumpResults;
    private RealmResults<PumpHistoryCGM> displayCgmResults;
    private long timeLastSGV;
    private int pumpBattery;

    private Toast toast;

    private Handler mUiRealmHandler = new Handler();
    private Handler mUiRefreshHandler = new Handler();
    private Runnable mUiRefreshRunnable = new RefreshDisplayRunnable();

    private Realm mRealm;
    private Realm storeRealm;
    private Realm historyRealm;
    private DataStore dataStore;

    private UserLogDisplay userLogDisplay;
    private AppUpdater appUpdater;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate called");
        super.onCreate(savedInstanceState);

        mContext = this.getBaseContext();

        RealmKit.compact(mContext);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        final boolean versionChanged = !mPrefs.getString("versionName", "n/a").equals(getString(R.string.versionName));
        final long now = System.currentTimeMillis();

        storeRealm = Realm.getInstance(UploaderApplication.getStoreConfiguration());
        dataStore = storeRealm.where(DataStore.class).findFirst();
        if (dataStore == null) {
            Log.i(TAG, "Creating initial dataStore in Realm");
            storeRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {
                    dataStore = realm.createObject(DataStore.class);
                    dataStore.setStartupTimestamp(now);
                    dataStore.setCnlPlugTimestamp(now);
                    dataStore.setCnlUnplugTimestamp(now);
                    dataStore.setCnlLimiterTimestamp(now);
                }
            });
        }

        if (versionChanged) {
            Log.i(TAG, String.format("Version changed: %s --> %s. Clearing log and setting init timestamp.",
                    mPrefs.getString("versionName", "n/a"), getString(R.string.versionName)));
            mPrefs.edit().putString("versionName", getString(R.string.versionName)).commit();
            // clear the log as string resource ids may have changed
            try {
                Realm realm = Realm.getInstance(UploaderApplication.getUserLogConfiguration());
                realm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(@NonNull Realm realm) {
                        realm.deleteAll();
                    }
                });
                realm.close();
            } catch (Exception ignored) {
            }
            // set the init time, init cleanup and always update older items in nightscout
            storeRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {
                    dataStore.setInitTimestamp(now);
                    dataStore.setNightscoutInitCleanup(true);
                    dataStore.setNightscoutAlwaysUpdateTimestamp(now);
                }
            });

            // v0.7.0 changed minimum backfill period to 7 days
            if (mPrefs.getString(getString(R.string.key_sysCgmHistoryDays), "").equals("1"))
                mPrefs.edit().putString(getString(R.string.key_sysCgmHistoryDays), getString(R.string.default_sysCgmHistoryDays)).apply();
            if (mPrefs.getString(getString(R.string.key_sysPumpHistoryDays), "").equals("1"))
                mPrefs.edit().putString(getString(R.string.key_sysPumpHistoryDays), getString(R.string.default_sysPumpHistoryDays)).apply();
            // v0.7.0 removed "Events Only" option, changed default from "90" to "60"
            if (mPrefs.getString(getString(R.string.key_sysPumpHistoryFrequency), "").equals("0"))
                mPrefs.edit().putString(getString(R.string.key_sysPumpHistoryFrequency), getString(R.string.default_sysPumpHistoryFrequency)).apply();
        }

        storeRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(@NonNull Realm realm) {
                dataStore.copyPrefs(mContext, mPrefs);
            }
        });

        mPrefs.registerOnSharedPreferenceChangeListener(this);

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

        mEnableCgmService = Eula.show(this, mPrefs)
                && mPrefs.getBoolean(getString(R.string.key_eulaAccepted), getResources().getBoolean(R.bool.default_eulaAccepted));

        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            getSupportActionBar().setElevation(0);
            getSupportActionBar().setTitle("Nightscout");
        }

        final PrimaryDrawerItem itemSettings = new PrimaryDrawerItem()
                .withName(R.string.main_menu__settings)
                .withIcon(GoogleMaterial.Icon.gmd_settings)
                .withSelectable(false);
        final PrimaryDrawerItem itemRegisterUsb = new PrimaryDrawerItem()
                .withName(R.string.main_menu__registered_devices)
                .withIcon(GoogleMaterial.Icon.gmd_usb)
                .withSelectable(false);
        final PrimaryDrawerItem itemStopCollecting = new PrimaryDrawerItem()
                .withName(R.string.main_menu__stop_collecting_data)
                .withIcon(GoogleMaterial.Icon.gmd_power_settings_new)
                .withSelectable(false);
        final PrimaryDrawerItem itemGetNow = new PrimaryDrawerItem()
                .withName(R.string.main_menu__read_data_now)
                .withIcon(GoogleMaterial.Icon.gmd_refresh)
                .withSelectable(false);
        final PrimaryDrawerItem itemUpdateProfile = new PrimaryDrawerItem()
                .withName(R.string.main_menu__update_pump_profile)
                .withIcon(GoogleMaterial.Icon.gmd_insert_chart)
                .withSelectable(false);
        final PrimaryDrawerItem itemClearLog = new PrimaryDrawerItem()
                .withName(R.string.main_menu__clear_log)
                .withIcon(GoogleMaterial.Icon.gmd_clear_all)
                .withSelectable(false);
        final PrimaryDrawerItem itemCheckForUpdate = new PrimaryDrawerItem()
                .withName(R.string.main_menu__check_for_app_update)
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
                                sendBroadcast(new Intent(MasterService.Constants.ACTION_READ_NOW));
                            } else {
                                UserLogMessage.getInstance().add(R.string.ul_main__cgm_service_disabled);
                            }
                        } else if (drawerItem.equals(itemUpdateProfile)) {
                            if (mEnableCgmService) {
                                if (dataStore.isNsEnableProfileUpload()) {
                                    sendBroadcast(new Intent(MasterService.Constants.ACTION_READ_PROFILE));
                                } else {
                                    UserLogMessage.getInstance().add(getString(R.string.ul_main__pump_profile_disabled));
                                }
                            } else {
                                UserLogMessage.getInstance().add(R.string.ul_main__cgm_service_disabled);
                            }
                        } else if (drawerItem.equals(itemClearLog)) {
                            UserLogMessage.getInstance().clear();
                        } else if (drawerItem.equals(itemCheckForUpdate)) {
                            checkForUpdateNow();
                        }

                        return false;
                    }
                })
                .build();

        landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        chartZoom = Integer.parseInt(mPrefs.getString("chartZoom", "3"));

        mChart = findViewById(R.id.chart);

        // disable scrolling at the moment
        mChart.getViewport().setScalable(false);
        mChart.getViewport().setScrollable(false);

        // due to bug in GraphView v4.2.1 using setNumHorizontalLabels reverted to using v4.0.1
        // setOnXAxisBoundsChangedListener is n/a in this version
        // setHumanRounding is n/a in this version

        mChart.getGridLabelRenderer().setNumHorizontalLabels(6);

        float factor = landscape ? 1.2f : 1.0f;
        float pixels = dipToPixels(getApplicationContext(), 12 * factor);
        mChart.getGridLabelRenderer().setTextSize(pixels);
        mChart.getGridLabelRenderer().setLabelHorizontalHeight((int) (pixels * 0.65));
        mChart.getGridLabelRenderer().reloadStyles();

        mChart.getGridLabelRenderer().setLabelFormatter(
                new DefaultLabelFormatter()
                {
                    @Override
                    public String formatLabel(double value, boolean isValueX) {
                        if (!isValueX)
                            return FormatKit.getInstance().formatAsGlucose((int) value);
                        else if (landscape)
                            return FormatKit.getInstance().formatAsClock((long) value);
                        else
                            return FormatKit.getInstance().formatAsClockNoAmPm((long) value);
                    }
                }
        );

        updateChart(null, now);

        mChart.setOnTouchListener(new OnSwipeTouchListener(mContext)
        {
            @Override
            public void onSwipeLeft() {
                chartTimeOffset -= chartZoom * 60 * 60000L;
                chartViewAdjusted();
            }
            @Override
            public void onSwipeRight() {
                chartTimeOffset += chartZoom * 60 * 60000L;
                chartViewAdjusted();
            }
            @Override
            public void onLongClick() {
                switch (chartZoom) {
                    case 1:
                        chartZoom = 3;
                        break;
                    case 3:
                        chartZoom = 6;
                        break;
                    case 6:
                        chartZoom = 12;
                        break;
                    case 12:
                        chartZoom = 24;
                        break;
                    default:
                        chartZoom = 1;
                }
                mPrefs.edit().putString("chartZoom", Integer.toString(chartZoom)).apply();
                chartViewAdjusted();
            }

        });

        findViewById(R.id.view_sgv).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                chartTimeOffset = 0;
                chartViewAdjusted();
            }
        });

        setScreenSleepMode();

        if (!landscape)
            userLogDisplay = new UserLogDisplay(mContext);
    }

    private void setScreenSleepMode() {
        if (mChart != null) {
            String sleep = mPrefs.getString("screenSleep", "1");
            if (sleep.equals("3")
                    || (sleep.equals("1") && landscape)
                    || (sleep.equals("2") && !landscape))
                mChart.setKeepScreenOn(true);
            else
                mChart.setKeepScreenOn(false);
        }
    }

    private void chartViewAdjusted() {
        chartChangeTimestamp = System.currentTimeMillis();

        long end = timeLastSGV == 0 ? chartChangeTimestamp - chartTimeOffset : timeLastSGV - chartTimeOffset;
        long start = end - chartZoom * 60 * 60000L;

        String t = String.format(getString(R.string.main_screen__value_hour_chart), chartZoom);

        String m = String.format("\n%s %s",
                FormatKit.getInstance().formatAsMonthName(start),
                FormatKit.getInstance().formatAsDay(start)
        );

        String d = String.format("\n%s - %s",
                FormatKit.getInstance().formatAsDayClock(start),
                FormatKit.getInstance().formatAsDayClock(end)
        );

        SpannableStringBuilder ssb = new SpannableStringBuilder();
        ssb.append(t);
        ssb.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), ssb.length() - t.length(), ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new StyleSpan(Typeface.BOLD), ssb.length() - t.length(), ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        ssb.append(m);
        ssb.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), ssb.length() - m.length(), ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new RelativeSizeSpan(0.75f), ssb.length() - m.length(), ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        ssb.append(d);
        ssb.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), ssb.length() - d.length(), ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new RelativeSizeSpan(0.85f), ssb.length() - d.length(), ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        toast = serveToast(ssb, toast, findViewById(R.id.view_sgv));

        refreshDisplayChart();
    }

    private Toast serveToast(SpannableStringBuilder ssb, Toast toast, View v) {
        if (toast != null) toast.cancel();

        toast = Toast.makeText(mContext, ssb, Toast.LENGTH_LONG);

        View parent = (View) v.getParent();
        int parentHeight = parent.getHeight();
        int yOffset = (parentHeight / 2) - (v.getTop() + ((v.getBottom() - v.getTop()) / 2));

        toast.setGravity(Gravity.NO_GRAVITY, 0, -yOffset);
        toast.show();
        return toast;
    }

    public class OnSwipeTouchListener implements View.OnTouchListener {

        private final GestureDetector gestureDetector;

        public OnSwipeTouchListener(Context context) {
            gestureDetector = new GestureDetector(context, new GestureListener());
        }

        public void onClick() {
        }

        public void onDoubleClick() {
        }

        public void onLongClick() {
        }

        public void onSwipeLeft() {
        }

        public void onSwipeRight() {
        }

        public boolean onTouch(View v, MotionEvent event) {
            v.performClick();
            return gestureDetector.onTouchEvent(event);
        }

        private final class GestureListener extends GestureDetector.SimpleOnGestureListener {

            private static final int SWIPE_DISTANCE_THRESHOLD = 100;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;

            @Override
            public boolean onDown(MotionEvent e) {
                return super.onDown(e);
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                onClick();
                return super.onSingleTapConfirmed(e);
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                onDoubleClick();
                return super.onDoubleTap(e);
            }

            @Override
            public void onLongPress(MotionEvent e) {
                onLongClick();
                super.onLongPress(e);
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                float distanceX = e2.getX() - e1.getX();
                float distanceY = e2.getY() - e1.getY();
                if (Math.abs(distanceX) > Math.abs(distanceY) && Math.abs(distanceX) > SWIPE_DISTANCE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (distanceX > 0)
                        onSwipeRight();
                    else
                        onSwipeLeft();
                    return true;
                }
                return false;
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        Log.d(TAG, "onSaveInstanceState called");
        outState.putLong("chartChangeTimestamp", chartChangeTimestamp);
        outState.putLong("chartTimeOffset", chartTimeOffset);
        outState.putBoolean("settingsChanged", settingsChanged);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        Log.d(TAG, "onRestoreInstanceState called");
        super.onRestoreInstanceState(savedInstanceState);
        chartChangeTimestamp = savedInstanceState.getLong("chartChangeTimestamp", 0);
        chartTimeOffset = savedInstanceState.getLong("chartTimeOffset", 0);
        settingsChanged = savedInstanceState.getBoolean("settingsChanged", false);
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart called");
        super.onStart();

        openRealmDefault();
        openRealmHistory();
        openRealmDatastore();

        checkForUpdateBackground(5);

        startDisplay();
        if (userLogDisplay != null)
            userLogDisplay.start(dataStore.isDbgEnableExtendedErrors());

        if (mEnableCgmService)
            sendBroadcast(new Intent(MasterService.Constants.ACTION_READ_OVERDUE));

        if (settingsChanged) {
            sendBroadcast(new Intent(MasterService.Constants.ACTION_SETTINGS_CHANGED));
            settingsChanged = false;
        }
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

        if (appUpdater != null) appUpdater.stop();
        if (userLogDisplay != null) userLogDisplay.stop();
        stopDisplay();

        closeRealm();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy called");
        super.onDestroy();

        PreferenceManager.getDefaultSharedPreferences(getBaseContext()).unregisterOnSharedPreferenceChangeListener(this);

        if (!mEnableCgmService) stopMasterService();
        if (realmAsyncTask != null) realmAsyncTask.cancel();

        shutdownMessage();

        closeRealm();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onPostCreate called");
        super.onPostCreate(savedInstanceState);
        startupMessage();
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

    synchronized private void openRealmDefault() {
        if (mRealm == null)
            mRealm = Realm.getDefaultInstance();
    }

    synchronized private void openRealmHistory() {
        if (historyRealm == null)
            historyRealm = Realm.getInstance(UploaderApplication.getHistoryConfiguration());
    }

    synchronized private void openRealmDatastore() {
        if (storeRealm == null)
            storeRealm = Realm.getInstance(UploaderApplication.getStoreConfiguration());
        if (dataStore == null)
            dataStore = storeRealm.where(DataStore.class).findFirst();
    }

    synchronized private void closeRealm() {
        if (storeRealm != null && !storeRealm.isClosed()) storeRealm.close();
        if (historyRealm != null && !historyRealm.isClosed()) historyRealm.close();
        if (mRealm != null && !mRealm.isClosed()) mRealm.close();
        dataStore = null;
        storeRealm = null;
        historyRealm = null;
        mRealm = null;
    }

    private void checkForUpdateNow() {
        if (appUpdater == null) appUpdater = new AppUpdater(this);
        else appUpdater.stop();

        appUpdater.setUpdateFrom(UpdateFrom.JSON)
                .setUpdateJSON("https://raw.githubusercontent.com/pazaan/600SeriesAndroidUploader/master/app/update.json")
                .showAppUpdated(true) // Show a dialog, even if there isn't an update
                .start();
    }

    private void checkForUpdateBackground(int checkEvery) {
        if (appUpdater == null) appUpdater = new AppUpdater(this);
        else appUpdater.stop();

        appUpdater.setUpdateFrom(UpdateFrom.JSON)
                .setUpdateJSON("https://raw.githubusercontent.com/pazaan/600SeriesAndroidUploader/master/app/update.json")
                .showEvery(checkEvery) // Only check for an update every `checkEvery` invocations
                .start();
    }

    private void startupMessage() {
        // userlog message at startup when no service is running
        if (!mPrefs.getBoolean("EnableCgmService", false)) {

            UserLogMessage.getInstance().add(UserLogMessage.TYPE.STARTUP, R.string.ul_main__hello);

            UserLogMessage.getInstance().add(UserLogMessage.TYPE.OPTION,
                    String.format("{id;%s}: {id;%s}",
                            R.string.ul_main__uploading,
                            dataStore.isNightscoutUpload() ? R.string.enabled : R.string.disabled));
            UserLogMessage.getInstance().add(UserLogMessage.TYPE.OPTION,
                    String.format("{id;%s}: {id;%s}",
                            R.string.ul_main__treatments,
                            dataStore.isNsEnableTreatments() ? R.string.enabled : R.string.disabled
                    ));
            UserLogMessage.getInstance().add(UserLogMessage.TYPE.OPTION,
                    String.format("{id;%s}: {qid;%s;%s}",
                            R.string.ul_main__poll_interval,
                            R.plurals.minutes,
                            dataStore.getPollInterval() / 60000L
                    ));
            UserLogMessage.getInstance().add(UserLogMessage.TYPE.OPTION,
                    String.format("{id;%s}: {qid;%s;%s}",
                            R.string.ul_main__low_battery_poll_interval,
                            R.plurals.minutes,
                            dataStore.getLowBatPollInterval() / 60000L
                    ));

            int historyFrequency = dataStore.getSysPumpHistoryFrequency();
            if (historyFrequency > 0) {
                UserLogMessage.getInstance().add(UserLogMessage.TYPE.OPTION,
                        String.format("{id;%s}: {qid;%s;%s}",
                                R.string.ul_main__auto_mode_update,
                                R.plurals.minutes,
                                historyFrequency
                        ));
            }

            deviceMessage();
        }
    }

    private void shutdownMessage() {
        // userlog message at shutdown when 'stop collecting data' selected
        if (!mPrefs.getBoolean("EnableCgmService", false)) {
            UserLogMessage.getInstance().add(UserLogMessage.TYPE.SHUTDOWN, R.string.ul_main__goodbye);
            UserLogMessage.getInstance().add("---------------------------------------------------");
        }
    }

    private void deviceMessage() {
        String device = String.format("UPLOADER: %s BRAND: %s DEVICE: %s ID: %s HARDWARE: %s MANUFACTURER: %s MODEL: %s PRODUCT: %s SDK: %s VERSION: %s",
                getString(R.string.versionName),
                Build.BRAND,
                Build.DEVICE,
                Build.ID,
                Build.HARDWARE,
                Build.MANUFACTURER,
                Build.MODEL,
                Build.PRODUCT,
                Build.VERSION.SDK_INT,
                Build.VERSION.RELEASE
        );
        Log.i(TAG, device);
        UserLogMessage.getInstance().add(UserLogMessage.TYPE.NOTE, UserLogMessage.FLAG.EXTENDED, device);
    }

    private void startMasterService() {
        Log.i(TAG, "startMasterService called");
        if (mEnableCgmService) {
            if (!mPrefs.getBoolean("EnableCgmService", false)) {
                mPrefs.edit().putBoolean("EnableCgmService", true).commit();
                openRealmDatastore();
                storeRealm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(@NonNull Realm realm) {
                        dataStore.setStartupTimestamp(System.currentTimeMillis());
                    }
                });
            }
            startService(new Intent(this, MasterService.class));
        } else {
            mPrefs.edit().putBoolean("EnableCgmService", false).commit();
            Log.i(TAG, "startMasterService: CgmService is disabled");
        }
    }

    private void stopMasterService() {
        Log.i(TAG, "stopMasterService called");
        UserLogMessage.getInstance().add(UserLogMessage.TYPE.INFO, R.string.ul_main__shutting_down_cgm_service);
        mPrefs.edit().putBoolean("EnableCgmService", false).commit();
        sendBroadcast(new Intent(MasterService.Constants.ACTION_STOP_SERVICE));
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Log.d(TAG, "onSharedPreferenceChanged called, key = " + key);

        if (key.equals(getString(R.string.key_eulaAccepted))) {
            if (!sharedPreferences.getBoolean(getString(R.string.key_eulaAccepted), getResources().getBoolean(R.bool.default_eulaAccepted))) {
                mEnableCgmService = false;
                stopMasterService();
            } else {
                mEnableCgmService = true;
                startMasterService();
            }
        }

        else if (key.equals(getString(R.string.key_chartZoom))) {
            chartZoom = Integer.parseInt(sharedPreferences.getString(getString(R.string.key_chartZoom), getString(R.string.default_chartZoom)));
        }

        else if (key.equals(getString(R.string.key_screenSleep))) {
            setScreenSleepMode();
        }

        else if (!key.equals("EnableCgmService")) {
            updatePrefs();
            if (mEnableCgmService) {
                if (key.contains("urchin") && mEnableCgmService) {
                    sendBroadcast(new Intent(MasterService.Constants.ACTION_URCHIN_UPDATE));
                } else {
                    sendBroadcast(new Intent(MasterService.Constants.ACTION_STATUS_UPDATE));
                    // send message to master service after exiting the settings menu
                    settingsChanged = true;
                }
            }
        }
    }

    private io.realm.RealmAsyncTask realmAsyncTask;

    public void updatePrefs() {
        try {
            if (storeRealm == null) {
                realmAsyncTask = Realm.getInstanceAsync(UploaderApplication.getStoreConfiguration(), new Realm.Callback() {
                    @Override
                    public void onSuccess(Realm realm) {
                        storeRealm = realm;
                        dataStore = realm.where(DataStore.class).findFirst();
                        realmAsyncTask = null;
                        copyPrefsToDataStore();
                    }
                });
            } else {
                copyPrefsToDataStore();
            }
        } catch (Exception e) {
            Log.e(TAG, "updatePrefs could not complete: " + e.getMessage());
        }
    }

    private void copyPrefsToDataStore() {
        try {
            storeRealm.executeTransactionAsync(new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {
                    Log.d(TAG, "copyPrefsToDataStore async started");
                    realm.where(DataStore.class).findFirst()
                            .copyPrefs(mContext, mPrefs);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "copyPrefsToDataStore could not complete: " + e.getMessage());
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

    private void startDisplay() {
        Log.d(TAG, "startDisplay");
        startDisplayPump();
        startDisplayCgm();
    }

    private void stopDisplay() {
        Log.d(TAG, "stopDisplay");
        mUiRefreshHandler.removeCallbacks(mUiRefreshRunnable);
        stopDisplayPump();
        stopDisplayCgm();
        stopDisplayChart();
    }

    private void startDisplayPump() {
        Log.d(TAG, "startDisplayPump");

        displayPumpResults = mRealm.where(PumpStatusEvent.class)
                .sort("eventDate", Sort.ASCENDING)
                .findAllAsync();

        displayPumpResults.addChangeListener(new OrderedRealmCollectionChangeListener<RealmResults<PumpStatusEvent>>() {
            @Override
            public void onChange(@NonNull RealmResults realmResults, @NonNull OrderedCollectionChangeSet changeSet) {
                Log.d(TAG, "displayPumpResults triggered size=" + displayPumpResults.size());
                refreshDisplayPump();
            }
        });
    }

    private void stopDisplayPump() {
        Log.d(TAG, "stopDisplayPump");
        if (displayPumpResults != null) {
            displayPumpResults.removeAllChangeListeners();
            displayPumpResults = null;
        }
    }

    private void startDisplayCgm() {
        Log.d(TAG, "startDisplayCgm");

        displayCgmResults = historyRealm.where(PumpHistoryCGM.class)
                .notEqualTo("sgv", 0)
                .sort("eventDate", Sort.ASCENDING)
                .findAllAsync();

        displayCgmResults.addChangeListener(new OrderedRealmCollectionChangeListener<RealmResults<PumpHistoryCGM>>() {
            @Override
            public void onChange(@NonNull RealmResults realmResults, @NonNull OrderedCollectionChangeSet changeSet) {
                Log.d(TAG, "displayCgmResults triggered size=" + displayCgmResults.size());
                refreshDisplayCgm();
                refreshDisplayChart();
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

    private void refreshDisplayPump() {
        Log.d(TAG, "refreshDisplayPump");

        double iob = 0;
        pumpBattery = -1;

        // most recent pump status
        if (displayPumpResults.size() > 0
                && displayPumpResults.last().getEventDate().getTime() > System.currentTimeMillis() - 60 * 60000L) {
            iob = displayPumpResults.last().getActiveInsulin();
            pumpBattery = displayPumpResults.last().getBatteryPercentage();
        }

        TextView textViewIOB = findViewById(R.id.textview_iob);
        textViewIOB.setText(String.format(Locale.getDefault(), "%s: %.2f %s", getString(R.string.main_screen__active_insulin), iob, getString(R.string.insulin_U)));
    }

    private void refreshDisplayCgm() {
        Log.d(TAG, "refreshDisplayCgm");

        mUiRefreshHandler.removeCallbacks(mUiRefreshRunnable);

        timeLastSGV = 0;

        TextView textViewBg = findViewById(R.id.textview_bg);
        TextView textViewUnits = findViewById(R.id.textview_units);
        if (dataStore.isMmolxl()) {
            textViewUnits.setText(R.string.glucose_mmol);
        } else {
            textViewUnits.setText(R.string.glucose_mgdl);
        }
        TextView textViewTrend = findViewById(R.id.textview_trend);

        String sgvString = "\u2014"; // &mdash;
        String trendString = "{ion_ios_minus_empty}";
        int trendRotation = 0;

        if (displayCgmResults.size() > 0) {
            timeLastSGV = displayCgmResults.last().getEventDate().getTime();
            sgvString = FormatKit.getInstance().formatAsGlucose(displayCgmResults.last().getSgv(), false, true);
            String trend = displayCgmResults.last().getCgmTrend();
            if (displayCgmResults.last().isEstimate()) {
                trendString = "{ion-ios-medical}";
            } else if (trend != null) {
                switch (PumpHistoryCGM.NS_TREND.valueOf(trend)) {
                    case TRIPLE_UP:
                        trendString = "{ion_ios_arrow_thin_up}{ion_ios_arrow_thin_up}{ion_ios_arrow_thin_up}";
                        break;
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
                    case TRIPLE_DOWN:
                        trendString = "{ion_ios_arrow_thin_down}{ion_ios_arrow_thin_down}{ion_ios_arrow_thin_down}";
                        break;
                    default:
                        trendString = "{ion_ios_minus_empty}";
                        break;
                }
            }
        }

        textViewBg.setText(sgvString);
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
            String timeString = getString(R.string.dots);

            if (timeLastSGV > 0) {
                nextRun = 60000L - (System.currentTimeMillis() - timeLastSGV) % 60000L;
                timeString = DateUtils.getRelativeTimeSpanString(timeLastSGV).toString();
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
                        batIcon.setTitle(getResources().getString(R.string.main_screen__icon_nightscout_status));
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

    private void refreshDisplayChart() {
        Log.d(TAG, "refreshDisplayChart");
        if (historyRealm.isInTransaction()) {
            mUiRealmHandler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            stopDisplayChart();
                            startDisplayChart();
                        }
                    });
        } else {
            stopDisplayChart();
            startDisplayChart();
        }
    }

    private void stopDisplayChart() {
        Log.d(TAG, "stopDisplayChart");
        mUiRealmHandler.removeCallbacks(mUiRefreshRunnable);
        if (displayChartResults != null) {
            displayChartResults.removeAllChangeListeners();
            displayChartResults = null;
        }
    }

    private void startDisplayChart() {
        Log.d(TAG, "startDisplayChart");

        // reset if last chart interaction was over 5 mins
        if (System.currentTimeMillis() - chartChangeTimestamp > 5 * 60000L)
            chartTimeOffset = 0;

        final long timestamp = timeLastSGV == 0 ? System.currentTimeMillis() : timeLastSGV - chartTimeOffset;

        displayChartResults = historyRealm.where(PumpHistoryCGM.class)
                .notEqualTo("sgv", 0)
                .greaterThan("eventDate", new Date(timestamp - 24 * 60 * 60000L))
                .lessThanOrEqualTo("eventDate", new Date(timestamp + 5 * 60000L))
                .sort("eventDate", Sort.ASCENDING)
                .findAllAsync();

        displayChartResults.addChangeListener(new OrderedRealmCollectionChangeListener<RealmResults<PumpHistoryCGM>>() {
            @Override
            public void onChange(@NonNull RealmResults<PumpHistoryCGM> realmResults, @NonNull OrderedCollectionChangeSet changeSet) {
                Log.d(TAG, "displayChartResults triggered size=" + displayChartResults.size());
                updateChart(realmResults, timestamp);
            }
        });
    }

    private void updateChart(RealmResults<PumpHistoryCGM> results, long timestamp) {

        // calc X & Y chart bounds with readable stepping for mmol & mg/dl
        // X needs offsetting as graphview will not always show points near edges
        long minX = (((timestamp + 150000L - (chartZoom * 60 * 60 * 1000L)) / 60000L) * 60000L);
        long maxX = timestamp + 90000L;
        double minY = 100;
        double maxY = 100;

        if (results != null) {
            RealmResults<PumpHistoryCGM> minmaxY = results.where()
                    .greaterThan("eventDate", new Date(minX))
                    .sort("sgv", Sort.ASCENDING)
                    .findAll();
            if (minmaxY.size() > 0) {
                minY = minmaxY.first().getSgv();
                maxY = minmaxY.last().getSgv();
            }
        }

        long rangeY, minRangeY;
        if (mPrefs.getBoolean("mmolxl", false)) {
            minY = Math.floor((minY / MMOLXLFACTOR) * 2);
            maxY = Math.ceil((maxY / MMOLXLFACTOR) * 2);
            rangeY = (long) (maxY - minY);
            minRangeY = ((rangeY / 4) + 1) * 4;
            minY = minY - Math.floor((minRangeY - rangeY) / 2);
            maxY = minY + minRangeY;
            minY = Math.floor(minY * MMOLXLFACTOR / 2);
            maxY = Math.floor(maxY * MMOLXLFACTOR / 2);
        } else {
            minY = Math.floor(minY / 10) * 10;
            maxY = Math.ceil(maxY / 10) * 10;
            rangeY = (long) (maxY - minY);
            minRangeY = ((rangeY / 20) + 1) * 20;
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
        DataPoint[] entries;;
        if (results != null && results.size() > 0) {
            entries = new DataPoint[results.size()];
            int pos = 0;
            for (PumpHistoryCGM event : results) {
                entries[pos++] = new DataPoint(
                        event.getEventDate(),
                        (double) event.getSgv(),
                        event.isEstimate(),
                        PumpHistoryParser.CGM_EXCEPTION.convert(event.getSensorException())
                );
            }
        } else {
            entries = new DataPoint[] {new DataPoint(
                    new Date(0),
                    100,
                    false,
                    PumpHistoryParser.CGM_EXCEPTION.NA)};
        }

        if (mChart.getSeries().size() == 0) {

            PointsGraphSeries sgvSeries = new PointsGraphSeries(entries);

            sgvSeries.setOnDataPointTapListener(new OnDataPointTapListener() {
                @Override
                public void onTap(Series series, DataPointInterface dataPoint) {
                    long timestamp = (long) dataPoint.getX();
                    int sgv = (int) dataPoint.getY();

                    PumpHistoryParser.CGM_EXCEPTION ex = ((MainActivity.DataPoint) dataPoint).getException();

                    String t = String.format("%s : %s",
                            FormatKit.getInstance().formatAsDayNameMonthNameDay(timestamp),
                            FormatKit.getInstance().formatAsClock(timestamp)
                    );

                    String v = String.format("\n%s",
                            FormatKit.getInstance().formatAsGlucose(sgv, true, true)
                    );

                    SpannableStringBuilder ssb = new SpannableStringBuilder();
                    ssb.append(t);
                    ssb.setSpan(new RelativeSizeSpan(0.85f), ssb.length() - t.length(), ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                    if (ex != PumpHistoryParser.CGM_EXCEPTION.SENSOR_OK) {
                        String x = String.format("\n(%s)", ex.string());
                        ssb.append(x);
                        ssb.setSpan(new RelativeSizeSpan(0.75f), ssb.length() - x.length(), ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        ssb.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), ssb.length() - x.length(), ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }

                    ssb.append(v);
                    ssb.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), ssb.length() - v.length(), ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    ssb.setSpan(new StyleSpan(Typeface.BOLD), ssb.length() - v.length(), ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                    toast = serveToast(ssb, toast, findViewById(R.id.view_sgv));
                }
            });

            sgvSeries.setCustomShape(new PointsGraphSeries.CustomShape() {
                @Override
                public void draw(Canvas canvas, Paint paint, float x, float y, DataPointInterface dataPoint) {
                    float factor = landscape ? 1.3f : 1.0f;
                    float dotSize;
                    double sgv = dataPoint.getY();

                    switch (((MainActivity.DataPoint) dataPoint).getException()) {
                        case SENSOR_END_OF_LIFE:
                            paint.setColor(0xFFA0A0A0);
                            break;
                        case SENSOR_ERROR:
                        case SENSOR_CHANGE_CAL_ERROR:
                        case SENSOR_CHANGE_SENSOR_ERROR:
                            paint.setColor(0xFFC040C0);
                            break;
                        case SENSOR_CAL_NEEDED:
                        case SENSOR_CAL_PENDING:
                            paint.setColor(0xFF0080FF);
                            break;
                        default:
                            if (sgv < 80)
                                paint.setColor(Color.RED);
                            else if (sgv <= 180)
                                paint.setColor(Color.GREEN);
                            else if (sgv <= 260)
                                paint.setColor(Color.YELLOW);
                            else
                                paint.setColor(Color.RED);
                    }

                    switch (chartZoom) {
                        case 1:
                            dotSize = 3.5f * factor;
                            break;
                        case 3:
                            dotSize = 2.5f * factor;
                            break;
                        case 6:
                            dotSize = 2.0f * factor;
                            break;
                        case 12:
                            dotSize = 1.65f * factor;
                            break;
                        case 24:
                            dotSize = 1.25f * factor;
                            break;
                        default:
                            dotSize = 3.0f * factor;
                    }

                    canvas.drawCircle(x, y, dipToPixels(getApplicationContext(), dotSize), paint);
                }
            });

            mChart.addSeries(sgvSeries);
        }

        else ((PointsGraphSeries) mChart.getSeries().get(0)).resetData(entries);

    }

    private class DataPoint implements DataPointInterface, Serializable {
        private static final long serialVersionUID = 1428263322645L;

        private double x;
        private double y;

        private boolean estimate;
        private PumpHistoryParser.CGM_EXCEPTION exception;

        public DataPoint(Date x, double y, boolean estimate, PumpHistoryParser.CGM_EXCEPTION exception) {
            this.x = x.getTime();
            this.y = y;
            this.estimate = estimate;
            this.exception = exception;
        }

        public boolean isEstimate() {
            return estimate;
        }

        public PumpHistoryParser.CGM_EXCEPTION getException() {
            return exception;
        }

        @Override
        public double getX() {
            return x;
        }

        @Override
        public double getY() {
            return y;
        }

        @Override
        public String toString() {
            return "[" + x + "/" + y + "]";
        }
    }

    private static float dipToPixels(Context context, float dipValue) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue, metrics);
    }

    private class UserLogDisplay {

        private Context context;

        private FloatingActionButton fabCurrent;
        private FloatingActionButton fabSearch;

        private RealmRecyclerView realmRecyclerView;
        private UserLogAdapter adapter;

        private Realm userLogRealm;
        private RealmResults<UserLog> userLogResults;

        private boolean autoScroll;
        private boolean extended;

        public UserLogDisplay(Context context) {
            Log.d(TAG, "UserLogDisplay init");

            this.context = context;

            realmRecyclerView = findViewById(R.id.recyclerview_log);
            realmRecyclerView.getRecycleView().setHasFixedSize(true);
            //realmRecyclerView.setItemViewCacheSize(30);
            realmRecyclerView.setDrawingCacheEnabled(true);
            realmRecyclerView.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);

            fabCurrent = findViewById(R.id.fab_log_current);
            fabCurrent.hide();

            // return to most recent log entry
            fabCurrent.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    if (userLogResults != null && adapter != null && realmRecyclerView != null) {

                        int t = adapter.getItemCount();
                        int c = realmRecyclerView.getChildCount();
                        if (c == 0) return;
                        int p = realmRecyclerView.findFirstVisibleItemPosition();
                        if (p < 0 || p > userLogResults.size() - 1) return;

                        if (t - c - p > 200) {
                            realmRecyclerView.scrollToPosition(t - 1);
                        } else {
                            realmRecyclerView.smoothScrollToPosition(t - 1);
                        }

                    }
                }
            });

            fabSearch = findViewById(R.id.fab_log_search);
            fabSearch.hide();

            // search click: in normal mode will scroll to errors/warnings, in extended mode this includes notes
            fabSearch.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    if (userLogResults != null && adapter != null && realmRecyclerView != null) {
                        int p = realmRecyclerView.findFirstVisibleItemPosition();
                        if (p >= 0 && p < userLogResults.size()) {

                            RealmResults<UserLog> rr = userLogResults.where()
                                    .lessThan("timestamp", userLogResults.get(p).getTimestamp())
                                    .beginGroup()
                                    .equalTo("type", UserLogMessage.TYPE.WARN.value())
                                    .or()
                                    .equalTo("type", UserLogMessage.TYPE.NOTE.value())
                                    .endGroup()
                                    .sort("timestamp", Sort.DESCENDING)
                                    .findAll();

                            if (rr.size() > 0) {
                                int ss = userLogResults.indexOf(rr.first());
                                int c = realmRecyclerView.getRecycleView().getLayoutManager().getChildCount() / 4;
                                int to = ss - (c < 1 ? 1 : c);
                                if (to < 0) to = 0;
                                if (Math.abs(p - to) > 400)
                                    realmRecyclerView.scrollToPosition(to);
                                else
                                    realmRecyclerView.smoothScrollToPosition(to);
                            }
                        }
                    }
                }
            });

            // search long click: in normal mode will scroll to the start of a session, in extended mode to notes
            fabSearch.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {

                    if (userLogResults != null && adapter != null && realmRecyclerView != null) {
                        int p = realmRecyclerView.findFirstVisibleItemPosition();
                        if (p >= 0 && p < userLogResults.size()) {

                            RealmResults<UserLog> rr;
                            if (extended) {
                                rr = userLogResults.where()
                                        .lessThan("timestamp", userLogResults.get(p).getTimestamp())
                                        .equalTo("type", UserLogMessage.TYPE.NOTE.value())
                                        .sort("timestamp", Sort.DESCENDING)
                                        .findAll();
                            } else {
                                rr = userLogResults.where()
                                        .lessThan("timestamp", userLogResults.get(p).getTimestamp() - 60 * 60000L)
                                        .equalTo("type", UserLogMessage.TYPE.WARN.value())
                                        .sort("timestamp", Sort.DESCENDING)
                                        .findAll();
                            }
                            int to = 0;
                            if (rr.size() > 0) {
                                int ss = userLogResults.indexOf(rr.first());
                                int c = realmRecyclerView.getRecycleView().getLayoutManager().getChildCount() / 4;
                                to = ss - (c < 1 ? 1 : c);
                                if (to < 0) to = 0;
                            }

                            if (Math.abs(p - to) > 400)
                                realmRecyclerView.scrollToPosition(to);
                            else
                                realmRecyclerView.smoothScrollToPosition(to);
                        }
                    }
                    return true;
                }
            });

            // show/hide the floating log buttons
            RecyclerView rv = realmRecyclerView.getRecycleView();
            rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    boolean fab = false;
                    if (userLogResults != null && adapter != null && realmRecyclerView != null) {
                        int t = adapter.getItemCount();
                        int p = realmRecyclerView.findFirstVisibleItemPosition();
                        int c = realmRecyclerView.getRecycleView().getLayoutManager().getChildCount();

                        if (p >= 0 && p < t && t - p - c > 4)
                            fab = true;
                    }
                    if (fab) {
                        fabCurrent.show();
                        fabSearch.show();
                    } else {
                        fabCurrent.hide();
                        fabSearch.hide();
                    }
                }
            });

            // don't autoscroll the log when screen is being touched by user
            rv.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
                @Override
                public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                    if (e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_MOVE)
                        autoScroll = false;
                    else
                        autoScroll = true;
                    return false;
                }

                @Override
                public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                }

                @Override
                public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
                }
            });

        }

        public void stop() {
            Log.d(TAG, "UserLogDisplay stop");

            if (adapter != null) {
                if (realmRecyclerView.getRecycleView() != null && realmRecyclerView.getRecycleView().getLayoutManager() != null)
                    realmRecyclerView.getRecycleView().getLayoutManager().removeAllViews();
                realmRecyclerView.setAdapter(null);
                adapter.close();
                adapter = null;
            }

            if (userLogResults != null) {
                userLogResults.removeAllChangeListeners();
                userLogResults = null;
            }

            if (userLogRealm != null) {
                if (!userLogRealm.isClosed()) userLogRealm.close();
                userLogRealm = null;
            }
        }

        public void focusCurrent() {
            int lastPosition = userLogResults.size() - 1;
            adapter.setLastAnimPosition(lastPosition);
            realmRecyclerView.scrollToPosition(lastPosition);
        }

        public void start() {
            start(false);
        }

        public void start(Boolean extended) {
            Log.d(TAG, "UserLogDisplay start");

            this.extended = extended;
            autoScroll = true;

            if (userLogRealm == null)
                userLogRealm = Realm.getInstance(UploaderApplication.getUserLogConfiguration());

            UserLogMessage.getInstance().stale();

            userLogResults = userLogRealm.where(UserLog.class)
                    .beginGroup()
                    .equalTo("flag", UserLogMessage.FLAG.NA.value())
                    .or()
                    .equalTo("flag", extended ? UserLogMessage.FLAG.EXTENDED.value() : UserLogMessage.FLAG.NORMAL.value())
                    .endGroup()
                    .sort("index", Sort.ASCENDING)
                    .findAllAsync();

            adapter = new UserLogAdapter(context, userLogResults, true);
            realmRecyclerView.setAdapter(adapter);

            userLogResults.addChangeListener(new OrderedRealmCollectionChangeListener<RealmResults<UserLog>>() {
                @Override
                public void onChange(@NonNull RealmResults realmResults, OrderedCollectionChangeSet changeSet) {
                    if (adapter == null || realmRecyclerView == null) return;

                    if (changeSet.getState().equals(OrderedCollectionChangeSet.State.INITIAL)) {
                        focusCurrent();
                    }

                    else if (changeSet.getState().equals(OrderedCollectionChangeSet.State.UPDATE)) {

                        final int i = changeSet.getInsertions().length;
                        final int d = changeSet.getDeletions().length;
                        if (d > 0) {
                            adapter.setLastAnimPosition(adapter.getLastAnimPosition() - d);
                        }

                        RecyclerView rv = realmRecyclerView.getRecycleView();

                        int r = rv.computeVerticalScrollRange();
                        int o = rv.computeVerticalScrollOffset();
                        int e = rv.computeVerticalScrollExtent();

                        if (autoScroll && (r - o - e < e / 2)) {
                            rv.post(new Runnable() {
                                public void run() {
                                    try {
                                        if (d - i > 2)
                                            realmRecyclerView.scrollToPosition(adapter.getItemCount() - 1);
                                        else
                                            realmRecyclerView.smoothScrollToPosition(adapter.getItemCount() - 1);
                                    } catch (Exception ignored) {
                                    }
                                }
                            });
                        }

                    }
                }
            });
        }

    }

}
