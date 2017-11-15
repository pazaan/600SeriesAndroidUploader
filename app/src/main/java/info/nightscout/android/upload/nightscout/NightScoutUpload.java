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

import android.support.annotation.NonNull;

import info.nightscout.android.utils.ConfigurationStore;
import info.nightscout.api.UploadApi;
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
                records, treatments);

        boolean deviceStatusUploaded = uploadDeviceStatus(uploadApi.getDeviceEndpoints(),
                uploaderBatteryLevel, records);

        return eventsUploaded && deviceStatusUploaded;
    }

    private boolean uploadEvents(
                                 List<PumpStatusEvent> records,
                                 boolean treatments) throws Exception {

/*
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
*/

        boolean uploaded = true;

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
