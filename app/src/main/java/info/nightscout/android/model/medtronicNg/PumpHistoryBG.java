package info.nightscout.android.model.medtronicNg;

import android.util.Log;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.android.medtronic.PumpHistoryParser;
import info.nightscout.api.TreatmentsEndpoints;
import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.annotations.Ignore;
import io.realm.annotations.Index;

import static info.nightscout.android.medtronic.MainActivity.MMOLXLFACTOR;

/**
 * Created by John on 26.10.17.
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

    byte bgUnits;
    byte bgSource;

    int bg;
    String serial;
    boolean calibrationFlag;

    boolean calibration;
    private Date calibrationDate;
    private int calibrationRTC;
    private int calibrationOFFSET;
    private double calibrationFactor;
    private int calibrationTarget;

    @Override
    public List nightscout() {
        List list = new ArrayList();

        TreatmentsEndpoints.Treatment treatment = new TreatmentsEndpoints.Treatment();
        list.add("treatment");
        list.add(uploadACK ? "update" : "new");
        list.add(treatment);

        BigDecimal bgl;
        String units;
        if (PumpHistoryParser.BG_UNITS.MG_DL.equals(bgUnits)) {
            bgl = new BigDecimal(bg).setScale(0);
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
        if (calibration) {
            long seconds = (calibrationDate.getTime() - bgDate.getTime()) / 1000;
            treatment.setNotes("CAL: ⋊ " + calibrationFactor + " (" + (seconds / 60) + "m" + (seconds % 60) + "s)");
        }

        return list;
    }

    public static void bg(Realm realm, Date eventDate, int eventRTC, int eventOFFSET,
                          boolean calibrationFlag,
                          int bg,
                          byte bgUnits,
                          byte bgSource,
                          String serial) {

        PumpHistoryBG object = realm.where(PumpHistoryBG.class)
                .equalTo("bgRTC", eventRTC)
                .findFirst();
        if (object == null) {
            // look for a calibration
            object = realm.where(PumpHistoryBG.class)
                    .equalTo("calibration", true)
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
            object.setSerial(serial);
            object.setKey("BG" + String.format("%08X", eventRTC));
            object.setUploadREQ(true);
        } else if (calibrationFlag && !object.isCalibrationFlag()){
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
