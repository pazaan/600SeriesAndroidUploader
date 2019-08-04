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
import info.nightscout.android.history.PumpHistoryHandler;
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

import static info.nightscout.android.history.PumpHistorySender.SENDER_ID_NIGHTSCOUT;
import static info.nightscout.android.medtronic.service.MedtronicCnlService.DEVICE_HEADER;

/*
Nightscout notes:

Device - POST a single device status, POST does not support bulk upload (have not checked QUERY & GET & DELETE support)
Entries - QUERY support, GET & POST & DELETE a single entry, POST & DELETE has bulk support
Treatments - QUERY support, GET & POST & DELETE a single treatment, POST has bulk support
Profile - no QUERY support, GET returns all profile sets, can POST & DELETE a single profile set, POST does not support bulk upload

*/

public class NightscoutUploadProcess {
    private static final String TAG = NightscoutUploadProcess.class.getSimpleName();

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
    private String enteredBy;
    private PumpHistoryHandler.ExtraInfo extraInfo;

    private boolean cancel;

    private UploadApi uploadApi;

    NightscoutUploadProcess(String url, String secret) throws Exception {
        uploadApi = new UploadApi(url, secret);
        deviceEndpoints = uploadApi.getDeviceEndpoints();
        entriesEndpoints = uploadApi.getEntriesEndpoints();
        treatmentsEndpoints = uploadApi.getTreatmentsEndpoints();
        profileEndpoints = uploadApi.getProfileEndpoints();
    }

    public void cancel() {
        cancel = true;
    }

    public boolean isCancel() {
        return cancel;
    }

    public void doRESTUpload(PumpHistorySender pumpHistorySender, Realm storeRealm, DataStore dataStore,
                             int uploaderBatteryLevel,
                             String device,
                             PumpHistoryHandler.ExtraInfo extraInfo,
                             List<PumpStatusEvent> statusRecords,
                             List<PumpHistoryInterface> records)
            throws Exception {

        cancel = false;

        this.pumpHistorySender = pumpHistorySender;

        this.storeRealm = storeRealm;
        this.dataStore = dataStore;

        this.extraInfo = extraInfo;

        if (dataStore.getNsDeviceName().length() == 0) this.device = device;
        else this.device = DEVICE_HEADER + dataStore.getNsDeviceName();

        if (dataStore.getNsEnteredBy().length() == 0) this.enteredBy = this.device;
        else enteredBy = DEVICE_HEADER + dataStore.getNsEnteredBy();

        if (dataStore.isNsEnableDeviceStatus())
            uploadStatus(statusRecords, uploaderBatteryLevel);

        if (!cancel) uploadEvents(records);
    }

