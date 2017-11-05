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

import info.nightscout.android.utils.ConfigurationStore;
import info.nightscout.api.UploadApi;
import info.nightscout.api.SgvEndpoints;
import info.nightscout.api.SgvEndpoints.SgvEntry;
import info.nightscout.api.MbgEndpoints;
import info.nightscout.api.MbgEndpoints.MbgEntry;
import info.nightscout.api.TreatmentEndpoints;
import info.nightscout.api.TreatmentEndpoints.TreatmentEntry;
import info.nightscout.api.TempBasalRateEndpoints.TempBasalRateEntry;
import info.nightscout.api.TempBasalRateEndpoints;
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

import static info.nightscout.android.medtronic.MainActivity.MMOLXLFACTOR;

class NightScoutUpload {
    private static final String TAG = NightScoutUpload.class.getSimpleName();

    private static final SimpleDateFormat ISO8601_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault());
    private static final SimpleDateFormat NOTE_DATE_FORMAT = new SimpleDateFormat("E h:mm a", Locale.getDefault());

    NightScoutUpload() {

    }

    boolean doRESTUpload(String url,
                         String secret,
                         boolean treatments,
                         int uploaderBatteryLevel,
                         List<PumpStatusEvent> records) throws Exception {
        return isUploaded(records, url, secret, treatments, uploaderBatteryLevel);
    }

    private boolean isUploaded(List<PumpStatusEvent> records,
                               String baseURL,
                               String secret,
                               boolean treatments,
                               int uploaderBatteryLevel) throws Exception {

        UploadApi uploadApi = new UploadApi(baseURL, formToken(secret));

        boolean eventsUploaded = uploadEvents(
                uploadApi.getSgvEndpoints(),
                uploadApi.getMbgEndpoints(),
                uploadApi.getTreatmentEndpoints(),
                uploadApi.getTempBasalRateEndpoints(),
                uploadApi.getTempBasalPercentEndpoints(),
                uploadApi.getTempBasalCancelEndpoints(),
                uploadApi.getNoteEndpoints(),
                records, treatments);

        boolean deviceStatusUploaded = uploadDeviceStatus(uploadApi.getDeviceEndpoints(),
                uploaderBatteryLevel, records);

        return eventsUploaded && deviceStatusUploaded;
    }

    private boolean uploadEvents(SgvEndpoints sgvEndpoints,
                                 MbgEndpoints mbgEndpoints,
                                 TreatmentEndpoints treatmentEndpoints,
                                 TempBasalRateEndpoints tempBasalRateEndpoints,
                                 TempBasalPercentEndpoints tempBasalPercentEndpoints,
                                 TempBasalCancelEndpoints tempBasalCancelEndpoints,
                                 NoteEndpoints noteEndpoints,
                                 List<PumpStatusEvent> records,
                                 boolean treatments) throws Exception {


        List<SgvEntry> sgvEntries = new ArrayList<>();
        List<MbgEntry> mbgEntries = new ArrayList<>();
        List<TreatmentEntry> treatmentEntries = new ArrayList<>();
        List<TempBasalRateEntry> tempBasalRateEntries = new ArrayList<>();
        List<TempBasalPercentEntry> tempBasalPercentEntries = new ArrayList<>();
        List<TempBasalCancelEntry> tempBasalCancelEntries = new ArrayList<>();
        List<NoteEntry> noteEntries = new ArrayList<>();

        for (PumpStatusEvent record : records) {
/*
            if (record.isValidSGV()) {
                SgvEntry sgvEntry = new SgvEntry();
                sgvEntry.setType("sgv");
                sgvEntry.setDirection(EntriesSerializer.getDirectionStringStatus(record.getCgmTrend()));
                sgvEntry.setDevice(record.getDeviceName());
                sgvEntry.setSgv(record.getSgv());
                sgvEntry.setDate(record.getCgmDate().getTime());
                sgvEntry.setDateString(record.getCgmDate().toString());
                sgvEntries.add(sgvEntry);
            }
*/
            if (record.isValidBGL()) {

                MbgEntry mbgEntry = new MbgEntry();
                mbgEntry.setType("mbg");
                mbgEntry.setDate(record.getEventDate().getTime());
                mbgEntry.setDateString(record.getEventDate().toString());
                mbgEntry.setDevice(record.getDeviceName());
                mbgEntry.setMbg(record.getRecentBGL());
                mbgEntry.setSgv(record.getSgv());
                mbgEntries.add(mbgEntry);

                // cgm offline or not in use (needed for NS to show bgl when no sgv data)
                if (!record.isCgmActive() || record.isCgmWarmUp()) {
                    ConfigurationStore configurationStore = ConfigurationStore.getInstance();
                    BigDecimal bgl;
                    String units;
                    if (configurationStore.isMmolxl()) {
                        bgl = new BigDecimal(record.getRecentBGL() / MMOLXLFACTOR).setScale(1, BigDecimal.ROUND_HALF_UP);
                        units = "mmol";
                    } else {
                        bgl = new BigDecimal(record.getRecentBGL()).setScale(0);
                        units = "mg/dl";
                    }

                    TreatmentEntry treatmentEntry = new TreatmentEntry();
                    treatmentEntry.setCreatedAt(ISO8601_DATE_FORMAT.format(record.getEventDate()));
                    treatmentEntry.setEventType("BG Check");
                    treatmentEntry.setGlucoseType("Finger");
                    treatmentEntry.setGlucose(bgl);
                    treatmentEntry.setUnits(units);
                    treatmentEntries.add(treatmentEntry);
                }
            }

            if (treatments) {

                if (record.isValidBolus()) {

                    if (record.isValidBolusDual()) {
                        TreatmentEntry treatmentEntry = new TreatmentEntry();
                        treatmentEntry.setCreatedAt(ISO8601_DATE_FORMAT.format(record.getLastBolusDate()));
                        treatmentEntry.setEventType("Bolus");
                        treatmentEntry.setInsulin(record.getLastBolusAmount());
                        treatmentEntry.setNotes("Dual bolus normal part delivered: " + record.getLastBolusAmount() + "u");
                        treatmentEntries.add(treatmentEntry);

                    } else if (record.isValidBolusSquare()) {
                        TreatmentEntry treatmentEntry = new TreatmentEntry();
                        treatmentEntry.setCreatedAt(ISO8601_DATE_FORMAT.format(record.getLastBolusDate()));
                        treatmentEntry.setEventType("Combo Bolus");
                        treatmentEntry.setDuration(record.getLastBolusDuration());
                        treatmentEntry.setSplitNow("0");
                        treatmentEntry.setSplitExt("100");
                        treatmentEntry.setRelative(2);
                        treatmentEntry.setEnteredinsulin(String.valueOf(record.getLastBolusAmount()));
                        treatmentEntries.add(treatmentEntry);

                        noteEntries.add(new NoteEntry(
                                "Announcement",
                                ISO8601_DATE_FORMAT.format(record.getLastBolusDate().getTime() + (record.getLastBolusDuration() * 60 * 1000)),
                                "Square bolus delivered: " + record.getLastBolusAmount() + "u Duration: " + record.getLastBolusDuration() + " minutes"
                        ));

                    } else {
                        TreatmentEntry treatmentEntry = new TreatmentEntry();
                        treatmentEntry.setCreatedAt(ISO8601_DATE_FORMAT.format(record.getLastBolusDate()));
                        treatmentEntry.setEventType("Bolus");
                        treatmentEntry.setInsulin(record.getLastBolusAmount());
                        treatmentEntries.add(treatmentEntry);
                    }
                }

                if (record.isValidTEMPBASAL()) {
                    if (record.getTempBasalMinutesRemaining() > 0 && record.getTempBasalPercentage() > 0) {
                        tempBasalPercentEntries.add(new TempBasalPercentEntry(
                                ISO8601_DATE_FORMAT.format(record.getEventDate()),
                                "Temp Basal started approx: " + NOTE_DATE_FORMAT.format(record.getTempBasalAfterDate()) + " - " + NOTE_DATE_FORMAT.format(record.getTempBasalBeforeDate()),
                                record.getTempBasalMinutesRemaining(),
                                record.getTempBasalPercentage() - 100
                        ));
                    } else if (record.getTempBasalMinutesRemaining() > 0) {
                        tempBasalRateEntries.add(new TempBasalRateEntry(
                                ISO8601_DATE_FORMAT.format(record.getEventDate()),
                                "Temp Basal started approx: " + NOTE_DATE_FORMAT.format(record.getTempBasalAfterDate()) + " - " + NOTE_DATE_FORMAT.format(record.getTempBasalBeforeDate()),
                                record.getTempBasalMinutesRemaining(),
                                record.getTempBasalRate()
                        ));
                    } else {
                        tempBasalCancelEntries.add(new TempBasalCancelEntry(
                                ISO8601_DATE_FORMAT.format(record.getEventDate()),
                                "Temp Basal stopped approx: " + NOTE_DATE_FORMAT.format(record.getTempBasalAfterDate()) + " - " + NOTE_DATE_FORMAT.format(record.getTempBasalBeforeDate())
                        ));
                    }
                }

                if (record.isValidSUSPEND()) {
                    tempBasalRateEntries.add(new TempBasalRateEntry(
                            ISO8601_DATE_FORMAT.format(record.getEventDate()),
                            "Pump suspended insulin delivery approx: " + NOTE_DATE_FORMAT.format(record.getSuspendAfterDate()) + " - " + NOTE_DATE_FORMAT.format(record.getSuspendBeforeDate()),
                            60,
                            0
                    ));
                }
                if (record.isValidSUSPENDOFF()) {
                    tempBasalCancelEntries.add(new TempBasalCancelEntry(
                            ISO8601_DATE_FORMAT.format(record.getEventDate()),
                            "Pump resumed insulin delivery approx: " + NOTE_DATE_FORMAT.format(record.getSuspendAfterDate()) + " - " + NOTE_DATE_FORMAT.format(record.getSuspendBeforeDate())
                    ));
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

        }

        boolean uploaded = true;
        if (sgvEntries.size() > 0) {
            Response<ResponseBody> result = sgvEndpoints.sendEntries(sgvEntries).execute();
            uploaded = result.isSuccessful();
        }
        if (mbgEntries.size() > 0) {
            Response<ResponseBody> result = mbgEndpoints.sendEntries(mbgEntries).execute();
            uploaded = uploaded && result.isSuccessful();
        }
        if (treatmentEntries.size() > 0) {
            Response<ResponseBody> result = treatmentEndpoints.sendEntries(treatmentEntries).execute();
            uploaded = uploaded && result.isSuccessful();
        }
        if (tempBasalRateEntries.size() > 0) {
            Response<ResponseBody> result = tempBasalRateEndpoints.sendEntries(tempBasalRateEntries).execute();
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
                    statusCGM += shorten ? "cal" : "calibrating";
                else if (record.isCgmCalibrationComplete())
                    statusCGM += shorten ? "cal" : "cal.complete";
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
