package info.nightscout.android.upload.nightscout;

import android.support.annotation.NonNull;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import info.nightscout.android.R;
import info.nightscout.android.history.NightscoutItem;
import info.nightscout.android.history.PumpHistoryParser;
import info.nightscout.android.history.PumpHistorySender;
import info.nightscout.android.model.medtronicNg.PumpHistoryInterface;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.model.store.DataStore;
import info.nightscout.android.upload.nightscout.DeviceEndpoints.Iob;
import info.nightscout.android.upload.nightscout.DeviceEndpoints.Battery;
import info.nightscout.android.upload.nightscout.DeviceEndpoints.PumpStatus;
import info.nightscout.android.upload.nightscout.DeviceEndpoints.PumpInfo;
import info.nightscout.android.upload.nightscout.DeviceEndpoints.DeviceStatus;
import info.nightscout.android.utils.FormatKit;
import io.realm.Realm;
import okhttp3.ResponseBody;
import retrofit2.Response;

/*
Nightscout notes:

Device - POST a single device status, POST does not support bulk upload (have not checked QUERY & GET & DELETE support)
Entries - QUERY support, GET & POST & DELETE a single entry, POST & DELETE has bulk support
Treatments - QUERY support, GET & POST & DELETE a single treatment, POST has bulk support
Profile - no QUERY support, GET returns all profile sets, can POST & DELETE a single profile set, POST does not support bulk upload

*/

public class NightScoutUpload {
    private static final String TAG = NightScoutUpload.class.getSimpleName();

    // delete all items or just the items without a Key600 field
    // debug use only as may have issues if there is a lot of treatment entries in NS
    private static final boolean CLEAN_COMPLETE = false;

    private DeviceEndpoints deviceEndpoints;
    private EntriesEndpoints entriesEndpoints;
    private TreatmentsEndpoints treatmentsEndpoints;
    private ProfileEndpoints profileEndpoints;

    private Realm storeRealm;
    private DataStore dataStore;

    private PumpHistorySender pumpHistorySender;

    private String device;
    private String info;

    NightScoutUpload() {}

    public void doRESTUpload(PumpHistorySender pumpHistorySender, Realm storeRealm, DataStore dataStore,
                             String url,
                             String secret,
                             int uploaderBatteryLevel,
                             String device,
                             String info,
                             List<PumpStatusEvent> statusRecords,
                             List<PumpHistoryInterface> records)
            throws Exception {

        this.pumpHistorySender = pumpHistorySender;

        this.storeRealm = storeRealm;
        this.dataStore = dataStore;

        this.device = device;
        this.info = info;

        UploadApi uploadApi = new UploadApi(url, secret);

        deviceEndpoints = uploadApi.getDeviceEndpoints();
        entriesEndpoints = uploadApi.getEntriesEndpoints();
        treatmentsEndpoints = uploadApi.getTreatmentsEndpoints();
        profileEndpoints = uploadApi.getProfileEndpoints();

        uploadStatus(statusRecords, uploaderBatteryLevel);
        uploadEvents(records);
    }

