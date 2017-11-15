package info.nightscout.android.medtronic;

import android.content.Intent;
import android.util.Log;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
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
import info.nightscout.android.model.medtronicNg.PumpHistoryMisc;
import info.nightscout.android.model.medtronicNg.PumpHistoryProfile;
import info.nightscout.android.model.medtronicNg.PumpHistorySegment;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;

import static info.nightscout.android.medtronic.service.MedtronicCnlService.pumpRTC;

/**
 * Created by John on 5.11.17.
 */

public class PumpHistoryHandler {
    private static final String TAG = PumpHistoryHandler.class.getSimpleName();

    public final static long HISTORY_STALE_MS = 30 * 24 * 60 * 60 * 1000L;
//    public final static long HISTORY_PULL_MS =  2 * 24 * 60 * 60 * 1000L;

//    public final static long HISTORY_STALE_MS = 3 * 24 * 60 * 60 * 1000L;
    public final static long HISTORY_PULL_MS =  1 * 24 * 60 * 60 * 1000L;

    public final static byte HISTORY_PUMP = 2;
    public final static byte HISTORY_CGM = 3;

    private long pumpEvent;
    private int pumpOFFSET;

    private boolean pullPump = false;

    private Realm historyRealm;

    public PumpHistoryHandler() {
        Log.d(TAG,"initialise new handler");
        historyRealm = Realm.getInstance(UploaderApplication.getHistoryConfiguration());
    }

    public void close() {
        if (historyRealm != null && !historyRealm.isClosed()) historyRealm.close();
    }

    public void setPumpEvent(long pumpEvent) {
        this.pumpEvent = pumpEvent;
    }

    public void setPumpOFFSET(int pumpOFFSET) {
        this.pumpOFFSET = pumpOFFSET;
    }

    public void setPullPump(boolean pullPump) {
        this.pullPump = pullPump;
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

        PumpHistoryProfile.profile(historyRealm, new Date(pumpEvent), pumpRTC, pumpOFFSET,
                basalPatterns,
                carbRatios,
                sensitivity,
                targets
        );

        historyRealm.commitTransaction();

    }

