package info.nightscout.android.medtronic;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.UploaderApplication;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;
import info.nightscout.android.medtronic.service.MasterService;
import info.nightscout.android.model.medtronicNg.PumpHistoryBG;
import info.nightscout.android.model.medtronicNg.PumpHistoryBasal;
import info.nightscout.android.model.medtronicNg.PumpHistoryBolus;
import info.nightscout.android.model.medtronicNg.PumpHistoryCGM;
import info.nightscout.android.model.medtronicNg.PumpHistoryLoop;
import info.nightscout.android.model.medtronicNg.PumpHistoryDebug;
import info.nightscout.android.model.medtronicNg.PumpHistoryInterface;
import info.nightscout.android.model.medtronicNg.PumpHistoryMisc;
import info.nightscout.android.model.medtronicNg.PumpHistoryPattern;
import info.nightscout.android.model.medtronicNg.PumpHistoryProfile;
import info.nightscout.android.model.medtronicNg.PumpHistorySegment;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.model.store.DataStore;
import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;

import static info.nightscout.android.medtronic.UserLogMessage.Icons.ICON_REFRESH;

/**
 * Created by Pogman on 5.11.17.
 */

public class PumpHistoryHandler {
    private static final String TAG = PumpHistoryHandler.class.getSimpleName();

    private final static long HISTORY_STALE_MS = 120 * 24 * 60 * 60000L;
    private final static long HISTORY_REQUEST_LIMITER_MS = 36 * 60 * 60000L;
    //private final static long HISTORY_SYNC_LIMITER_MS =  10 * 24 * 60 * 60000L;

    private final static byte HISTORY_PUMP = 2;
    private final static byte HISTORY_CGM = 3;

    private Context mContext;

    private Realm realm;
    private Realm historyRealm;
    private Realm storeRealm;
    private DataStore dataStore;

    private List<DBitem> historyDB;
    private List<PumpHistoryInterface> uploadRecords;