    // Format date to Zulu (UTC) time
    public static String formatDateForNS(Date date) {
        return formatDateForNS(date.getTime());
    }
    public static String formatDateForNS(long time) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(time) + "Z";
    }

    private void uploadEvents(List<PumpHistoryInterface> records) throws Exception {

        cleanupCheck();

        List<EntriesEndpoints.Entry> entries = new ArrayList<>();
        List<TreatmentsEndpoints.Treatment> treatments = new ArrayList<>();

        for (PumpHistoryInterface record : records) {
            List<NightscoutItem> nightscoutItems = record.nightscout(pumpHistorySender, "NS");
            for (NightscoutItem nightscoutItem : nightscoutItems) {
                if (nightscoutItem.isEntry())
                    processEntry(modeOverride(nightscoutItem), nightscoutItem.getEntry(), entries);
                else if (nightscoutItem.isTreatment())
                    processTreatment(modeOverride(nightscoutItem), nightscoutItem.getTreatment(), treatments);
                else if (nightscoutItem.isProfile())
                    processProfile(nightscoutItem.getMode(), nightscoutItem.getProfile());
            }
        }

        // bulk uploading for entries and treatments

        if (entries.size() > 0) {
            Response<ResponseBody> result = entriesEndpoints.sendEntries(entries).execute();
            if (!result.isSuccessful()) throw new Exception("(entries) " + result.message());
        }
        if (treatments.size() > 0) {
            Response<ResponseBody> result = treatmentsEndpoints.sendTreatments(treatments).execute();
            if (!result.isSuccessful()) throw new Exception("(treatments) " + result.message());
        }
    }

    private NightscoutItem.MODE modeOverride(NightscoutItem nightscoutItem) {
        // items normally check if a record already exists in nightscout before writing
        // can override to always update when items are older then a certain time
        NightscoutItem.MODE mode = nightscoutItem.getMode();
        if (mode == NightscoutItem.MODE.CHECK
                && nightscoutItem.getTimestamp() < dataStore.getNightscoutAlwaysUpdateTimestamp())
            mode = NightscoutItem.MODE.UPDATE;
        return mode;
    }

    private void processEntry(NightscoutItem.MODE mode, EntriesEndpoints.Entry entry, List<EntriesEndpoints.Entry> entries) throws Exception {

        String key = entry.getKey600();

        Response<List<EntriesEndpoints.Entry>> response = entriesEndpoints.checkKey("2017", key).execute();

        if (response.isSuccessful()) {
            List<EntriesEndpoints.Entry> list = response.body();
            if (list.size() > 0) {
                Log.d(TAG, "found " + list.size() + " already in nightscout for KEY: " + key);

                for (EntriesEndpoints.Entry item : list) {
                    if (item.getPumpMAC600() == null ||
                            (item.getPumpMAC600().equals(entry.getPumpMAC600()) &&
                                    mode == NightscoutItem.MODE.UPDATE || mode == NightscoutItem.MODE.DELETE)) {
                        Response<ResponseBody> responseBody = entriesEndpoints.deleteKey(item.getDate().toString(), key).execute();
                        if (responseBody.isSuccessful()) {
                            Log.d(TAG, "deleted this item! ID: " + item.get_id() + " with KEY: " + key);
                        } else {
                            Log.d(TAG, "no DELETE response from nightscout site");
                            throw new Exception("(processEntry) " + responseBody.message());
                        }
                    }
                }
            }

            if (mode == NightscoutItem.MODE.UPDATE || mode == NightscoutItem.MODE.CHECK) {
                Log.d(TAG, "queued item for nightscout entries bulk upload, KEY: " + key);
                entry.setDevice(device);
                entries.add(entry);
            }

        } else {
            Log.d(TAG, "no response from nightscout site!");
            throw new Exception("(processEntry) " + response.message());
        }
    }

    private void processTreatment(NightscoutItem.MODE mode, TreatmentsEndpoints.Treatment treatment, List<TreatmentsEndpoints.Treatment> treatments) throws Exception {

        String key = treatment.getKey600();

        Response<List<TreatmentsEndpoints.Treatment>> response = treatmentsEndpoints.checkKey("2017", key).execute();

        if (response.isSuccessful()) {
            List<TreatmentsEndpoints.Treatment> list = response.body();
            if (list.size() > 0) {
                Log.d(TAG, "found " + list.size() + " already in nightscout for KEY: " + key);

                for (TreatmentsEndpoints.Treatment item : list) {
                    if (item.getPumpMAC600() == null ||
                            (item.getPumpMAC600().equals(treatment.getPumpMAC600()) &&
                                    mode == NightscoutItem.MODE.UPDATE || mode == NightscoutItem.MODE.DELETE)) {
                        Response<ResponseBody> responseBody = treatmentsEndpoints.deleteID(item.get_id()).execute();
                        if (responseBody.isSuccessful()) {
                            Log.d(TAG, "deleted this item! ID: " + item.get_id() + " with KEY: " + key);
                        } else {
                            Log.d(TAG, "no DELETE response from nightscout site");
                            throw new Exception("(processTreatment) " + responseBody.message());
                        }
                    }
                }

            }

            if (mode == NightscoutItem.MODE.UPDATE || mode == NightscoutItem.MODE.CHECK) {
                Log.d(TAG, "queued item for nightscout treatments bulk upload, KEY: " + key);
                treatments.add(treatment);
            }

        } else {
            Log.d(TAG, "no response from nightscout site!");
            throw new Exception("(processTreatment) " + response.message());
        }
    }

    private void processProfile(NightscoutItem.MODE mode, ProfileEndpoints.Profile profile) throws Exception {

        String key = profile.getKey600();
        Response<List<ProfileEndpoints.Profile>> response = profileEndpoints.getProfiles().execute();

        if (response.isSuccessful()) {

            List<ProfileEndpoints.Profile> list = response.body();

            if (list.size() > 0) {
                Log.d(TAG, "found " + list.size() + " profiles sets in nightscout");

                String foundID;
                String foundKey;
                int count = 0;

                if (dataStore.isNsEnableProfileSingle()) {
                    Log.d(TAG, "single profile enabled, deleting obsolete profiles");

                    for (ProfileEndpoints.Profile item : list) {
                        foundID = item.get_id();
                        Response<ResponseBody> responseBody = profileEndpoints.deleteID(foundID).execute();
                        if (responseBody.isSuccessful()) {
                            Log.d(TAG, "deleted this item! ID: " + foundID);
                        } else {
                            Log.d(TAG, "no DELETE response from nightscout site");
                            throw new Exception("(processProfile) " + responseBody.message());
                        }
                    }

                } else {

                    for (ProfileEndpoints.Profile item : list) {
                        foundKey = item.getKey600();
                        if (foundKey != null && foundKey.equals(key)) count++;
                    }

                    if (count > 0) {
                        Log.d(TAG, "found " + count + " already in nightscout for KEY: " + key);

                        if (mode == NightscoutItem.MODE.UPDATE || mode == NightscoutItem.MODE.DELETE || count > 1) {
                            for (ProfileEndpoints.Profile item : list) {
                                foundKey = item.getKey600();
                                if (foundKey != null && foundKey.equals(key)) {
                                    foundID = item.get_id();
                                    Response<ResponseBody> responseBody = profileEndpoints.deleteID(foundID).execute();
                                    if (responseBody.isSuccessful()) {
                                        Log.d(TAG, "deleted this item! KEY: " + key + " ID: " + foundID);
                                    } else {
                                        Log.d(TAG, "no DELETE response from nightscout site");
                                        throw new Exception("(processProfile) " + responseBody.message());
                                    }
                                    if (--count == 1) break;
                                }
                            }
                        }
                    }

                    if (count > 0) return;
                }
            }

            if (mode == NightscoutItem.MODE.UPDATE || mode == NightscoutItem.MODE.CHECK) {
                Log.d(TAG, "new item sending to nightscout profile, KEY: " + key);
                Response<ResponseBody> responseBody = profileEndpoints.sendProfile(profile).execute();
                if (!responseBody.isSuccessful()) {
                    Log.d(TAG, "no POST response from nightscout site");
                    throw new Exception("(processProfile) " + responseBody.message());
                }
            }

        } else {
            Log.d(TAG, "no response from nightscout site!");
            throw new Exception("(processProfile) " + response.message());
        }
    }

    private void uploadStatus(List<PumpStatusEvent> records, int uploaderBatteryLevel) throws Exception {

        String info = this.info;

        List<DeviceEndpoints.DeviceStatus> deviceEntries = new ArrayList<>();
        for (PumpStatusEvent record : records) {

            Iob iob = new Iob(record.getEventDate(), record.getActiveInsulin());
            Battery battery = new Battery(record.getBatteryPercentage());
            PumpStatus pumpstatus;

            if (record.isCgmLostSensor())
                info = "";

            // shorten pump status when needed to accommodate mobile browsers
            boolean shorten = false;
            if (info.length() > 0
                    || (record.isBolusingSquare() || record.isBolusingDual()) && record.isTempBasalActive()) {
                shorten = true;
            }

            StringBuilder sb = new StringBuilder();

            if (record.getAlert() > 0) {
                sb.append("⚠");
            }
            if (record.isBolusingNormal()) {
                sb.append(sb.length() == 0 ? "" : " ");
                sb.append("bolusing");
            }
            else if (record.isSuspended()) {
                sb.append(sb.length() == 0 ? "" : " ");
                sb.append("suspended");
            }
            else if (record.isBolusingSquare() || record.isBolusingDual() || record.getTempBasalMinutesRemaining() > 0) {
                if (record.isBolusingSquare()) {
                    sb.append(sb.length() == 0 ? "" : " ");
                    if (shorten)
                        sb.append(String.format("S>%s-%s",
                                FormatKit.getInstance().formatAsInsulin((double) record.getBolusingDelivered()),
                                FormatKit.getInstance().formatMinutesAsDHM(record.getBolusingMinutesRemaining())));
                    else
                        sb.append(String.format("Square>%s-%s",
                                FormatKit.getInstance().formatAsInsulin((double) record.getBolusingDelivered()),
                                FormatKit.getInstance().formatMinutesAsDHM(record.getBolusingMinutesRemaining())));
                    shorten = true;
                } else if (record.isBolusingDual()) {
                    sb.append(sb.length() == 0 ? "" : " ");
                    if (shorten)
                        sb.append(String.format("D>%s-%s",
                                FormatKit.getInstance().formatAsInsulin((double) record.getBolusingDelivered()),
                                FormatKit.getInstance().formatMinutesAsDHM(record.getBolusingMinutesRemaining())));
                    else
                        sb.append(String.format("Dual>%s-%s",
                                FormatKit.getInstance().formatAsInsulin((double) record.getBolusingDelivered()),
                                FormatKit.getInstance().formatMinutesAsDHM(record.getBolusingMinutesRemaining())));
                    shorten = true;
                }
                if (record.getTempBasalMinutesRemaining() > 0 & record.getTempBasalPercentage() != 0) {
                    sb.append(sb.length() == 0 ? "" : " ");
                    if (shorten)
                        sb.append(String.format("T>%s-%s",
                                FormatKit.getInstance().formatAsPercent(record.getTempBasalPercentage()),
                                FormatKit.getInstance().formatMinutesAsDHM(record.getTempBasalMinutesRemaining())));
                    else
                        sb.append(String.format("Temp>%s-%s",
                                FormatKit.getInstance().formatAsPercent(record.getTempBasalPercentage()),
                                FormatKit.getInstance().formatMinutesAsDHM(record.getTempBasalMinutesRemaining())));
                    shorten = true;
                } else if (record.getTempBasalMinutesRemaining() > 0) {
                    sb.append(sb.length() == 0 ? "" : " ");
                    if (shorten)
                        sb.append(String.format("T>%s-%s",
                                FormatKit.getInstance().formatAsInsulin((double) record.getTempBasalRate()),
                                FormatKit.getInstance().formatMinutesAsDHM(record.getTempBasalMinutesRemaining())));
                    else
                        sb.append(String.format("Temp>%s-%s",
                                FormatKit.getInstance().formatAsInsulin((double) record.getTempBasalRate()),
                                FormatKit.getInstance().formatMinutesAsDHM(record.getTempBasalMinutesRemaining())));
                    shorten = true;
                }
            }

            sb.append(sb.length() == 0 ? "" : " ");
            sb.append("|");

            if (record.isCgmActive()) {
                if (!shorten || sb.length() <= 5) {
                    sb.append(sb.length() == 0 ? "" : " ");
                    sb.append(FormatKit.getInstance().formatAsPercent(record.getTransmitterBattery()));
                }

                PumpHistoryParser.CGM_EXCEPTION cgmException;
                if (record.isCgmException())
                    cgmException = PumpHistoryParser.CGM_EXCEPTION.convert(
                            record.getCgmExceptionType());
                else if (record.isCgmCalibrating())
                    cgmException = PumpHistoryParser.CGM_EXCEPTION.SENSOR_CAL_PENDING;
                else
                    cgmException = PumpHistoryParser.CGM_EXCEPTION.NA;

                if (cgmException != PumpHistoryParser.CGM_EXCEPTION.NA) {
                    sb.append(sb.length() == 0 ? "" : " ");
                    sb.append(shorten ? cgmException.abbriviation() : cgmException.string());
                }

                if (record.getCalibrationDueMinutes() > 0) {
                    sb.append(sb.length() == 0 ? "" : " ");
                    sb.append(shorten ?
                            (record.getCalibrationDueMinutes() >= 100 ?
                                    record.getCalibrationDueMinutes() / 60 + FormatKit.getInstance().getString(R.string.time_h)
                                    : record.getCalibrationDueMinutes() + FormatKit.getInstance().getString(R.string.time_m))
                            :
                            (record.getCalibrationDueMinutes() >= 60 ?
                                    record.getCalibrationDueMinutes() / 60 + FormatKit.getInstance().getString(R.string.time_h)
                                            + record.getCalibrationDueMinutes() % 60 + FormatKit.getInstance().getString(R.string.time_m)
                                    : record.getCalibrationDueMinutes() % 60 + FormatKit.getInstance().getString(R.string.time_m))
                    );
                }

            } else if (record.isCgmLostSensor()) {
                sb.append(sb.length() == 0 ? "" : " ");
                sb.append(shorten ? "lost" : "lost sensor");
            } else {
                sb.append(sb.length() == 0 ? "" : " ");
                sb.append("no cgm");
            }

            if (info.length() > 0) {
                sb.append(sb.length() == 0 ? "" : " ");
                sb.append(info);
            }

            pumpstatus = new PumpStatus(false, false, sb.toString());

            PumpInfo pumpInfo = new PumpInfo(
                    formatDateForNS(record.getEventDate()),
                    new BigDecimal(record.getReservoirAmount()).setScale(1, BigDecimal.ROUND_HALF_UP),
                    iob,
                    battery,
                    pumpstatus
            );

            DeviceStatus deviceStatus = new DeviceStatus(
                    uploaderBatteryLevel,
                    record.getDeviceName(),
                    formatDateForNS(record.getEventDate()),
                    pumpInfo
            );

            deviceEntries.add(deviceStatus);
        }

        for (DeviceStatus status : deviceEntries) {
            Response<ResponseBody> result = deviceEndpoints.sendDeviceStatus(status).execute();
            if (!result.isSuccessful()) throw new Exception("(device status) " + result.message());
        }
    }

    private void cleanupCheck() throws Exception {

        if (dataStore.isNsEnableHistorySync()) {

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);

            long now = System.currentTimeMillis();

            int cgmDays = dataStore.getSysCgmHistoryDays();
            int pumpDays = dataStore.getSysPumpHistoryDays();

            if (CLEAN_COMPLETE) {
                cgmDays = 90;
                pumpDays = 90;
            }

            final long cgmFrom = now - cgmDays * (24 * 60 * 60000L);
            long pumpFrom = now - pumpDays * (24 * 60 * 60000L);

            long cgmTo = dataStore.getNightscoutCgmCleanFrom();
            long pumpTo = dataStore.getNightscoutPumpCleanFrom();

            long limit = dataStore.getNightscoutLimitDate().getTime();

            Response<ResponseBody> responseBody;
            Response<List<TreatmentsEndpoints.Treatment>> response;

            if (cgmTo == 0) cgmTo = limit;
            if (pumpTo == 0) pumpTo = limit;

            Log.d(TAG, "cleanup: limit date " + dateFormat.format(limit));

            if (cgmFrom < cgmTo) {
                Log.d(TAG, "cleanup: entries (cgm history) " + dateFormat.format(cgmFrom) + " to " + dateFormat.format(cgmTo));

                if (CLEAN_COMPLETE) {
                    responseBody = entriesEndpoints.deleteCleanupItems(
                            "" + cgmFrom,
                            "" + cgmTo).execute();
                } else {
                    responseBody = entriesEndpoints.deleteCleanupItems(
                            "" + cgmFrom,
                            "" + cgmTo,
                            "").execute();
                }

                if (responseBody.isSuccessful()) {
                    Log.d(TAG, "cleanup: bulk deleted entries");

                    storeRealm.executeTransaction(new Realm.Transaction() {
                        @Override
                        public void execute(@NonNull Realm realm) {
                            dataStore.setNightscoutCgmCleanFrom(cgmFrom);
                        }
                    });

                } else {
                    Log.d(TAG, "cleanup: no DELETE response from nightscout site");
                }

            }

            if (pumpFrom < pumpTo) {

                // cleaning treatments can be slow, limit the period per pass
                long limiter = 7 * 24 * 60 * 60000L;
                if (pumpTo - pumpFrom > limiter) pumpFrom = pumpTo - limiter;

                Log.d(TAG, "cleanup: treatments (pump history) " + dateFormat.format(pumpFrom) + " to " + dateFormat.format(pumpTo));

                boolean finished = false;
                boolean success = true;

                while (!finished && success) {

                    if (CLEAN_COMPLETE) {
                        response = treatmentsEndpoints.findDateRangeCount(
                                dateFormat.format(pumpFrom),
                                dateFormat.format(pumpTo),
                                "20").execute();
                    } else {
                        response = treatmentsEndpoints.findCleanupItems(
                                dateFormat.format(pumpFrom),
                                dateFormat.format(pumpTo),
                                "Note",
                                "", "20").execute();
                    }

                    if (response.isSuccessful()) {
                        List<TreatmentsEndpoints.Treatment> list = response.body();
                        int count = list.size();
                        if (count > 0) {
                            Log.d(TAG, "cleanup: found " + list.size());

                            while (count > 0) {
                                responseBody = treatmentsEndpoints.deleteID(list.get(count - 1).get_id()).execute();
                                if (responseBody.isSuccessful()) {
                                    Log.d(TAG, "cleanup: deleted this item! ID: " + list.get(count - 1).get_id());
                                } else {
                                    Log.d(TAG, "cleanup: no DELETE response from nightscout site");
                                }
                                count--;
                            }
                        } else finished = true;
                    } else success = false;
                }

                final long pumpFromFinal = pumpFrom;
                if (success) {
                    storeRealm.executeTransaction(new Realm.Transaction() {
                        @Override
                        public void execute(@NonNull Realm realm) {
                            dataStore.setNightscoutPumpCleanFrom(pumpFromFinal);
                        }
                    });
                }
            }

        }
    }

}
