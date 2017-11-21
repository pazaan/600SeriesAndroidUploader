package info.nightscout.android.medtronic;

import android.content.Intent;
import android.util.Log;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
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
import info.nightscout.android.model.medtronicNg.PumpHistoryInterface;
import info.nightscout.android.model.medtronicNg.PumpHistoryMisc;
import info.nightscout.android.model.medtronicNg.PumpHistoryProfile;
import info.nightscout.android.model.medtronicNg.PumpHistorySegment;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.utils.DataStore;
import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;

import static info.nightscout.android.medtronic.service.MasterService.ICON_REFRESH;

/**
 * Created by John on 5.11.17.
 */

public class PumpHistoryHandler {
    private static final String TAG = PumpHistoryHandler.class.getSimpleName();

    public final static long HISTORY_STALE_MS = 90 * 24 * 60 * 60000L;
    public final static long HISTORY_SYNC_LIMITER_MS =  30 * 24 * 60 * 60000L;
    public final static long HISTORY_REQUEST_LIMITER_MS =  1 * 24 * 60 * 60000L;

    public final static byte HISTORY_PUMP = 2;
    public final static byte HISTORY_CGM = 3;

    private Realm realm;
    private Realm historyRealm;
    private DataStore dataStore;

    private List historyDB;
    private List<PumpHistoryInterface> uploadRecords;

