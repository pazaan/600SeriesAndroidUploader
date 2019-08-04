package info.nightscout.android.urchin;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.PebbleKit.PebbleAckReceiver;
import com.getpebble.android.kit.util.PebbleDictionary;

import java.math.RoundingMode;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import info.nightscout.android.R;
import info.nightscout.android.UploaderApplication;
import info.nightscout.android.history.PumpHistoryHandler;
import info.nightscout.android.history.PumpHistoryParser;
import info.nightscout.android.medtronic.UserLogMessage;
import info.nightscout.android.medtronic.service.MasterService;
import info.nightscout.android.model.medtronicNg.PumpHistoryBG;
import info.nightscout.android.model.medtronicNg.PumpHistoryBasal;
import info.nightscout.android.model.medtronicNg.PumpHistoryBolus;
import info.nightscout.android.model.medtronicNg.PumpHistoryCGM;
import info.nightscout.android.model.medtronicNg.PumpHistoryMisc;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.model.store.DataStore;
import info.nightscout.android.utils.FormatKit;
import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;

import static info.nightscout.android.medtronic.service.MedtronicCnlService.POLL_PERIOD_MS;

/**
 * Created by Pogman on 10.12.17.
 */

public class UrchinService extends Service {
    private static final String TAG = UrchinService.class.getSimpleName();

    private static final UUID URCHIN_UUID = UUID.fromString("ea361603-0373-4865-9824-8f52c65c6e07");

    private final int GRAPH_MAX_SGV_COUNT = 144;
    private final int STATUS_BAR_MAX_LENGTH = 256;
    private final int PREDICTION_MAX_LENGTH = 60;
    private final int NO_DELTA_VALUE = 65536;
    private final long TIME_STEP = 300000L;

    private Context mContext;
    private PebbleAckReceiver pebbleAckReceiver;
    private Update updateThread;

    private Realm realm;
    private Realm storeRealm;
    private Realm historyRealm;
    private DataStore dataStore;
    private PumpStatusEvent pumpStatusEvent;

    private int receivedCount = 0;

    private long ignoreACK = 0;
    private int updateID = 0;

    private long timeNow;

    private long eventTime; // urchin recency = now - event time / 1000;
    private byte[] sgvs;
    private int sgv;
    private int trend;
    private int delta;
    private String text;
    private byte[] extra;

    private enum KEY {
        msgType,
        recency,
        sgvCount,
        sgvs,
        lastSgv,
        trend,
        delta,
        statusText,
        graphExtra,
        statusRecency,
        prediction1,
        prediction2,
        prediction3,
        predictionRecency
    }

    private enum MSG_TYPE {
        ERROR,
        DATA,
        PREFERENCES
    }

    private enum TREND {
        NONE(0),
        DOUBLE_UP(1),
        SINGLE_UP(2),
        FOURTY_FIVE_UP(3),
        FLAT(4),
        FOURTY_FIVE_DOWN(5),
        SINGLE_DOWN(6),
        DOUBLE_DOWN(7),
        NOT_COMPUTABLE(0),
        RATE_OUT_OF_RANGE(0),
        NOT_SET(0);

        private int value;

        TREND(int value) {
            this.value = value;
        }

        public int value() {
            return this.value;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate called");
        mContext = this.getBaseContext();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy called");

        if (pebbleAckReceiver != null) mContext.unregisterReceiver(pebbleAckReceiver);
        pebbleAckReceiver = null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Received start id " + startId + "  : " + intent);

        if (intent == null) {
            return START_NOT_STICKY;
        }

        String action = intent.getAction();

        if (action.equals("update") && updateThread == null) {

            updateThread = new Update();
            updateThread.setPriority(Thread.MIN_PRIORITY);
            updateThread.start();

            return START_STICKY;
        }

        return START_NOT_STICKY;
    }

    private synchronized void refresh() {
        Log.d(TAG, "sending data to Pebble");

        timeNow = System.currentTimeMillis();
        ignoreACK = timeNow + 2000L;

        PebbleDictionary out = new PebbleDictionary();

        int recency = (int) (timeNow - eventTime) / 1000;

        out.addInt32(KEY.msgType.ordinal(), MSG_TYPE.DATA.ordinal());
        out.addInt32(KEY.recency.ordinal(), recency);
        out.addInt32(KEY.sgvCount.ordinal(), GRAPH_MAX_SGV_COUNT);
        out.addBytes(KEY.sgvs.ordinal(), sgvs);
        out.addInt32(KEY.lastSgv.ordinal(), sgv);
        out.addInt32(KEY.trend.ordinal(), trend);
        out.addInt32(KEY.delta.ordinal(), delta);
        out.addString(KEY.statusText.ordinal(), text);
        out.addBytes(KEY.graphExtra.ordinal(), extra);

        updateID += 13; // odd offset used to stop us syncing to urchin pebble JS ack
        updateID &= 0xFF;
        Log.d(TAG, "refresh: updateID = " + updateID);

        PebbleKit.sendDataToPebbleWithTransactionId(mContext, URCHIN_UUID, out, updateID);

        messageReceiver();
    }

    private void received() {
        if (receivedCount++ == 1) {
            UserLogMessage.send(mContext, UserLogMessage.TYPE.SHARE, String.format("{id;%s} {id;%s}",
                    R.string.ul_share__urchin, R.string.ul_share__is_available));
        }
    }

    private void messageReceiver() {
        if (pebbleAckReceiver == null) {
            pebbleAckReceiver = new PebbleAckReceiver(URCHIN_UUID) {
                @Override
                public void receiveAck(Context context, int transactionId) {

                    Log.d(TAG, "received ACK id=" + transactionId);

                    // an ack from pebble urchin watchface that does not equal the last update id
                    // means a data refresh is needed (workaround for lack of a direct message)

                    // a short ignore period after update/refresh is used to stop a refresh race condition

                    if (transactionId != updateID && System.currentTimeMillis() > ignoreACK)
                        refresh();
                    else
                        received();
                }
            };

            PebbleKit.registerReceivedAckHandler(mContext, pebbleAckReceiver);
        }
    }

    private class Update extends Thread {

