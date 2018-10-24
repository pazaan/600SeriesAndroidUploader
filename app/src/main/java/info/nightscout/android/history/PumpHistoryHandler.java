package info.nightscout.android.history;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Pair;

import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.R;
import info.nightscout.android.UploaderApplication;
import info.nightscout.android.medtronic.MedtronicCnlReader;
import info.nightscout.android.medtronic.Stats;
import info.nightscout.android.medtronic.UserLogMessage;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;
import info.nightscout.android.medtronic.message.ReadHistoryResponseMessage;
import info.nightscout.android.model.medtronicNg.HistorySegment;
import info.nightscout.android.model.medtronicNg.PumpHistoryAlarm;
import info.nightscout.android.model.medtronicNg.PumpHistoryBG;
import info.nightscout.android.model.medtronicNg.PumpHistoryBasal;
import info.nightscout.android.model.medtronicNg.PumpHistoryBolus;
import info.nightscout.android.model.medtronicNg.PumpHistoryCGM;
import info.nightscout.android.model.medtronicNg.PumpHistoryDaily;
import info.nightscout.android.model.medtronicNg.PumpHistoryLoop;
import info.nightscout.android.model.medtronicNg.PumpHistoryInterface;
import info.nightscout.android.model.medtronicNg.PumpHistoryMarker;
import info.nightscout.android.model.medtronicNg.PumpHistoryMisc;
import info.nightscout.android.model.medtronicNg.PumpHistoryPattern;
import info.nightscout.android.model.medtronicNg.PumpHistoryProfile;
import info.nightscout.android.model.medtronicNg.PumpHistorySystem;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.model.store.DataStore;
import info.nightscout.android.model.store.StatPoll;
import info.nightscout.android.utils.FormatKit;
import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmResults;
import io.realm.Sort;

/**
 * Created by Pogman on 5.11.17.
 */

public class PumpHistoryHandler {
    private static final String TAG = PumpHistoryHandler.class.getSimpleName();

    private final static long HISTORY_STALE_MS = 120 * 24 * 60 * 60000L;
    private final static long HISTORY_REQUEST_LIMITER_MS = 4 * 24 * 60 * 60000L;

    private final static byte HISTORY_PUMP = 2;
    private final static byte HISTORY_CGM = 3;

    private Context mContext;

    private Realm realm;
    private Realm historyRealm;
    private Realm storeRealm;
    private DataStore dataStore;

    private List<DBitem> historyDB;

    public PumpHistorySender pumpHistorySender;

