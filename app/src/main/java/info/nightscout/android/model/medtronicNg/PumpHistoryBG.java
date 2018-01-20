package info.nightscout.android.model.medtronicNg;

import android.util.Log;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.android.medtronic.PumpHistoryParser;
import info.nightscout.android.model.store.DataStore;
import info.nightscout.api.EntriesEndpoints;
import info.nightscout.api.TreatmentsEndpoints;
import info.nightscout.api.UploadItem;
import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.annotations.Ignore;
import io.realm.annotations.Index;

import static info.nightscout.android.medtronic.MainActivity.MMOLXLFACTOR;

/**
 * Created by Pogman on 26.10.17.
 */

public class PumpHistoryBG extends RealmObject implements PumpHistoryInterface {
    @Ignore
    private static final String TAG = PumpHistoryBG.class.getSimpleName();

    @Index
    private Date eventDate;

    @Index
    private boolean uploadREQ = false;
    private boolean uploadACK = false;

    private boolean xdripREQ = false;
    private boolean xdripACK = false;

    private String key; // unique identifier for nightscout, key = "ID" + RTC as 8 char hex ie. "CGM6A23C5AA"

    @Index
    private int bgRTC;
    private int bgOffset;
    private Date bgDate;

    private byte bgUnits;
    private byte bgSource;
    private byte bgContext;

    private int bg;
    private String serial;
    private boolean calibrationFlag;

    private boolean calibration;
    private Date calibrationDate;

    @Index
    private int calibrationRTC;
    private int calibrationOFFSET;
    private double calibrationFactor;
    private int calibrationTarget;

    @Override
    public List nightscout(DataStore dataStore) {
        List<UploadItem> uploadItems = new ArrayList<>();

        if (dataStore.isNsEnableTreatments() && dataStore.isNsEnableFingerBG()) {

            if (PumpHistoryParser.BG_CONTEXT.BG_SENT_FOR_CALIB.equals(bgContext)
                    && !dataStore.isNsEnableCalibrationInfo()) {
                return uploadItems;
            }

            UploadItem uploadItem = new UploadItem();
            uploadItems.add(uploadItem);
            TreatmentsEndpoints.Treatment treatment = uploadItem.ack(uploadACK).treatment();

            String notes = "";

            BigDecimal bgl;
            String units;
            if (PumpHistoryParser.BG_UNITS.MG_DL.equals(bgUnits)) {
                bgl = new BigDecimal(bg);
                units = "mg/dl";
            } else {
                bgl = new BigDecimal(bg / MMOLXLFACTOR).setScale(1, BigDecimal.ROUND_HALF_UP);
                units = "mmol";
            }
            treatment.setKey600(key);
            treatment.setCreated_at(bgDate);

            treatment.setEventType("BG Check");
            treatment.setGlucoseType("Finger");
            treatment.setGlucose(bgl);
            treatment.setUnits(units);

            if (calibration && dataStore.isNsEnableCalibrationInfo()) {
                long seconds = (calibrationDate.getTime() - bgDate.getTime()) / 1000;
                notes += "CAL: ⋊ " + calibrationFactor + " (" + (seconds / 60) + "m" + (seconds % 60) + "s)";
                uploadItem.update();
            }

            //notes += " [DEBUG: bgContext=" + bgContext + " " + PumpHistoryParser.BG_CONTEXT.convert(bgContext).name() + " bgSource=" + bgSource + " cal=" + calibrationFlag + " rtc=" + String.format("%08X", bgRTC) + "]";

            if (!notes.equals("")) treatment.setNotes(notes);

            // insert BG reading as CGM chart entry
            if (dataStore.isNsEnableTreatments() && dataStore.isNsEnableInsertBGasCGM()
                    && !PumpHistoryParser.BG_CONTEXT.BG_SENT_FOR_CALIB.equals(bgContext)) {

                UploadItem uploadItem1 = new UploadItem();
                uploadItems.add(uploadItem1);
                EntriesEndpoints.Entry entry = uploadItem1.ack(uploadACK).entry();

                entry.setKey600(key + "CGM");
                entry.setType("sgv");
                entry.setDate(bgDate.getTime());
                entry.setDateString(bgDate.toString());
                entry.setSgv(bg);
            }

        }

        return uploadItems;
    }

