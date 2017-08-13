package info.nightscout.android.upload.nightscout;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.upload.nightscout.serializer.EntriesSerializer;

import android.support.annotation.NonNull;

import info.nightscout.api.UploadApi;
import info.nightscout.api.GlucoseEndpoints;
import info.nightscout.api.GlucoseEndpoints.GlucoseEntry;
import info.nightscout.api.BolusEndpoints;
import info.nightscout.api.BolusEndpoints.BolusEntry;
import info.nightscout.api.TreatmentEndpoints;
import info.nightscout.api.TreatmentEndpoints.TreatmentEntry;
import info.nightscout.api.TempBasalAbsoluteEndpoints.TempBasalAbsoluteEntry;
import info.nightscout.api.TempBasalAbsoluteEndpoints;
import info.nightscout.api.TempBasalPercentEndpoints;
import info.nightscout.api.TempBasalPercentEndpoints.TempBasalPercentEntry;
import info.nightscout.api.TempBasalCancelEndpoints;
import info.nightscout.api.TempBasalCancelEndpoints.TempBasalCancelEntry;
import info.nightscout.api.NoteEndpoints;
import info.nightscout.api.NoteEndpoints.NoteEntry;
import info.nightscout.api.DeviceEndpoints;
import info.nightscout.api.DeviceEndpoints.Iob;
import info.nightscout.api.DeviceEndpoints.Battery;
import info.nightscout.api.DeviceEndpoints.PumpStatus;
import info.nightscout.api.DeviceEndpoints.PumpInfo;
import info.nightscout.api.DeviceEndpoints.DeviceStatus;
import okhttp3.ResponseBody;
import retrofit2.Response;

class NightScoutUpload {