        public void run() {

            storeRealm = Realm.getInstance(UploaderApplication.getStoreConfiguration());
            dataStore = storeRealm.where(DataStore.class).findFirst();

            if (!dataStore.isUrchinEnable()) {

                if (pebbleAckReceiver != null) {
                    mContext.unregisterReceiver(pebbleAckReceiver);
                    pebbleAckReceiver = null;
                }

            } else {

                realm = Realm.getDefaultInstance();
                historyRealm = Realm.getInstance(UploaderApplication.getHistoryConfiguration());

                timeNow = System.currentTimeMillis();
                ignoreACK = timeNow + 2000L;

                RealmResults<PumpStatusEvent> pumpresults = realm.where(PumpStatusEvent.class)
                        .greaterThan("eventDate", new Date(timeNow - 24 * 60 * 1000L))
                        .sort("eventDate", Sort.DESCENDING)
                        .findAll();

                RealmResults<PumpStatusEvent> cgmresults = pumpresults.where()
                        .equalTo("cgmActive", true)
                        .sort("cgmDate", Sort.DESCENDING)
                        .findAll();

                RealmResults<PumpHistoryCGM> sgvresults = historyRealm.where(PumpHistoryCGM.class)
                        .notEqualTo("sgv", 0)
                        .greaterThan("eventDate", new Date(timeNow - 2 * 60 * 60000L))
                        .sort("eventDate", Sort.DESCENDING)
                        .findAll();

                long lastReceivedEventTime;
                long lastReceivedCgmTime;
                long lastActualCgmTime;

                if (pumpresults.size() > 0) {
                    pumpStatusEvent = pumpresults.first();
                    lastReceivedEventTime = pumpStatusEvent.getEventDate().getTime();

                    if (cgmresults.size() > 0) {
                        lastReceivedCgmTime = cgmresults.first().getCgmDate().getTime();
                        // normalise last received cgm time to current time window
                        lastActualCgmTime = lastReceivedCgmTime + (((timeNow - lastReceivedCgmTime) / POLL_PERIOD_MS) * POLL_PERIOD_MS);

                        // normalise last received pump event to cgm time window
                        if (lastActualCgmTime > lastReceivedEventTime) {
                            // no current event data from pump
                            eventTime = lastReceivedEventTime - ((lastReceivedEventTime - lastReceivedCgmTime) % POLL_PERIOD_MS);
                        } else {
                            eventTime = lastActualCgmTime;
                        }

                    } else {
                        // no cgm data
                        eventTime = lastReceivedEventTime;
                    }
                } else {
                    // no pump data
                    eventTime = timeNow - 24 * 60 * 60000L;
                }

                sgv = 0;
                delta = NO_DELTA_VALUE;
                trend = TREND.NONE.value();

                if (sgvresults.size() > 0) {
                    long age = (eventTime - sgvresults.first().getEventDate().getTime()) / 1000L;

                    // don't show any sgv if older then 60mins
                    if (age < 61 * 60)
                        sgv = sgvresults.first().getSgv();

                    // don't show trend/delta if older then 10mins
                    if (age < 11 * 60) {
                        if (sgvresults.first().getCgmTrend() != null)
                            trend = TREND.valueOf(PumpHistoryCGM.NS_TREND.valueOf(sgvresults.first().getCgmTrend()).dexcom().name()).value();

                        // don't show delta if sgv period older then 5 mins
                        if (sgvresults.size() > 1
                                && sgvresults.get(0).getCgmRTC() - sgvresults.get(1).getCgmRTC() < 6 * 60)
                            delta = sgvresults.get(0).getSgv() - sgvresults.get(1).getSgv();
                    }
                }

                text = buildStatusLayout();

                if (text.length() > STATUS_BAR_MAX_LENGTH)
                    text = text.substring(0, STATUS_BAR_MAX_LENGTH);

                sgvs = graphSgv(eventTime - (TIME_STEP / 2));

                extra = graphBasal(eventTime - (TIME_STEP / 2));
                extra = graphBolus(eventTime - TIME_STEP, extra);
                extra = graphBolusPop(eventTime - TIME_STEP, extra);

                // drop ref to Realm object
                pumpStatusEvent = null;

                refresh();
            }

            closeRealm();

            updateThread = null;
        }
    }

    private void closeRealm() {
        if (historyRealm != null && !historyRealm.isClosed()) historyRealm.close();
        if (storeRealm != null && !storeRealm.isClosed()) storeRealm.close();
        if (realm != null && !realm.isClosed()) realm.close();
        dataStore = null;
        historyRealm = null;
        storeRealm = null;
        realm = null;
    }

    private byte[] graphSgv(long time) {

        byte[] graph = new byte[GRAPH_MAX_SGV_COUNT];

        RealmResults<PumpHistoryCGM> results = historyRealm.where(PumpHistoryCGM.class)
                .greaterThan("eventDate", new Date(time - (GRAPH_MAX_SGV_COUNT * TIME_STEP)))
                .notEqualTo("sgv", 0)
                .sort("eventDate", Sort.DESCENDING)
                .findAll();

        Iterator<PumpHistoryCGM> iterator = results.iterator();
        PumpHistoryCGM record = null;
        int sgv;

        for (int i = 0; i < GRAPH_MAX_SGV_COUNT; i++) {
            sgv = 0;

            do {
                if (record == null && iterator.hasNext()) record = iterator.next();
                if (record != null && record.getEventDate().getTime() >= time) {
                    sgv = record.getSgv();
                    record = null;
                } else {
                    break;
                }
            } while (iterator.hasNext());

            graph[i] = (byte) (sgv >> 1);

            time = time - TIME_STEP;
        }

        return graph;
    }

    private byte[] graphBasal(long time) {

        // period to use for scaling largest to smallest basal
        int basalPeriod = (dataStore.getUrchinBasalPeriod() + 1) * 12;

        Date limitDate = new Date(timeNow - (basalPeriod * TIME_STEP));

        RealmResults<PumpStatusEvent> results = realm
                .where(PumpStatusEvent.class)
                .greaterThanOrEqualTo("eventDate", limitDate)
                .sort("eventDate", Sort.DESCENDING)
                .findAll();

        Iterator<PumpStatusEvent> iterator = results.iterator();
        PumpStatusEvent record = null;

        Float[] basals = new Float[basalPeriod];
        Float basal = (float) 0;
        Float largest = (float) 0;

        // get basals for each time step in period
        for (int i = 0; i < basalPeriod; i++) {

            do {
                if (record == null && iterator.hasNext()) record = iterator.next();
                if (record != null && record.getEventDate().getTime() < time + (TIME_STEP * 2)) {

                    if (record.getEventDate().getTime() >= time) {
                        if (record.isSuspended()) {
                            basal = (float) 0;
                        } else if (record.isTempBasalActive()) {
                            if (record.getTempBasalPercentage() != 0)
                                basal = (record.getTempBasalPercentage() * record.getBasalRate()) / 100;
                            else
                                basal = record.getTempBasalRate();
                        } else {
                            basal = record.getBasalRate();
                        }
                        record = null;
                    }
                    break;

                } else record = null;
            } while (iterator.hasNext());

            basals[i] = basal;
            if (basal > largest) largest = basal;
            time = time - TIME_STEP;
        }

        float scale = dataStore.getUrchinBasalScale() / largest; // urchin graph range 0-31

        // create the graph

        byte[] graph = new byte[GRAPH_MAX_SGV_COUNT];

        int y;
        for (int i = 0; i < GRAPH_MAX_SGV_COUNT; i++) {

            if (i < basalPeriod) {
                basal = basals[i];
                if (basal > 0) {
                    y = (int) (basal * scale);
                    if (y < 2) y = 2;
                } else
                    y = 0;
            } else
                y = 0;

            graph[i] = (byte) (y << 1);
        }

        return graph;
    }

    private byte[] graphBolus(long time) {
        return graphBolus(time, new byte[GRAPH_MAX_SGV_COUNT]);
    }