    private DateFormat dateFormatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US);

    public PumpHistoryHandler(Context context) {
        Log.d(TAG, "initialise history handler");

        mContext = context;

        realm = Realm.getDefaultInstance();
        storeRealm = Realm.getInstance(UploaderApplication.getStoreConfiguration());
        dataStore = storeRealm.where(DataStore.class).findFirst();

        historyRealm = Realm.getInstance(UploaderApplication.getHistoryConfiguration());

        historyDB = new ArrayList<>();
        historyDB.add(new DBitem("CGM", false, 300, historyRealm.where(PumpHistoryCGM.class).findAll()));
        historyDB.add(new DBitem("BOLUS", true, 20, historyRealm.where(PumpHistoryBolus.class).findAll()));
        historyDB.add(new DBitem("BASAL", true,20, historyRealm.where(PumpHistoryBasal.class).findAll()));
        historyDB.add(new DBitem("PATTERN", true,10, historyRealm.where(PumpHistoryPattern.class).findAll()));
        historyDB.add(new DBitem("BG", true,20, historyRealm.where(PumpHistoryBG.class).findAll()));
        historyDB.add(new DBitem("PROFILE", false,10, historyRealm.where(PumpHistoryProfile.class).findAll()));
        historyDB.add(new DBitem("MISC", true,10, historyRealm.where(PumpHistoryMisc.class).findAll()));
        historyDB.add(new DBitem("LOOP", true,200, historyRealm.where(PumpHistoryLoop.class).findAll()));
        historyDB.add(new DBitem("DEBUG", true,20, historyRealm.where(PumpHistoryDebug.class).findAll()));
    }

    private class DBitem {
        String name; // name for logging
        int limiter; // upload limiter
        boolean careportal; // requires careportal and treatments
        RealmResults<PumpHistoryInterface> results;

        DBitem (String name, boolean careportal, int limiter, RealmResults results) {
            this.name = name;
            this.careportal = careportal;
            this.limiter = limiter;
            //this.limiter = 5000; // debug use only
            this.results = results;
        }
    }

    public void close() {
        Log.d(TAG,"close history handler");
        if (realm != null && !realm.isClosed()) realm.close();
        if (storeRealm != null && !storeRealm.isClosed()) storeRealm.close();
        if (historyRealm != null && !historyRealm.isClosed()) historyRealm.close();
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

    public void records() {
        for (DBitem dBitem : historyDB) {
            Log.d(TAG, dBitem.name + " records: " + dBitem.results.size() + (dBitem.results.size() > 0 ? " start: " + dateFormatter.format(dBitem.results.first().getEventDate()) + " end: " + dateFormatter.format(dBitem.results.last().getEventDate()) : ""));
        }
    }

    public void stale(Date oldest) {
        int count = 0;

        for (DBitem dBitem : historyDB) {

            final RealmResults stale = dBitem.results.where().lessThan("eventDate", oldest).findAll();

            if (stale.size() > 0) {
                Log.d(TAG, dBitem.name + " deleting " + stale.size() + " records from realm");
                count += stale.size();
                historyRealm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(Realm realm) {
                        stale.deleteAllFromRealm();
                    }
                });
            }
        }
        Log.d(TAG, "stale date: " + dateFormatter.format(oldest) + " deleted: " + count + " history records");
    }

    public List uploadREQ() {
        int count;

        Date limitDate;
        if (dataStore.isNsEnableHistorySync())
            limitDate = new Date(System.currentTimeMillis() - (180 * 24 * 60 * 60000L));
        else
            limitDate = dataStore.getNightscoutLimitDate();
        Log.d(TAG, "Nightscout upload limitdate: " + dateFormatter.format(limitDate));

        uploadRecords = new ArrayList<>();

        for (DBitem dBitem : historyDB) {
            if (!dBitem.careportal || (dataStore.isNightscoutCareportal() && dataStore.isNsEnableTreatments())) {
                count = 0;
                for (PumpHistoryInterface record : dBitem.results.where()
                        .equalTo("uploadREQ", true)
                        .greaterThanOrEqualTo("eventDate", limitDate)
                        .findAllSorted("eventDate", Sort.DESCENDING)) {
                    if (++count <= dBitem.limiter) uploadRecords.add(record);
                }
                Log.d(TAG, dBitem.name + " records to upload: " + count + " limit: " + dBitem.limiter);
            } else
                Log.d(TAG, dBitem.name + " records ignored, careportal=" + dataStore.isNightscoutCareportal() + " treatments=" + dataStore.isNsEnableTreatments());
        }

        Log.d(TAG, "total records to upload: " + uploadRecords.size());
        return uploadRecords;
    }

    public void uploadACK() {
        historyRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                for (PumpHistoryInterface record : uploadRecords) {
                    record.setUploadACK(true);
                    record.setUploadREQ(false);
                }
            }
        });
    }

    public void debugNote(final Date eventDate, final String note) {
        historyRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                PumpHistoryDebug.note(historyRealm, eventDate, note);
            }
        });
    }

    public Date debugNoteLastDate() {
        RealmResults<PumpHistoryDebug> results = historyRealm
                .where(PumpHistoryDebug.class)
                .findAllSorted("eventDate", Sort.DESCENDING);
        if (results.size() > 0) return results.first().getEventDate();
        return null;
    }

    public boolean isLoopActive() {
        long now = System.currentTimeMillis();

        RealmResults<PumpHistoryLoop> results = historyRealm
                .where(PumpHistoryLoop.class)
                .greaterThan("eventDate", new Date(now - 6 * 60 * 60000L))
                .findAllSorted("eventDate", Sort.DESCENDING);

        return results.size() > 0 && results.first().isLoopActive();
    }

    // loop is active or has activation potential due to use during timeframe
    public boolean isLoopActivePotential() {
        long now = System.currentTimeMillis();

        RealmResults<PumpHistoryLoop> results = historyRealm
                .where(PumpHistoryLoop.class)
                .greaterThan("eventDate", new Date(now - 6 * 60 * 60000L))
                .equalTo("loopActive", true)
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

        RealmResults<PumpHistorySegment> results = historyRealm
                .where(PumpHistorySegment.class)
                .equalTo("historyType", HISTORY_PUMP)
                .findAllSorted("toDate", Sort.DESCENDING);

        if (results.size() > 0) return System.currentTimeMillis() - results.first().getToDate().getTime();

        return -1;
    }

    public void profile(final MedtronicCnlReader cnlReader) throws EncryptionException, IOException, ChecksumException, TimeoutException, UnexpectedMessageException {

        userLogMessage("Reading pump basal patterns");
        final byte[] basalPatterns = cnlReader.getBasalPatterns();
        userLogMessage("Reading pump carb ratios");
        final byte[] carbRatios = cnlReader.getBolusWizardCarbRatios();
        userLogMessage("Reading pump sensitivity factors");
        final byte[] sensitivity = cnlReader.getBolusWizardSensitivity();
        userLogMessage("Reading pump hi-lo targets");
        final byte[] targets = cnlReader.getBolusWizardTargets();

        historyRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                PumpHistoryProfile.profile(historyRealm, cnlReader.getSessionDate(), cnlReader.getSessionRTC(), cnlReader.getSessionOFFSET(),
                        basalPatterns,
                        carbRatios,
                        sensitivity,
                        targets
                );
            }
        });

        userLogMessage("Sending updated profile to Nightscout");

        // update NS historical treatments when pattern naming has changed

        if (dataStore.isNameBasalPatternChanged() &&
                (dataStore.isNsEnableProfileSingle() || dataStore.isNsEnableProfileOffset())) {

            final RealmResults<PumpHistoryPattern> results = historyRealm
                    .where(PumpHistoryPattern.class)
                    .findAll();

            if (results.size() > 0) {
                Log.d(TAG, "NameBasalPatternChanged: Found " + results.size() + " pattern switch treatments to update");
                userLogMessage("Basal Pattern Names changed, updating pattern switch treatments in Nightscout");

                historyRealm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(Realm realm) {
                        for (PumpHistoryPattern record : results) {
                            record.setUploadREQ(true);
                            record.setUploadACK(true);
                        }
                    }
                });

            }

            storeRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
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
                    .equalTo("carbUnits", PumpHistoryParser.CARB_UNITS.EXCHANGES.get())
                    .findAll();

            if (results.size() > 0) {
                Log.d(TAG, "GramsPerExchangeChanged: Found " + results.size() + " carb/bolus treatments to update");
                userLogMessage("Grams per Exchange changed, updating carb/bolus treatments in Nightscout");

                historyRealm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(Realm realm) {
                        for (PumpHistoryBolus record : results) {
                            record.setUploadREQ(true);
                            record.setUploadACK(true);
                        }
                    }
                });
            }

            storeRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    dataStore.setNsGramsPerExchangeChanged(false);
                }
            });
        }
    }

    public void cgm(final PumpStatusEvent pumpRecord) {

        // push the current sgv from status (always have latest sgv available even if there are comms errors after this)
        if (pumpRecord.isCgmActive()) {

            final Date date = pumpRecord.getCgmDate();
            final int rtc = pumpRecord.getCgmRTC();
            final int offset = pumpRecord.getCgmOFFSET();

            final RealmResults<PumpHistoryCGM> results = historyRealm
                    .where(PumpHistoryCGM.class)
                    .findAllSorted("eventDate", Sort.ASCENDING);

            // cgm is available do we need the backfill?
            if (dataStore.isSysEnableCgmHistory()
                    && results.size() == 0
                    || (results.size() > 0 && date.getTime() - results.last().getEventDate().getTime() > 9 * 60 * 1000L)) {
                userLogMessage(ICON_REFRESH + "history: cgm backfill");
                storeRealm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(Realm realm) {
                        dataStore.setRequestCgmHistory(true);
                    }
                });
            }

            historyRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    PumpHistoryCGM.cgm(historyRealm, date, rtc, offset,
                            pumpRecord.getSgv(),
                            pumpRecord.getCgmExceptionType(),
                            pumpRecord.getCgmTrendString()
                    );
                }
            });
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
                public void execute(Realm realm) {
                    dataStore.setRequestCgmHistory(false);
                }
            });
            if (dataStore.isSysEnableCgmHistory()) updateHistorySegments(cnlReader, dataStore.getSysCgmHistoryDays(), oldest, newest, HISTORY_CGM, true, "CGM history: ");

            // clear the request flag now as segment marker will get added
            storeRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    dataStore.setRequestPumpHistory(false);
                }
            });
            if (dataStore.isSysEnablePumpHistory()) updateHistorySegments(cnlReader, dataStore.getSysPumpHistoryDays(), oldest, newest, HISTORY_PUMP, pullPUMP, "PUMP history: ");

        } else {

            // clear the request flag now as segment marker will get added
            storeRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    dataStore.setRequestPumpHistory(false);
                }
            });
            if (dataStore.isSysEnablePumpHistory()) updateHistorySegments(cnlReader, dataStore.getSysPumpHistoryDays(), oldest, newest, HISTORY_PUMP, pullPUMP, "PUMP history: ");

            // clear the request flag now as segment marker will get added
            storeRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    dataStore.setRequestCgmHistory(false);
                }
            });
            if (dataStore.isSysEnableCgmHistory()) updateHistorySegments(cnlReader, dataStore.getSysCgmHistoryDays(), oldest, newest, HISTORY_CGM, false, "CGM history: ");
        }

        records();
    }

    private void updateHistorySegments(MedtronicCnlReader cnlReader, int days, final long oldest, final long newest, final byte historyType, boolean pullHistory, String historyTAG)
            throws EncryptionException, IOException, ChecksumException, TimeoutException, UnexpectedMessageException {

        final RealmResults<PumpHistorySegment> segment = historyRealm
                .where(PumpHistorySegment.class)
                .equalTo("historyType", historyType)
                .findAllSorted("fromDate", Sort.DESCENDING);

        // add initial segment if needed
        if (segment.size() == 0) {
            pullHistory = true;
            Log.d(TAG, historyTAG + "adding initial segment");
            historyRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    historyRealm.createObject(PumpHistorySegment.class).addSegment(new Date(oldest), historyType);
                }
            });
            // store size changed
        } else if (segment.last().getFromDate().getTime() - oldest > 60 * 60000L) {
            Log.d(TAG, historyTAG + "store size has increased, adding segment");
            historyRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    historyRealm.createObject(PumpHistorySegment.class).addSegment(new Date(oldest), historyType);
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
                        public void execute(Realm realm) {
                            segment.deleteFromRealm(segment.size() - 1);
                        }
                    });
                else checkNext = false;
            }
            // update the oldest segment marker
            historyRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
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
            Log.d(TAG, historyTAG + "adding history pull marker");
            historyRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    historyRealm.createObject(PumpHistorySegment.class).addSegment(new Date(newest), historyType);
                }
            });
        }

        if (segment.size() > 1) {
            for (int i = 0; i < segment.size(); i++) {
                Log.d(TAG, historyTAG + "segments=" + segment.size() + " segment[" + i + "] start= " + dateFormatter.format(segment.get(i).getFromDate()) + " end=" + dateFormatter.format(segment.get(i).getToDate()));
            }

            Date needFrom = segment.get(1).getToDate();
            Date needTo = segment.get(0).getFromDate();

            long now = cnlReader.getSessionDate().getTime();
            long start = needFrom.getTime();
            long end = needTo.getTime();

/*
            // sync limiter
            if (now - start > HISTORY_SYNC_LIMITER_MS)
                start = now - HISTORY_SYNC_LIMITER_MS;
            if (end - start < 0) {
                Log.d(TAG, historyTAG + "sync limit reached, min date: " + dateFormatter.format(now - HISTORY_SYNC_LIMITER_MS) + " max days: " + (HISTORY_SYNC_LIMITER_MS / (24 * 60 * 60000L)));
                return;
            }
*/
            // sync limiter
            long daysMS = days * 24 * 60 * 60000L;
            if (now - start > daysMS)
                start = now - daysMS;
            if (end - start < 0) {
                Log.d(TAG, historyTAG + "sync limit reached, min date: " + dateFormatter.format(now - daysMS) + " max days: " + days);
                return;
            }

            // per request limiter
            if (end - start > HISTORY_REQUEST_LIMITER_MS)
                start = end - HISTORY_REQUEST_LIMITER_MS;

            Log.d(TAG, historyTAG + "requested " + dateFormatter.format(start) + " - " + dateFormatter.format(end));
            userLogMessage(historyTAG + "requested \n      " + dateFormatter.format(start) + " - " + dateFormatter.format(end));

            Date[] range = cnlReader.getHistory(start, end, historyType);

            Log.d(TAG, historyTAG + "received  " + (range[0] == null ? "null" : dateFormatter.format(range[0])) + " - " + (range[1] == null ? "null" : dateFormatter.format(range[1])));
            if (dataStore.isDbgEnableExtendedErrors())
                userLogMessage(historyTAG + "received \n      " + (range[0] == null ? "null" : dateFormatter.format(range[0])) + " - " + (range[1] == null ? "null" : dateFormatter.format(range[1])));

            final Date pulledFrom = range[0];

            if (pulledFrom.getTime() > segment.get(1).getToDate().getTime()) {
                // update the segment fromDate, we still need more history for this segment
                historyRealm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(Realm realm) {
                        segment.get(0).setFromDate(pulledFrom);
                    }
                });
            } else {
                // segments now overlap, combine to single segment
                historyRealm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(Realm realm) {
                        segment.get(1).setToDate(segment.get(0).getToDate());
                        segment.deleteFromRealm(0);
                    }
                });
                // check if any remaining segments need combining or deleting
                boolean checkNext = true;
                while (checkNext && segment.size() > 1) {
                    // delete next segment if not needed as we have the events from recent pull
                    if (segment.get(1).getFromDate().getTime() > pulledFrom.getTime()) {
                        historyRealm.executeTransaction(new Realm.Transaction() {
                            @Override
                            public void execute(Realm realm) {
                                segment.deleteFromRealm(1);
                            }
                        });
                    }
                    // combine segments if needed
                    else {
                        checkNext = false;
                        if (segment.get(1).getToDate().getTime() > pulledFrom.getTime()) {
                            historyRealm.executeTransaction(new Realm.Transaction() {
                                @Override
                                public void execute(Realm realm) {
                                    segment.get(1).setToDate(segment.get(0).getToDate());
                                    segment.deleteFromRealm(0);
                                }
                            });
                        }
                    }
                }
                // finally update segment fromDate if needed
                if (segment.get(0).getFromDate().getTime() > pulledFrom.getTime()) {
                    historyRealm.executeTransaction(new Realm.Transaction() {
                        @Override
                        public void execute(Realm realm) {
                            segment.get(0).setFromDate(pulledFrom);
                        }
                    });
                }
            }

        }

        for (int i = 0; i < segment.size(); i++) {
            Log.d(TAG, historyTAG + "segments=" + segment.size() + " segment[" + i + "] start= " + dateFormatter.format(segment.get(i).getFromDate()) + " end=" + dateFormatter.format(segment.get(i).getToDate()));
        }
    }

}