    private static final String TAG = NightscoutUploadIntentService.class.getSimpleName();
    private static final SimpleDateFormat ISO8601_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault());
    private static final SimpleDateFormat NOTE_DATE_FORMAT = new SimpleDateFormat("E h:mm a", Locale.getDefault());

    NightScoutUpload() {

    }

    boolean doRESTUpload(String url,
                         String secret,
                         int uploaderBatteryLevel,
                         List<PumpStatusEvent> records) throws Exception {
        return isUploaded(records, url, secret, uploaderBatteryLevel);
    }

    private boolean isUploaded(List<PumpStatusEvent> records,
                               String baseURL,
                               String secret,
                               int uploaderBatteryLevel) throws Exception {

        UploadApi uploadApi = new UploadApi(baseURL, formToken(secret));

        boolean eventsUploaded = uploadEvents(
                uploadApi.getGlucoseEndpoints(),
                uploadApi.getBolusEndpoints(),
                uploadApi.getTreatmentEndpoints(),
                uploadApi.getTempBasalAbsoluteEndpoints(),
                uploadApi.getTempBasalPercentEndpoints(),
                uploadApi.getTempBasalCancelEndpoints(),
                uploadApi.getNoteEndpoints(),
                records);

        boolean deviceStatusUploaded = uploadDeviceStatus(uploadApi.getDeviceEndpoints(),
                uploaderBatteryLevel, records);

        return eventsUploaded && deviceStatusUploaded;
    }

    private boolean uploadEvents(GlucoseEndpoints glucoseEndpoints,
                                 BolusEndpoints bolusEndpoints,
                                 TreatmentEndpoints treatmentEndpoints,
                                 TempBasalAbsoluteEndpoints tempBasalAbsoluteEndpoints,
                                 TempBasalPercentEndpoints tempBasalPercentEndpoints,
                                 TempBasalCancelEndpoints tempBasalCancelEndpoints,
                                 NoteEndpoints noteEndpoints,
                                 List<PumpStatusEvent> records) throws Exception {


        List<GlucoseEntry> glucoseEntries = new ArrayList<>();
        List<BolusEntry> bolusEntries = new ArrayList<>();
        List<TreatmentEntry> treatmentEntries = new ArrayList<>();
        List<TempBasalAbsoluteEntry> tempBasalAbsoluteEntries = new ArrayList<>();
        List<TempBasalPercentEntry> tempBasalPercentEntries = new ArrayList<>();
        List<TempBasalCancelEntry> tempBasalCancelEntries = new ArrayList<>();
        List<NoteEntry> noteEntries = new ArrayList<>();

        for (PumpStatusEvent record : records) {

            if (record.isValidSGV()) {
                GlucoseEntry glucoseEntry = new GlucoseEntry();
                glucoseEntry.setType("sgv");
                glucoseEntry.setDirection(EntriesSerializer.getDirectionStringStatus(record.getCgmTrend()));
                glucoseEntry.setDevice(record.getDeviceName());
                glucoseEntry.setSgv(record.getSgv());
                glucoseEntry.setDate(record.getCgmDate().getTime());
                glucoseEntry.setDateString(record.getCgmDate().toString());
                glucoseEntries.add(glucoseEntry);
            }

            if (record.isValidBGL()) {
                BolusEntry bolusEntry = new BolusEntry();
                bolusEntry.setType("mbg");
                bolusEntry.setDate(record.getEventDate().getTime());
                bolusEntry.setDateString(record.getEventDate().toString());
                bolusEntry.setDevice(record.getDeviceName());
                bolusEntry.setMbg(record.getRecentBGL());
                bolusEntries.add(bolusEntry);
            }

            if (record.isValidBolus()) {
                TreatmentEntry treatmentEntry = new TreatmentEntry();
                treatmentEntry.setCreatedAt(ISO8601_DATE_FORMAT.format(record.getLastBolusDate()));
                if (record.isValidBolusDual()) {
                    treatmentEntry.setEventType("Bolus");
                    treatmentEntry.setInsulin(record.getLastBolusAmount());
                    treatmentEntry.setNotes("Dual bolus normal part delivered: " + record.getLastBolusAmount() + "u");

                } else if (record.isValidBolusSquare()) {
                    treatmentEntry.setEventType("Combo Bolus");
                    treatmentEntry.setDuration(record.getLastBolusDuration());
                    treatmentEntry.setSplitNow("0");
                    treatmentEntry.setSplitExt("100");
                    treatmentEntry.setRelative(2);
                    treatmentEntry.setEnteredinsulin(String.valueOf(record.getLastBolusAmount()));

                    noteEntries.add(new NoteEntry(
                            "Announcement",
                            ISO8601_DATE_FORMAT.format(record.getLastBolusDate().getTime() + (record.getLastBolusDuration() * 60 * 1000)),
                            "Square bolus delivered: " + record.getLastBolusAmount() + "u Duration: " + record.getLastBolusDuration() + " minutes"
                    ));
                } else {
                    treatmentEntry.setEventType("Bolus");
                    treatmentEntry.setInsulin(record.getLastBolusAmount());
                }
                treatmentEntries.add(treatmentEntry);
            }

            if (record.isValidTEMPBASAL()) {
                if (record.getTempBasalMinutesRemaining() > 0 && record.getTempBasalPercentage() > 0) {
                    tempBasalPercentEntries.add(new TempBasalPercentEntry(
                            ISO8601_DATE_FORMAT.format(record.getEventDate()),
                            "Temp Basal started approx: " + NOTE_DATE_FORMAT.format(record.getTempBasalAfterDate()) + " - " + NOTE_DATE_FORMAT.format(record.getEventDate()),
                            record.getTempBasalMinutesRemaining(),
                            record.getTempBasalPercentage() - 100
                    ));
                } else if (record.getTempBasalMinutesRemaining() > 0) {
                    tempBasalAbsoluteEntries.add(new TempBasalAbsoluteEntry(
                            ISO8601_DATE_FORMAT.format(record.getEventDate()),
                            "Temp Basal started approx: " + NOTE_DATE_FORMAT.format(record.getTempBasalAfterDate()) + " - " + NOTE_DATE_FORMAT.format(record.getEventDate()),
                            record.getTempBasalMinutesRemaining(),
                            record.getTempBasalRate()
                    ));
                } else {
                    tempBasalCancelEntries.add(new TempBasalCancelEntry(
                            ISO8601_DATE_FORMAT.format(record.getEventDate()),
                            "Temp Basal stopped approx: " + NOTE_DATE_FORMAT.format(record.getTempBasalAfterDate()) + " - " + NOTE_DATE_FORMAT.format(record.getEventDate())
                    ));
                }
            }

            if (record.isValidSAGE()) {
                noteEntries.add(new NoteEntry(
                        "Sensor Start",
                        ISO8601_DATE_FORMAT.format(record.getSageAfterDate().getTime() - (record.getSageAfterDate().getTime() - record.getSageBeforeDate().getTime()) / 2),
                        "Sensor changed approx: " + NOTE_DATE_FORMAT.format(record.getSageAfterDate()) + " - " + NOTE_DATE_FORMAT.format(record.getSageBeforeDate())
                ));
            }
            if (record.isValidCAGE()) {
                noteEntries.add(new NoteEntry(
                        "Site Change",
                        ISO8601_DATE_FORMAT.format(record.getCageAfterDate().getTime() - (record.getCageAfterDate().getTime() - record.getCageBeforeDate().getTime()) / 2),
                        "Reservoir changed approx: " + NOTE_DATE_FORMAT.format(record.getCageAfterDate()) + " - " + NOTE_DATE_FORMAT.format(record.getCageBeforeDate())
                ));
            }
            if (record.isValidBATTERY()) {
                noteEntries.add(new NoteEntry(
                        "Note",
                        ISO8601_DATE_FORMAT.format(record.getBatteryAfterDate().getTime() - (record.getBatteryAfterDate().getTime() - record.getBatteryBeforeDate().getTime()) / 2),
                        "Pump battery changed approx: " + NOTE_DATE_FORMAT.format(record.getBatteryAfterDate()) + " - " + NOTE_DATE_FORMAT.format(record.getBatteryBeforeDate())
                ));
            }

        }

        boolean uploaded = true;
        if (glucoseEntries.size() > 0) {
            Response<ResponseBody> result = glucoseEndpoints.sendEntries(glucoseEntries).execute();
            uploaded = result.isSuccessful();
        }
        if (bolusEntries.size() > 0) {
            Response<ResponseBody> result = bolusEndpoints.sendEntries(bolusEntries).execute();
            uploaded = uploaded && result.isSuccessful();
        }
        if (treatmentEntries.size() > 0) {
            Response<ResponseBody> result = treatmentEndpoints.sendEntries(treatmentEntries).execute();
            uploaded = uploaded && result.isSuccessful();
        }
        if (tempBasalAbsoluteEntries.size() > 0) {
            Response<ResponseBody> result = tempBasalAbsoluteEndpoints.sendEntries(tempBasalAbsoluteEntries).execute();
            uploaded = uploaded && result.isSuccessful();
        }
        if (tempBasalPercentEntries.size() > 0) {
            Response<ResponseBody> result = tempBasalPercentEndpoints.sendEntries(tempBasalPercentEntries).execute();
            uploaded = uploaded && result.isSuccessful();
        }
        if (tempBasalCancelEntries.size() > 0) {
            Response<ResponseBody> result = tempBasalCancelEndpoints.sendEntries(tempBasalCancelEntries).execute();
            uploaded = uploaded && result.isSuccessful();
        }
        if (noteEntries.size() > 0) {
            Response<ResponseBody> result = noteEndpoints.sendEntries(noteEntries).execute();
            uploaded = uploaded && result.isSuccessful();
        }
        return uploaded;
    }

    private boolean uploadDeviceStatus(DeviceEndpoints deviceEndpoints,
                                       int uploaderBatteryLevel,
                                       List<PumpStatusEvent> records) throws Exception {


        List<DeviceStatus> deviceEntries = new ArrayList<>();
        for (PumpStatusEvent record : records) {

            Iob iob = new Iob(record.getEventDate(), record.getActiveInsulin());
            Battery battery = new Battery(record.getBatteryPercentage());
            PumpStatus pumpstatus;

            String statusPUMP = "normal";
            if (record.isBolusingNormal())
                statusPUMP = "bolusing";
            else if (record.isBolusingSquare())
                statusPUMP = "square>>" + record.getBolusingDelivered() + "u-" + (record.getBolusingMinutesRemaining() >= 60 ? record.getBolusingMinutesRemaining() / 60 + "h" : "") + record.getBolusingMinutesRemaining() % 60 + "m";
            else if (record.isBolusingDual())
                statusPUMP = "dual>>" + record.getBolusingDelivered() + "u-" + (record.getBolusingMinutesRemaining() >= 60 ? record.getBolusingMinutesRemaining() / 60 + "h" : "") + record.getBolusingMinutesRemaining() % 60 + "m";
            else if (record.isSuspended())
                statusPUMP = "suspended";
            else if (record.getTempBasalMinutesRemaining() > 0 & record.getTempBasalPercentage() != 0)
                statusPUMP = "temp>>" + record.getTempBasalPercentage() + "%-" + (record.getTempBasalMinutesRemaining() >= 60 ? record.getTempBasalMinutesRemaining() / 60 + "h" : "") + record.getTempBasalMinutesRemaining() % 60 + "m";
            else if (record.getTempBasalMinutesRemaining() > 0)
                statusPUMP = "temp>>" + record.getTempBasalRate() + "u-" + (record.getTempBasalMinutesRemaining() >= 60 ? record.getTempBasalMinutesRemaining() / 60 + "h" : "") + record.getTempBasalMinutesRemaining() % 60 + "m";
            if (record.getAlert() > 0)
                statusPUMP = "⚠ " + statusPUMP;

            String statusCGM = "";
            String statusCGMbattery = "";
            if (record.isCgmActive()) {
                if (record.getTransmitterBattery() > 80)
                    statusCGMbattery = "::::";
                else if (record.getTransmitterBattery() > 55)
                    statusCGMbattery = ":::.";
                else if (record.getTransmitterBattery() > 30)
                    statusCGMbattery = "::..";
                else if (record.getTransmitterBattery() > 10)
                    statusCGMbattery = ":...";
                else
                    statusCGMbattery = "....";
                if (record.isCgmCalibrating())
                    statusCGM += " calibrating";
                else if (record.isCgmCalibrationComplete())
                    statusCGM += " cal.complete";
                else {
                    if (record.isCgmWarmUp())
                        statusCGM += "warmup ";
                    if (record.getCalibrationDueMinutes() > 0)
                        statusCGM += (record.getCalibrationDueMinutes() >= 60 ? record.getCalibrationDueMinutes() / 60 + "h" : "") + record.getCalibrationDueMinutes() % 60 + "m";
                    else
                        statusCGM += "calibrate now!";
                }
            } else {
                statusCGM+= "cgm n/a";
            }

            pumpstatus = new PumpStatus(false, false, statusPUMP + " " +  statusCGMbattery + " " + statusCGM);

            PumpInfo pumpInfo = new PumpInfo(
                    ISO8601_DATE_FORMAT.format(record.getEventDate()),
                    new BigDecimal(record.getReservoirAmount()).setScale(0, BigDecimal.ROUND_HALF_UP),
                    iob,
                    battery,
                    pumpstatus
            );

            DeviceStatus deviceStatus = new DeviceStatus(
                    uploaderBatteryLevel,
                    record.getDeviceName(),
                    ISO8601_DATE_FORMAT.format(record.getEventDate()),
                    pumpInfo
            );

            deviceEntries.add(deviceStatus);
        }

        boolean uploaded = true;
        for (DeviceStatus status : deviceEntries) {
            Response<ResponseBody> result = deviceEndpoints.sendDeviceStatus(status).execute();
            uploaded = uploaded && result.isSuccessful();
        }

        return uploaded;
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