    private byte[] graphBolus(long time, byte[] graph) {

        if (dataStore.isUrchinBolusGraph()) {

            RealmResults<PumpHistoryBolus> results = historyRealm.where(PumpHistoryBolus.class)
                    .greaterThan("eventDate", new Date(time - (GRAPH_MAX_SGV_COUNT * TIME_STEP)))
                    .equalTo("programmed", true)
                    .sort("eventDate", Sort.DESCENDING)
                    .findAll();

            Iterator<PumpHistoryBolus> iterator = results.iterator();
            PumpHistoryBolus record = null;

            int flag;

            for (int i = 0; i < GRAPH_MAX_SGV_COUNT; i++) {
                flag = 0;

                do {
                    if (record == null && iterator.hasNext()) record = iterator.next();
                    if (record != null && record.getProgrammedDate().getTime() < time + TIME_STEP) {
                        if (record.getProgrammedDate().getTime() >= time) {
                            flag = 1;
                            record = null;
                        }
                        break;
                    } else record = null;
                } while (iterator.hasNext());

                graph[i] = (byte) (graph[i] & 0xFE | flag);

                time = time - TIME_STEP;
            }
        }

        return graph;
    }

    private byte[] graphBolusPop(long time, byte[] graph) {
        int bolusPop = dataStore.getUrchinBolusPop() << 1;
        if (bolusPop > 0 ) {

            RealmResults<PumpHistoryBolus> results = historyRealm.where(PumpHistoryBolus.class)
                    .greaterThan("eventDate", new Date(time - (GRAPH_MAX_SGV_COUNT * TIME_STEP) - (8 * 60 * 60000L)))
                    .notEqualTo("bolusType", PumpHistoryParser.BOLUS_TYPE.NORMAL_BOLUS.value())
                    .equalTo("programmed", true)
                    .sort("eventDate", Sort.DESCENDING)
                    .findAll();

            Iterator<PumpHistoryBolus> iterator = results.iterator();
            PumpHistoryBolus record = null;

            boolean flag;

            for (int i = 0; i < GRAPH_MAX_SGV_COUNT; i++) {
                flag = false;

                do {
                    if (record == null && iterator.hasNext()) record = iterator.next();
                    if (record != null && record.getProgrammedDate().getTime() < time + TIME_STEP) {
                        if ((record.isSquareDelivered() && record.getSquareDeliveredDate().getTime() > time)
                                || (!record.isSquareDelivered() && record.getProgrammedDate().getTime() + (record.getSquareProgrammedDuration() * 60000L) > time)) {
                            flag = true;
                        }
                        break;
                    }
                    record = null;
                } while (iterator.hasNext());

                if (flag) graph[i] = (byte) (graph[i] & 0x01 | bolusPop);

                time = time - TIME_STEP;
            }
        }

        return graph;
    }

    private String iob() {
        if (pumpStatusEvent != null && pumpStatusEvent.getEventDate().getTime() > timeNow  - 4 * 60 * 60000L) {
            return pumpStatusEvent.getActiveInsulin() + styleUnits();
        }
        return FormatKit.getInstance().getString(R.string.urchin_watchface_NoData);
    }

    private String bolusing() {
        if (pumpStatusEvent != null && pumpStatusEvent.getEventDate().getTime() > timeNow - 8 * 60 * 60000L) {
            if (!pumpStatusEvent.isBolusingNormal()
                    && (pumpStatusEvent.isBolusingSquare() || pumpStatusEvent.isBolusingDual())) {
                return String.format("%s%s%s%s%s",
                        FormatKit.getInstance().getString(R.string.urchin_watchface_Bolusing),
                        FormatKit.getInstance().formatAsDecimal(pumpStatusEvent.getBolusingDelivered(), 1, 1, RoundingMode.HALF_UP),
                        styleUnits(),
                        styleConcatenate(),
                        styleDuration(pumpStatusEvent.getBolusingMinutesRemaining()));
            }
        }
        return "";
    }

    private String lastBolus() {
        RealmResults<PumpHistoryBolus> results = historyRealm.where(PumpHistoryBolus.class)
                .greaterThan("eventDate", new Date(timeNow - 12 * 60 * 60000L))
                .equalTo("programmed", true)
                .sort("eventDate", Sort.DESCENDING)
                .findAll();

        if (results.size() > 0) {
            Double insulin;
            String tag = "";

            if (PumpHistoryParser.BOLUS_TYPE.DUAL_WAVE.equals(results.first().getBolusType())) {
                if (dataStore.isUrchinBolusTags()) tag = FormatKit.getInstance().getString(R.string.urchin_watchface_Dual);
                if (results.first().isSquareDelivered())
                    insulin = results.first().getNormalDeliveredAmount() + results.first().getSquareDeliveredAmount();
                else if (results.first().isNormalDelivered())
                    insulin = results.first().getNormalDeliveredAmount() + results.first().getSquareProgrammedAmount();
                else
                    insulin = results.first().getNormalProgrammedAmount() + results.first().getSquareProgrammedAmount();

            } else if (PumpHistoryParser.BOLUS_TYPE.SQUARE_WAVE.equals(results.first().getBolusType())) {
                if (dataStore.isUrchinBolusTags()) tag = FormatKit.getInstance().getString(R.string.urchin_watchface_Square);
                if (results.first().isSquareDelivered())
                    insulin = results.first().getSquareDeliveredAmount();
                else
                    insulin = results.first().getSquareProgrammedAmount();

            } else {
                if (results.first().isNormalDelivered())
                    insulin =  results.first().getNormalDeliveredAmount();
                else
                    insulin =  results.first().getNormalProgrammedAmount();
            }

            return String.format("%s%s%s%s%s",
                    tag,
                    FormatKit.getInstance().formatAsDecimal(insulin, 0, 1, RoundingMode.HALF_UP),
                    styleUnits(),
                    styleConcatenate(),
                    styleTime(results.first().getProgrammedDate().getTime()));
        }

        return "";
    }

    private String basal() {
        if (pumpStatusEvent != null && pumpStatusEvent.getEventDate().getTime() > timeNow - 12 * 60 * 60000L) {
            float rate = 0.f;

            if (pumpStatusEvent.isSuspended()) {
                RealmResults<PumpHistoryBasal> suspend = historyRealm.where(PumpHistoryBasal.class)
                        .greaterThan("eventDate", new Date(timeNow - 12 * 60 * 60000L))
                        .equalTo("recordtype", PumpHistoryBasal.RECORDTYPE.SUSPEND.value())
                        .or()
                        .equalTo("recordtype", PumpHistoryBasal.RECORDTYPE.RESUME.value())
                        .sort("eventDate", Sort.DESCENDING)
                        .findAll();
                // check if most recent suspend is in history and show the start time
                if (suspend.size() > 0 && PumpHistoryBasal.RECORDTYPE.SUSPEND.equals(suspend.first().getRecordtype()))
                    rate = (float) 0;

            } else if (pumpStatusEvent.isTempBasalActive()) {
                int percent = pumpStatusEvent.getTempBasalPercentage();
                if (percent != 0)
                    rate += (percent * pumpStatusEvent.getBasalRate()) / 100;
                else
                    rate = pumpStatusEvent.getTempBasalRate();

            } else rate = pumpStatusEvent.getBasalRate();

            return String.format("%s%s",
                    FormatKit.getInstance().formatAsDecimal(rate, 1, 2, RoundingMode.HALF_EVEN),
                    styleUnits());
        }

        return FormatKit.getInstance().getString(R.string.urchin_watchface_NoData);
    }

