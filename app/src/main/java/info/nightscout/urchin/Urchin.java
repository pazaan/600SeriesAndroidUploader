package info.nightscout.urchin;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.PebbleKit.PebbleAckReceiver;
import com.getpebble.android.kit.util.PebbleDictionary;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

import info.nightscout.android.UploaderApplication;
import info.nightscout.android.medtronic.PumpHistoryParser;
import info.nightscout.android.medtronic.service.MasterService;
import info.nightscout.android.model.medtronicNg.PumpHistoryBasal;
import info.nightscout.android.model.medtronicNg.PumpHistoryBolus;
import info.nightscout.android.model.medtronicNg.PumpHistoryCGM;
import info.nightscout.android.model.medtronicNg.PumpHistoryMisc;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.model.store.DataStore;
import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;

import static info.nightscout.android.medtronic.service.MedtronicCnlService.POLL_PERIOD_MS;

/**
 * Created by Pogman on 10.12.17.
 */

public class Urchin {
    private static final String TAG = Urchin.class.getSimpleName();

    private static final UUID URCHIN_UUID = UUID.fromString("ea361603-0373-4865-9824-8f52c65c6e07");

    private final int GRAPH_MAX_SGV_COUNT = 144;
    private final int STATUS_BAR_MAX_LENGTH = 256;
    private final int PREDICTION_MAX_LENGTH = 60;
    private final int NO_DELTA_VALUE = 65536;
    private final long TIME_STEP = 300000L;

    private Context mContext;
    private PebbleAckReceiver pebbleAckReceiver;