    // Format date to Zulu (UTC) time
    public static String formatDateForNS(Date date) {
        return formatDateForNS(date.getTime());
    }
    public static String formatDateForNS(long time) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(time) + "Z";
    }

    private void uploadEvents(List<PumpHistoryInterface> records) throws Exception {

        cleanupCheck();

        List<EntriesEndpoints.Entry> entries = new ArrayList<>();
        List<TreatmentsEndpoints.Treatment> treatments = new ArrayList<>();

        for (PumpHistoryInterface record : records) {
            List<NightscoutItem> nightscoutItems = record.nightscout(pumpHistorySender, SENDER_ID_NIGHTSCOUT);
            for (NightscoutItem nightscoutItem : nightscoutItems) {
                if (nightscoutItem.isEntry())
                    processEntry(modeOverride(nightscoutItem), nightscoutItem.getEntry(), entries);
                else if (nightscoutItem.isTreatment())
                    processTreatment(modeOverride(nightscoutItem), nightscoutItem.getTreatment(), treatments);
                else if (nightscoutItem.isProfile())
                    processProfile(nightscoutItem.getMode(), nightscoutItem.getProfile());
            }
            if (cancel) break;
        }

        // bulk uploading for entries and treatments

        if (!cancel && entries.size() > 0) {
            Response<ResponseBody> result = entriesEndpoints.sendEntries(entries).execute();
            if (!result.isSuccessful()) throw new Exception("(entries) " + result.message());
        }
        if (!cancel && treatments.size() > 0) {
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

        String from = Long.toString(new SimpleDateFormat("yyyy", Locale.ENGLISH).parse("2017").getTime());

        String key = entry.getKey600();
        String mac = entry.getPumpMAC600();

        Response<List<EntriesEndpoints.Entry>> response = entriesEndpoints.findKey(from, key).execute();

        if (response.isSuccessful()) {
            List<EntriesEndpoints.Entry> list = response.body();
            int count = list.size();

            if (count > 0) {
                Log.d(TAG, "found " + count + " already in nightscout for KEY: " + key);

                for (EntriesEndpoints.Entry item : list) {

                    // v0.6.1 did not record the pump MAC, remove and rewrite keys with mac as needed
                    if (count > 1 || item.getPumpMAC600() == null ||
                            (item.getPumpMAC600().equals(mac) &&
                                    mode == NightscoutItem.MODE.UPDATE || mode == NightscoutItem.MODE.DELETE)) {
                        Response<ResponseBody> responseBody = entriesEndpoints.deleteID(item.getDate().toString(), item.get_id()).execute();
                        if (responseBody.isSuccessful()) {
                            Log.d(TAG, String.format("deleted entry ID: %s with KEY: %s MAC: %s DATE: %s (%s)",
                                    item.get_id(), item.getKey600(), item.getPumpMAC600(), item.getDateString(), item.getDate()));
                        } else {
                            Log.d(TAG, "no DELETE response from nightscout site");
                            throw new Exception("(processEntry) " + responseBody.message());
                        }
                    }

                    // in check mode and 1 item already in nightscout
                    else return;

                    count--;
                }
            }

            if (mode == NightscoutItem.MODE.UPDATE || mode == NightscoutItem.MODE.CHECK) {
                Log.d(TAG, String.format("queued item for nightscout entries bulk upload. KEY: %s MAC: %s DATE: %s (%s)", key, mac, entry.getDateString(), entry.getDate()));
                entry.setDevice(device);
                entries.add(entry);
            }

        } else {
            Log.d(TAG, "no response from nightscout site!");
            throw new Exception("(processEntry) " + response.message());
        }
    }

    private void processTreatment(NightscoutItem.MODE mode, TreatmentsEndpoints.Treatment treatment, List<TreatmentsEndpoints.Treatment> treatments) throws Exception {

        String from = "2017";

        String key = treatment.getKey600();
        String mac = treatment.getPumpMAC600();

        Response<List<TreatmentsEndpoints.Treatment>> response = treatmentsEndpoints.findKey(from, key).execute();

        if (response.isSuccessful()) {
            List<TreatmentsEndpoints.Treatment> list = response.body();
            int count = list.size();

            if (count > 0) {
                Log.d(TAG, "found " + count + " already in nightscout for KEY: " + key);

                for (TreatmentsEndpoints.Treatment item : list) {

                    // v0.6.1 did not record the pump MAC, remove and rewrite keys with mac as needed
                    if (count > 1 || item.getPumpMAC600() == null ||
                            (item.getPumpMAC600().equals(mac) &&
                                    mode == NightscoutItem.MODE.UPDATE || mode == NightscoutItem.MODE.DELETE)) {
                        Response<ResponseBody> responseBody = dataStore.isNightscoutUseQuery()
                                ? treatmentsEndpoints.deleteID(item.getCreated_at(), item.get_id()).execute()
                                : treatmentsEndpoints.deleteID(item.get_id()).execute();
                        if (responseBody.isSuccessful()) {
                            Log.d(TAG, String.format("deleted treatment ID: %s with KEY: %s MAC: %s DATE: %s QUERY: %s",
                                    item.get_id(), item.getKey600(), item.getPumpMAC600(), item.getCreated_at(), dataStore.isNightscoutUseQuery()));
                        } else {
                            Log.d(TAG, "no DELETE response from nightscout site");
                            throw new Exception("(processTreatment) " + responseBody.message());
                        }
                    }

                    // in check mode and 1 item already in nightscout
                    else return;

                    count--;
                }
            }

            if (mode == NightscoutItem.MODE.UPDATE || mode == NightscoutItem.MODE.CHECK) {
                Log.d(TAG, String.format("queued item for nightscout treatments bulk upload. KEY: %s MAC: %s DATE: %s", key, mac, treatment.getCreated_at()));
                if (enteredBy.length() > 0) treatment.setEnteredBy(enteredBy);
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
        List<DeviceEndpoints.DeviceStatus> deviceEntries = new ArrayList<>();
        DeviceStatus deviceStatus;

        if (dataStore.isNsEnableDevicePUMP()) {

            for (PumpStatusEvent record : records) {

                deviceStatus = new DeviceStatus();
                deviceStatus.setCreatedAt(formatDateForNS(record.getEventDate()));
                deviceStatus.setDevice(device);
                deviceStatus.setUploaderBattery(uploaderBatteryLevel);

                if (dataStore.isNsEnableDevicePUMP()) {

                    Iob iob = new Iob(record.getEventDate(), record.getActiveInsulin());
                    Battery battery = new Battery(record.getBatteryPercentage());
                    PumpStatus pumpstatus = new PumpStatus(false, false, buildPumpString(record));

                    PumpInfo pumpInfo = new PumpInfo(
                            formatDateForNS(record.getEventDate()),
                            new BigDecimal(record.getReservoirAmount()).setScale(1, BigDecimal.ROUND_HALF_UP),
                            iob,
                            battery,
                            pumpstatus
                    );

                    deviceStatus.setPump(pumpInfo);
                }

                deviceEntries.add(deviceStatus);

                if (cancel) break;
            }

        } else {
            // pump pill / iob device status disabled then just send the uploader battery state
            deviceStatus = new DeviceStatus();
            deviceStatus.setCreatedAt(formatDateForNS(System.currentTimeMillis()));
            deviceEntries.add(deviceStatus);
            deviceStatus.setDevice(device);
            deviceStatus.setUploaderBattery(uploaderBatteryLevel);
        }

        for (DeviceStatus status : deviceEntries) {
            Response<ResponseBody> result = deviceEndpoints.sendDeviceStatus(status).execute();
            if (!result.isSuccessful()) throw new Exception("(device status) " + result.message());
            if (cancel) break;
        }
    }

    private String buildPumpString(PumpStatusEvent record) {

        // shorten pump status when needed to accommodate mobile browsers
        boolean shorten = (record.isBolusingSquare() || record.isBolusingDual()) && record.isTempBasalActive();

        String info = "";
        if (!record.isCgmLostSensor()
                && extraInfo != null
                && record.getEventDate().getTime() >= extraInfo.getEventDate().getTime()) {
            info = shorten ? extraInfo.getInfoShort() : extraInfo.getInfo();
            shorten = true;
        }

        StringBuilder sb = new StringBuilder();

        if (record.getAlert() > 0) {
            sb.append(FormatKit.getInstance().getString(R.string.nightscout_pump_pill__active_alert));
        }
        if (record.isBolusingNormal()) {
            sb.append(sb.length() == 0 ? "" : " ");
            sb.append(info.length() > 5
                    ? FormatKit.getInstance().getString(R.string.nightscout_pump_pill__bolusing_abreviation)
                    : FormatKit.getInstance().getString(R.string.nightscout_pump_pill__bolusing));
        }
        else if (record.isSuspended()) {
            sb.append(sb.length() == 0 ? "" : " ");
            sb.append(info.length() > 5
                    ? FormatKit.getInstance().getString(R.string.nightscout_pump_pill__suspended_abreviation)
                    : FormatKit.getInstance().getString(R.string.nightscout_pump_pill__suspended));
        }
        else {
            if (record.isBolusingSquare()) {
                sb.append(sb.length() == 0 ? "" : " ");
                sb.append(String.format("%s:%s-%s",
                        FormatKit.getInstance().getString(R.string.nightscout_pump_pill__square),
                        FormatKit.getInstance().formatAsInsulin((double) record.getBolusingDelivered()),
                        FormatKit.getInstance().formatMinutesAsDHM(record.getBolusingMinutesRemaining())));
            }
            else if (record.isBolusingDual()) {
                sb.append(sb.length() == 0 ? "" : " ");
                sb.append(String.format("%s:%s-%s",
                        FormatKit.getInstance().getString(R.string.nightscout_pump_pill__dual),
                        FormatKit.getInstance().formatAsInsulin((double) record.getBolusingDelivered()),
                        FormatKit.getInstance().formatMinutesAsDHM(record.getBolusingMinutesRemaining())));
            }
            if (record.getTempBasalMinutesRemaining() > 0 & record.getTempBasalPercentage() != 0) {
                sb.append(sb.length() == 0 ? "" : " ");
                sb.append(String.format("%s:%s-%s",
                        FormatKit.getInstance().getString(R.string.nightscout_pump_pill__temp),
                        FormatKit.getInstance().formatAsPercent(record.getTempBasalPercentage()),
                        FormatKit.getInstance().formatMinutesAsDHM(record.getTempBasalMinutesRemaining())));
            }
            else if (record.getTempBasalMinutesRemaining() > 0) {
                sb.append(sb.length() == 0 ? "" : " ");
                sb.append(String.format("%s:%s-%s",
                        FormatKit.getInstance().getString(R.string.nightscout_pump_pill__temp),
                        FormatKit.getInstance().formatAsInsulin((double) record.getTempBasalRate()),
                        FormatKit.getInstance().formatMinutesAsDHM(record.getTempBasalMinutesRemaining())));
            }
        }

        sb.append(sb.length() == 0 ? "|" : " |");

        if (record.isCgmActive()) {
            if (!shorten && !(record.isCgmException() && sb.length() > 5)) {
                sb.append(sb.length() == 0 ? "" : " ");
                sb.append(FormatKit.getInstance().formatAsPercent(record.getTransmitterBattery()));
            }

            if (record.getCalibrationDueMinutes() > 0) {
                sb.append(sb.length() == 0 ? "" : " ");
                sb.append(record.getCalibrationDueMinutes() < 100
                        ? record.getCalibrationDueMinutes() + FormatKit.getInstance().getString(R.string.minute_m)
                        : (shorten
                        ? (record.getCalibrationDueMinutes() + 30) / 60 + FormatKit.getInstance().getString(R.string.hour_h)
                        : record.getCalibrationDueMinutes() / 60 + FormatKit.getInstance().getString(R.string.hour_h)
                        + record.getCalibrationDueMinutes() % 60 + FormatKit.getInstance().getString(R.string.minute_m)));
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

        } else if (record.isCgmLostSensor()) {
            sb.append(sb.length() == 0 ? "" : " ");
            sb.append(shorten
                    ? FormatKit.getInstance().getString(R.string.nightscout_pump_pill__lost_sensor_abreviation)
                    : FormatKit.getInstance().getString(R.string.nightscout_pump_pill__lost_sensor));
        } else {
            sb.append(sb.length() == 0 ? "" : " ");
            sb.append(FormatKit.getInstance().getString(R.string.nightscout_pump_pill__no_cgm));
        }

        if (info.length() > 0) {
            sb.append(sb.length() == 0 ? "" : " ");
            sb.append(info);
        }

        return sb.toString();
    }

    private void cleanupCheck() throws Exception {
        Log.d(TAG, "running cleanup check");
        long now = System.currentTimeMillis();

        if (dataStore.isNightscoutInitCleanup()) {

            String cleanFrom = formatDateForNS(now - dataStore.getSysPumpHistoryDays() * 24 * 60 * 60000L);
            String cleanTo = formatDateForNS(now);

            // delete debug notes
            while (deleteTreatments(treatmentsEndpoints.findNotesRegex(
                    "2017", cleanTo,
                    "debug",
                    "20").execute()) == 20) ;
            while (deleteTreatments(treatmentsEndpoints.findNotesRegex(
                    "2017", cleanTo,
                    "Debug",
                    "20").execute()) == 20) ;

            // delete events that have changed for v.7.0
            while (deleteTreatments(treatmentsEndpoints.findKeyRegexNoPumpMAC(
                    cleanFrom, cleanTo,
                    "BG Check", "", "20").execute()) == 20) ;
            while (deleteTreatments(treatmentsEndpoints.findNotesRegexNoPumpMAC(
                    cleanFrom, cleanTo,
                    "Sensor changed", "", "", "20").execute()) == 20) ;
            while (deleteTreatments(treatmentsEndpoints.findNotesRegexNoPumpMAC(
                    cleanFrom, cleanTo,
                    "Pump battery changed", "", "", "20").execute()) == 20) ;
            while (deleteTreatments(treatmentsEndpoints.findNotesRegexNoPumpMAC(
                    cleanFrom, cleanTo,
                    "Reservoir changed", "", "", "20").execute()) == 20) ;

            storeRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {
                    dataStore.setNightscoutInitCleanup(false);
                }
            });

        } else {

            // clean up any old alarm or system messages that may remain in NS when multiple uploaders are in use
            String cleanTo = formatDateForNS(now - 24 * 60 * 60000L);

            while (deleteTreatments(treatmentsEndpoints.findKeyRegex(
                    "2017",
                    cleanTo,
                    "SYS", "20").execute()) == 20) ;

            if (!(dataStore.isNsEnableAlarms() && dataStore.getNsAlarmTTL() == 0)) {
                while (deleteTreatments(treatmentsEndpoints.findKeyRegex(
                        "2017",
                        dataStore.isNsEnableAlarms()
                                ? formatDateForNS(now - dataStore.getNsAlarmTTL() * 60 * 60000L)
                                : cleanTo,
                        "ALARM", "20").execute()) == 20) ;
            }
        }
    }

    private void cleanupNonKeyed() throws Exception {
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

        long limit = dataStore.getInitTimestamp();

        if (cgmTo == 0) cgmTo = limit;
        if (pumpTo == 0) pumpTo = limit;

        Log.d(TAG, "cleanup: limit date " + formatDateForNS(limit));

        if (cgmFrom < cgmTo) {
            Log.d(TAG, "cleanup: entries (cgm history) " + formatDateForNS(cgmFrom) + " to " + formatDateForNS(cgmTo));

            Response<ResponseBody> responseBody = CLEAN_COMPLETE
                    ? entriesEndpoints.deleteCleanupItems(
                    String.valueOf(cgmFrom), String.valueOf(cgmTo)).execute()
                    : entriesEndpoints.deleteCleanupItemsNonKeyed(
                    String.valueOf(cgmFrom), String.valueOf(cgmTo), "").execute();

            if (responseBody.isSuccessful()) {
                Log.d(TAG, "cleanup: bulk deleted entries");

                storeRealm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(@NonNull Realm realm) {
                        dataStore.setNightscoutCgmCleanFrom(cgmFrom);
                    }
                });

            } else {
                Log.d(TAG, "no DELETE response from nightscout site");
            }

        }

        if (pumpFrom < pumpTo) {

            // cleaning treatments can be slow, limit the period per pass
            long limiter = 7 * 24 * 60 * 60000L;
            if (pumpTo - pumpFrom > limiter) pumpFrom = pumpTo - limiter;

            Log.d(TAG, "cleanup: treatments (pump history) " + formatDateForNS(pumpFrom) + " to " + formatDateForNS(pumpTo));

            int result;
            do {
                result = deleteTreatments(
                        CLEAN_COMPLETE
                                ? treatmentsEndpoints.findDateRangeCount(
                                formatDateForNS(pumpFrom),
                                formatDateForNS(pumpTo),
                                "20").execute()
                                : treatmentsEndpoints.findCleanupItems(
                                formatDateForNS(pumpFrom),
                                formatDateForNS(pumpTo),
                                "Note", "", "20").execute()
                );
            } while (result == 20);

            if (result >= 0) {
                final long pumpFromFinal = pumpFrom;
                storeRealm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(@NonNull Realm realm) {
                        dataStore.setNightscoutPumpCleanFrom(pumpFromFinal);
                    }
                });
            }
        }
    }

    private int deleteTreatments(Response<List<TreatmentsEndpoints.Treatment>> response) throws Exception {
        int result = 0;
        if (response.isSuccessful()) {
            List<TreatmentsEndpoints.Treatment> list = response.body();
            for (TreatmentsEndpoints.Treatment item : list) {
                Response<ResponseBody> responseBody = dataStore.isNightscoutUseQuery()
                        ? treatmentsEndpoints.deleteID(item.getCreated_at(), item.get_id()).execute()
                        : treatmentsEndpoints.deleteID(item.get_id()).execute();
                if (responseBody.isSuccessful()) {
                    Log.d(TAG, String.format("deleted treatment ID: %s with KEY: %s MAC: %s DATE: %s QUERY: %s",
                            item.get_id(), item.getKey600(), item.getPumpMAC600(), item.getCreated_at(), dataStore.isNightscoutUseQuery()));
                } else {
                    Log.d(TAG, "no DELETE response from nightscout site");
                    return -1;
                }
                result++;
            }
        } else return -1;
        return result;
    }

}