    private String basalCombo() {
        if (pumpStatusEvent != null && pumpStatusEvent.getEventDate().getTime() > timeNow - 12 * 60 * 60000L) {
            float rate;

            if (pumpStatusEvent.isSuspended()) {
                RealmResults<PumpHistoryBasal> suspend = historyRealm.where(PumpHistoryBasal.class)
                        .greaterThan("eventDate", new Date(timeNow - 12 * 60 * 60000L))
                        .equalTo("recordtype", PumpHistoryBasal.RECORDTYPE.SUSPEND.value())
                        .or()
                        .equalTo("recordtype", PumpHistoryBasal.RECORDTYPE.RESUME.value())
                        .sort("eventDate", Sort.DESCENDING)
                        .findAll();
                // check if most recent suspend is in history and show the start time
                if (suspend.size() > 0 && PumpHistoryBasal.RECORDTYPE.SUSPEND.equals(suspend.first().getRecordtype()))
                    return String.format("%s%s%s",
                            FormatKit.getInstance().getString(R.string.urchin_watchface_Suspend),
                            styleConcatenate(),
                            styleTime(suspend.first().getEventDate().getTime()));

            } else if (pumpStatusEvent.isTempBasalActive()) {
                rate = pumpStatusEvent.getTempBasalRate();
                int percent = pumpStatusEvent.getTempBasalPercentage();
                int minutes = pumpStatusEvent.getTempBasalMinutesRemaining();
                if (percent != 0) {
                    rate += (percent * pumpStatusEvent.getBasalRate()) / 100;
                }
                return String.format("%s%s%s%s",
                        FormatKit.getInstance().formatAsDecimal(rate, 1, 2, RoundingMode.HALF_EVEN),
                        styleUnits(),
                        styleConcatenate(),
                        styleDuration(minutes));

            } else {
                rate = pumpStatusEvent.getBasalRate();
                return String.format("%s%s",
                        FormatKit.getInstance().formatAsDecimal(rate, 1, 2, RoundingMode.HALF_EVEN),
                        styleUnits());
            }

        }

        return FormatKit.getInstance().getString(R.string.urchin_watchface_NoData);
    }

    private String basalState() {
        if (pumpStatusEvent != null && pumpStatusEvent.getEventDate().getTime() > timeNow - 12 * 60 * 60000L) {

            if (pumpStatusEvent.isSuspended()) {
                RealmResults<PumpHistoryBasal> suspend = historyRealm.where(PumpHistoryBasal.class)
                        .greaterThan("eventDate", new Date(timeNow - 12 * 60 * 60000L))
                        .equalTo("recordtype", PumpHistoryBasal.RECORDTYPE.SUSPEND.value())
                        .or()
                        .equalTo("recordtype", PumpHistoryBasal.RECORDTYPE.RESUME.value())
                        .sort("eventDate", Sort.DESCENDING)
                        .findAll();
                // check if most recent suspend is in history and show the start time
                if (suspend.size() > 0 && PumpHistoryBasal.RECORDTYPE.SUSPEND.equals(suspend.first().getRecordtype()))
                    return String.format("%s%s%s",
                            FormatKit.getInstance().getString(R.string.urchin_watchface_Suspend),
                            styleConcatenate(),
                            styleTime(suspend.first().getEventDate().getTime()));

            } else if (pumpStatusEvent.isTempBasalActive()) {
                int minutes = pumpStatusEvent.getTempBasalMinutesRemaining();
                return String.format("%s%s%s",
                        FormatKit.getInstance().getString(R.string.urchin_watchface_Temp),
                        styleConcatenate(),
                        styleDuration(minutes));
            }
        }

        return "";
    }

    private String calibration() {

        if (pumpStatusEvent != null
                && pumpStatusEvent.isCgmActive()
                && pumpStatusEvent.getEventDate().getTime() >= timeNow - 15 *60000L) {

            long due = pumpStatusEvent.getCalibrationDueMinutes() + ((pumpStatusEvent.getCgmDate().getTime() - timeNow) / 60000L);
            if (due < 0) due = 0;
            String dueString = due >= 100
                    ? ((due + 30) / 60 + FormatKit.getInstance().getString(R.string.hour_h))
                    : (due % 100 + FormatKit.getInstance().getString(R.string.minute_m));

            PumpHistoryParser.CGM_EXCEPTION cgmException;
            if (pumpStatusEvent.isCgmException())
                cgmException = PumpHistoryParser.CGM_EXCEPTION.convert(
                        pumpStatusEvent.getCgmExceptionType());
            else if (pumpStatusEvent.isCgmCalibrating())
                cgmException = PumpHistoryParser.CGM_EXCEPTION.SENSOR_CAL_PENDING;
            else
                cgmException = PumpHistoryParser.CGM_EXCEPTION.NA;

            switch (cgmException) {
                case NA:
                    return dueString;
                case SENSOR_INIT:
                    return String.format("%s%s",
                            FormatKit.getInstance().getString(R.string.urchin_watchface_WarmUp),
                            dueString);
                case SENSOR_CAL_PENDING:
                    return FormatKit.getInstance().getString(R.string.urchin_watchface_Calibrating);
                case SENSOR_CAL_NEEDED:
                    return FormatKit.getInstance().getString(R.string.urchin_watchface_CalibrateNow);
                case SENSOR_CHANGE_CAL_ERROR:
                case SENSOR_CHANGE_SENSOR_ERROR:
                case SENSOR_END_OF_LIFE:
                    return FormatKit.getInstance().getString(R.string.urchin_watchface_SensorEndOfLife);
                default:
                    return FormatKit.getInstance().getString(R.string.urchin_watchface_SensorError);
            }
        }

        return   FormatKit.getInstance().getString(R.string.urchin_watchface_NoCGM);
    }

    private String uploaderBattery() {
        int battery = MasterService.getUploaderBatteryLevel();
        if (battery > 0) return styleBattery(battery);
        return FormatKit.getInstance().getString(R.string.urchin_watchface_NoData);
    }

    private String pumpBattery() {
        if (pumpStatusEvent != null)
            return styleBattery(pumpStatusEvent.getBatteryPercentage());
        return FormatKit.getInstance().getString(R.string.urchin_watchface_NoData);
    }

    private String pumpBatteryAge() {
        RealmResults<PumpHistoryMisc> results = historyRealm.where(PumpHistoryMisc.class)
                .equalTo("recordtype", PumpHistoryMisc.RECORDTYPE.CHANGE_BATTERY.value())
                .sort("eventDate", Sort.DESCENDING)
                .findAll();
        if (results.size() > 0)
            return String.format("%s%s",
                    (timeNow - results.first().getEventDate().getTime()) / (24 * 60 * 60000L),
                    FormatKit.getInstance().getString(R.string.day_d));
        return FormatKit.getInstance().getString(R.string.urchin_watchface_NoData);
    }

