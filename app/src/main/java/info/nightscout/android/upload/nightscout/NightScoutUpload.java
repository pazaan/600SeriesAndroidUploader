package info.nightscout.android.upload.nightscout;

import android.support.annotation.NonNull;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import info.nightscout.android.model.medtronicNg.PumpHistoryInterface;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.model.store.DataStore;
import info.nightscout.api.DeviceEndpoints;
import info.nightscout.api.DeviceEndpoints.Iob;
import info.nightscout.api.DeviceEndpoints.Battery;
import info.nightscout.api.DeviceEndpoints.PumpStatus;
import info.nightscout.api.DeviceEndpoints.PumpInfo;
import info.nightscout.api.DeviceEndpoints.DeviceStatus;
import info.nightscout.api.EntriesEndpoints;
import info.nightscout.api.ProfileEndpoints;
import info.nightscout.api.TreatmentsEndpoints;
import info.nightscout.api.UploadApi;
import info.nightscout.api.UploadItem;
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

    NightScoutUpload() {}

    public void doRESTUpload(Realm storeRealm, DataStore dataStore,
                             String url,
                             String secret,
                             int uploaderBatteryLevel,
                             List<PumpStatusEvent> statusRecords,
                             List<PumpHistoryInterface> records)
            throws Exception {

        this.storeRealm = storeRealm;
        this.dataStore = dataStore;

        UploadApi uploadApi = new UploadApi(url, formToken(secret));

        deviceEndpoints = uploadApi.getDeviceEndpoints();
        entriesEndpoints = uploadApi.getEntriesEndpoints();
        treatmentsEndpoints = uploadApi.getTreatmentsEndpoints();
        profileEndpoints = uploadApi.getProfileEndpoints();

        uploadStatus(statusRecords, uploaderBatteryLevel);
        uploadEvents(records);
    }

    public static String formatDateForNS(Date date) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
        SimpleDateFormat dftz = new SimpleDateFormat("Z", Locale.getDefault());
        String tz = dftz.format(date);
        if (tz.length() == 5 && (tz.startsWith("+") || tz.startsWith("-")))
            return df.format(date) + tz.subSequence(0,3) + ":" + tz.subSequence(3,5);
        return df.format(date);
    }

    private void uploadEvents(List<PumpHistoryInterface> records) throws Exception {

        cleanupCheck();

        List<EntriesEndpoints.Entry> entries = new ArrayList<>();
        List<TreatmentsEndpoints.Treatment> treatments = new ArrayList<>();

        for (PumpHistoryInterface record : records) {
            List<UploadItem> uploadItems = record.nightscout(dataStore);
            for (UploadItem uploadItem : uploadItems) {
                if (uploadItem.isEntry())
                    processEntry(uploadItem.getMode(), uploadItem.getEntry(), entries);
                else if (uploadItem.isTreatment())
                    processTreatment(uploadItem.getMode(), uploadItem.getTreatment(), treatments);
                else if (uploadItem.isProfile())
                    processProfile(uploadItem.getMode(), uploadItem.getProfile());
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

    private void processEntry(UploadItem.MODE mode, EntriesEndpoints.Entry entry, List<EntriesEndpoints.Entry> entries) throws Exception {

        String key = entry.getKey600();
        Response<List<EntriesEndpoints.Entry>> response = entriesEndpoints.checkKey("2017", key).execute();

        if (response.isSuccessful()) {
            List<EntriesEndpoints.Entry> list = response.body();
            int count = list.size();
            if (count > 0) {
                Log.d(TAG, "found " + list.size() + " already in nightscout for KEY: " + key);

                if (mode == UploadItem.MODE.UPDATE || mode == UploadItem.MODE.DELETE || count > 1) {
                    Response<ResponseBody> responseBody = entriesEndpoints.deleteKey("2017", key).execute();
                    if (responseBody.isSuccessful()) {
                        Log.d(TAG, "deleted " + count + " with KEY: " + key);
                    } else {
                        Log.d(TAG, "no DELETE response from nightscout site");
                        throw new Exception("(processEntry) " + responseBody.message());
                    }
                } else return;
            }

            if (mode == UploadItem.MODE.UPDATE || mode == UploadItem.MODE.CHECK) {
                Log.d(TAG, "queued item for nightscout entries bulk upload, KEY: " + key);
                entries.add(entry);
            }

        } else {
            Log.d(TAG, "no response from nightscout site!");
            throw new Exception("(processEntry) " + response.message());
        }
    }

    private void processTreatment(UploadItem.MODE mode, TreatmentsEndpoints.Treatment treatment, List<TreatmentsEndpoints.Treatment> treatments) throws Exception {

        String key = treatment.getKey600();
        Response<List<TreatmentsEndpoints.Treatment>> response = treatmentsEndpoints.checkKey("2017", key).execute();

        if (response.isSuccessful()) {
            List<TreatmentsEndpoints.Treatment> list = response.body();
            int count = list.size();
            if (count > 0) {
                Log.d(TAG, "found " + list.size() + " already in nightscout for KEY: " + key);

                while (count > 0 && (mode == UploadItem.MODE.UPDATE || mode == UploadItem.MODE.DELETE || count > 1)) {
                    Response<ResponseBody> responseBody = treatmentsEndpoints.deleteID(list.get(count - 1).get_id()).execute();
                    if (responseBody.isSuccessful()) {
                        Log.d(TAG, "deleted this item! KEY: " + key + " ID: " + list.get(count - 1).get_id());
                    } else {
                        Log.d(TAG, "no DELETE response from nightscout site");
                        throw new Exception("(processTreatment) " + responseBody.message());
                    }
                    count--;
                }

                if (count > 0) return;
            }

            if (mode == UploadItem.MODE.UPDATE || mode == UploadItem.MODE.CHECK) {
                Log.d(TAG, "queued item for nightscout treatments bulk upload, KEY: " + key);
                treatments.add(treatment);
            }

        } else {
            Log.d(TAG, "no response from nightscout site!");
            throw new Exception("(processTreatment) " + response.message());
        }
    }

    private void processProfile(UploadItem.MODE mode, ProfileEndpoints.Profile profile) throws Exception {

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

                        if (mode == UploadItem.MODE.UPDATE || mode == UploadItem.MODE.DELETE || count > 1) {
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

            if (mode == UploadItem.MODE.UPDATE || mode == UploadItem.MODE.CHECK) {
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

        List<DeviceEndpoints.DeviceStatus> deviceEntries = new ArrayList<>();
        for (PumpStatusEvent record : records) {

            Iob iob = new Iob(record.getEventDate(), record.getActiveInsulin());
            Battery battery = new Battery(record.getBatteryPercentage());
            PumpStatus pumpstatus;

            // shorten pump status when needed to accommodate mobile browsers
            boolean shorten = false;
            if ((record.isBolusingSquare() || record.isBolusingDual()) && record.isTempBasalActive())
                shorten = true;

            String statusPUMP = "normal";
            if (record.isBolusingNormal()) {
                statusPUMP = "bolusing";
            } else if (record.isSuspended()) {
                statusPUMP = "suspended";
            } else {
                if (record.isBolusingSquare()) {
                    if (shorten)
                        statusPUMP = "S>" + record.getBolusingDelivered() + "u-" + record.getBolusingMinutesRemaining() + "m";
                    else
                        statusPUMP = "square>>" + record.getBolusingDelivered() + "u-" + (record.getBolusingMinutesRemaining() >= 60 ? record.getBolusingMinutesRemaining() / 60 + "h" : "") + record.getBolusingMinutesRemaining() % 60 + "m";
                    shorten = true;
                } else if (record.isBolusingDual()) {
                    if (shorten)
                        statusPUMP = "D>" + record.getBolusingDelivered() + "-" + record.getBolusingMinutesRemaining() + "m";
                    else
                        statusPUMP = "dual>>" + record.getBolusingDelivered() + "u-" + (record.getBolusingMinutesRemaining() >= 60 ? record.getBolusingMinutesRemaining() / 60 + "h" : "") + record.getBolusingMinutesRemaining() % 60 + "m";
                    shorten = true;
                }
                if (record.getTempBasalMinutesRemaining() > 0 & record.getTempBasalPercentage() != 0) {
                    if (shorten)
                        statusPUMP = " T>" + record.getTempBasalPercentage() + "%-" + record.getTempBasalMinutesRemaining() + "m";
                    else
                        statusPUMP = "temp>>" + record.getTempBasalPercentage() + "%-" + (record.getTempBasalMinutesRemaining() >= 60 ? record.getTempBasalMinutesRemaining() / 60 + "h" : "") + record.getTempBasalMinutesRemaining() % 60 + "m";
                    shorten = true;
                } else if (record.getTempBasalMinutesRemaining() > 0) {
                    if (shorten)
                        statusPUMP = " T>" + record.getTempBasalRate() + "-" + record.getTempBasalMinutesRemaining() + "m";
                    else
                        statusPUMP = "temp>>" + record.getTempBasalRate() + "u-" + (record.getTempBasalMinutesRemaining() >= 60 ? record.getTempBasalMinutesRemaining() / 60 + "h" : "") + record.getTempBasalMinutesRemaining() % 60 + "m";
                    shorten = true;
                }
            }

            if (record.getAlert() > 0)
                statusPUMP = "⚠ " + statusPUMP;

            String statusCGM = "";
            if (record.isCgmActive()) {
                if (record.getTransmitterBattery() > 80)
                    statusCGM = shorten ? "" : ":::: ";
                else if (record.getTransmitterBattery() > 55)
                    statusCGM = shorten ? "" : ":::. ";
                else if (record.getTransmitterBattery() > 30)
                    statusCGM = shorten ? "" : "::.. ";
                else if (record.getTransmitterBattery() > 10)
                    statusCGM = shorten ? "" : ":... ";
                else
                    statusCGM = shorten ? "" : ".... ";
                if (record.isCgmCalibrating())
                    statusCGM += shorten ? "CAL" : "calibrating";
                else if (record.isCgmCalibrationComplete())
                    statusCGM += shorten ? "CAL" : "cal.complete";
                else {
                    if (record.isCgmWarmUp())
                        statusCGM += shorten ? "WU" : "warmup ";
                    if (record.getCalibrationDueMinutes() > 0) {
                        if (shorten)
                            statusCGM += (record.getCalibrationDueMinutes() >= 120 ? record.getCalibrationDueMinutes() / 60 + "h" : record.getCalibrationDueMinutes() + "m");
                        else
                            statusCGM += (record.getCalibrationDueMinutes() >= 60 ? record.getCalibrationDueMinutes() / 60 + "h" : "") + record.getCalibrationDueMinutes() % 60 + "m";
                    }
                    else
                        statusCGM += shorten ? "cal.now" : "calibrate now!";
                }
            } else {
                statusCGM += shorten ? "n/a" : "cgm n/a";
            }

            pumpstatus = new PumpStatus(false, false, statusPUMP + " " + statusCGM);

            PumpInfo pumpInfo = new PumpInfo(
                    formatDateForNS(record.getEventDate()),
                    new BigDecimal(record.getReservoirAmount()).setScale(0, BigDecimal.ROUND_HALF_UP),
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

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm");

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
                        public void execute(Realm realm) {
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
                        public void execute(Realm realm) {
                            dataStore.setNightscoutPumpCleanFrom(pumpFromFinal);
                        }
                    });
                }
            }

        }
    }

    @NonNull
    private String formToken(String secret) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] bytes = secret.getBytes("UTF-8");
        digest.update(bytes, 0, bytes.length);
        bytes = digest.digest();
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}