    public void history(PumpStatusEvent pumpRecord, MedtronicCnlReader cnlReader) throws EncryptionException, IOException, ChecksumException, TimeoutException, UnexpectedMessageException {
//        final long newest = System.currentTimeMillis();
        final long newest = pumpEvent;
        final long oldest = newest - HISTORY_STALE_MS;

        Date staleDate = new Date(oldest);
        PumpHistoryCGM.stale(historyRealm, staleDate);
        PumpHistoryBolus.stale(historyRealm, staleDate);
        PumpHistoryBasal.stale(historyRealm, staleDate);
        PumpHistoryBG.stale(historyRealm, staleDate);
        PumpHistoryMisc.stale(historyRealm, staleDate);
        PumpHistoryProfile.stale(historyRealm, staleDate);

        boolean pullCGM = false;

        // push the current sgv from status (always have latest sgv available even if there are comms errors after this)
        if (pumpRecord.isValidSGV()) {

            int sgv = pumpRecord.getSgv();
            int rtc = pumpRecord.getCgmRTC();
            int offset = pumpRecord.getCgmOFFSET();
            Date date = pumpRecord.getCgmDate();
            String trend = pumpRecord.getCgmTrendString();

            // sgv is available do we need the backfill?
            final RealmResults<PumpHistoryCGM> results = historyRealm
                    .where(PumpHistoryCGM.class)
                    .findAllSorted("eventDate", Sort.ASCENDING);
            if (results.size() == 0) {
                pullCGM = true;
            } else if (date.getTime() - results.last().getEventDate().getTime() > 9 * 60 * 1000) {
                pullCGM = true;
            }

            Log.d(TAG, "adding status SGV event to PumpHistoryCGM");

            String key = "CGM" + String.format("%08X", rtc);

            final PumpHistoryCGM object = new PumpHistoryCGM();
            object.setKey(key);
            object.setSgv(sgv);
            object.setCgmTrend(trend);
            object.setEventRTC(rtc);
            object.setEventOFFSET(offset);
            object.setEventDate(date);
            object.setUploadREQ(true);

            historyRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    realm.copyToRealm(object);
                }
            });
        }

        // if CGM backfill is needed pull that first as pump can be busy after history pulls
        if (pullCGM) {
            updateHistrorySegments(cnlReader, historyRealm, oldest, newest, HISTORY_CGM, pullCGM, "CGM history: ");
            updateHistrorySegments(cnlReader, historyRealm, oldest, newest, HISTORY_PUMP, pullPump, "PUMP history: ");
        } else {
            updateHistrorySegments(cnlReader, historyRealm, oldest, newest, HISTORY_PUMP, pullPump, "PUMP history: ");
            updateHistrorySegments(cnlReader, historyRealm, oldest, newest, HISTORY_CGM, pullCGM, "CGM history: ");
        }

        PumpHistoryCGM.records(historyRealm);
        PumpHistoryBolus.records(historyRealm);
        PumpHistoryBasal.records(historyRealm);
        PumpHistoryBG.records(historyRealm);
        PumpHistoryMisc.records(historyRealm);
        PumpHistoryProfile.records(historyRealm);
    }

    private void updateHistrorySegments(MedtronicCnlReader cnlReader, final Realm historyRealm, final long oldest, final long newest, final byte historyType, boolean pullHistory, String historyTAG)
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
        } else if (segment.last().getFromDate().getTime() - oldest > 60 * 60 * 1000) {
            Log.d(TAG, historyTAG + "store sized has increased, adding segment");
            historyRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    historyRealm.createObject(PumpHistorySegment.class).addSegment(new Date(oldest), historyType);
                }
            });
        } else {
            // update the segment marker
            // TODO need to check if there are any more outdated segments as a lot of time may have passed!!!
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
                Log.d(TAG, historyTAG + "segments=" + segment.size() + " segment[" + i + "] start= " + dateFormatterFull.format(segment.get(i).getFromDate()) + " end=" + dateFormatterFull.format(segment.get(i).getToDate()));
            }

            Date needFrom = segment.get(1).getToDate();
            Date needTo = segment.get(0).getFromDate();

            long start = needFrom.getTime();
            long end = needTo.getTime();
            if (end - start > HISTORY_PULL_MS)
                start = end - HISTORY_PULL_MS;

            Log.d(TAG, historyTAG + "requested " + dateFormatterFull.format(start) + " - " + dateFormatterFull.format(end));
            sendStatus(historyTAG + "requested \n      " + dateFormatterFull.format(start) + " - " + dateFormatterFull.format(end));

            Date[] range = cnlReader.getHistoryX(start, end, pumpOFFSET, historyType);

            Log.d(TAG, historyTAG + "received  " + (range[0] == null ? "null" : dateFormatterFull.format(range[0])) + " - " + (range[1] == null ? "null" : dateFormatterFull.format(range[1])));
            sendStatus(historyTAG + "received \n      " + (range[0] == null ? "null" : dateFormatterFull.format(range[0])) + " - " + (range[1] == null ? "null" : dateFormatterFull.format(range[1])));

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
            Log.d(TAG, historyTAG + "segments=" + segment.size() + " segment[" + i + "] start= " + dateFormatterFull.format(segment.get(i).getFromDate()) + " end=" + dateFormatterFull.format(segment.get(i).getToDate()));
        }
    }

    private DateFormat dateFormatterFull = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US);

    protected void sendStatus(String message) {
        try {
            Intent intent =
                    new Intent(MasterService.Constants.ACTION_STATUS_MESSAGE)
                            .putExtra(MasterService.Constants.EXTENDED_DATA, message);
            UploaderApplication.getAppContext().sendBroadcast(intent);
        } catch (Exception e) {
        }
    }

}