    private String pumpReservoirUnits() {
        if (pumpStatusEvent != null)
            return String.format("%s%s",
                    FormatKit.getInstance().formatAsDecimal(pumpStatusEvent.getReservoirAmount(), 0, 0, RoundingMode.HALF_UP),
                    styleUnits());
        return FormatKit.getInstance().getString(R.string.urchin_watchface_NoData);
    }

    private String pumpReservoirAge() {
        RealmResults<PumpHistoryMisc> results = historyRealm.where(PumpHistoryMisc.class)
                .equalTo("recordtype", PumpHistoryMisc.RECORDTYPE.CHANGE_CANNULA.value())
                .sort("eventDate", Sort.DESCENDING)
                .findAll();
        if (results.size() > 0)
            return String.format("%s%s",
                    (timeNow - results.first().getEventDate().getTime()) / (60 * 60000L),
                    FormatKit.getInstance().getString(R.string.hour_h));
        return "";
    }

    private String transmitterBattery() {
        String s = "";
        if (pumpStatusEvent != null)
            s = styleBattery(pumpStatusEvent.getTransmitterBattery());
        return s;
    }

    private int sensorHours() {
        RealmResults<PumpHistoryMisc> results = historyRealm.where(PumpHistoryMisc.class)
                .equalTo("recordtype", PumpHistoryMisc.RECORDTYPE.CHANGE_SENSOR.value())
                .sort("eventDate", Sort.DESCENDING)
                .findAll();
        if (results.size() > 0)
            return (int) ((timeNow - results.first().getEventDate().getTime()) / (60 * 60000L));
        else return -1;
    }

    private String sensorAge() {
        int hours = sensorHours();
        if (hours == -1)
            return FormatKit.getInstance().getString(R.string.urchin_watchface_NoData);
        else
            return (hours / 24) + FormatKit.getInstance().getString(R.string.day_d);
    }

    // assumes a liftime of 6 days, sensors with longer life will produce a wrong result
    private String sensorLife() {
        int hours = sensorHours();
        if (hours == -1)
            return FormatKit.getInstance().getString(R.string.urchin_watchface_NoData);
        hours = 145 - hours;
        if (hours < 0) hours = 0;
        if (hours > 120) return "6" + FormatKit.getInstance().getString(R.string.day_d);
        else if (hours > 10) return ((hours / 24) + 1) + FormatKit.getInstance().getString(R.string.day_d);
        else return hours + FormatKit.getInstance().getString(R.string.hour_h);
    }

    private String isig(boolean stability) {
        if (dataStore.isReportIsigAvailable()) {
            PumpHistoryHandler pumpHistoryHandler = new PumpHistoryHandler(this);
            PumpHistoryHandler.IsigReport isigReport = pumpHistoryHandler.isigReport();
            isigReport.formatter(1, 1);
            String s = stability ? isigReport.formatStabilityAsSymbol() : "";
            s = isigReport.formatIsig(0, s);
            pumpHistoryHandler.close();
            return s;
        }
        return "";
    }

    private String estimate() {
        if (dataStore.isSysEnableEstimateSGV()) {
            RealmResults<PumpHistoryCGM> cgmResults = historyRealm
                    .where(PumpHistoryCGM.class)
                    .sort("eventDate", Sort.DESCENDING)
                    .findAll();
            if (cgmResults.size() > 0 && cgmResults.first().isEstimate())
                return FormatKit.getInstance().getString(R.string.urchin_watchface_Estimate);
        }
        return "";
    }

    private String bgTime() {
        RealmResults<PumpHistoryBG> bgResults = historyRealm
                .where(PumpHistoryBG.class)
                .sort("eventDate", Sort.ASCENDING)
                .findAll();
        if (bgResults.size() > 0)
            return String.format("%s%s%s",
                    FormatKit.getInstance().formatAsGlucose(bgResults.last().getBg()),
                    styleConcatenate(),
                    styleTime(bgResults.last().getBgDate().getTime())
            );
        return "";
    }

    private String bgAge() {
        RealmResults<PumpHistoryBG> bgResults = historyRealm
                .where(PumpHistoryBG.class)
                .sort("eventDate", Sort.ASCENDING)
                .findAll();
        if (bgResults.size() > 0)
            return String.format("%s%s%s",
                    FormatKit.getInstance().formatAsGlucose(bgResults.last().getBg()),
                    styleConcatenate(),
                    styleDuration((int) ((timeNow - bgResults.last().getBgDate().getTime()) / 60000L))
            );
        return "";
    }

    private String bg(long timeNow) {
        RealmResults<PumpHistoryBG> bgResults = historyRealm
                .where(PumpHistoryBG.class)
                .greaterThanOrEqualTo("eventDate", new Date(timeNow))
                .sort("eventDate", Sort.ASCENDING)
                .findAll();
        if (bgResults.size() > 0)
            return FormatKit.getInstance().formatAsGlucose(bgResults.last().getBg());
        return "";
    }

    private String factor(long time) {
        RealmResults<PumpHistoryBG> bgResults = historyRealm
                .where(PumpHistoryBG.class)
                .equalTo("calibration", true)
                .greaterThanOrEqualTo("calibrationDate", new Date(time))
                .sort("eventDate", Sort.ASCENDING)
                .findAll();
        if (bgResults.size() > 0) {
            return FormatKit.getInstance().formatAsDecimal(bgResults.last().getCalibrationFactor(), 1, 1, RoundingMode.DOWN);
        }
        return "";
    }

    private String alert() {
        if (pumpStatusEvent != null
                && pumpStatusEvent.getAlert() > 0) {
            int minutes = (int) ((timeNow - pumpStatusEvent.getAlertDate().getTime()) / 60000L);
            return String.format("%s%s%s",
                    FormatKit.getInstance().getString(R.string.urchin_watchface_Alert),
                    styleConcatenate(),
                    styleDuration(minutes)
            );
        }
        return "";
    }

    private String alertTime() {
        if (pumpStatusEvent != null
                && pumpStatusEvent.getAlert() > 0) {
            return String.format("%s%s%s",
                    FormatKit.getInstance().getString(R.string.urchin_watchface_Alert),
                    styleConcatenate(),
                    styleTime(pumpStatusEvent.getAlertDate().getTime())
            );
        }
        return "";
    }

    private String buildStatusLayout() {
        StringBuilder sb = new StringBuilder();

        byte[] statusLayout = dataStore.getUrchinStatusLayout();
        boolean group = false;
        boolean skip = false;
        String s;

        for (byte item : statusLayout) {
            s = getStatusItem(item);
            if (s.equals("start")) {
                group = true;
                skip = false;
            } else if (s.equals("end")) {
                group = false;
            } else if (group && !skip && s.length() > 0) {
                sb.append(s);
                skip = true;
            } else if (!group) {
                sb.append(s);
            }
        }
        Log.d(TAG,"status layout:\n" + sb.toString());

        return sb.toString();
    }