    private Realm realm;
    private Realm storeRealm;
    private Realm historyRealm;
    private DataStore dataStore;
    private PumpStatusEvent pumpStatusEvent;

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
        NONE,
        DOUBLE_UP,
        SINGLE_UP,
        FOURTYFIVE_UP,
        FLAT,
        FOURTYFIVE_DOWN,
        SINGLE_DOWN,
        DOUBLE_DOWN,
        NOT_COMPUTABLE,
        OUT_OF_RANGE
    }

    public Urchin(Context context) {
        Log.d(TAG, "initialising");

        mContext = context;

        update();
    }

    private void openRealm() {
        realm = Realm.getDefaultInstance();
        storeRealm = Realm.getInstance(UploaderApplication.getStoreConfiguration());
        historyRealm = Realm.getInstance(UploaderApplication.getHistoryConfiguration());
        dataStore = storeRealm.where(DataStore.class).findFirst();
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

    public void close() {
        Log.d(TAG, "close");

        if (pebbleAckReceiver != null) mContext.unregisterReceiver(pebbleAckReceiver);
        pebbleAckReceiver = null;

        closeRealm();
    }

    public void refresh() {
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

    public void update() {
        openRealm();

        if (!dataStore.isUrchinEnable()) {
            Log.d(TAG, "update: urchin disabled");
            if (pebbleAckReceiver != null) mContext.unregisterReceiver(pebbleAckReceiver);
            pebbleAckReceiver = null;

        } else {

            Log.d(TAG, "update: urchin enabled");

            timeNow = System.currentTimeMillis();
            ignoreACK = timeNow + 2000L;

            RealmResults<PumpStatusEvent> pumpresults = realm.where(PumpStatusEvent.class)
                    .greaterThan("eventDate", new Date(timeNow - 24 * 60 * 1000L))
                    .findAllSorted("eventDate", Sort.DESCENDING);

            RealmResults<PumpStatusEvent> cgmresults = pumpresults.where()
                    .equalTo("cgmActive", true)
                    .findAllSorted("cgmDate", Sort.DESCENDING);

            RealmResults<PumpHistoryCGM> sgvresults = historyRealm.where(PumpHistoryCGM.class)
                    .notEqualTo("sgv", 0)
                    .greaterThan("eventDate", new Date(timeNow - 2 * 60 * 60000L))
                    .findAllSorted("eventDate", Sort.DESCENDING);

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
            trend = TREND.NONE.ordinal();

            if (sgvresults.size() > 0) {
                long age = (eventTime - sgvresults.first().getEventDate().getTime()) / 1000L;

                // don't show any sgv if older then 60mins
                if (age < 61 * 60)
                    sgv = sgvresults.first().getSgv();

                // don't show trend/delta if older then 10mins
                if (age < 11 * 60) {
                    if (sgvresults.first().getCgmTrend() != null)
                        trend = PumpStatusEvent.CGM_TREND.valueOf(sgvresults.first().getCgmTrend()).ordinal();

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

                }
            };

            PebbleKit.registerReceivedAckHandler(mContext, pebbleAckReceiver);
        }
    }

    private byte[] graphSgv(long time) {

        byte[] graph = new byte[GRAPH_MAX_SGV_COUNT];

        RealmResults<PumpHistoryCGM> results = historyRealm.where(PumpHistoryCGM.class)
                .greaterThan("eventDate", new Date(time - (GRAPH_MAX_SGV_COUNT * TIME_STEP)))
                .notEqualTo("sgv", 0)
                .findAllSorted("eventDate", Sort.DESCENDING);

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
                .findAllSorted("eventDate", Sort.DESCENDING);

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
                    .findAllSorted("eventDate", Sort.DESCENDING);

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
                    .notEqualTo("bolusType", PumpHistoryParser.BOLUS_TYPE.NORMAL_BOLUS.get())
                    .equalTo("programmed", true)
                    .findAllSorted("eventDate", Sort.DESCENDING);

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
        String text = "";

        if (pumpStatusEvent != null && pumpStatusEvent.getEventDate().getTime() > timeNow  - 4 * 60 * 60000L) {
            text = pumpStatusEvent.getActiveInsulin() + styleUnits();
        }

        return text;
    }

    private String bolusing() {
        String text = "";

        if (pumpStatusEvent != null && pumpStatusEvent.getEventDate().getTime() > timeNow - 8 * 60 * 60000L) {

            if (!pumpStatusEvent.isBolusingNormal()
                    && (pumpStatusEvent.isBolusingSquare() || pumpStatusEvent.isBolusingDual())) {
                text += ">"
                        + new BigDecimal(pumpStatusEvent.getBolusingDelivered()).setScale(1, BigDecimal.ROUND_HALF_UP)
                        + styleUnits() + styleConcatenate() + styleDuration(pumpStatusEvent.getBolusingMinutesRemaining());
            }
        }

        return text;
    }

    private String lastBolus() {
        String text = "";
        Double insulin;

        RealmResults<PumpHistoryBolus> results = historyRealm.where(PumpHistoryBolus.class)
                .greaterThan("eventDate", new Date(timeNow - 12 * 60 * 60000L))
                .equalTo("programmed", true)
                .findAllSorted("eventDate", Sort.DESCENDING);

        if (results.size() > 0) {
            if (PumpHistoryParser.BOLUS_TYPE.DUAL_WAVE.equals(results.first().getBolusType())) {
                if (dataStore.isUrchinBolusTags()) text += "d";
                if (results.first().isSquareDelivered())
                    insulin = results.first().getNormalDeliveredAmount() + results.first().getSquareDeliveredAmount();
                else if (results.first().isNormalDelivered())
                    insulin = results.first().getNormalDeliveredAmount() + results.first().getSquareProgrammedAmount();
                else
                    insulin = results.first().getNormalProgrammedAmount() + results.first().getSquareProgrammedAmount();

            } else if (PumpHistoryParser.BOLUS_TYPE.SQUARE_WAVE.equals(results.first().getBolusType())) {
                if (dataStore.isUrchinBolusTags()) text += "s";
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

            text += new BigDecimal(insulin).setScale(1, BigDecimal.ROUND_HALF_UP).stripTrailingZeros().toPlainString()
                    + styleUnits() + styleConcatenate() + styleTime(results.first().getProgrammedDate().getTime());
        }

        return text;
    }

    private String basal() {
        float rate = (float) 0;

        if (pumpStatusEvent != null && pumpStatusEvent.getEventDate().getTime() > timeNow - 12 * 60 * 60000L) {

            if (pumpStatusEvent.isSuspended()) {
                RealmResults<PumpHistoryBasal> suspend = historyRealm.where(PumpHistoryBasal.class)
                        .greaterThan("eventDate", new Date(timeNow - 12 * 60 * 60000L))
                        .equalTo("suspend", true)
                        .or()
                        .equalTo("resume", true)
                        .findAllSorted("eventDate", Sort.DESCENDING);
                // check if most recent suspend is in history and show the start time
                if (suspend.size() > 0 && suspend.first().isSuspend())
                    rate = (float) 0;

            } else if (pumpStatusEvent.isTempBasalActive()) {
                int percent = pumpStatusEvent.getTempBasalPercentage();
                if (percent != 0)
                    rate += (percent * pumpStatusEvent.getBasalRate()) / 100;
                else
                    rate = pumpStatusEvent.getTempBasalRate();

            } else rate = pumpStatusEvent.getBasalRate();
        }

        return rate + styleUnits();
    }

    private String basalCombo() {
        String text = "";

        if (pumpStatusEvent != null && pumpStatusEvent.getEventDate().getTime() > timeNow - 12 * 60 * 60000L) {

            if (pumpStatusEvent.isSuspended()) {
                RealmResults<PumpHistoryBasal> suspend = historyRealm.where(PumpHistoryBasal.class)
                        .greaterThan("eventDate", new Date(timeNow - 12 * 60 * 60000L))
                        .equalTo("suspend", true)
                        .or()
                        .equalTo("resume", true)
                        .findAllSorted("eventDate", Sort.DESCENDING);
                // check if most recent suspend is in history and show the start time
                if (suspend.size() > 0 && suspend.first().isSuspend())
                    text = "S" + styleConcatenate() + styleTime(suspend.first().getEventDate().getTime());

            } else if (pumpStatusEvent.isTempBasalActive()) {
                float rate = pumpStatusEvent.getTempBasalRate();
                int percent = pumpStatusEvent.getTempBasalPercentage();
                int minutes = pumpStatusEvent.getTempBasalMinutesRemaining();
                if (percent != 0) {
                    rate += (percent * pumpStatusEvent.getBasalRate()) / 100;
                }
                text = rate + styleUnits() + styleConcatenate() + styleDuration(minutes);

            } else {
                text = pumpStatusEvent.getBasalRate() + styleUnits();
            }

        }

        return text;
    }

    private String basalState() {
        String text = "";

        if (pumpStatusEvent != null && pumpStatusEvent.getEventDate().getTime() > timeNow - 12 * 60 * 60000L) {

            if (pumpStatusEvent.isSuspended()) {
                RealmResults<PumpHistoryBasal> suspend = historyRealm.where(PumpHistoryBasal.class)
                        .greaterThan("eventDate", new Date(timeNow - 12 * 60 * 60000L))
                        .equalTo("suspend", true)
                        .or()
                        .equalTo("resume", true)
                        .findAllSorted("eventDate", Sort.DESCENDING);
                // check if most recent suspend is in history and show the start time
                if (suspend.size() > 0 && suspend.first().isSuspend())
                    text = "S" + styleConcatenate() + styleTime(suspend.first().getEventDate().getTime());

            } else if (pumpStatusEvent.isTempBasalActive()) {
                int minutes = pumpStatusEvent.getTempBasalMinutesRemaining();
                text = "T" + styleConcatenate() + styleDuration(minutes);
            }
        }

        return text;
    }

    private String calibration() {
        String text = "";

        if (pumpStatusEvent != null
                && pumpStatusEvent.isCgmActive()
                && pumpStatusEvent.getEventDate().getTime() >= timeNow - 15 *60000L) {

            if (pumpStatusEvent.isCgmCalibrating())
                text = "C";
            else {
                if (pumpStatusEvent.isCgmWarmUp())
                    text = "W";
                long timer = ((pumpStatusEvent.getCgmDate().getTime() - timeNow) / 60000L) + pumpStatusEvent.getCalibrationDueMinutes();
                if (timer >= 120)
                    text +=  timer / 60 + "h";
                else if (timer > 0)
                    text += (timer >= 100 ? "2h" : timer % 100 + "m");
                else
                    text = "C!";
            }

        } else
            text ="!!";

        return text;
    }

    private String uploaderBattery() {
        int battery = MasterService.getUploaderBatteryLevel();
        if (battery > 0) return styleBattery(battery);
        return "?";
    }

    private String pumpBattery() {
        String text = "";
        if (pumpStatusEvent != null)
            text = styleBattery(pumpStatusEvent.getBatteryPercentage());
        return text;
    }

    private String pumpBatteryAge() {
        String text = "";
        RealmResults<PumpHistoryMisc> results = historyRealm.where(PumpHistoryMisc.class)
                .equalTo("item", 2)
                .findAllSorted("eventDate", Sort.DESCENDING);
        if (results.size() > 0)
            text = ((timeNow - results.first().getEventDate().getTime()) / (24 * 60 * 60000L)) + "d";
        return text;
    }

    private String pumpReservoirUnits() {
        String text = "";
        if (pumpStatusEvent != null)
            text = new BigDecimal(pumpStatusEvent.getReservoirAmount()).setScale(0, BigDecimal.ROUND_HALF_UP) + styleUnits();
        return text;
    }

    private String pumpReservoirAge() {
        String text = "";
        RealmResults<PumpHistoryMisc> results = historyRealm.where(PumpHistoryMisc.class)
                .equalTo("item", 3)
                .findAllSorted("eventDate", Sort.DESCENDING);
        if (results.size() > 0)
            text = ((timeNow - results.first().getEventDate().getTime()) / (60 * 60000L)) + "h";
        return text;
    }

    private String transmitterBattery() {
        String text = "";
        if (pumpStatusEvent != null)
            text = styleBattery(pumpStatusEvent.getTransmitterBattery());
        return text;
    }

    private int sensorHours() {
        RealmResults<PumpHistoryMisc> results = historyRealm.where(PumpHistoryMisc.class)
                .equalTo("item", 1)
                .findAllSorted("eventDate", Sort.DESCENDING);
        if (results.size() > 0)
            return (int) ((timeNow - results.first().getEventDate().getTime()) / (60 * 60000L));
        else return -1;
    }

    private String sensorAge() {
        int hours = sensorHours();
        if (hours == -1) return "??";
        return (hours / 24) + "d";
    }

    // assumes a liftime of 6 days, sensors with longer life will produce a wrong result
    private String sensorLife() {
        int hours = sensorHours();
        if (hours == -1) return "??";
        hours = 145 - hours;
        if (hours < 0) hours = 0;
        if (hours > 120) return "6d";
        else if (hours > 10) return ((hours / 24) + 1) + "d";
        else return hours + "h";
    }

    private String buildStatusLayout() {
        String text = "";

        byte[] statusLayout = dataStore.getUrchinStatusLayout();
        for (int i=0; i < 20; i++) {
            text += addStatusItem(statusLayout[i]);
        }

        return text;
    }

    // Pebble font available chars = " 0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz°•·-`~!@#$%^&*()_+=[]\\{}|;':\",./<>?";

    private String addStatusItem(byte item) {
        String text;
        switch (item) {
            case 1:
                text = "\n";
                break;
            case 2:
                text = " ";
                break;
            case 3:
                text = "  ";
                break;
            case 4:
                text = ":";
                break;
            case 5:
                text = " : ";
                break;
            case 6:
                text = "|";
                break;
            case 7:
                text = " | ";
                break;
            case 8:
                text = "·";
                break;
            case 9:
                text = "•";
                break;
            case 10:
                text = "/";
                break;
            case 11:
                text = "\\";
                break;
            case 12:
                text = iob();
                break;
            case 13:
                text = basal();
                break;
            case 14:
                text = basalState();
                break;
            case 15:
                text = basalCombo();
                break;
            case 16:
                text = lastBolus();
                break;
            case 17:
                text = bolusing();
                break;
            case 18:
                text = bolusing();
                if (text.equals("")) text = lastBolus();
                break;
            case 19:
                text = calibration();
                break;
            case 20:
                text = bolusing();
                if (text.equals("")) {
                    text = lastBolus();
                    if (!text.equals("")) text = " " + text;
                    text = calibration() + text;
                }
                break;
            case 21:
                text = uploaderBattery();
                break;
            case 22:
                text = pumpBattery();
                break;
            case 23:
                text = transmitterBattery();
                break;
            case 24:
                text = sensorLife();
                break;
            case 25:
                text = sensorAge();
                break;
            case 26:
                text = pumpReservoirAge();
                break;
            case 27:
                text = pumpBatteryAge();
                break;
            case 28:
                text = pumpReservoirUnits();
                break;
            case 29:
                text = dfDay.format(timeNow);
                break;
            case 30:
                text = dfDayName.format(timeNow);
                break;
            case 31:
                text = dataStore.getUrchinCustomText1();
                break;
            case 32:
                text = dataStore.getUrchinCustomText2();
                break;
            default:
                text = "";
        }
        return text;
    }

    private DateFormat dfDay = new SimpleDateFormat("d");
    private DateFormat dfDayName = new SimpleDateFormat("E");
    private DateFormat df24 = new SimpleDateFormat("HH:mm");
    private DateFormat df12 = new SimpleDateFormat("h:mm");
    private DateFormat dfAMPM = new SimpleDateFormat("a", Locale.US);

    private String styleTime(long time) {
        String text;
        switch (dataStore.getUrchinTimeStyle()) {
            case 1:
                text = df12.format(time) + (dfAMPM.format(time).equals("AM") ? "a" : "p");
                break;
            case 2:
                text = df12.format(time) + (dfAMPM.format(time).equals("AM") ? "A" : "P");
                break;
            case 3:
                text = df12.format(time) + (dfAMPM.format(time).equals("AM") ? "am" : "pm");
                break;
            case 4:
                text = df12.format(time) + (dfAMPM.format(time).equals("AM") ? "AM" : "PM");
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
        String text;
        switch (dataStore.getUrchinDurationStyle()) {
            case 1:
                text = duration + "m";
                break;
            case 2:
                text = (duration >= 60 ? duration / 60 + "h" + duration % 60 : duration % 60 + "");
                break;
            case 3:
                text = (duration >= 60 ? duration / 60 + "h" + duration % 60 + "" : duration % 60 + "m");
                break;
            case 4:
                text = (duration >= 60 ? duration / 60 + "h" + duration % 60 + "m" : duration % 60 + "m");
                break;
            default:
                text = duration + "";
        }
        return text;
    }

    private String styleUnits() {
        String text;
        switch (dataStore.getUrchinUnitsStyle()) {
            case 1:
                text = "u";
                break;
            case 2:
                text = "U";
                break;
            default:
                text = "";
        }
        return text;
    }

    private String styleBattery(int battery) {
        String text;
        switch (dataStore.getUrchinBatteyStyle()) {
            case 1:
                text = battery + "%";
                break;
            case 2:
                text = (battery > 99 ? "99" : battery) + "";
                break;
            case 3:
                text = (battery > 99 ? "99" : battery) + "%";
                break;
            case 4:
                battery = battery / 10;
                text = (battery > 9 ? "9" : battery) + "";
                break;
            case 5:
                if (battery > 25) text = "Hi";
                else text = "Lo";
                break;
            case 6:
                if (battery > 65) text = "H";
                else if (battery > 25) text = "M";
                else text = "L";
                break;
            case 7:
                if (battery > 80) text = "F";
                else if (battery > 60) text = "H";
                else if (battery > 30) text = "M";
                else if (battery > 15) text = "L";
                else text = "E";
                break;
            default:
                text = battery + "";
        }
        return text;
    }

    private String styleConcatenate() {
        String text;
        switch (dataStore.getUrchinConcatenateStyle()) {
            case 1:
                text = " ";
                break;
            case 2:
                text = "·";
                break;
            case 3:
                text = "•";
                break;
            case 4:
                text = "-";
                break;
            case 5:
                text = "/";
                break;
            case 6:
                text = "\\";
                break;
            case 7:
                text = "|";
                break;
            default:
                text = "";
        }
        return text;
    }

    protected void userLogMessage(String message) {
        try {
            Intent intent =
                    new Intent(MasterService.Constants.ACTION_USERLOG_MESSAGE)
                            .putExtra(MasterService.Constants.EXTENDED_DATA, message);
            mContext.sendBroadcast(intent);
        } catch (Exception ignored) {
        }
    }
}