    DateFormat dateFormatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US);

    public PumpHistoryHandler() {
        Log.d(TAG,"initialise history handler");

        realm = Realm.getDefaultInstance();
        dataStore = realm.where(DataStore.class).findFirst();

        historyRealm = Realm.getInstance(UploaderApplication.getHistoryConfiguration());

        historyDB = new ArrayList();
        historyDB.add("CGM");
        historyDB.add(400); // upload limiter
        historyDB.add(historyRealm.where(PumpHistoryCGM.class).findAll());
        historyDB.add("BOLUS");
        historyDB.add(20); // upload limiter
        historyDB.add(historyRealm.where(PumpHistoryBolus.class).findAll());
        historyDB.add("BASAL");
        historyDB.add(20); // upload limiter
        historyDB.add(historyRealm.where(PumpHistoryBasal.class).findAll());
        historyDB.add("BG");
        historyDB.add(20); // upload limiter
        historyDB.add(historyRealm.where(PumpHistoryBG.class).findAll());
        historyDB.add("PROFILE");
        historyDB.add(20); // upload limiter
        historyDB.add(historyRealm.where(PumpHistoryProfile.class).findAll());
        historyDB.add("MISC");
        historyDB.add(20); // upload limiter
        historyDB.add(historyRealm.where(PumpHistoryMisc.class).findAll());
    }

    public void close() {
        Log.d(TAG,"close history handler");
        if (realm != null && !realm.isClosed()) realm.close();
        if (historyRealm != null && !historyRealm.isClosed()) historyRealm.close();
    }

    protected void sendStatus(String message) {
        try {
            Intent intent =
                    new Intent(MasterService.Constants.ACTION_STATUS_MESSAGE)
                            .putExtra(MasterService.Constants.EXTENDED_DATA, message);
            UploaderApplication.getAppContext().sendBroadcast(intent);
        } catch (Exception e) {
        }
    }

    public void records() {
        RealmResults<PumpHistoryInterface> results;
        String type;

        Iterator iterator = historyDB.iterator();

        while (iterator.hasNext()) {
            type = (String) iterator.next();
            iterator.next();
            results = (RealmResults) iterator.next();
            Log.d(TAG, type + " records: " + results.size() + (results.size() > 0 ? " start: " + dateFormatter.format(results.first().getEventDate()) + " end: " + dateFormatter.format(results.last().getEventDate()) : ""));
        }
    }

    public void stale(Date oldest) {
        RealmResults<PumpHistoryInterface> results;
        String type;
        int count = 0;

        Iterator iterator = historyDB.iterator();

        while (iterator.hasNext()) {
            type = (String) iterator.next();
            iterator.next();
            results = (RealmResults) iterator.next();
            final RealmResults stale = results.where().lessThan("eventDate", oldest).findAll();
            if (stale.size() > 0) {
                Log.d(TAG, type + " deleting " + results.size() + " records from realm");
                count += results.size();
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
        RealmResults<PumpHistoryInterface> results;
        String type;
        int limit;
        int count;

        uploadRecords = new ArrayList<>();

        Iterator iterator = historyDB.iterator();

        while (iterator.hasNext()) {
            type = (String) iterator.next();
            limit = (int) iterator.next();
            results = (RealmResults) iterator.next();
            count = 0;
            for (PumpHistoryInterface record : results.where().equalTo("uploadREQ", true).findAllSorted("eventDate", Sort.DESCENDING)) {
                if (++count <= limit) uploadRecords.add(record);
            }
            Log.d(TAG, type + " records to upload: " + count + " limit: " + limit);
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

    public void profile(MedtronicCnlReader cnlReader) throws EncryptionException, IOException, ChecksumException, TimeoutException, UnexpectedMessageException {

        sendStatus("Reading pump basal patterns");
        byte[] basalPatterns = cnlReader.getBasalPatterns();
        sendStatus("Reading pump carb ratios");
        byte[] carbRatios = cnlReader.getBolusWizardCarbRatios();
        sendStatus("Reading pump sensitivity factors");
        byte[] sensitivity = cnlReader.getBolusWizardSensitivity();
        sendStatus("Reading pump hi-lo targets");
        byte[] targets = cnlReader.getBolusWizardTargets();

        historyRealm.beginTransaction();

        PumpHistoryProfile.profile(historyRealm, cnlReader.getSessionDate(), cnlReader.getSessionRTC(), cnlReader.getSessionOFFSET(),
                basalPatterns,
                carbRatios,
                sensitivity,
                targets
        );

        historyRealm.commitTransaction();
    }

    public void cgm(PumpStatusEvent pumpRecord) throws EncryptionException, IOException, ChecksumException, TimeoutException, UnexpectedMessageException {

        boolean pullCGM = false;

        final RealmResults<PumpHistoryCGM> results = historyRealm
                .where(PumpHistoryCGM.class)
                .findAllSorted("eventDate", Sort.ASCENDING);
        if (results.size() == 0) pullCGM = true;

        // push the current sgv from status (always have latest sgv available even if there are comms errors after this)
        if (pumpRecord.isValidSGV()) {

            Date date = pumpRecord.getCgmDate();
            int rtc = pumpRecord.getCgmRTC();
            int offset = pumpRecord.getCgmOFFSET();

            // sgv is available do we need the backfill?
            if (results.size() > 0 && date.getTime() - results.last().getEventDate().getTime() > 9 * 60 * 1000)
                pullCGM = true;

            historyRealm.beginTransaction();

            PumpHistoryCGM.cgm(historyRealm, date, rtc, offset,
                    pumpRecord.getSgv(),
                    pumpRecord.getCgmTrendString()
            );

            historyRealm.commitTransaction();
        }

        if (pullCGM) {
            sendStatus(ICON_REFRESH + "history: cgm backfill");

            realm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    dataStore.setRequestCgmHistory(true);
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
            realm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    dataStore.setRequestCgmHistory(false);
                }
            });
            updateHistorySegments(cnlReader, historyRealm, oldest, newest, HISTORY_CGM, pullCGM, "CGM history: ");

            // clear the request flag now as segment marker will get added
            realm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    dataStore.setRequestPumpHistory(false);
                }
            });
            updateHistorySegments(cnlReader, historyRealm, oldest, newest, HISTORY_PUMP, pullPUMP, "PUMP history: ");

        } else {

            // clear the request flag now as segment marker will get added
            realm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    dataStore.setRequestPumpHistory(false);
                }
            });
            updateHistorySegments(cnlReader, historyRealm, oldest, newest, HISTORY_PUMP, pullPUMP, "PUMP history: ");

            // clear the request flag now as segment marker will get added
            realm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    dataStore.setRequestCgmHistory(false);
                }
            });
            updateHistorySegments(cnlReader, historyRealm, oldest, newest, HISTORY_CGM, pullCGM, "CGM history: ");
        }

        records();
    }

    private void updateHistorySegments(MedtronicCnlReader cnlReader, final Realm historyRealm, final long oldest, final long newest, final byte historyType, boolean pullHistory, String historyTAG)
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

            // sync limiter
            if (now - start > HISTORY_SYNC_LIMITER_MS)
                start = now - HISTORY_SYNC_LIMITER_MS;

            if (end - start < 0) {
                Log.d(TAG, historyTAG + "sync limit reached, min date: " + dateFormatter.format(now - HISTORY_SYNC_LIMITER_MS) + " max days: " + (HISTORY_SYNC_LIMITER_MS / (24 * 60 * 60000L)));
                return;
            }

            // per request limiter
            if (end - start > HISTORY_REQUEST_LIMITER_MS)
                start = end - HISTORY_REQUEST_LIMITER_MS;

            Log.d(TAG, historyTAG + "requested " + dateFormatter.format(start) + " - " + dateFormatter.format(end));
            sendStatus(historyTAG + "requested \n      " + dateFormatter.format(start) + " - " + dateFormatter.format(end));

            Date[] range = cnlReader.getHistory(start, end, historyType);

            Log.d(TAG, historyTAG + "received  " + (range[0] == null ? "null" : dateFormatter.format(range[0])) + " - " + (range[1] == null ? "null" : dateFormatter.format(range[1])));
            sendStatus(historyTAG + "received \n      " + (range[0] == null ? "null" : dateFormatter.format(range[0])) + " - " + (range[1] == null ? "null" : dateFormatter.format(range[1])));

            final Date pulledFrom = range[0];
            final Date pulledTo = range[1];
/*
            // update segment toDate as there may be more or less available then we requested
            if (pulledTo.getTime() > segment.get(0).getToDate().getTime()) {
                // update segment toDate
                historyRealm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(Realm realm) {
                        segment.get(0).setToDate(pulledTo);
                    }
                });
            }
*/
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