    // Pebble font available chars = " 0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz°•·-`~!@#$%^&*()_+=[]\\{}|;':\",./<>?";

    private String getStatusItem(int item) {
        String s;
        switch (item & 0xFF) {
            case 1:
                s = "\n";
                break;

            case 2:
                s = " ";
                break;
            case 3:
                s = "  ";
                break;
            case 4:
                s = ":";
                break;
            case 5:
                s = " : ";
                break;
            case 6:
                s = "|";
                break;
            case 7:
                s = " | ";
                break;
            case 8:
                s = "·";
                break;
            case 9:
                s = "•";
                break;
            case 10:
                s = "/";
                break;
            case 11:
                s = "\\";
                break;

            case 12:
                s = iob();
                break;

            case 13:
                s = basal();
                break;
            case 14:
                s = basalState();
                break;
            case 15:
                s = basalCombo();
                break;

            case 16:
                s = lastBolus();
                break;
            case 17:
                s = bolusing();
                break;
            case 18:
                s = bolusing();
                if (s.length() == 0) s = lastBolus();
                break;

            case 19:
                s = calibration();
                break;
            case 20:
                s = bolusing();
                if (s.length() == 0) {
                    s = lastBolus();
                    s = s.length() == 0 ? calibration() : calibration() + " " + s;
                }
                break;

            case 21:
                s = uploaderBattery();
                break;
            case 22:
                s = pumpBattery();
                break;
            case 23:
                s = transmitterBattery();
                break;
            case 24:
                s = sensorLife();
                break;
            case 25:
                s = sensorAge();
                break;
            case 26:
                s = pumpReservoirAge();
                break;
            case 27:
                s = pumpBatteryAge();
                break;
            case 28:
                s = pumpReservoirUnits();
                break;

            case 29:
                s = dfDay.format(timeNow);
                break;
            case 30:
                s = dfDayName.format(timeNow);
                break;
            case 31:
                s = dataStore.getUrchinCustomText1();
                break;
            case 32:
                s = dataStore.getUrchinCustomText2();
                break;

            case 101:
                s = dfMonth.format(timeNow);
                break;
            case 102:
                s = dfMonthName.format(timeNow);
                break;

            case 111:
                s = bgTime();
                break;
            case 112:
                s = bgAge();
                break;
            case 113:
                s = bg(timeNow - 15 * 60000L);
                break;
            case 114:
                s = bg(timeNow - 15 * 60000L);
                if (s.length() > 0)
                    s = String.format("%s%s%s",
                            FormatKit.getInstance().getString(R.string.urchin_watchface_BG),
                            styleConcatenate(),
                            s);
                break;
            case 115:
                s = bg(timeNow - 60 * 60000L);
                if (s.length() > 0)
                    s = String.format("%s%s%s",
                            FormatKit.getInstance().getString(R.string.urchin_watchface_BG),
                            styleConcatenate(),
                            s);
                break;

            case 121:
                s = factor(timeNow - 15 * 60000L);
                break;
            case 122:
                s = factor(timeNow - 15 * 60000L);
                if (s.length() > 0)
                    s = String.format("%s%s%s",
                            FormatKit.getInstance().getString(R.string.urchin_watchface_Factor),
                            styleConcatenate(),
                            s);
                break;
            case 123:
                s = factor(timeNow - 60 * 60000L);
                if (s.length() > 0)
                    s = String.format("%s%s%s",
                            FormatKit.getInstance().getString(R.string.urchin_watchface_Factor),
                            styleConcatenate(),
                            s);
                break;

            case 131:
                s = isig(true);
                break;
            case 132:
                s = isig(false);
                if (s.length() > 0)
                    s = String.format("%s%s%s",
                            FormatKit.getInstance().getString(R.string.urchin_watchface_ISIG),
                            styleConcatenate(),
                            s);
                break;

            case 141:
                s = estimate();
                break;

            case 151:
                s = alertTime();
                break;
            case 152:
                s = alert();
                break;

            case 201:
                s = "start";
                break;
            case 202:
                s = "end";
                break;

            default:
                s = "";
        }
        return s;
    }

    private DateFormat dfDay = new SimpleDateFormat("d", Locale.getDefault());
    private DateFormat dfDayName = new SimpleDateFormat("EEE", Locale.getDefault());
    private DateFormat dfMonth = new SimpleDateFormat("M", Locale.getDefault());
    private DateFormat dfMonthName = new SimpleDateFormat("MMM", Locale.getDefault());
    private DateFormat df24 = new SimpleDateFormat("HH:mm", Locale.US);
    private DateFormat df12 = new SimpleDateFormat("h:mm", Locale.US);
    private DateFormat dfAMPM = new SimpleDateFormat("a", Locale.getDefault());

    private String styleTime(long time) {
        String text;

        String ampm = dfAMPM.format(time).replace(".", "").replace(",", "").toLowerCase();
        ampm = ampm.length() > 1 ? ampm.substring(0, 2) : (ampm.length() > 0 ? ampm.substring(0, 1) : "");
        String ap = ampm.length() > 0 ? ampm.substring(0, 1) : "";

        switch (dataStore.getUrchinTimeStyle()) {
            case 1:
                text = df12.format(time) + ap;
                break;
            case 2:
                text = df12.format(time) + ap.toUpperCase();
                break;
            case 3:
                text = df12.format(time) + ampm;
                break;
            case 4:
                text = df12.format(time) + ampm.toUpperCase();
                break;
            case 5:
                text = df24.format(time);
                break;
            default:
                text = df12.format(time);
        }
        return text;
    }

    private String styleDuration(int duration) {
        String s;
        switch (dataStore.getUrchinDurationStyle()) {
            case 1:
                s = duration + FormatKit.getInstance().getString(R.string.minute_m);
                break;
            case 2:
                s = String.format("%s%s",
                        duration < 60 ? "" : duration / 60 + FormatKit.getInstance().getString(R.string.hour_h),
                        duration % 60);
                break;
            case 3:
                s = String.format("%s",
                        duration < 60 ? duration % 60 + FormatKit.getInstance().getString(R.string.minute_m) :
                                duration / 60 + FormatKit.getInstance().getString(R.string.hour_h) + duration % 60);
                break;
            case 4:
                s = String.format("%s%s",
                        duration < 60 ? "" : duration / 60 + FormatKit.getInstance().getString(R.string.hour_h),
                        duration % 60 + FormatKit.getInstance().getString(R.string.minute_m));
                break;
            default:
                s = String.valueOf(duration);
        }
        return s;
    }

    private String styleUnits() {
        String s;
        switch (dataStore.getUrchinUnitsStyle()) {
            case 1:
                s = FormatKit.getInstance().getString(R.string.insulin_U).toLowerCase();
                break;
            case 2:
                s = FormatKit.getInstance().getString(R.string.insulin_U).toUpperCase();
                break;
            default:
                s = "";
        }
        return s;
    }