    private DateFormat dateFormatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US);

    public PumpHistoryHandler(Context context) {
        Log.d(TAG, "initialise history handler");

        mContext = context;

        realm = Realm.getDefaultInstance();
        storeRealm = Realm.getInstance(UploaderApplication.getStoreConfiguration());
        dataStore = storeRealm.where(DataStore.class).findFirst();

        historyRealm = Realm.getInstance(UploaderApplication.getHistoryConfiguration());

        historyDB = new ArrayList<>();
        historyDB.add(new DBitem(PumpHistoryCGM.class));
        historyDB.add(new DBitem(PumpHistoryBolus.class));
        historyDB.add(new DBitem(PumpHistoryBasal.class));
        historyDB.add(new DBitem(PumpHistoryPattern.class));
        historyDB.add(new DBitem(PumpHistoryBG.class));
        historyDB.add(new DBitem(PumpHistoryProfile.class));
        historyDB.add(new DBitem(PumpHistoryMisc.class));
        historyDB.add(new DBitem(PumpHistoryMarker.class));
        historyDB.add(new DBitem(PumpHistoryLoop.class));
        historyDB.add(new DBitem(PumpHistoryDaily.class));
        historyDB.add(new DBitem(PumpHistoryAlarm.class));
        historyDB.add(new DBitem(PumpHistorySystem.class));

        pumpHistorySender = new PumpHistorySender().buildSenders(dataStore);

        Stats.open();
    }

    public void close() {
        Log.d(TAG,"close history handler");
        Stats.close();
        if (realm != null && !realm.isClosed()) realm.close();
        if (storeRealm != null && !storeRealm.isClosed()) storeRealm.close();
        if (historyRealm != null && !historyRealm.isClosed()) historyRealm.close();
        dataStore = null;
        realm = null;
        historyRealm = null;
        storeRealm = null;
    }

    private class DBitem {
        String historydb;
        RealmResults<PumpHistoryInterface> results;

        DBitem (Class clazz) {
            this.historydb = clazz.getSimpleName();
            this.results = historyRealm.where(clazz).findAll();
        }
    }

    // records as requested for a sender and ready for uploading / processing
    public List<PumpHistoryInterface> getSenderRecordsREQ(String senderID) {

        PumpHistorySender.Sender sender = pumpHistorySender.getSender(senderID);

        long now = System.currentTimeMillis();
        Date limitDate = new Date(now - sender.process);

        StringBuilder log = new StringBuilder();
        StringBuilder logdb = new StringBuilder();
        log.append(String.format("sender[%s] limitdate: %s",senderID, dateFormatter.format(limitDate)));

        List<PumpHistoryInterface> records = new ArrayList<>();

        for (DBitem dBitem : historyDB) {

            if (sender.request.contains(dBitem.historydb)) {

                RealmResults<PumpHistoryInterface> requested = dBitem.results.where()
                        .greaterThanOrEqualTo("eventDate", limitDate)
                        .contains("senderREQ", senderID)
                        .findAll();

                records.addAll(requested);

                if (requested.size() > 0) logdb.append(String.format(" %s: %s", dBitem.historydb, requested.size()));
            }
        }

        log.append(String.format(" requested: %s", records.size()));

        // sort complete list from oldest to newest
        Collections.sort(records, new Comparator<PumpHistoryInterface>() {
            @Override
            public int compare(PumpHistoryInterface record1, PumpHistoryInterface record2)
            {
                return  record1.getEventDate().compareTo(record2.getEventDate());
            }
        });

        // limiter
        if (records.size() > sender.limiter)
            records = records.subList(records.size() - sender.limiter, records.size());

        log.append(String.format(" limiter: %s final: %s", sender.limiter, records.size()));
        log.append(logdb.toString());
        Log.d(TAG, log.toString());

        return records;
    }

    // post uploading / processing, clear the request and acknowledge
    public void setSenderRecordsACK(final List<PumpHistoryInterface> records, final String senderID) {
        historyRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(@NonNull Realm realm) {

                for (PumpHistoryInterface record : records) {
                    record.setSenderREQ(record.getSenderREQ().replace(senderID, ""));
                    record.setSenderACK(record.getSenderACK().replace(senderID, "").concat(senderID));
                }

            }
        });
    }

    public void setSenderRecordACK(final PumpHistoryInterface record, final String senderID) {
        historyRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(@NonNull Realm realm) {

                record.setSenderREQ(record.getSenderREQ().replace(senderID, ""));
                record.setSenderACK(record.getSenderACK().replace(senderID, "").concat(senderID));

            }
        });
    }

    public void processSenderTTL(final String senderID) {

        long now = System.currentTimeMillis();

        PumpHistorySender.Sender sender = pumpHistorySender.getSender(senderID);

        List<Pair<String, Long>> ttl = sender.ttl;

        final List<PumpHistoryInterface> recordsToDelete = new ArrayList<>();
        final List<PumpHistoryInterface> recordsToUndelete = new ArrayList<>();

        RealmResults<PumpHistoryInterface> resultsToDelete;
        RealmResults<PumpHistoryInterface> resultsToUndelete;

        for (DBitem dBitem : historyDB) {

            for (Pair<String, Long>ttlItem : ttl) {

                if (dBitem.historydb.equals(ttlItem.first)) {

                    Date ttlDate = new Date(now - ttlItem.second);

                    // tag records for deletion (helps keep NS history clean)
                    resultsToDelete = dBitem.results.where()
                            .lessThan("eventDate", ttlDate)
                            .beginGroup()
                            .contains("senderREQ", senderID)
                            .or()
                            .contains("senderACK", senderID)
                            .endGroup()
                            .not()
                            .contains("senderDEL", senderID)
                            .findAll();

                    recordsToDelete.addAll(resultsToDelete);

                    // ttl date changed? recover already deleted records (resend to NS)
                    resultsToUndelete = dBitem.results.where()
                            .greaterThanOrEqualTo("eventDate", ttlDate)
                            .contains("senderDEL", senderID)
                            .findAll();

                    recordsToUndelete.addAll(resultsToUndelete);

                    Log.d(TAG, String.format("sender[%s] db: %s ttldate: %s delete: %s undelete: %s",
                            senderID, ttlItem.first, dateFormatter.format(ttlDate), resultsToDelete.size(), resultsToUndelete.size()));
                }
            }
        }

        if (recordsToDelete.size() > 0 || recordsToUndelete.size() > 0) {
            historyRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {

                    for (PumpHistoryInterface record : recordsToDelete) {
                        record.setSenderREQ(record.getSenderREQ().replace(senderID, "").concat(senderID));
                        record.setSenderDEL(record.getSenderDEL().replace(senderID, "").concat(senderID));
                    }

                    for (PumpHistoryInterface record : recordsToUndelete) {
                        record.setSenderREQ(record.getSenderREQ().replace(senderID, "").concat(senderID));
                        record.setSenderDEL(record.getSenderDEL().replace(senderID, ""));
                    }

                }
            });
        }

    }

    public int records() {
        int total = 0;
        for (DBitem dBitem : historyDB) {
            total += dBitem.results.size();
            Log.d(TAG, dBitem.historydb + " records: " + dBitem.results.size() + (dBitem.results.size() > 0 ? " start: " + dateFormatter.format(dBitem.results.first().getEventDate()) + " end: " + dateFormatter.format(dBitem.results.last().getEventDate()) : ""));
        }
        return total;
    }

    public void stale(final Date oldest) {
        historyRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(@NonNull Realm realm) {

                int count = 0;
                for (DBitem dBitem : historyDB) {
                    RealmResults<PumpHistoryInterface> stale = dBitem.results.where().lessThan("eventDate", oldest).findAll();
                    count += stale.size();
                    stale.deleteAllFromRealm();
                }

                Log.d(TAG, "stale date: " + dateFormatter.format(oldest) + " deleted: " + count + " history records");
            }
        });
    }

    public void reupload(final Class clazz, final String senderID) {
        historyRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(@NonNull Realm realm) {
                String db = clazz.getSimpleName();
                RealmResults<PumpHistoryInterface> results;

                for (DBitem dBitem : historyDB) {

                    if (dBitem.historydb.equals(db)) {
                        results = dBitem.results.where()
                                .contains("senderACK", senderID)
                                .findAll();
                        for (PumpHistoryInterface record : results)
                            if (!record.getSenderREQ().contains(senderID)) record.setSenderREQ(record.getSenderREQ() + senderID);
                    }
                }
            }

        });
    }


    public void systemStatus(PumpHistorySystem.STATUS status) {
        systemStatus(status, null);
    }

    public void systemStatus(PumpHistorySystem.STATUS status, RealmList<String> data) {
        RealmResults<PumpStatusEvent> results = realm
                .where(PumpStatusEvent.class)
                .sort("eventDate", Sort.DESCENDING)
                .findAll();
        if (results.size() > 0) {
            systemStatus(status, results.first().getEventDate(), String.format("%8X", results.first().getEventRTC()), data);
        }
    }

    public PumpHistoryHandler systemStatus(final PumpHistorySystem.STATUS status, final Date date, final String key, final RealmList<String> data) {

        historyRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(@NonNull Realm realm) {

                PumpHistorySystem.event(pumpHistorySender, historyRealm,
                        date,
                        key,
                        status,
                        data
                );

            }
        });

        return this;
    }

    public void debugNote(final Date eventDate, final String note) {
        historyRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(@NonNull Realm realm) {
                systemStatus(PumpHistorySystem.STATUS.DEBUG,
                        eventDate, String.format("%16X", eventDate.getTime()),
                        new RealmList<>(note));
            }
        });
    }

    public Date debugNoteLastDate() {
        RealmResults<PumpHistorySystem> results = historyRealm
                .where(PumpHistorySystem.class)
                .equalTo("status", PumpHistorySystem.STATUS.DEBUG.value())
                .sort("eventDate", Sort.DESCENDING)
                .findAll();
        if (results.size() > 0) return results.first().getEventDate();
        return null;
    }

    public boolean isLoopActive() {
        long now = System.currentTimeMillis();

        RealmResults<PumpHistoryLoop> results = historyRealm
                .where(PumpHistoryLoop.class)
                .greaterThan("eventDate", new Date(now - 6 * 60 * 60000L))
                .sort("eventDate", Sort.DESCENDING)
                .findAll();

        return results.size() > 0
                && (PumpHistoryLoop.RECORDTYPE.MICROBOLUS.equals(results.first().getRecordtype())
                || PumpHistoryLoop.RECORDTYPE.TRANSITION_IN.equals(results.first().getRecordtype()));
    }

    // loop is active or has activation potential due to use during timeframe
    public boolean isLoopActivePotential() {
        long now = System.currentTimeMillis();

        RealmResults<PumpHistoryLoop> results = historyRealm
                .where(PumpHistoryLoop.class)
                .greaterThan("eventDate", new Date(now - 6 * 60 * 60000L))
                .findAll();

        return results.size() > 0;
    }

    public boolean isProfileUploaded() {

        RealmResults<PumpHistoryProfile> results = historyRealm
                .where(PumpHistoryProfile.class)
                .findAll();

        return results.size() > 0;
    }

    // Recency
    public long pumpHistoryRecency() {

        RealmResults<HistorySegment> results = historyRealm
                .where(HistorySegment.class)
                .equalTo("historyType", HISTORY_PUMP)
                .sort("toDate", Sort.DESCENDING)
                .findAll();

        if (results.size() > 0) return System.currentTimeMillis() - results.first().getToDate().getTime();

        return -1;
    }

    public void profile(final MedtronicCnlReader cnlReader) throws EncryptionException, IOException, ChecksumException, TimeoutException, UnexpectedMessageException {

        UserLogMessage.send(mContext, R.string.history_reading_pump_basal);
        final byte[] basalPatterns = cnlReader.getBasalPatterns();
        UserLogMessage.send(mContext, R.string.history_reading_carb);
        final byte[] carbRatios = cnlReader.getBolusWizardCarbRatios();
        UserLogMessage.send(mContext, R.string.history_reading_sensitivity);
        final byte[] sensitivity = cnlReader.getBolusWizardSensitivity();
        UserLogMessage.send(mContext, R.string.history_reading_targets);
        final byte[] targets = cnlReader.getBolusWizardTargets();

        // user settings
        final String units = dataStore.isMmolxl() ? "mmol" : "mg/dl";
        byte checkProfile = (byte) dataStore.getNsProfileDefault();
        final double insulinDuration = dataStore.getNsActiveInsulinTime();
        final double insulinDelay = 20; // NS default value 20 - delay from action to activation for insulin?
        final double carbsPerHour = 20; // NS default value 20 - The number of carbs that are processed per hour.

        // if no profile switch event is available in NS it will use the default
        // setting the default to the current basal or auto mode basal will show the correct basal graph for first-time users
        if (checkProfile == 0) {
            if (isLoopActive())
                checkProfile = 9; // auto mode profile
            else {
                RealmResults<PumpStatusEvent> pumpStatusEvents = realm.where(PumpStatusEvent.class)
                        .greaterThan("eventDate", new Date(System.currentTimeMillis() - (60 * 60000L)))
                        .greaterThan("activeBasalPattern", 0)
                        .sort("eventDate", Sort.DESCENDING)
                        .findAll();
                if (pumpStatusEvents.size() > 0) {
                    if (pumpStatusEvents.first().getActiveBasalPattern() != 0)
                        checkProfile = pumpStatusEvents.first().getActiveBasalPattern();
                    else
                        checkProfile = 1;
                }
            }
        }
        final byte defaultProfile = checkProfile;

        historyRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(@NonNull Realm realm) {
                PumpHistoryProfile.profile(pumpHistorySender, historyRealm, cnlReader.getSessionDate(), cnlReader.getSessionRTC(), cnlReader.getSessionOFFSET(),
                        units,
                        insulinDuration,
                        insulinDelay,
                        carbsPerHour,
                        defaultProfile,
                        basalPatterns,
                        carbRatios,
                        sensitivity,
                        targets
                );
            }
        });

        UserLogMessage.send(mContext, R.string.history_sending_profile);

        // update NS historical treatments when pattern naming has changed

        if (dataStore.isNameBasalPatternChanged() &&
                (dataStore.isNsEnableProfileSingle() || dataStore.isNsEnableProfileOffset())) {

            final RealmResults<PumpHistoryPattern> results = historyRealm
                    .where(PumpHistoryPattern.class)
                    .findAll();

            if (results.size() > 0) {
                Log.d(TAG, "NameBasalPatternChanged: Found " + results.size() + " pattern switch treatments to update");
                UserLogMessage.send(mContext, R.string.history_pattern_names_changed);

                historyRealm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(@NonNull Realm realm) {
                        for (PumpHistoryPattern record : results) {
                            pumpHistorySender.setSenderREQ(record);
                            pumpHistorySender.setSenderACK(record);
                        }
                    }
                });

            }

            storeRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {
                    dataStore.setNameBasalPatternChanged(false);
                }
            });
        }
    }

    public void checkGramsPerExchangeChanged() {

        if (dataStore.isNsGramsPerExchangeChanged()) {

            final RealmResults<PumpHistoryBolus> results = historyRealm
                    .where(PumpHistoryBolus.class)
                    .equalTo("programmed", true)
                    .equalTo("estimate", true)
                    .equalTo("carbUnits", PumpHistoryParser.CARB_UNITS.EXCHANGES.value())
                    .findAll();

            if (results.size() > 0) {
                Log.d(TAG, "GramsPerExchangeChanged: Found " + results.size() + " carb/bolus treatments to update");
                UserLogMessage.send(mContext, R.string.history_grams_changed);

                historyRealm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(@NonNull Realm realm) {
                        for (PumpHistoryBolus record : results) {
                            pumpHistorySender.setSenderREQ(record);
                            pumpHistorySender.setSenderACK(record);
                        }
                    }
                });
            }

            storeRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {
                    dataStore.setNsGramsPerExchangeChanged(false);
                }
            });
        }
    }

    public void cgm(final PumpStatusEvent pumpRecord) {

        if (!pumpRecord.isCgmActive()) return;

        boolean status = true;
        boolean backfill = false;
        boolean estimate = false;

        final Date date = pumpRecord.getCgmDate();
        final int rtc = pumpRecord.getCgmRTC();
        final int offset = pumpRecord.getCgmOFFSET();
        final int sgv = pumpRecord.getSgv();
        final String trend = pumpRecord.getCgmTrendString();
        final byte exception = pumpRecord.getCgmExceptionType();

        RealmResults<PumpHistoryCGM> cgmResults = historyRealm
                .where(PumpHistoryCGM.class)
                .sort("eventDate", Sort.DESCENDING)
                .findAll();

        // already processed?
        if (cgmResults.size() > 0 && cgmResults.first().getCgmRTC() == rtc) {
            status = false;
        }

        // cgm is available do we need the backfill?
        if (dataStore.isSysEnableCgmHistory()) {

            // empty history?
            if (cgmResults.size() == 0) {
                backfill = true;
            }

            else if (status) {

                // estimate sgv?
                if (dataStore.isSysEnableEstimateSGV()
                        && (
                        (PumpHistoryParser.CGM_EXCEPTION.SENSOR_CAL_NEEDED.equals(exception)
                                || PumpHistoryParser.CGM_EXCEPTION.SENSOR_END_OF_LIFE.equals(exception)
                                || (PumpHistoryParser.CGM_EXCEPTION.SENSOR_CAL_PENDING.equals(exception) && sgv == 0)
                        ) && pumpHistoryRecency() < 6 * 60 * 60000L)
                        ) {

                    RealmResults<PumpHistoryMisc> miscResults = historyRealm
                            .where(PumpHistoryMisc.class)
                            .equalTo("recordtype", PumpHistoryMisc.RECORDTYPE.CHANGE_SENSOR.value())
                            .sort("eventDate", Sort.DESCENDING)
                            .findAll();
                    if (miscResults.size() > 0) {

                        // current sensor lifetime
                        Date sensorDate = miscResults.first().getEventDate();
                        Date sensorDateEnd = new Date(System.currentTimeMillis());

                        // current sensor calibrations
                        RealmResults<PumpHistoryBG> bgResults = historyRealm
                                .where(PumpHistoryBG.class)
                                .equalTo("calibration", true)
                                .greaterThanOrEqualTo("eventDate", sensorDate)
                                .lessThan("eventDate", sensorDateEnd)
                                .sort("eventDate", Sort.DESCENDING)
                                .findAll();
                        if (bgResults.size() > 0) {
                            status = false;
                            backfill = true;
                            estimate = true;
                            ((StatPoll) Stats.getInstance().readRecord(StatPoll.class)).incHistoryReqEstimate();
                            UserLogMessage.send(mContext, UserLogMessage.TYPE.HISTORY,
                                    String.format("{id;%s}: estimate sgv", R.string.history_text));
                        }
                    }
                }

                // missed readings?
                else if (!dataStore.isRequestCgmHistory() && date.getTime() - cgmResults.first().getEventDate().getTime() > 9 * 60 * 1000L) {

                    // ignore missed readings during the warm-up phase
                    if (!PumpHistoryParser.CGM_EXCEPTION.SENSOR_INIT.equals(exception) &&
                            !PumpHistoryParser.CGM_EXCEPTION.SENSOR_INIT.equals(cgmResults.first().getSensorException())) {
                        backfill = true;
                        ((StatPoll) Stats.getInstance().readRecord(StatPoll.class)).incHistoryReqBackfill();
                        UserLogMessage.send(mContext, UserLogMessage.TYPE.HISTORY,
                                String.format("{id;%s}: {id;%s}", R.string.history_text, R.string.history_cgm_backfill));
                    }

                }
            }
        }

        if (status) {
            // push the current sgv from status (always have latest sgv available even if there are comms errors after this)
            historyRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {
                    PumpHistoryCGM.cgmFromStatus(pumpHistorySender, historyRealm, date, rtc, offset,
                            sgv,
                            exception,
                            trend
                    );
                }
            });
        }

        if (backfill) {
            storeRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {
                    dataStore.setRequestCgmHistory(true);
                }
            });
        }

        if (estimate) {
            storeRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {
                    dataStore.setRequestEstimate(true);
                }
            });
        }

    }

    public void estimateSgv() {

        storeRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(@NonNull Realm realm) {
                dataStore.setRequestEstimate(false);
            }
        });

        int sensorToUse = 1;
        int bgToUse = 1;

        RealmResults<PumpHistoryMisc> miscResults = historyRealm
                .where(PumpHistoryMisc.class)
                .equalTo("recordtype", PumpHistoryMisc.RECORDTYPE.CHANGE_SENSOR.value())
                .sort("eventDate", Sort.DESCENDING)
                .findAll();
        if (miscResults.size() < sensorToUse) return;

        Date sensorDate = miscResults.get(sensorToUse - 1).getEventDate();
        Date sensorDateEnd = new Date(System.currentTimeMillis());
        if (sensorToUse > 1) sensorDateEnd = miscResults.get(sensorToUse - 2).getEventDate();

        RealmResults<PumpHistoryBG> bgResults = historyRealm
                .where(PumpHistoryBG.class)
                .equalTo("calibration", true)
                .greaterThanOrEqualTo("eventDate", sensorDate)
                .lessThan("eventDate", sensorDateEnd)
                .sort("eventDate", Sort.DESCENDING)
                .findAll();
        if (bgResults.size() < 1) return;

        if (bgToUse > bgResults.size()) bgToUse = bgResults.size();
        Date bgDate = bgResults.get(bgToUse - 1).getEventDate();
        Date bgDateEnd = sensorDateEnd;

        RealmResults<PumpHistoryCGM> cgmResults = historyRealm
                .where(PumpHistoryCGM.class)
                .equalTo("history", true)
                .equalTo("discardData", false)
                .equalTo("noisyData", false)
                .greaterThanOrEqualTo("eventDate", bgDate)
                .lessThan("eventDate", bgDateEnd)
                .sort("eventDate", Sort.ASCENDING)
                .findAll();
        if (cgmResults.size() == 0) return;

        // Kalman Filter
        // X = K * Z + (1 - K) * X1
        // X = current estimation
        // X1 = previous estimation
        // Z = current measurement
        // K = Kalman gain

        double caltime = 10 * 60000L;
        double offset = 3.0;
        double clip = 2.5;
        double k = 0.7;

        double sgv = 0;
        double isig = 0;
        double factor = 0;

        double offsetSum = 0;
        double offsetAvg = 0;
        double offsetCount = 0;

        double z = 0;
        double x = 0;

        int bgpos = bgToUse - 1;
        int t = 0;

        DateFormat dfLog = new SimpleDateFormat("MM/dd HH:mm", Locale.US);
        DecimalFormat dfNumber = new DecimalFormat("0.00");

        for (int i = 0; i < cgmResults.size(); i++) {

            if (bgpos >= 0) {
                if (cgmResults.get(i).getEventDate().getTime() - bgResults.get(bgpos).getEventDate().getTime() >= caltime) {

                    if (false) {
                        Log.d(TAG, String.format("### BG [%s] %s | %s / %s | %s",
                                bgpos,
                                dfLog.format(bgResults.get(bgpos).getEventDate()),
                                bgResults.get(bgpos).getBg(),
                                FormatKit.getInstance().formatAsGlucoseMMOL(bgResults.get(bgpos).getBg(), false),
                                bgResults.get(bgpos).getCalibrationFactor()
                        ));
                    }

                    if (bgResults.get(bgpos).getCalibrationFactor() > 0) {
                        factor = bgResults.get(bgpos).getCalibrationFactor();
                        t = 0;
                        offsetSum = 0;
                        offsetCount = 0;
                    }

                    bgpos--;
                }
            }

            t++;

            isig = cgmResults.get(i).getIsig();

            if (x == 0) x = isig;
            else if (isig - x > clip) z = x + clip;
            else if (x - isig > clip) z = x - clip;
            else z = isig;

            if (factor > 0) x = k * z + (1 - k) * x;

            //sgv = (x - offset) * factor;
            sgv = (x - offsetAvg) * factor;

            if (!cgmResults.get(i).isEstimate() && cgmResults.get(i).getSgv() > 0) {
                offsetCount++;
                offsetSum += cgmResults.get(i).getIsig() - (cgmResults.get(i).getSgv() / factor);
                offsetAvg = offsetSum / offsetCount;
            }

            if (false) {
                Log.d(TAG, String.format("### SGV [%s] %s | %s / %s | %s %s %s %s",
                        i, dfLog.format(cgmResults.get(i).getEventDate()),
                        (int) Math.round(sgv),
                        FormatKit.getInstance().formatAsGlucoseMMOL((int) Math.round(sgv), false),
                        t, factor, isig, x
                ));
            }

            if (sgv > 0 && cgmResults.get(i).getSgv() == 0 && !cgmResults.get(i).isEstimate()) {

                // don't flood the userlog during extensive backfills
                if (cgmResults.size() - i <= 5) {

                    UserLogMessage.sendN(mContext, UserLogMessage.TYPE.ESTIMATE,
                            String.format("estimated SGV {sgv;%s} at {time.sgv;%s}",
                                    (int) Math.round(sgv),
                                    cgmResults.get(i).getEventDate().getTime()
                            ));
                    UserLogMessage.sendE(mContext, UserLogMessage.TYPE.ESTIMATE,
                            String.format("estimated SGV {sgv;%s} at {time.sgv.e;%s}",
                                    (int) Math.round(sgv),
                                    cgmResults.get(i).getEventDate().getTime()
                            ));

                    if (cgmResults.size() - i <= 1) {

                        UserLogMessage.sendE(mContext,
                                String.format("isig: %s vctr: %s roc: %s s: %s/%s %s%s%s%s%s",
                                        cgmResults.get(i).getIsig(),
                                        cgmResults.get(i).getVctr(),
                                        cgmResults.get(i).getRateOfChange(),
                                        cgmResults.get(i).getSensorStatus(),
                                        cgmResults.get(i).getReadingStatus(),
                                        cgmResults.get(i).isNoisyData() ? "N" : "",
                                        cgmResults.get(i).isDiscardData() ? "D" : "",
                                        cgmResults.get(i).isSensorError() ? "E" : "",
                                        cgmResults.get(i).isBackfilledData() ? "B" : "",
                                        cgmResults.get(i).isSettingsChanged() ? "S" : ""
                                ));
                        UserLogMessage.sendE(mContext,
                                String.format("t+%s f: %s o: %s x: %s",
                                        t,
                                        factor,
                                        dfNumber.format(offsetAvg),
                                        dfNumber.format(x)
                                ));
                    }
                }

                final int estSgv = (int) Math.round(sgv);
                final PumpHistoryCGM pumpHistoryCGM = cgmResults.get(i);

                historyRealm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(@NonNull Realm realm) {

                        pumpHistoryCGM.setSgv(estSgv);
                        pumpHistoryCGM.setEstimate(true);
                        pumpHistorySender.setSenderREQ(pumpHistoryCGM);

                    }
                });

            }

        }
    }

    public void isig() {
        RealmResults<PumpHistoryCGM> cgmResults = historyRealm
                .where(PumpHistoryCGM.class)
                .sort("eventDate", Sort.ASCENDING)
                .findAll();
        if (cgmResults.size() > 0) {
            UserLogMessage.send(mContext,
                    String.format("isig: %s vctr: %s roc: %s s: %s/%s %s%s%s%s%s",
                            cgmResults.last().getIsig(),
                            cgmResults.last().getVctr(),
                            cgmResults.last().getRateOfChange(),
                            cgmResults.last().getSensorStatus(),
                            cgmResults.last().getReadingStatus(),
                            cgmResults.last().isNoisyData() ? "N" : "",
                            cgmResults.last().isDiscardData() ? "D" : "",
                            cgmResults.last().isSensorError() ? "E" : "",
                            cgmResults.last().isBackfilledData() ? "B" : "",
                            cgmResults.last().isSettingsChanged() ? "S" : ""
                    ));
        }
    }

    public void update(MedtronicCnlReader cnlReader) throws EncryptionException, IOException, ChecksumException, TimeoutException, UnexpectedMessageException {
        long newest = cnlReader.getSessionDate().getTime();
        long oldest = newest - HISTORY_STALE_MS;

        stale(new Date(oldest));

        boolean pullCGM = dataStore.isRequestCgmHistory();
        boolean pullPUMP = dataStore.isRequestPumpHistory();

        // if CGM backfill is needed pull that first as pump can be busy after history pulls
        if (pullCGM) {

            // clear the request flag now as segment marker will get added
            storeRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {
                    dataStore.setRequestCgmHistory(false);
                }
            });
            if (dataStore.isSysEnableCgmHistory()) updateHistorySegments(cnlReader, dataStore.getSysCgmHistoryDays(), oldest, newest, HISTORY_CGM, true, "CGM history:", mContext.getString(R.string.history_read_cgm));

            // process sgv estimates now as cgm data is available, avoids any comms errors after this
            if (dataStore.isRequestEstimate()) estimateSgv();

            // clear the request flag now as segment marker will get added
            storeRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {
                    dataStore.setRequestPumpHistory(false);
                }
            });
            if (dataStore.isSysEnablePumpHistory()) updateHistorySegments(cnlReader, dataStore.getSysPumpHistoryDays(), oldest, newest, HISTORY_PUMP, pullPUMP, "PUMP history:", mContext.getString(R.string.history_read_pump));

        } else {

            // clear the request flag now as segment marker will get added
            storeRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {
                    dataStore.setRequestPumpHistory(false);
                }
            });
            if (dataStore.isSysEnablePumpHistory()) updateHistorySegments(cnlReader, dataStore.getSysPumpHistoryDays(), oldest, newest, HISTORY_PUMP, pullPUMP, "PUMP history:", mContext.getString(R.string.history_read_pump));

            // clear the request flag now as segment marker will get added
            storeRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {
                    dataStore.setRequestCgmHistory(false);
                }
            });
            if (dataStore.isSysEnableCgmHistory()) updateHistorySegments(cnlReader, dataStore.getSysCgmHistoryDays(), oldest, newest, HISTORY_CGM, false, "CGM history:", mContext.getString(R.string.history_read_cgm));
        }

        records();
    }

    private void updateHistorySegments(MedtronicCnlReader cnlReader, int days, final long oldest, final long newest, final byte historyType, boolean pullHistory, String logTAG, String userlogTAG)
            throws EncryptionException, IOException, ChecksumException, TimeoutException, UnexpectedMessageException {

        logTAG = "=== " + logTAG;

        final RealmResults<HistorySegment> segment = historyRealm
                .where(HistorySegment.class)
                .equalTo("historyType", historyType)
                .sort("fromDate", Sort.DESCENDING)
                .findAll();

        // add initial segment if needed
        if (segment.size() == 0) {
            pullHistory = true;
            Log.d(TAG, logTAG + " adding initial segment");
            historyRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {
                    historyRealm.createObject(HistorySegment.class).addSegment(new Date(oldest), historyType);
                }
            });
            // store size changed
        } else if (segment.last().getFromDate().getTime() - oldest > 60 * 60000L) {
            Log.d(TAG, logTAG + " store size has increased, adding segment");
            historyRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {
                    historyRealm.createObject(HistorySegment.class).addSegment(new Date(oldest), historyType);
                }
            });
            // update oldest segment
        } else {
            boolean checkNext = true;
            while (checkNext && segment.size() > 1) {
                // delete oldest segment if not needed as next segment now contains oldest
                if (segment.get(segment.size() - 2).getFromDate().getTime() < oldest)
                    historyRealm.executeTransaction(new Realm.Transaction() {
                        @Override
                        public void execute(@NonNull Realm realm) {
                            segment.deleteFromRealm(segment.size() - 1);
                        }
                    });
                else checkNext = false;
            }
            // update the oldest segment marker
            historyRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {
                    segment.last().setFromDate(new Date(oldest));
                    if (segment.last().getToDate().getTime() < oldest)
                        segment.last().setToDate(new Date(oldest));
                }
            });
        }

        // async history puller
        // works by reducing segment gaps over time until there is a single segment containing the entire range
        // giving priority to most recent needed history

        // [ab]................... < empty history with single segment marking the oldest dates
        // [ab]...............[ab] < add a segment set to current date, history pulled async
        // [ab]..........[a*****b] < *pull*
        // [ab]....[a******-----b] < *pull*
        // [a********-----------b] < *pull*, combine, complete

        // [ab]..........[a-----b] < some history pulled and user exits
        // [ab]...[a-----b]....... < user returns, time has passed
        // [ab]...[a-----b]...[ab] < need recent, add a segment set to current date
        // [ab]...[a-----b].[a**b] < *pull*
        // [ab]...[a-----*****--b] < *pull*, combine
        // [a*******------------b] < *pull*, combine, complete

        if (pullHistory) {
            // add marker
            Log.d(TAG, logTAG + " adding history pull marker");
            historyRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {
                    historyRealm.createObject(HistorySegment.class).addSegment(new Date(newest), historyType);
                }
            });
        }

        if (segment.size() > 1) {

            for (int i = 0; i < segment.size(); i++) {
                Log.d(TAG, String.format("%s segment: %s/%s start: %s end: %s",
                        logTAG,
                        i + 1,
                        segment.size(),
                        dateFormatter.format(segment.get(i).getFromDate()),
                        dateFormatter.format(segment.get(i).getToDate())
                ));
            }

            Date needFrom = segment.get(1).getToDate();
            Date needTo = segment.get(0).getFromDate();

            long now = cnlReader.getSessionDate().getTime();
            long start = needFrom.getTime();
            long end = needTo.getTime();

            // sync limiter
            long daysMS = days * 24 * 60 * 60000L;
            if (now - start > daysMS)
                start = now - daysMS;
            if (end - start < 0) {
                Log.d(TAG, String.format("%s sync limit reached, min date: %s max days: %s",
                        logTAG,
                        dateFormatter.format(now - daysMS),
                        days
                ));
                return;
            }

            // per request limiter
            if (end - start > HISTORY_REQUEST_LIMITER_MS)
                start = end - HISTORY_REQUEST_LIMITER_MS;

            // stats for requested history retrieval
            StatPoll statPoll = (StatPoll) Stats.getInstance().readRecord(StatPoll.class);
            if (historyType == HISTORY_CGM) statPoll.incHistoryCgmRequest();
            else statPoll.incHistoryPumpRequest();

            Log.d(TAG, String.format("%s requested: %s - %s",
                    logTAG,
                    dateFormatter.format(start),
                    dateFormatter.format(end)
            ));

            UserLogMessage.sendN(mContext, UserLogMessage.TYPE.REQUESTED,
                    String.format("%s {id;%s}", userlogTAG, R.string.history_requested));
            UserLogMessage.sendE(mContext, UserLogMessage.TYPE.REQUESTED,
                    String.format("%s {id;%s}\n   {time.hist.e;%s} - {time.hist.e;%s}",
                            userlogTAG, R.string.history_requested, start, end));

            Date[] range;
            ReadHistoryResponseMessage response = cnlReader.getHistory(start, end, historyType);
            if (response == null) {
                // no history data for period, will update the segment data using the requested start/end dates
                range = new Date[] {new Date(start), new Date(end)};
            } else {

                // for efficiency limit the parse time range
                // the pump sends large periods of data and for recent cgm backfill or auto mode microbolus updates,
                // these can have a lot of already processed items that would otherwise need to be checked and discarded
                long parseFrom = 0;
                if (segment.get(0).getFromDate().getTime() == segment.get(0).getToDate().getTime()
                        && segment.get(1).getFromDate().getTime() != segment.get(1).getToDate().getTime())
                    parseFrom = segment.get(1).getToDate().getTime() - 30 * 60000L;

                long timer = System.currentTimeMillis();
                range = new PumpHistoryParser(response.getEventData()).process(
                        pumpHistorySender,
                        cnlReader.getSessionRTC(),
                        cnlReader.getSessionOFFSET(),
                        cnlReader.getSessionClockDifference(),
                        response.getReqStartTime(),
                        response.getReqEndTime(),
                        parseFrom,
                        0);
                timer = System.currentTimeMillis() - timer;
                Log.d(TAG, logTAG + " parser processing took " + timer + "ms");
            }

            Log.d(TAG, String.format("%s received: %s - %s", logTAG,
                    range[0] == null ? "null" : dateFormatter.format(range[0]),
                    range[1] == null ? "null" : dateFormatter.format(range[1])));

            UserLogMessage.sendE(mContext, UserLogMessage.TYPE.RECEIVED,
                    String.format("%s {id;%s}\n   {time.hist.e;%s} - {time.hist.e;%s}",
                            userlogTAG, R.string.history_received,
                            range[0] == null ? 0 : range[0].getTime(),
                            range[1] == null ? 0 : range[1].getTime()));

//            final Date haveFrom = range[0] != null && range[0].getTime() < start ? range[0] : new Date(start);
//            final Date haveTo = range[1] != null && range[1].getTime() - start > -1000L ? range[1] : new Date(end);

            final Date haveFrom = range[0] != null && range[0].getTime() < start ? range[0] : new Date(start);
            final Date haveTo = range[1] != null && range[1].getTime() > haveFrom.getTime() ? range[1] : new Date(end);
            Log.d(TAG, String.format("%s have: %s - %s",
                    logTAG,
                    dateFormatter.format(haveFrom),
                    dateFormatter.format(haveTo)
            ));

            historyRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {

                    // if first segment is empty, update it's toDate as pump may return less/more data
                    if (segment.get(0).getFromDate().getTime() == segment.get(0).getToDate().getTime()) {
                        segment.get(0).setToDate(haveTo);
                    }

                    if (haveFrom.getTime() > segment.get(1).getToDate().getTime()) {
                        // update the segment fromDate, we still need more history for this segment
                        segment.get(0).setFromDate(haveFrom);

                    } else {
                        // segments now overlap, combine to single segment
                        segment.get(1).setToDate(segment.get(0).getToDate());
                        segment.deleteFromRealm(0);

                        // check if any remaining segments need combining or deleting
                        boolean checkNext = true;
                        while (checkNext && segment.size() > 1) {
                            // delete next segment if not needed as we have the events from recent pull
                            if (segment.get(1).getFromDate().getTime() > haveFrom.getTime()) {
                                segment.deleteFromRealm(1);
                            }
                            // combine segments if needed
                            else {
                                checkNext = false;
                                if (segment.get(1).getToDate().getTime() > haveFrom.getTime()) {
                                    segment.get(1).setToDate(segment.get(0).getToDate());
                                    segment.deleteFromRealm(0);
                                }
                            }
                        }

                        // finally update segment fromDate if needed
                        if (segment.get(0).getFromDate().getTime() > haveFrom.getTime()) {
                            segment.get(0).setFromDate(haveFrom);
                        }
                    }

                }
            });

            // stats for successful history retrieval
            if (historyType == HISTORY_CGM) statPoll.incHistoryCgmSuccess();
            else statPoll.incHistoryPumpSuccess();
        }

        for (int i = 0; i < segment.size(); i++) {
            Log.d(TAG, String.format("%s segment: %s/%s start: %s end: %s",
                    logTAG,
                    i + 1,
                    segment.size(),
                    dateFormatter.format(segment.get(i).getFromDate()),
                    dateFormatter.format(segment.get(i).getToDate())
            ));
        }
    }

}