    public static void bg(Realm realm, Date eventDate, int eventRTC, int eventOFFSET,
                          boolean calibrationFlag,
                          int bg,
                          byte bgUnits,
                          byte bgSource,
                          byte bgContext,
                          String serial) {

        switch (PumpHistoryParser.BG_CONTEXT.convert(bgContext)) {
            case BG_READING_RECEIVED:
                break;
            case USER_ACCEPTED_REMOTE_BG:
                return;
            case USER_REJECTED_REMOTE_BG:
                return;
            case REMOTE_BG_ACCEPTANCE_SCREEN_TIMEOUT:
                return;
            case BG_SI_PASS_RESULT_RECD_FRM_GST:
                return;
            case BG_SI_FAIL_RESULT_RECD_FRM_GST:
                return;
            case BG_SENT_FOR_CALIB:
                break;
            case USER_REJECTED_SENSOR_CALIB:
                return;
            case ENTERED_IN_BG_ENTRY:
                break;
            case ENTERED_IN_MEAL_WIZARD:
                break;
            case ENTERED_IN_BOLUS_WIZRD:
                break;
            case ENTERED_IN_SENSOR_CALIB:
                break;
            case ENTERED_AS_BG_MARKER:
                break;
        }

        PumpHistoryBG object = realm.where(PumpHistoryBG.class)
                .equalTo("bgRTC", eventRTC)
                .findFirst();

        if (object == null) {
            // look for a calibration
            object = realm.where(PumpHistoryBG.class)
                    .equalTo("calibration", true)
                    .equalTo("calibrationFlag", false)
                    .greaterThan("calibrationRTC", eventRTC)
                    .lessThan("calibrationRTC", eventRTC + 20 * 60)
                    .findFirst();
            if (object == null) {
                Log.d(TAG, "*new*" + " bg");
                object = realm.createObject(PumpHistoryBG.class);
                object.setEventDate(eventDate);
            } else {
                Log.d(TAG, "*update*" + " add bg to calibration");
            }

            object.setBgDate(eventDate);
            object.setBgRTC(eventRTC);
            object.setBgOffset(eventOFFSET);
            object.setCalibrationFlag(calibrationFlag);
            object.setBg(bg);
            object.setBgUnits(bgUnits);
            object.setBgSource(bgSource);
            object.setBgContext(bgContext);
            object.setSerial(serial);
            object.setKey("BG" + String.format("%08X", eventRTC));
            object.setUploadREQ(true);

        } else if (PumpHistoryParser.BG_CONTEXT.NA.equals(bgContext) && calibrationFlag && !object.isCalibrationFlag()){
            Log.d(TAG, "*update*" + " bg used for calibration");
            object.setCalibrationFlag(true);
        }
    }

    public static void calibration(Realm realm, Date eventDate, int eventRTC, int eventOFFSET,
                                   double calFactor,
                                   int bgTarget) {

        PumpHistoryBG object = realm.where(PumpHistoryBG.class)
                .equalTo("calibrationRTC", eventRTC)
                .findFirst();
        if (object == null) {
            // look for a bg
            object = realm.where(PumpHistoryBG.class)
                    .greaterThan("bgRTC", eventRTC - 20 * 60)
                    .lessThan("bgRTC", eventRTC)
                    .equalTo("calibrationFlag", true)
                    .equalTo("calibration", false)
                    .findFirst();
            if (object == null) {
                Log.d(TAG, "*new*" + " calibration");
                object = realm.createObject(PumpHistoryBG.class);
                object.setEventDate(eventDate);
            } else {
                Log.d(TAG, "*update*"  + " bg with calibration");
                object.setUploadREQ(true);
            }
            object.setCalibration(true);
            object.setCalibrationDate(eventDate);
            object.setCalibrationRTC(eventRTC);
            object.setCalibrationOFFSET(eventOFFSET);
            object.setCalibrationFactor(calFactor);
            object.setCalibrationTarget(bgTarget);
        }
    }