    private String styleBattery(int battery) {
        String s;
        switch (dataStore.getUrchinBatteyStyle()) {
            case 1:
                s = battery + "%";
                break;
            case 2:
                s = (battery > 99 ? "99" : battery) + "";
                break;
            case 3:
                s = (battery > 99 ? "99" : battery) + "%";
                break;
            case 4:
                battery = battery / 10;
                s = (battery > 9 ? "9" : battery) + "";
                break;
            case 5:
                if (battery > 25)
                    s = FormatKit.getInstance().getString(R.string.urchin_watchface_Battery_Hi);
                else
                    s = FormatKit.getInstance().getString(R.string.urchin_watchface_Battery_Lo);
                break;
            case 6:
                if (battery > 65)
                    s = FormatKit.getInstance().getString(R.string.urchin_watchface_Battery_High);
                else if (battery > 25)
                    s = FormatKit.getInstance().getString(R.string.urchin_watchface_Battery_Medium);
                else
                    s = FormatKit.getInstance().getString(R.string.urchin_watchface_Battery_Low);
                break;
            case 7:
                if (battery > 80)
                    s = FormatKit.getInstance().getString(R.string.urchin_watchface_Battery_Full);
                else if (battery > 60)
                    s = FormatKit.getInstance().getString(R.string.urchin_watchface_Battery_High);
                else if (battery > 30)
                    s = FormatKit.getInstance().getString(R.string.urchin_watchface_Battery_Medium);
                else if (battery > 15)
                    s = FormatKit.getInstance().getString(R.string.urchin_watchface_Battery_Low);
                else
                    s = FormatKit.getInstance().getString(R.string.urchin_watchface_Battery_Empty);
                break;
            default:
                s = battery + "";
        }
        return s;
    }

    private String styleConcatenate() {
        String s;
        switch (dataStore.getUrchinConcatenateStyle()) {
            case 1:
                s = " ";
                break;
            case 2:
                s = "·";
                break;
            case 3:
                s = "•";
                break;
            case 4:
                s = "-";
                break;
            case 5:
                s = "/";
                break;
            case 6:
                s = "\\";
                break;
            case 7:
                s = "|";
                break;
            default:
                s = "";
        }
        return s;
    }

