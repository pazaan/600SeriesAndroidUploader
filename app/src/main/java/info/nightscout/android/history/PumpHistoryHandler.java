package info.nightscout.android.history;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Pair;

import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
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
import info.nightscout.android.medtronic.exception.IntegrityException;
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

import static info.nightscout.android.medtronic.service.MedtronicCnlService.POLL_PERIOD_MS;

/**
 * Created by Pogman on 5.11.17.
 */

public class PumpHistoryHandler {
    private static final String TAG = PumpHistoryHandler.class.getSimpleName();

    private final static long HISTORY_STALE_MS = 120 * 24 * 60 * 60000L;
    private final static long HISTORY_REQUEST_LIMITER_MS = 7 * 24 * 60 * 60000L;

    private final static byte HISTORY_PUMP = 2;
    private final static byte HISTORY_CGM = 3;

    public Context mContext;

    public Realm realm;
    public Realm historyRealm;
    public Realm storeRealm;
    public DataStore dataStore;

    private List<DBitem> historyDB;

    public PumpHistorySender pumpHistorySender;

    private DateFormat dateFormatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US);

    public PumpHistoryHandler(Context context) {
        mContext = context;
        init();
    }

    private void init() {
        Log.d(TAG, "initialise history handler");

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

    public PumpHistorySender getPumpHistorySender() {
        return pumpHistorySender;
    }

    public void refresh() {
        Log.d(TAG,"refresh realm db and senders");
        if (realm != null && !realm.isClosed())
            realm.refresh();
        if (historyRealm != null && !historyRealm.isClosed())
            historyRealm.refresh();
        if (storeRealm != null && !storeRealm.isClosed()) {
            storeRealm.refresh();
            if (dataStore != null)
                pumpHistorySender = new PumpHistorySender().buildSenders(dataStore);
        }
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
        Date limitDate = new Date(now - sender.getProcess());

        StringBuilder log = new StringBuilder();
        StringBuilder logdb = new StringBuilder();
        log.append(String.format("sender[%s] limitdate: %s",senderID, dateFormatter.format(limitDate)));

        List<PumpHistoryInterface> records = new ArrayList<>();

        List<String> request = sender.getRequest();

        for (DBitem dBitem : historyDB) {

            if (request.contains(dBitem.historydb)) {

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
        if (records.size() > sender.getLimiter())
            records = records.subList(records.size() - sender.getLimiter(), records.size());

        log.append(String.format(" limiter: %s final: %s", sender.getLimiter(), records.size()));
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

        List<Pair<String, Long>> ttl = sender.getTtl();

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

    public void reset() {
        historyRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(@NonNull Realm realm) {

                int count = 0;
                for (DBitem dBitem : historyDB) {
                    RealmResults<PumpHistoryInterface> records = dBitem.results.where().findAll();
                    count += records.size();
                    records.deleteAllFromRealm();
                }

                Log.d(TAG, "reset history database: deleted " + count + " history records");

                final RealmResults<HistorySegment> segment = historyRealm
                        .where(HistorySegment.class)
                        .findAll();
                segment.deleteAllFromRealm();

                Log.d(TAG, "reset history segments: deleted " + segment.size() + " segment records");
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
                                .not()
                                .contains("senderDEL", senderID)
                                .findAll();
                        for (PumpHistoryInterface record : results)
                            if (!record.getSenderREQ().contains(senderID)) record.setSenderREQ(record.getSenderREQ() + senderID);
                    }
                }
            }

        });
    }

    public SystemEvent systemEvent(PumpHistorySystem.STATUS status) {
        return new SystemEvent(status);
    }

    public SystemEvent systemEvent() {
        return new SystemEvent();
    }

    public class SystemEvent {
        PumpHistorySystem.STATUS status;
        Date eventDate;
        Date startDate;
        String key;
        RealmList<String> data;

        public SystemEvent () {}

        public SystemEvent (PumpHistorySystem.STATUS status) {
            this.status = status;
            eventDate = new Date(System.currentTimeMillis());
            startDate = eventDate;
            key = String.format("%016X", eventDate.getTime());
        }

        public SystemEvent event(Date date) {
            this.eventDate = date;
            return this;
        }

        public SystemEvent start(Date date) {
            this.startDate = date;
            return this;
        }

        public SystemEvent key(String key) {
            this.key = key;
            return this;
        }

        public SystemEvent key(int key) {
            this.key = String.format("%08X", key);
            return this;
        }

        public SystemEvent key(long key) {
            this.key = String.format("%016X", key);
            return this;
        }

        public SystemEvent data(int i) {
            this.data = new RealmList<>(Integer.toString(i));
            return this;
        }

        public SystemEvent data(String s) {
            this.data = new RealmList<>(s);
            return this;
        }

        public SystemEvent lastConnect() {
            RealmResults<PumpStatusEvent> results = realm
                    .where(PumpStatusEvent.class)
                    .greaterThan("eventDate", new Date(System.currentTimeMillis() - 24 * 60 * 60000L))
                    .sort("eventDate", Sort.DESCENDING)
                    .findAll();
            if (results.size() > 0) {
                key(results.first().getEventRTC());
                start(results.first().getEventDate());
            } else {
                key = null;
            }
            return this;
        }

        public SystemEvent process() {
            if (key != null) {
                historyRealm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(@NonNull Realm realm) {
                        PumpHistorySystem.event(pumpHistorySender, historyRealm,
                                eventDate,
                                startDate,
                                key,
                                status,
                                data
                        );
                    }
                });
            }
            return this;
        }

        public void closeHandler() {
            close();
        }

        public SystemEvent dismiss(PumpHistorySystem.STATUS status, final String senderID) {
            // dismiss pending unsent status events for selected sender
            RealmResults<PumpHistorySystem> results = historyRealm
                    .where(PumpHistorySystem.class)
                    .equalTo("status", status.value())
                    .contains("senderREQ", senderID)
                    .findAll();
            if (results.size() > 0) {
                Log.d(TAG, String.format("SystemEvent dismiss: %s senderID = %s count = %s",
                        status.name(), senderID, results.size()));
                for (PumpHistorySystem record : results) {
                    final PumpHistorySystem r = record;
                    historyRealm.executeTransaction(new Realm.Transaction() {
                        @Override
                        public void execute(@NonNull Realm realm) {
                            r.setSenderREQ(r.getSenderREQ().replace(senderID, ""));
                        }
                    });
                }
            }
            return this;
        }

        public SystemEvent dismiss(PumpHistorySystem.STATUS status) {
            // dismiss pending unsent status events for all associated senders
            RealmResults<PumpHistorySystem> results = historyRealm
                    .where(PumpHistorySystem.class)
                    .equalTo("status", status.value())
                    .notEqualTo("senderREQ", "")
                    .findAll();
            if (results.size() > 0) {
                Log.d(TAG, String.format("SystemEvent dismiss: %s count = %s",
                        status.name(), results.size()));
                for (PumpHistorySystem record : results) {
                    final PumpHistorySystem r = record;
                    historyRealm.executeTransaction(new Realm.Transaction() {
                        @Override
                        public void execute(@NonNull Realm realm) {
                            r.setSenderREQ("");
                        }
                    });
                }
            }
            return this;
        }

    }

    public void debugNote(final String note) {
        systemEvent(PumpHistorySystem.STATUS.DEBUG)
                .data(note)
                .process();
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

    public boolean isProfileInHistory() {

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
        return results.size() == 0 ? -1 : System.currentTimeMillis() - results.first().getToDate().getTime();
    }

    public long historyRecency() {
        RealmResults<HistorySegment> results = historyRealm
                .where(HistorySegment.class)
                .sort("toDate", Sort.DESCENDING)
                .findAll();
        return results.size() == 0 ? -1 : System.currentTimeMillis() - results.first().getToDate().getTime();
    }

    public void readProfile(final MedtronicCnlReader cnlReader) throws EncryptionException, IOException, ChecksumException, TimeoutException, UnexpectedMessageException {

        UserLogMessage.send(mContext, R.string.ul_history__reading_pump_basal_basal_patterns);
        final byte[] basalPatterns = cnlReader.getBasalPatterns();
        UserLogMessage.send(mContext, R.string.ul_history__reading_pump_carb_ratios);
        final byte[] carbRatios = cnlReader.getBolusWizardCarbRatios();
        UserLogMessage.send(mContext, R.string.ul_history__reading_pump_sensitivity_factors);
        final byte[] sensitivity = cnlReader.getBolusWizardSensitivity();
        UserLogMessage.send(mContext, R.string.ul_history__reading_pump_hi_lo_targets);
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
                    checkProfile = pumpStatusEvents.first().getActiveBasalPattern();
                    if (checkProfile < 1 && checkProfile > 8)
                        checkProfile = 1;
                }
            }
        }
        final byte defaultProfile = checkProfile; // range 1 to 9

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
    }

    public void checkResendRequests() {

        final boolean basal = dataStore.isResendPumpHistoryBasal();
        final boolean bolus = dataStore.isResendPumpHistoryBolus();
        final boolean bg = dataStore.isResendPumpHistoryBG();
        final boolean misc = dataStore.isResendPumpHistoryMisc();
        final boolean alarm = dataStore.isResendPumpHistoryAlarm();
        final boolean system = dataStore.isResendPumpHistorySystem();
        final boolean daily = dataStore.isResendPumpHistoryDaily();
        final boolean pattern = dataStore.isResendPumpHistoryPattern();

        if (basal) reupload(PumpHistoryBasal.class, PumpHistorySender.SENDER_ID_NIGHTSCOUT);
        if (bolus) reupload(PumpHistoryBolus.class, PumpHistorySender.SENDER_ID_NIGHTSCOUT);
        if (bg) reupload(PumpHistoryBG.class, PumpHistorySender.SENDER_ID_NIGHTSCOUT);
        if (misc) reupload(PumpHistoryMisc.class, PumpHistorySender.SENDER_ID_NIGHTSCOUT);
        if (alarm) reupload(PumpHistoryAlarm.class, PumpHistorySender.SENDER_ID_NIGHTSCOUT);
        if (system) reupload(PumpHistorySystem.class, PumpHistorySender.SENDER_ID_NIGHTSCOUT);
        if (daily) reupload(PumpHistoryDaily.class, PumpHistorySender.SENDER_ID_NIGHTSCOUT);
        if (pattern) reupload(PumpHistoryPattern.class, PumpHistorySender.SENDER_ID_NIGHTSCOUT);

        if (basal |bolus | bg | misc | alarm | system | daily | pattern) {
            storeRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {
                    if (basal) dataStore.setResendPumpHistoryBasal(false);
                    if (bolus) dataStore.setResendPumpHistoryBolus(false);
                    if (bg) dataStore.setResendPumpHistoryBG(false);
                    if (misc) dataStore.setResendPumpHistoryMisc(false);
                    if (alarm) dataStore.setResendPumpHistoryAlarm(false);
                    if (system) dataStore.setResendPumpHistorySystem(false);
                    if (daily) dataStore.setResendPumpHistoryDaily(false);
                    if (pattern) dataStore.setResendPumpHistoryPattern(false);
                }
            });
        }
    }

    IntegrityException integrityException;

    public void cgm(final PumpStatusEvent pumpRecord) throws IntegrityException {

        if (!pumpRecord.isCgmActive()) return;

        boolean backfill = false;
        boolean estimate = false;
        boolean isig = false;

        final Date date = pumpRecord.getCgmDate();
        final int rtc = pumpRecord.getCgmRTC();
        final int offset = pumpRecord.getCgmOFFSET();
        final long pumpMAC = pumpRecord.getPumpMAC();
        final int sgv = pumpRecord.getSgv();
        final String trend = pumpRecord.getCgmTrendString();
        final byte exception = pumpRecord.getCgmExceptionType();

        RealmResults<PumpHistoryCGM> cgmResults = historyRealm
                .where(PumpHistoryCGM.class)
                .sort("eventDate", Sort.DESCENDING)
                .findAll();

        // already processed?
        boolean processed = cgmResults.size() > 0 && cgmResults.first().getCgmRTC() == rtc;

        // cgm gap?
        boolean gap = cgmResults.size() > 0 && date.getTime() - cgmResults.first().getEventDate().getTime() > 9 * 60 * 1000L;

        // cgm is available do we need the backfill?
        if (dataStore.isSysEnableCgmHistory()) {

            // empty history?
            if (cgmResults.size() == 0) {
                backfill = true;
            }

            else if (!processed) {

                // estimate needed?
                if (dataStore.isSysEnableEstimateSGV() && (
                        PumpHistoryParser.CGM_EXCEPTION.SENSOR_CAL_NEEDED.equals(exception)
                                || (sgv == 0 && PumpHistoryParser.CGM_EXCEPTION.SENSOR_CAL_PENDING.equals(exception))
                                || (dataStore.isSysEnableEstimateSGVerror() && PumpHistoryParser.CGM_EXCEPTION.SENSOR_ERROR.equals(exception))
                                || (dataStore.isSysEnableEstimateSGVeol() && (
                                PumpHistoryParser.CGM_EXCEPTION.SENSOR_END_OF_LIFE.equals(exception)
                                        || PumpHistoryParser.CGM_EXCEPTION.SENSOR_CHANGE_SENSOR_ERROR.equals(exception)
                                        || PumpHistoryParser.CGM_EXCEPTION.SENSOR_CHANGE_CAL_ERROR.equals(exception)))
                )) {

                    if (pumpHistoryRecency() <= 6 * 60 * 60000L) {

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
                                processed = true;
                                backfill = true;
                                estimate = true;
                                ((StatPoll) Stats.getInstance().readRecord(StatPoll.class)).incHistoryReqEstimate();
                                UserLogMessage.send(mContext, UserLogMessage.TYPE.HISTORY,
                                        String.format("{id;%s}: {id;%s}", R.string.ul_history__history, R.string.ul_history__estimate_sgv));
                            }

                        } else {
                            Log.w(TAG, "sgv estimate not available: no sensor change event found");
                        }

                    } else {
                        Log.w(TAG,"sgv estimate not available: history recency > 6 hours");
                    }

                }

                // isig report needed?
                if (dataStore.isSysEnableReportISIG()) {
                    final long now = System.currentTimeMillis();
                    long min = dataStore.getReportIsigTimestamp() + dataStore.getSysReportISIGminimum() * 60000L;

                    long sensormin = 0;
                    RealmResults<PumpHistoryMisc> miscResults = historyRealm
                            .where(PumpHistoryMisc.class)
                            .equalTo("recordtype", PumpHistoryMisc.RECORDTYPE.CHANGE_SENSOR.value())
                            .sort("eventDate", Sort.DESCENDING)
                            .findAll();
                    if (miscResults.size() > 0) {
                        sensormin = miscResults.first().getEventDate().getTime() + dataStore.getSysReportISIGnewsensor() * 60000L;
                    }

                    if (sgv <= 70 || sensormin > now || min > now) {
                        backfill = true;
                        isig = true;

                        if (!estimate) {
                            UserLogMessage.send(mContext, UserLogMessage.TYPE.HISTORY,
                                    String.format("{id;%s}: {id;%s}", R.string.ul_history__history, R.string.ul_history__report_isig));
                            if (sgv <= 70) {
                                storeRealm.executeTransaction(new Realm.Transaction() {
                                    @Override
                                    public void execute(@NonNull Realm realm) {
                                        dataStore.setReportIsigTimestamp(now);
                                    }
                                });
                            }
                        }
                    }
                }

                // missed readings?
                if (gap && !estimate && !isig) {

                    // ignore missed readings during the warm-up phase
                    if (!PumpHistoryParser.CGM_EXCEPTION.SENSOR_INIT.equals(exception) &&
                            !PumpHistoryParser.CGM_EXCEPTION.SENSOR_INIT.equals(cgmResults.first().getSensorException())) {
                        backfill = true;
                        ((StatPoll) Stats.getInstance().readRecord(StatPoll.class)).incHistoryReqBackfill();
                        UserLogMessage.send(mContext, UserLogMessage.TYPE.HISTORY,
                                String.format("{id;%s}: {id;%s}", R.string.ul_history__history, R.string.ul_history__cgm_backfill));
                    }

                }
            }
        }

        if (!processed) {
            // push the current sgv from status (always have latest sgv available even if there are comms errors after this)
            historyRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {
                    try {
                        PumpHistoryCGM.cgmFromStatus(
                                pumpHistorySender, historyRealm, pumpMAC,
                                date, rtc, offset,
                                sgv,
                                exception,
                                trend
                        );
                    } catch (IntegrityException e) {
                        integrityException = e;
                    }
                }
            });
            if (integrityException != null) throw integrityException;
        }

        if (backfill | estimate | isig) {
            final boolean final_backfill = backfill;
            final boolean final_estimate = estimate;
            final boolean final_isig = isig;
            storeRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {
                    dataStore.setRequestCgmHistory(final_backfill);
                    dataStore.setRequestEstimate(final_estimate);
                    dataStore.setRequestIsig(final_isig);
                }
            });
        }

        // if isig report was previously available and isig is still required then keep it available until next successful cgm history update
        if (dataStore.isReportIsigAvailable() && (!dataStore.isSysEnableReportISIG() || (!processed && !isig))) {
            storeRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {
                    dataStore.setReportIsigAvailable(false);
                }
            });
        }
    }

    public void runEstimate() {
        Estimate estimate = new Estimate();
        estimate.updateEstimate();
        estimate.updateOptions();
    }

    public Estimate getEstimate() {
        return new Estimate();
    }

    public class Estimate {
        private String TAG = this.getClass().getDeclaringClass().getSimpleName() + " " + this.getClass().getSimpleName();

        private DateFormat df = new SimpleDateFormat("MM/dd HH:mm", Locale.getDefault());
        private int log = 0; // 0=none 1=info 2=debug

        private long RECORD_LIMIT_MS = 90 * 24 * 60 * 60000L;
        private long SENSOR_PERIOD_MS = 10 * 24 * 60 * 60000L;

        private double OFFSET = 3.0;
        private double CLIP = 10;
        private double DROP = 20;
        private double K = 0.7;
        private double K_ERR = 0.2;
        private double K_EOL = 0.5;

        private Date limitDate = new Date(System.currentTimeMillis() - RECORD_LIMIT_MS);

        private boolean optEst = dataStore.isSysEnableEstimateSGV();
        private boolean optEol = dataStore.isSysEnableEstimateSGVeol();
        private boolean optErr = dataStore.isSysEnableEstimateSGVerror();

        private boolean optUserlog = false;

        private List<PumpHistoryCGM> updateRecords = new ArrayList();
        private List<Integer> updateSgv = new ArrayList();

        private int processRTC;

        public Estimate setOptions(boolean optEst, boolean optEol, boolean optErr) {
            this.optEst = optEst;
            this.optEol = optEol;
            this.optErr = optErr;
            return this;
        }

        public Estimate setLimit(long limit) {
            limitDate = new Date(System.currentTimeMillis() - limit);
            return this;
        }

        public Estimate setUserlog(boolean optUserlog) {
            this.optUserlog = optUserlog;
            return this;
        }

        public void updateEstimate() {

            if (optEst) {

                RealmResults<HistorySegment> results = historyRealm
                        .where(HistorySegment.class)
                        .sort("toDate", Sort.DESCENDING)
                        .findAll();
                if (results.size() == 0) return;

                // only consider cgm results up to this date as pump history may be stale
                Date sessionEndDate = new Date (results.first().getToDate().getTime() + 6 * 60 * 60000L);

                Date sensorStartDate = new Date(System.currentTimeMillis());

                // viable sensor records
                RealmResults<PumpHistoryMisc> sensorRecords = historyRealm
                        .where(PumpHistoryMisc.class)
                        .equalTo("recordtype", PumpHistoryMisc.RECORDTYPE.CHANGE_SENSOR.value())
                        .greaterThan("eventDate", limitDate)
                        .sort("eventDate", Sort.DESCENDING)
                        .findAll();
                if (sensorRecords.size() == 0) return;

                // use sensor records that are within set interval
                // due to potential incomplete cal/cgm data
                for (PumpHistoryMisc pumpHistoryMisc : sensorRecords) {
                    if (sensorStartDate.getTime() - pumpHistoryMisc.getEventDate().getTime() < SENSOR_PERIOD_MS)
                        sensorStartDate = pumpHistoryMisc.getEventDate();
                    else break;
                }

                sensorRecords = historyRealm
                        .where(PumpHistoryMisc.class)
                        .equalTo("recordtype", PumpHistoryMisc.RECORDTYPE.CHANGE_SENSOR.value())
                        .greaterThanOrEqualTo("eventDate", sensorStartDate)
                        .sort("eventRTC", Sort.ASCENDING)
                        .findAll();
                if (sensorRecords.size() == 0) return;
                int sensorStartRTC = sensorRecords.first().getEventRTC();

                // calibration records
                RealmResults<PumpHistoryBG> calRecords = historyRealm
                        .where(PumpHistoryBG.class)
                        .equalTo("calibration", true)
                        .greaterThan("calibrationRTC", sensorStartRTC)
                        .sort("calibrationRTC", Sort.ASCENDING)
                        .findAll();
                if (calRecords.size() == 0) return;
                Date calStartDate = calRecords.first().getCalibrationDate();
                int calStartRTC = calRecords.first().getCalibrationRTC();

                if (log >= 1) {
                    Log.d(TAG, String.format("RECORDS sensor: %s start %s cal: %s start %s",
                            sensorRecords.size(),
                            df.format(sensorStartDate),
                            calRecords.size(),
                            df.format(calStartDate)
                    ));
                }

                Byte[] exceptions;
                if (optEol && optErr)
                    exceptions = new Byte[]
                            {
                                    (byte) PumpHistoryParser.CGM_EXCEPTION.SENSOR_OK.value(),
                                    (byte) PumpHistoryParser.CGM_EXCEPTION.SENSOR_CAL_NEEDED.value(),
                                    (byte) PumpHistoryParser.CGM_EXCEPTION.SENSOR_CAL_PENDING.value(),
                                    (byte) PumpHistoryParser.CGM_EXCEPTION.SENSOR_END_OF_LIFE.value(),
                                    (byte) PumpHistoryParser.CGM_EXCEPTION.SENSOR_ERROR.value(),
                                    (byte) PumpHistoryParser.CGM_EXCEPTION.SENSOR_CHANGE_SENSOR_ERROR.value(),
                                    (byte) PumpHistoryParser.CGM_EXCEPTION.SENSOR_CHANGE_CAL_ERROR.value()
                            };
                else if (optEol)
                    exceptions = new Byte[]
                            {
                                    (byte) PumpHistoryParser.CGM_EXCEPTION.SENSOR_OK.value(),
                                    (byte) PumpHistoryParser.CGM_EXCEPTION.SENSOR_CAL_NEEDED.value(),
                                    (byte) PumpHistoryParser.CGM_EXCEPTION.SENSOR_CAL_PENDING.value(),
                                    (byte) PumpHistoryParser.CGM_EXCEPTION.SENSOR_END_OF_LIFE.value()
                            };
                else if (optErr)
                    exceptions = new Byte[]
                            {
                                    (byte) PumpHistoryParser.CGM_EXCEPTION.SENSOR_OK.value(),
                                    (byte) PumpHistoryParser.CGM_EXCEPTION.SENSOR_CAL_NEEDED.value(),
                                    (byte) PumpHistoryParser.CGM_EXCEPTION.SENSOR_CAL_PENDING.value(),
                                    (byte) PumpHistoryParser.CGM_EXCEPTION.SENSOR_ERROR.value(),
                                    (byte) PumpHistoryParser.CGM_EXCEPTION.SENSOR_CHANGE_SENSOR_ERROR.value(),
                                    (byte) PumpHistoryParser.CGM_EXCEPTION.SENSOR_CHANGE_CAL_ERROR.value()
                            };
                else
                    exceptions = new Byte[]
                            {
                                    (byte) PumpHistoryParser.CGM_EXCEPTION.SENSOR_OK.value(),
                                    (byte) PumpHistoryParser.CGM_EXCEPTION.SENSOR_CAL_NEEDED.value(),
                                    (byte) PumpHistoryParser.CGM_EXCEPTION.SENSOR_CAL_PENDING.value()

                            };

                // viable cgm records
                RealmResults<PumpHistoryCGM> cgmRecords = historyRealm
                        .where(PumpHistoryCGM.class)
                        .equalTo("history", true)
                        .equalTo("discardData", false)
                        .equalTo("noisyData", false)
                        .in("sensorException", exceptions)
                        .greaterThan("cgmRTC", calStartRTC)
                        .lessThan("eventDate", sessionEndDate)
                        .sort("cgmRTC", Sort.ASCENDING)
                        .findAll();

                processRTC = calStartRTC;

                int n = 0;
                boolean complete;
                do {
                    // unprocessed cgm records
                    RealmResults<PumpHistoryCGM> unprocessedCgmRecords = cgmRecords.where()
                            .equalTo("estimate", false)
                            .equalTo("sgv", 0)
                            .greaterThan("cgmRTC", processRTC)
                            .findAll();

                    if (unprocessedCgmRecords.size() > 0) {
                        Log.d(TAG, "processing #" + n++);
                        processRTC = unprocessedCgmRecords.first().getCgmRTC();
                        complete = process(unprocessedCgmRecords, cgmRecords, sensorRecords, calRecords);
                    } else complete = true;

                } while (!complete);

                // update records
                if (updateRecords.size() > 0) {
                    historyRealm.executeTransaction(new Realm.Transaction() {
                        @Override
                        public void execute(@NonNull Realm realm) {

                            for (int i = 0; i < updateRecords.size(); i++) {
                                PumpHistoryCGM record = updateRecords.get(i);
                                record.setSgv(updateSgv.get(i));
                                record.setEstimate(true);
                                pumpHistorySender.setSenderREQ(record);
                            }

                        }
                    });
                    updateRecords.clear();
                    updateSgv.clear();
                }
            }
        }

        public void updateOptions() {

            RealmResults<PumpHistoryCGM> cgmRecords = null;
            if (!optEst) cgmRecords = historyRealm
                    .where(PumpHistoryCGM.class)
                    .equalTo("estimate", true)
                    .greaterThan("eventDate", limitDate)
                    .findAll();

            else if (!optEol && !optErr) cgmRecords = historyRealm
                    .where(PumpHistoryCGM.class)
                    .equalTo("estimate", true)
                    .in("sensorException", new Byte[] {
                            (byte) PumpHistoryParser.CGM_EXCEPTION.SENSOR_END_OF_LIFE.value(),
                            (byte) PumpHistoryParser.CGM_EXCEPTION.SENSOR_CHANGE_SENSOR_ERROR.value(),
                            (byte) PumpHistoryParser.CGM_EXCEPTION.SENSOR_CHANGE_CAL_ERROR.value(),
                            (byte) PumpHistoryParser.CGM_EXCEPTION.SENSOR_ERROR.value()})
                    .greaterThan("eventDate", limitDate)
                    .findAll();

            else if (!optEol) cgmRecords = historyRealm
                    .where(PumpHistoryCGM.class)
                    .equalTo("estimate", true)
                    .greaterThan("eventDate", limitDate)
                    .equalTo("sensorException", PumpHistoryParser.CGM_EXCEPTION.SENSOR_END_OF_LIFE.value())
                    .greaterThan("eventDate", limitDate)
                    .findAll();

            else if (!optErr) cgmRecords = historyRealm
                    .where(PumpHistoryCGM.class)
                    .equalTo("estimate", true)
                    .in("sensorException", new Byte[] {
                            (byte) PumpHistoryParser.CGM_EXCEPTION.SENSOR_ERROR.value(),
                            (byte) PumpHistoryParser.CGM_EXCEPTION.SENSOR_CHANGE_SENSOR_ERROR.value(),
                            (byte) PumpHistoryParser.CGM_EXCEPTION.SENSOR_CHANGE_CAL_ERROR.value()})
                    .greaterThan("eventDate", limitDate)
                    .findAll();

            if (cgmRecords != null && cgmRecords.size() > 0) {
                Log.d(TAG, String.format("clearing cgm records with estimated sgv. Count = %s", cgmRecords.size()));

                final RealmResults<PumpHistoryCGM> records = cgmRecords;
                historyRealm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(@NonNull Realm realm) {

                        for (PumpHistoryCGM pumpHistoryCGM : records) {
                            pumpHistoryCGM.setSgv(0);
                            pumpHistoryCGM.setEstimate(false);
                            pumpHistorySender.setSenderREQ(pumpHistoryCGM);
                        }

                    }
                });
            }

        }

        private boolean process(
                RealmResults<PumpHistoryCGM> unprocessedCgmRecords,
                RealmResults<PumpHistoryCGM> cgmRecords,
                RealmResults<PumpHistoryMisc> sensorRecords,
                RealmResults<PumpHistoryBG> calRecords) {

            // unprocessed cgm record
            Date cgmStartDate = unprocessedCgmRecords.first().getEventDate();
            int cgmStart = unprocessedCgmRecords.first().getCgmRTC();

            // sensor start/end
            RealmResults<PumpHistoryMisc> sensorResults = sensorRecords.where()
                    .greaterThan("eventRTC", HistoryUtils.offsetRTC(cgmStart, - 10 * 24 * 60 * 60))
                    .lessThanOrEqualTo("eventRTC", cgmStart)
                    .sort("eventRTC", Sort.DESCENDING)
                    .findAll();
            if (sensorResults.size() == 0) return true;

            Date sensorStartDate = sensorResults.first().getEventDate();
            int sensorStart = sensorResults.first().getEventRTC();
            Date sensorEndDate = new Date(cgmStartDate.getTime() + 10 * 24 * 60 * 60000L);
            int sensorEnd = HistoryUtils.offsetRTC(cgmStart,  10 * 24 * 60 * 60);

            sensorResults = sensorRecords.where()
                    .greaterThan("eventRTC", sensorStart)
                    .lessThanOrEqualTo("eventRTC", sensorEnd)
                    .sort("eventRTC", Sort.ASCENDING)
                    .findAll();
            if (sensorResults.size() > 0) {
                sensorEndDate = sensorResults.first().getEventDate();
                sensorEnd = sensorResults.first().getEventRTC();
            }

            if (log >= 1) {
                Log.d(TAG, String.format("sensorStart: %s sensorEnd: %s",
                        df.format(sensorStartDate),
                        df.format(sensorEndDate)
                ));
            }

            // cal start/end
            RealmResults<PumpHistoryBG> calResults = calRecords.where()
                    .greaterThan("calibrationRTC", sensorStart)
                    .lessThanOrEqualTo("calibrationRTC", cgmStart)
                    .sort("calibrationRTC", Sort.DESCENDING)
                    .findAll();
            if (calResults.size() == 0) {
                // flag record as not available for estimation
                if (log >= 1) {
                    Log.d(TAG, "record outside calibration period, no estimated sgv available");
                }

                updateRecords.add(unprocessedCgmRecords.first());
                updateSgv.add(0);

                return false;
            }

            Date calStartDate = calResults.first().getCalibrationDate();
            int calStart = calResults.first().getCalibrationRTC();
            Date calEndDate = sensorEndDate;
            int calEnd = sensorEnd;

            calResults = calRecords.where()
                    .greaterThanOrEqualTo("calibrationRTC", calStart)
                    .lessThanOrEqualTo("calibrationRTC", sensorEnd)
                    .sort("calibrationRTC", Sort.ASCENDING)
                    .findAll();
            if (calResults.size() > 1) {
                calEndDate = calResults.get(1).getCalibrationDate();
                calEnd = calResults.get(1).getCalibrationRTC();
            }

            if (log >= 1) {
                Log.d(TAG, String.format("calStart: %s calEnd: %s",
                        df.format(calStartDate),
                        df.format(calEndDate)
                ));
            }

            // cgm records for calibrated period
            RealmResults<PumpHistoryCGM> calCgmRecords = cgmRecords.where()
                    .greaterThanOrEqualTo("cgmRTC", calStart)
                    .lessThan("cgmRTC", calEnd)
                    .findAll();
            if (calCgmRecords.size() == 0) return true;

            // Kalman Filter
            // X = K * Z + (1 - K) * X1
            // X = current estimation
            // X1 = previous estimation
            // Z = current measurement
            // K = Kalman gain

            double factor = calResults.first().getCalibrationFactor();
            int target = calResults.first().getCalibrationTarget();

            int sgv;
            double isig;

            double offsetCount = 1;
            double offsetAvg = OFFSET;
            double offsetSum = OFFSET;

            double z;
            double x = 0;

            boolean est;
            double estsgv;

            PumpHistoryParser.CGM_EXCEPTION ex;

            if (log >= 2) {
                Log.d(TAG, String.format("BG %s cal: %s target: %s(%s) factor: %s",
                        df.format(calResults.first().getEventDate()),
                        df.format(calResults.first().getCalibrationDate()),
                        target,
                        FormatKit.getInstance().formatAsGlucoseMMOL(target, false, 1),
                        factor
                ));
            }

            Log.d(TAG, String.format("[CAL] %s",
                    target / (calCgmRecords.get(0).getIsig() - OFFSET)
            ));
            //factor = target / (calCgmRecords.get(0).getIsig() - OFFSET);

            for (int i = 0; i < calCgmRecords.size(); i++) {
                processRTC = calCgmRecords.get(i).getCgmRTC();

                est = calCgmRecords.get(i).isEstimate();
                estsgv = 0;

                sgv = calCgmRecords.get(i).getSgv();
                isig = calCgmRecords.get(i).getIsig();
                ex = PumpHistoryParser.CGM_EXCEPTION.convert(calCgmRecords.get(i).getSensorException());

                if (x == 0) x = isig;

                else if (Math.abs(isig - x) < DROP) {

                    if (isig - x > CLIP) z = x + CLIP;
                    else if (x - isig > CLIP) z = x - CLIP;
                    else z = isig;

                    // use lower Kalman gain when sensor is in error/eol
                    if (PumpHistoryParser.CGM_EXCEPTION.SENSOR_ERROR == ex
                            || PumpHistoryParser.CGM_EXCEPTION.SENSOR_CHANGE_SENSOR_ERROR == ex
                            || PumpHistoryParser.CGM_EXCEPTION.SENSOR_CHANGE_CAL_ERROR == ex)
                        x = K_ERR * z + (1 - K_ERR) * x;
                    else if (PumpHistoryParser.CGM_EXCEPTION.SENSOR_END_OF_LIFE == ex)
                        x = K_EOL * z + (1 - K_EOL) * x;
                    else
                        x = K * z + (1 - K) * x;

                    estsgv = (x - OFFSET) * factor;

                    // offset average may indicate estimation quality
                    // ideal offset value is 3.0
                    if (!est && sgv > 0) {
                        offsetCount++;
                        offsetSum += isig - (sgv / factor);
                        offsetAvg = offsetSum / offsetCount;
                    }

                } else if (log >= 1) {
                    Log.d(TAG, String.format("No Estimate due to isig difference: %s >= %s",
                            FormatKit.getInstance().formatAsDecimal(Math.abs(isig - x), 2),
                            FormatKit.getInstance().formatAsDecimal(DROP, 2)
                    ));
                }

                if (log >= 2) {
                    Log.d(TAG, String.format("[%s] t+%s %s | pump: %s(%s) isig: %s vctr: %s off: %s | est: %s(%s) %s | factor: %s offset: %s ex: %s",
                            est ? "EST" : "SGV",
                            i,
                            df.format(calCgmRecords.get(i).getEventDate()),
                            est ? 0 : sgv,
                            est ? 0 : FormatKit.getInstance().formatAsGlucoseMMOL(sgv, false, 1),
                            FormatKit.getInstance().formatAsDecimal(isig, 2),
                            FormatKit.getInstance().formatAsDecimal(calCgmRecords.get(i).getVctr(), 2),
                            sgv == 0 ? 0 : FormatKit.getInstance().formatAsDecimal(isig - (sgv / factor),2),
                            (int) Math.round(estsgv),
                            FormatKit.getInstance().formatAsGlucoseMMOL((int) Math.round(estsgv), false, 1),
                            FormatKit.getInstance().formatAsDecimal(x, 2),
                            factor,
                            FormatKit.getInstance().formatAsDecimal(offsetAvg,2),
                            ex.name()
                    ));
                }

                // discard estimate if out of range
                if (estsgv < 40 || estsgv > 500)
                    estsgv = 0;

                if (!est && sgv == 0) {
                    int finalEstimate = (int) Math.round(estsgv);

                    updateRecords.add(calCgmRecords.get(i));
                    updateSgv.add(finalEstimate);

                    if (log >= 1) {
                        Log.d(TAG, String.format("Final Estimate %s(%s) isig=%s vctr=%s t+%s f=%s o=%s x=%s ex=%s",
                                finalEstimate,
                                FormatKit.getInstance().formatAsGlucoseMMOL(finalEstimate, false, 1),
                                isig,
                                calCgmRecords.get(i).getVctr(),
                                i,
                                factor,
                                FormatKit.getInstance().formatAsDecimal(offsetAvg,2),
                                FormatKit.getInstance().formatAsDecimal(x,2),
                                ex.name()
                        ));
                    }

                    if (optUserlog && finalEstimate > 0
                            && System.currentTimeMillis() - calCgmRecords.get(i).getEventDate().getTime() < 20 * 60000L) {

                        UserLogMessage.sendN(mContext, UserLogMessage.TYPE.ESTIMATE,
                                String.format("{id;%s} {sgv;%s} {id;%s} {time.sgv;%s}",
                                        R.string.ul_poll__estimated_sgv,
                                        finalEstimate,
                                        R.string.ul_poll__reading_time__at,
                                        calCgmRecords.get(i).getEventDate().getTime()
                                ));
                        UserLogMessage.sendE(mContext, UserLogMessage.TYPE.ESTIMATE,
                                String.format("{id;%s} {sgv;%s} {id;%s} {time.sgv.e;%s}",
                                        R.string.ul_poll__estimated_sgv,
                                        finalEstimate,
                                        R.string.ul_poll__reading_time__at,
                                        calCgmRecords.get(i).getEventDate().getTime()
                                ));

                        UserLogMessage.sendE(mContext,
                                String.format("isig: %s vctr: %s roc: %s s: %s/%s %s%s%s%s%s",
                                        calCgmRecords.get(i).getIsig(),
                                        calCgmRecords.get(i).getVctr(),
                                        calCgmRecords.get(i).getRateOfChange(),
                                        calCgmRecords.get(i).getSensorStatus(),
                                        calCgmRecords.get(i).getReadingStatus(),
                                        calCgmRecords.get(i).isNoisyData() ? "N" : "",
                                        calCgmRecords.get(i).isDiscardData() ? "D" : "",
                                        calCgmRecords.get(i).isSensorError() ? "E" : "",
                                        calCgmRecords.get(i).isBackfilledData() ? "B" : "",
                                        calCgmRecords.get(i).isSettingsChanged() ? "S" : ""
                                ));
                        UserLogMessage.sendE(mContext,
                                String.format("t+%s f: %s o: %s x: %s",
                                        i,
                                        factor,
                                        FormatKit.getInstance().formatAsDecimal(offsetAvg, 2),
                                        FormatKit.getInstance().formatAsDecimal(x, 2)
                                ));
                    }

                }

            }

            return false;
        }

    }

    public IsigReport isigReport() {
        return new IsigReport();
    }

    public class IsigReport {
        private Date eventDate;
        private Date sensorDate;
        private int period;
        private double gain;
        private double average;
        private List<Double> isig = new ArrayList();
        private List<Double> delta = new ArrayList();
        private DecimalFormat df;

        public IsigReport() {
            df = new DecimalFormat("0", DecimalFormatSymbols.getInstance(Locale.getDefault()));
            df.setMinimumFractionDigits(1);
            df.setMaximumFractionDigits(2);
            readIsig(10);
            calcGain(30, 0.5);
        }

        public void readIsig(int max) {
            Date limit = new Date(System.currentTimeMillis() - 24 * 60 * 60000L);

            // limit isig to current sensor
            RealmResults<PumpHistoryMisc> miscResults = historyRealm
                    .where(PumpHistoryMisc.class)
                    .equalTo("recordtype", PumpHistoryMisc.RECORDTYPE.CHANGE_SENSOR.value())
                    .sort("eventDate", Sort.DESCENDING)
                    .findAll();
            if (miscResults.size() > 0) {
                sensorDate = miscResults.first().getEventDate();
                limit = sensorDate;
            }

            RealmResults<PumpHistoryCGM> cgmResults = historyRealm
                    .where(PumpHistoryCGM.class)
                    .greaterThanOrEqualTo("eventDate", limit)
                    .equalTo("history", true)
                    .sort("eventDate", Sort.DESCENDING)
                    .findAll();
            if (cgmResults.size() > 0) {
                eventDate = cgmResults.first().getEventDate();
                int cgmRTC = cgmResults.first().getCgmRTC() - 60;
                int pos = 0;
                while (pos < max && pos < cgmResults.size()) {
                    if (cgmResults.get(pos).getCgmRTC() > cgmRTC - (pos * 300)) {
                        isig.add(cgmResults.get(pos).getIsig());
                        delta.add(0.0);
                        if (pos > 0) delta.set(pos - 1, isig.get(pos - 1) - isig.get(pos));
                    }
                    pos++;
                }
            } else {
                // no current cgm event, set the event date to now
                eventDate = new Date(System.currentTimeMillis());
            }
        }

        public void logcat() {
            for (int i = 0; i < isig.size(); i++) {
                Log.d(TAG, String.format("(%s) isig: %s delta %s",
                        i, df.format(isig.get(i)), df.format(delta.get(i))));
            }
        }

        public Date getEventDate() {
            return eventDate;
        }

        public int getIsigSize() {
            return isig.size();
        }

        public void calcGain(int minutes, double k) {

            // Kalman Filter
            // X = K * Z + (1 - K) * X1
            // X = current estimation
            // X1 = previous estimation
            // Z = current measurement
            // K = Kalman gain

            double z = 0;
            double x = 0;
            double x1 = 0;
            double p;
            double p1 = 0;
            double sum = 0;

            period = (minutes / 5);
            if (isig.size() < period) period = isig.size();
            period--;

            if (period > 0) {
                StringBuilder log = new StringBuilder();
                for (int i = 0; i <= period; i++) {
                    p = isig.get(period - i);
                    if (i >= 1) z = p - p1;
                    if (i >= 2) x = (k * z) + (1 - k) * x1;
                    else x = z;
                    p1 = p;
                    x1 = x;
                    sum = sum + z;
                    log.append(String.format("%s=%s %s (%s) ",
                            i, df.format(x), df.format(p), df.format(z)
                    ));
                }
                gain = x;
                average = sum / period;
                Log.d(TAG, String.format("calcGain: minutes: %s gain: %s average: %s calc: %s",
                        (period + 1) * 5, df.format(gain), df.format(average), log.toString()));
            }
        }

        public void formatter(int min, int max) {
            df.setMinimumFractionDigits(min);
            df.setMaximumFractionDigits(max);
        }

        public String formatIsig(int pos) {
            return isig.size() > pos ? df.format(isig.get(pos)) : "";
        }

        public String formatIsig(int pos, String s) {
            if (isig.size() > pos) {
                if (s.length() > 0) s = s + " ";
                s = s + df.format(isig.get(pos));
            }
            return s;
        }

        public String formatDelta(int pos) {
            return delta.size() > pos ? df.format(delta.get(pos)) : "";
        }

        public String formatGain() {
            return df.format(gain);
        }

        public String formatROC5min() {
            return df.format(delta.size() > 0 ? delta.get(0) : 0);
        }

        public String formatROC10min() {
            return df.format((delta.size() > 0 ? delta.get(0) : 0)
                    + (delta.size() > 1 ? delta.get(1) : 0));
        }
        public String formatStability() {
            if (isig.size() < 3) return "";
            if (Math.abs(gain) >= 2) return FormatKit.getInstance().getString(R.string.ul_isig__unstable);
            if (gain >= 0.8) return FormatKit.getInstance().getString(R.string.ul_isig__rising_fast);
            if (gain >= 0.3) return FormatKit.getInstance().getString(R.string.ul_isig__rising);
            if (gain <= -0.8) return FormatKit.getInstance().getString(R.string.ul_isig__falling_fast);
            if (gain <= -0.3) return FormatKit.getInstance().getString(R.string.ul_isig__falling);
            return FormatKit.getInstance().getString(R.string.ul_isig__steady);
        }

        public int formatStabilityAsId() {
            if (Math.abs(gain) >= 2) return R.string.ul_isig__unstable;
            if (gain >= 0.8) return R.string.ul_isig__rising_fast;
            if (gain >= 0.3) return R.string.ul_isig__rising;
            if (gain <= -0.8) return R.string.ul_isig__falling_fast;
            if (gain <= -0.3) return R.string.ul_isig__falling;
            return R.string.ul_isig__steady;
        }

        public String formatStabilityAsSymbol() {
            if (isig.size() < 3) return "";
            if (Math.abs(gain) >= 2) return "+-";
            if (gain >= 0.8) return "++";
            if (gain >= 0.3) return "+";
            if (gain <= -0.8) return "--";
            if (gain <= -0.3) return "-";
            if (Math.abs(gain) >= 0.1) return "=";
            return "==";
        }

    }

    public void isigAvailable() {

        if (dataStore.isRequestEstimate()) {
            storeRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {
                    dataStore.setRequestEstimate(false);
                }
            });

            new Estimate().setLimit(10 * 24 * 60 * 60000L).setUserlog(true).updateEstimate();
            systemEventEstimateIsActive();
        }

        if (dataStore.isRequestIsig()) {
            storeRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {
                    dataStore.setRequestIsig(false);
                    dataStore.setReportIsigAvailable(true);
                }
            });
            isigUserlog();
        }
    }

    private void systemEventEstimateIsActive() {
        // get the last non-estimated cgm reading
        RealmResults<PumpHistoryCGM> results = historyRealm
                .where(PumpHistoryCGM.class)
                .equalTo("estimate", false)
                .sort("eventDate", Sort.DESCENDING)
                .findAll();
        if (results.size() > 0) {
            Date date = new Date(results.first().getEventDate().getTime() + POLL_PERIOD_MS);
            int rtc = results.first().getCgmRTC() + (int) (POLL_PERIOD_MS / 1000L);
            Log.d(TAG, String.format("Estimate: active start date %s", date));
            systemEvent(PumpHistorySystem.STATUS.ESTIMATE_ACTIVE)
                    .key(rtc)
                    .start(date)
                    .process();
        }
    }

    public void isigUserlog() {

        IsigReport isigReport = new IsigReport();

        if (isigReport.getIsigSize() > 0) {
            UserLogMessage.send(mContext, UserLogMessage.TYPE.ISIG, String.format("{id;%s}: %s %s %s %s %s",
                    R.string.ul_isig__isig,
                    isigReport.formatIsig(0),
                    isigReport.formatIsig(1),
                    isigReport.formatIsig(2),
                    isigReport.formatIsig(3),
                    isigReport.formatIsig(4)
            ));
        }

        if (isigReport.getIsigSize() > 2) {
            UserLogMessage.send(mContext, UserLogMessage.TYPE.ISIG, String.format("{id;%s}: %s\\%s %s\\%s %s\\k",
                    isigReport.formatStabilityAsId(),
                    isigReport.formatROC5min(),
                    FormatKit.getInstance().formatMinutesAsM(5),
                    isigReport.formatROC10min(),
                    FormatKit.getInstance().formatMinutesAsM(10),
                    isigReport.formatGain()
            ));
        }
    }

    // extra info for the nightscout pump status pill
    // when the experimental features are in use
    public ExtraInfo getExtraInfo() {
        ExtraInfo extraInfo = null;

        if (dataStore.isReportIsigAvailable()) {
            extraInfo = new ExtraInfo();

            IsigReport isigReport = new IsigReport();
            isigReport.formatter(1, 1);

            extraInfo.eventDate = isigReport.getEventDate();
            extraInfo.info = isigReport.formatStabilityAsSymbol();
            extraInfo.infoShort = extraInfo.info;

            int include = dataStore.getSysReportISIGinclude();
            for (int pos = 0; pos < include; pos++) {
                extraInfo.info = isigReport.formatIsig(pos,  extraInfo.info);
                if (pos < 3) extraInfo.infoShort =  extraInfo.info;
            }

        } else if (dataStore.isSysEnableEstimateSGV()) {

            RealmResults<PumpHistoryCGM> cgmResults = historyRealm
                    .where(PumpHistoryCGM.class)
                    .sort("eventDate", Sort.ASCENDING)
                    .findAll();
            if (cgmResults.size() > 0 && cgmResults.last().isEstimate()) {
                extraInfo = new ExtraInfo();
                extraInfo.eventDate = cgmResults.last().getEventDate();
                extraInfo.info = FormatKit.getInstance().getString(R.string.nightscout_pump_pill__estimate);
                extraInfo.infoShort = FormatKit.getInstance().getString(R.string.nightscout_pump_pill__estimate_abreviation);
            }
        }

        return extraInfo;
    }

    public class ExtraInfo {
        private Date eventDate;
        private String info;
        private String infoShort;

        public Date getEventDate() {
            return eventDate;
        }

        public String getInfo() {
            return info;
        }

        public String getInfoShort() {
            return infoShort;
        }
    }

    public boolean update(MedtronicCnlReader cnlReader) throws EncryptionException, IOException, ChecksumException, TimeoutException, UnexpectedMessageException, IntegrityException {

        boolean limited = false;

        long last = historyRecency();
        boolean recent = last >= 0 && last <= 24 * 60 * 60000L;

        if (dataStore.isRequestProfile()
                || !isProfileInHistory()) {
            readProfile(cnlReader);
            storeRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {
                    dataStore.setRequestProfile(false);
                }
            });
        }

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
            if (dataStore.isSysEnableCgmHistory())
                limited |= updateHistorySegments(cnlReader, dataStore.getSysCgmHistoryDays(), oldest, newest, HISTORY_CGM, true, "CGM history:", R.string.ul_history__cgm_history);

            // process sgv estimate / isig now as cgm data is available, avoids any comms errors after this
            isigAvailable();

            // first run has a tendency for the pump to be busy and cause a comms error
            // only do a single pass when no recent history
            if (recent) {
                // clear the request flag now as segment marker will get added
                storeRealm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(@NonNull Realm realm) {
                        dataStore.setRequestPumpHistory(false);
                    }
                });
                if (dataStore.isSysEnablePumpHistory())
                    limited |= updateHistorySegments(cnlReader, dataStore.getSysPumpHistoryDays(), oldest, newest, HISTORY_PUMP, pullPUMP, "PUMP history:", R.string.ul_history__pump_history);
            }
            else limited = dataStore.isSysEnablePumpHistory();

        } else {

            // clear the request flag now as segment marker will get added
            storeRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {
                    dataStore.setRequestPumpHistory(false);
                }
            });
            if (dataStore.isSysEnablePumpHistory())
                limited |= updateHistorySegments(cnlReader, dataStore.getSysPumpHistoryDays(), oldest, newest, HISTORY_PUMP, pullPUMP, "PUMP history:", R.string.ul_history__pump_history);

            // first run has a tendency for the pump to be busy and cause a comms error
            // only do a single pass when no recent history
            if (recent) {
                // clear the request flag now as segment marker will get added
                storeRealm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(@NonNull Realm realm) {
                        dataStore.setRequestCgmHistory(false);
                    }
                });
                if (dataStore.isSysEnableCgmHistory())
                    limited |= updateHistorySegments(cnlReader, dataStore.getSysCgmHistoryDays(), oldest, newest, HISTORY_CGM, false, "CGM history:", R.string.ul_history__cgm_history);
            }
            else limited = dataStore.isSysEnableCgmHistory();

        }

        records();
        return limited;
    }

    private boolean updateHistorySegments(MedtronicCnlReader cnlReader, int days, final long oldest, final long newest, final byte historyType, boolean pullHistory, String logTAG, int userlogTAG)
            throws EncryptionException, IOException, ChecksumException, TimeoutException, UnexpectedMessageException, IntegrityException {

        boolean limited = false;

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

            logSegments(logTAG, segment);

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
                return false;
            }

            // per request limiter
            if (end - start > HISTORY_REQUEST_LIMITER_MS) {
                start = end - HISTORY_REQUEST_LIMITER_MS;
                limited = true;
            }

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
                    String.format("{id;%s}: {id;%s}", userlogTAG, R.string.ul_history__requested));
            UserLogMessage.sendE(mContext, UserLogMessage.TYPE.REQUESTED,
                    String.format("{id;%s}: {id;%s}\n   {time.hist.e;%s} - {time.hist.e;%s}",
                            userlogTAG, R.string.ul_history__requested, start, end));

            Date[] range;
            ReadHistoryResponseMessage response = cnlReader.getHistory(start, end, historyType);
            if (response == null) {
                // no history data for period, will update the segment data using the requested start/end dates
                range = new Date[] {new Date(start), new Date(end)};
            } else {

                // for efficiency limit the parse time range
                // the pump sends large periods of data and for recent cgm backfill or automode microbolus updates,
                // these can have a lot of already processed items that would otherwise need to be checked and discarded
                long parseFrom = 0;
                if (segment.get(0).getFromDate().getTime() == segment.get(0).getToDate().getTime()
                        && segment.get(1).getFromDate().getTime() != segment.get(1).getToDate().getTime())
                    parseFrom = segment.get(1).getToDate().getTime() - 30 * 60000L;

                long timer = System.currentTimeMillis();
                range = new PumpHistoryParser(response.getEventData()).process(
                        pumpHistorySender,
                        cnlReader.getPumpSession().getPumpMAC(),
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
                    String.format("{id;%s}: {id;%s}\n   {time.hist.e;%s} - {time.hist.e;%s}",
                            userlogTAG, R.string.ul_history__received,
                            range[0] == null ? 0 : range[0].getTime(),
                            range[1] == null ? 0 : range[1].getTime()));

            final Date haveFrom = range[0] != null && range[0].getTime() < start ? range[0] : new Date(start);
            final Date haveTo = range[1] != null && range[1].getTime() > haveFrom.getTime() ? range[1] : new Date(end);

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

        logSegments(logTAG, segment);
        return limited;
    }

    private void logSegments(String logTAG, RealmResults<HistorySegment> segment) {
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