    @Override
    public Date getEventDate() {
        return eventDate;
    }

    @Override
    public void setEventDate(Date eventDate) {
        this.eventDate = eventDate;
    }

    @Override
    public boolean isUploadREQ() {
        return uploadREQ;
    }

    @Override
    public void setUploadREQ(boolean uploadREQ) {
        this.uploadREQ = uploadREQ;
    }

    @Override
    public boolean isUploadACK() {
        return uploadACK;
    }

    @Override
    public void setUploadACK(boolean uploadACK) {
        this.uploadACK = uploadACK;
    }

    @Override
    public boolean isXdripREQ() {
        return xdripREQ;
    }

    @Override
    public void setXdripREQ(boolean xdripREQ) {
        this.xdripREQ = xdripREQ;
    }

    @Override
    public boolean isXdripACK() {
        return xdripACK;
    }

    @Override
    public void setXdripACK(boolean xdripACK) {
        this.xdripACK = xdripACK;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public void setKey(String key) {
        this.key = key;
    }

    public Date getBgDate() {
        return bgDate;
    }

    public void setBgDate(Date bgDate) {
        this.bgDate = bgDate;
    }

    public int getBgRTC() {
        return bgRTC;
    }

    public void setBgRTC(int bgRTC) {
        this.bgRTC = bgRTC;
    }

    public int getBgOffset() {
        return bgOffset;
    }

    public void setBgOffset(int bgOffset) {
        this.bgOffset = bgOffset;
    }

    public int getBgUnits() {
        return bgUnits;
    }

    public void setBgUnits(byte bgUnits) {
        this.bgUnits = bgUnits;
    }

    public int getBgSource() {
        return bgSource;
    }

    public void setBgSource(byte bgSource) {
        this.bgSource = bgSource;
    }

    public byte getBgContext() {
        return bgContext;
    }

    public void setBgContext(byte bgContext) {
        this.bgContext = bgContext;
    }

    public int getBg() {
        return bg;
    }

    public void setBg(int bg) {
        this.bg = bg;
    }

    public String getSerial() {
        return serial;
    }

    public void setSerial(String serial) {
        this.serial = serial;
    }

    public boolean isCalibrationFlag() {
        return calibrationFlag;
    }

    public void setCalibrationFlag(boolean calibrationFlag) {
        this.calibrationFlag = calibrationFlag;
    }

    public boolean isCalibration() {
        return calibration;
    }

    public void setCalibration(boolean calibration) {
        this.calibration = calibration;
    }

    public Date getCalibrationDate() {
        return calibrationDate;
    }

    public void setCalibrationDate(Date calibrationDate) {
        this.calibrationDate = calibrationDate;
    }

    public int getCalibrationRTC() {
        return calibrationRTC;
    }

    public void setCalibrationRTC(int calibrationRTC) {
        this.calibrationRTC = calibrationRTC;
    }

    public int getCalibrationOFFSET() {
        return calibrationOFFSET;
    }

    public void setCalibrationOFFSET(int calibrationOFFSET) {
        this.calibrationOFFSET = calibrationOFFSET;
    }

    public double getCalibrationFactor() {
        return calibrationFactor;
    }

    public void setCalibrationFactor(double calibrationFactor) {
        this.calibrationFactor = calibrationFactor;
    }

    public int getCalibrationTarget() {
        return calibrationTarget;
    }

    public void setCalibrationTarget(int calibrationTarget) {
        this.calibrationTarget = calibrationTarget;
    }
}