    public static List getListPreferenceItems() {

        List<String> items = new ArrayList<>();

        FormatKit fk = FormatKit.getInstance();

        items.add("0");
        items.add(String.format("%s",
                fk.getString(R.string.urchin_status_layout_Empty))
        );
        items.add("1");
        items.add(String.format("%s",
                fk.getString(R.string.urchin_status_layout_Nextline)
        ));
        items.add("2");
        items.add(String.format("%s: %s",
                fk.getString(R.string.urchin_status_layout_Text),
                fk.getString(R.string.urchin_text_space)
        ));
        items.add("3");
        items.add(String.format("%s: %s",
                fk.getString(R.string.urchin_status_layout_Text),
                fk.getString(R.string.urchin_text_double_space)
        ));
        items.add("4");
        items.add(String.format("%s: %s ':'",
                fk.getString(R.string.urchin_status_layout_Text),
                fk.getString(R.string.urchin_text_colon)
        ));
        items.add("5");
        items.add(String.format("%s: %s ' : '",
                fk.getString(R.string.urchin_status_layout_Text),
                fk.getString(R.string.urchin_text_colon)
        ));
        items.add("6");
        items.add(String.format("%s: %s '|'",
                fk.getString(R.string.urchin_status_layout_Text),
                fk.getString(R.string.urchin_text_line)
        ));
        items.add("7");
        items.add(String.format("%s: %s ' | '",
                fk.getString(R.string.urchin_status_layout_Text),
                fk.getString(R.string.urchin_text_line)
        ));
        items.add("8");
        items.add(String.format("%s: %s '·'",
                fk.getString(R.string.urchin_status_layout_Text),
                fk.getString(R.string.urchin_text_dot)
        ));
        items.add("9");
        items.add(String.format("%s: %s '•'",
                fk.getString(R.string.urchin_status_layout_Text),
                fk.getString(R.string.urchin_text_bullet)
        ));
        items.add("10");
        items.add(String.format("%s: %s '/'",
                fk.getString(R.string.urchin_status_layout_Text),
                fk.getString(R.string.urchin_text_forward_slash)
        ));
        items.add("11");
        items.add(String.format("%s: %s '\\'",
                fk.getString(R.string.urchin_status_layout_Text),
                fk.getString(R.string.urchin_text_backslash)
        ));

        items.add("12");
        items.add(String.format("%s: '%s'",
                fk.getString(R.string.urchin_status_layout_Active_Insulin),
                fk.formatAsInsulin(3.5)
        ));
        items.add("13");
        items.add(String.format("%s: '%s'",
                fk.getString(R.string.urchin_status_layout_Basal),
                fk.formatAsInsulin(0.85)
        ));
        items.add("14");
        items.add(String.format("%s: '%s·%s' '%s·%s' (%s)",
                fk.getString(R.string.urchin_status_layout_Basal_State),
                fk.getString(R.string.urchin_watchface_Temp),
                fk.formatMinutesAsM(45),
                fk.getString(R.string.urchin_watchface_Suspend),
                "11:30",
                fk.getString(R.string.urchin_status_layout_when_active)
        ));
        items.add("15");
        items.add(String.format("%s / %s: '%s·%s' '%s·%s' (%s)",
                fk.getString(R.string.urchin_status_layout_Basal),
                fk.getString(R.string.urchin_status_layout_Basal_State),
                fk.formatAsInsulin(0.0),
                fk.formatMinutesAsM(45),
                fk.getString(R.string.urchin_watchface_Suspend),
                "11:30",
                fk.getString(R.string.urchin_status_layout_dynamic)
        ));

        items.add("16");
        items.add(String.format("%s: '%s·%s'",
                fk.getString(R.string.urchin_status_layout_Last_Bolus),
                fk.formatAsInsulin(3.5),
                "11:30"
        ));
        items.add("17");
        items.add(String.format("%s: '%s%s·%s' (%s)",
                fk.getString(R.string.urchin_status_layout_Bolusing),
                fk.getString(R.string.urchin_watchface_Bolusing),
                fk.formatAsInsulin(3.5),
                fk.formatMinutesAsM(45),
                fk.getString(R.string.urchin_status_layout_when_active)
        ));
        items.add("18");
        items.add(String.format("%s / %s: (%s)",
                fk.getString(R.string.urchin_status_layout_Bolusing),
                fk.getString(R.string.urchin_status_layout_Last_Bolus),
                fk.getString(R.string.urchin_status_layout_dynamic)
        ));

        items.add("19");
        items.add(String.format("%s: '%s' '%s'",
                fk.getString(R.string.urchin_status_layout_Calibration),
                fk.formatMinutesAsM(45),
                fk.getString(R.string.urchin_watchface_CalibrateNow)
        ));
        items.add("20");
        items.add(String.format("%s / %s & %s: (%s)",
                fk.getString(R.string.urchin_status_layout_Bolusing),
                fk.getString(R.string.urchin_status_layout_Calibration),
                fk.getString(R.string.urchin_status_layout_Last_Bolus),
                fk.getString(R.string.urchin_status_layout_dynamic)
        ));

        items.add("21");
        items.add(String.format("%s",
                fk.getString(R.string.urchin_status_layout_Uploader_Battery)
        ));
        items.add("22");
        items.add(String.format("%s",
                fk.getString(R.string.urchin_status_layout_Pump_Battery)
        ));
        items.add("23");
        items.add(String.format("%s",
                fk.getString(R.string.urchin_status_layout_Transmitter_Battery)
        ));

        items.add("24");
        items.add(String.format("%s: '%s'",
                fk.getString(R.string.urchin_status_layout_Sensor_Life),
                fk.formatDaysAsD(6)
        ));
        items.add("25");
        items.add(String.format("%s: '%s'",
                fk.getString(R.string.urchin_status_layout_Sensor_Age),
                fk.formatDaysAsD(6)
        ));

        items.add("26");
        items.add(String.format("%s: '%s'",
                fk.getString(R.string.urchin_status_layout_Reservoir_Age),
                fk.formatHoursAsH(45)
        ));
        items.add("27");
        items.add(String.format("%s: '%s'",
                fk.getString(R.string.urchin_status_layout_Pump_Battery_Age),
                fk.formatDaysAsD(6)
        ));

        items.add("28");
        items.add(String.format("%s: '%s'",
                fk.getString(R.string.urchin_status_layout_Reservoir_Units),
                fk.formatAsInsulin(99.0)
        ));

        items.add("29");
        items.add(String.format("%s: '%s'",
                fk.getString(R.string.urchin_status_layout_Day),
                fk.formatAsDay(System.currentTimeMillis())
        ));
        items.add("30");
        items.add(String.format("%s: '%s'",
                fk.getString(R.string.urchin_status_layout_Day),
                fk.formatAsDayNameShort(System.currentTimeMillis())
        ));

        items.add("101");
        items.add(String.format("%s: '%s'",
                fk.getString(R.string.urchin_status_layout_Month),
                fk.formatAsMonth(System.currentTimeMillis())
        ));
        items.add("102");
        items.add(String.format("%s: '%s'",
                fk.getString(R.string.urchin_status_layout_Month),
                fk.formatAsMonthNameShort(System.currentTimeMillis())
        ));

        items.add("31");
        items.add(String.format("%s 1",
                fk.getString(R.string.urchin_status_layout_Custom_Text)
        ));
        items.add("32");
        items.add(String.format("%s 2",
                fk.getString(R.string.urchin_status_layout_Custom_Text)
        ));

        items.add("111");
        items.add(String.format("%s: '%s·%s'",
                fk.getString(R.string.urchin_status_layout_BG),
                fk.formatAsGlucose(100),
                "11:30"
        ));
        items.add("112");
        items.add(String.format("%s: '%s·%s'",
                fk.getString(R.string.urchin_status_layout_BG),
                fk.formatAsGlucose(100),
                fk.formatMinutesAsM(45)
        ));
        items.add("113");
        items.add(String.format("%s: '%s' (%s %s)",
                fk.getString(R.string.urchin_status_layout_BG),
                fk.formatAsGlucose(100),
                fk.getString(R.string.urchin_status_layout_when_recent),
                fk.formatMinutesAsM(15)
        ));
        items.add("114");
        items.add(String.format("%s: '%s·%s' (%s %s)",
                fk.getString(R.string.urchin_status_layout_BG),
                fk.getString(R.string.urchin_watchface_BG),
                fk.formatAsGlucose(100),
                fk.getString(R.string.urchin_status_layout_when_recent),
                fk.formatMinutesAsM(15)
        ));
        items.add("115");
        items.add(String.format("%s: '%s·%s' (%s %s)",
                fk.getString(R.string.urchin_status_layout_BG),
                fk.getString(R.string.urchin_watchface_BG),
                fk.formatAsGlucose(100),
                fk.getString(R.string.urchin_status_layout_when_recent),
                fk.formatMinutesAsM(60)
        ));

        items.add("121");
        items.add(String.format("%s: '%s' (%s %s)",
                fk.getString(R.string.urchin_status_layout_Factor),
                fk.formatAsDecimal(4.5, 1),
                fk.getString(R.string.urchin_status_layout_when_recent),
                fk.formatMinutesAsM(15)
        ));
        items.add("122");
        items.add(String.format("%s: '%s·%s' (%s %s)",
                fk.getString(R.string.urchin_status_layout_Factor),
                fk.getString(R.string.urchin_watchface_Factor),
                fk.formatAsDecimal(4.5, 1),
                fk.getString(R.string.urchin_status_layout_when_recent),
                fk.formatMinutesAsM(15)
        ));
        items.add("123");
        items.add(String.format("%s: '%s·%s' (%s %s)",
                fk.getString(R.string.urchin_status_layout_Factor),
                fk.getString(R.string.urchin_watchface_Factor),
                fk.formatAsDecimal(4.5, 1),
                fk.getString(R.string.urchin_status_layout_when_recent),
                fk.formatMinutesAsM(60)
        ));

        items.add("131");
        items.add(String.format("%s: '+- %s' (%s)",
                fk.getString(R.string.urchin_status_layout_ISIG),
                fk.formatAsDecimal(31.4, 1),
                fk.getString(R.string.urchin_status_layout_when_available)
        ));
        items.add("132");
        items.add(String.format("%s: '%s·%s' (%s)",
                fk.getString(R.string.urchin_status_layout_ISIG),
                fk.getString(R.string.urchin_watchface_ISIG),
                fk.formatAsDecimal(31.4, 1),
                fk.getString(R.string.urchin_status_layout_when_available)
        ));

        items.add("141");
        items.add(String.format("%s: '%s' (%s)",
                fk.getString(R.string.urchin_status_layout_Estimate),
                fk.getString(R.string.urchin_watchface_Estimate),
                fk.getString(R.string.urchin_status_layout_when_available)
        ));

        items.add("151");
        items.add(String.format("%s: '%s·%s' (%s)",
                fk.getString(R.string.urchin_status_layout_Pump_Alert),
                fk.getString(R.string.urchin_watchface_Alert),
                "11:30",
                fk.getString(R.string.urchin_status_layout_when_active)
        ));
        items.add("152");
        items.add(String.format("%s: '%s·%s' (%s)",
                fk.getString(R.string.urchin_status_layout_Pump_Alert),
                fk.getString(R.string.urchin_watchface_Alert),
                fk.formatMinutesAsM(45),
                fk.getString(R.string.urchin_status_layout_when_active)
        ));

        items.add("201");
        items.add(String.format("[%s] %s",
                fk.getString(R.string.urchin_status_layout_GROUP_START),
                fk.getString(R.string.urchin_status_layout_first_active_only)
        ));
        items.add("202");
        items.add(String.format("[%s]",
                fk.getString(R.string.urchin_status_layout_GROUP_END)
        ));

        return items;
    }

}