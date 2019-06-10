package info.nightscout.android.model.medtronicNg;

import android.util.Log;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.android.R;
import info.nightscout.android.history.HistoryUtils;
import info.nightscout.android.history.MessageItem;
import info.nightscout.android.history.NightscoutItem;
import info.nightscout.android.history.PumpHistoryParser;
import info.nightscout.android.history.PumpHistorySender;
import info.nightscout.android.utils.FormatKit;
import info.nightscout.android.upload.nightscout.EntriesEndpoints;
import info.nightscout.android.upload.nightscout.TreatmentsEndpoints;
import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.Sort;
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
    private String senderREQ = "";
    @Index
    private String senderACK = "";
    @Index
    private String senderDEL = "";

    @Index
    private Date eventDate;
    @Index
    private long pumpMAC;

    private String key; // unique identifier for nightscout, key = "ID" + RTC as 8 char hex ie. "CGM6A23C5AA"

    @Index
    private int bgRTC;
    private int bgOffset;
    private Date bgDate;

    private byte bgUnits;
    private byte bgSource;
    private RealmList<String> bgContext = new RealmList<>();

    @Index
    private boolean entered;

    @Index
    private int bg;
    private String serial;
    @Index
    private boolean calibrationFlag;

    @Index
    private boolean calibration;
    private Date calibrationDate;

    @Index
    private int calibrationRTC;
    private int calibrationOFFSET;
    private double calibrationFactor;
    private int calibrationTarget;

    @Override
    public List<NightscoutItem> nightscout(PumpHistorySender pumpHistorySender, String senderID) {
        List<NightscoutItem> nightscoutItems = new ArrayList<>();

        if (!pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.BG_INFO)) {
            HistoryUtils.nightscoutDeleteTreatment(nightscoutItems, this, senderID);
            HistoryUtils.nightscoutDeleteEntry(nightscoutItems, this, senderID).setKey600(key + "CGM");
            return nightscoutItems;
        }

        TreatmentsEndpoints.Treatment treatment = HistoryUtils.nightscoutTreatment(nightscoutItems, this, senderID, bgDate);
        treatment.setEventType("BG Check");
        treatment.setGlucoseType("Finger");

        BigDecimal bgl;
        String units;
        if (PumpHistoryParser.BG_UNITS.MG_DL.equals(bgUnits)) {
            bgl = new BigDecimal(bg);
            units = "mg/dl";
        } else {
            bgl = new BigDecimal(bg / MMOLXLFACTOR).setScale(1, BigDecimal.ROUND_HALF_UP);
            units = "mmol";
        }
        treatment.setGlucose(bgl);
        treatment.setUnits(units);

        if (calibration && pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.CALIBRATION_INFO)) {
            treatment.setNotes(String.format("%s: %s %s: %s (%s)",
                    FormatKit.getInstance().getString(R.string.text__Factor),
                    calibrationFactor,
                    FormatKit.getInstance().getString(R.string.text__Target),
                    FormatKit.getInstance().formatAsGlucose(calibrationTarget, true),
                    FormatKit.getInstance().formatSecondsAsDHMS((int) ((calibrationDate.getTime() - bgDate.getTime()) / 1000L))
            ));
            nightscoutItems.get(0).update();
        }

        // insert BG reading as CGM chart entry
        if (pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.INSERT_BG_AS_CGM)) {
            EntriesEndpoints.Entry entry = HistoryUtils.nightscoutEntry(nightscoutItems,this, senderID, bgDate);
            entry.setKey600(key + "CGM");
            entry.setType("sgv");
            entry.setSgv(bg);
        } else {
            HistoryUtils.nightscoutDeleteEntry(nightscoutItems, this, senderID).setKey600(key + "CGM");
        }
/*
        // debug for 670 testing, resend all context updates
        String s = treatment.getNotes();
        if (s == null) s = "";
        s = s + "<br>{ debug: ";
        for (int i = 0; i < bgContext.size(); i++) {
            s = s + PumpHistoryParser.BG_CONTEXT.convert(Integer.parseInt(bgContext.get(i))).name() + " ";
        }
        s = s +" }";
        treatment.setNotes(s);
        nightscoutItems.get(0).update();
*/
        return nightscoutItems;
    }

    @Override
    public List<MessageItem> message(PumpHistorySender pumpHistorySender, String senderID) {
        List<MessageItem> messageItems = new ArrayList<>();

        MessageItem.TYPE type;
        Date date;
        String title;
        String message;

        if (calibration && pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.CALIBRATION_INFO)) {
            type = MessageItem.TYPE.CALIBRATION;
            date = calibrationDate;
            title = "Calibration";
            message = String.format("%s %s %s %s",
                    FormatKit.getInstance().getString(R.string.text__Factor),
                    calibrationFactor,
                    FormatKit.getInstance().getString(R.string.text__Target),
                    FormatKit.getInstance().formatAsGlucose(bg, pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.GLUCOSE_UNITS)));

        } else if (!senderACK.contains(senderID) && pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.BG_INFO)) {
            type = MessageItem.TYPE.BG;
            date = eventDate;
            title = "BG";
            message = String.format("%s %s",
                    FormatKit.getInstance().getString(R.string.text__Finger_BG),
                    FormatKit.getInstance().formatAsGlucose(bg, pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.GLUCOSE_UNITS)));

        } else return messageItems;

        messageItems.add(new MessageItem()
                .type(type)
                .date(date)
                .clock(FormatKit.getInstance().formatAsClock(date.getTime()))
                .title(title)
                .message(message));

        return messageItems;
    }

    public static void bg(
            PumpHistorySender pumpHistorySender, Realm realm, long pumpMAC,
            Date eventDate, int eventRTC, int eventOFFSET,
            boolean calibrationFlag,
            int bg,
            byte bgUnits,
            byte bgSource,
            byte bgContext,
            String serial) {

        PumpHistoryBG record;

        boolean entered = false;
        if (PumpHistoryParser.BG_CONTEXT.BG_READING_RECEIVED.equals(bgContext)
                || PumpHistoryParser.BG_CONTEXT.ENTERED_IN_BG_ENTRY.equals(bgContext)
                || PumpHistoryParser.BG_CONTEXT.ENTERED_IN_MEAL_WIZARD.equals(bgContext)
                || PumpHistoryParser.BG_CONTEXT.ENTERED_IN_BOLUS_WIZRD.equals(bgContext)
                || PumpHistoryParser.BG_CONTEXT.ENTERED_IN_SENSOR_CALIB.equals(bgContext)
                || PumpHistoryParser.BG_CONTEXT.ENTERED_AS_BG_MARKER.equals(bgContext)
        )
        {
            entered = true;
            record = realm.where(PumpHistoryBG.class)
                    .equalTo("pumpMAC", pumpMAC)
                    .equalTo("bg", bg)
                    .greaterThanOrEqualTo("bgRTC", eventRTC)
                    .lessThanOrEqualTo("bgRTC", HistoryUtils.offsetRTC(eventRTC, 15 * 60))
                    .sort("eventDate", Sort.ASCENDING)
                    .findFirst();
        } else {
            record = realm.where(PumpHistoryBG.class)
                    .equalTo("pumpMAC", pumpMAC)
                    .equalTo("bg", bg)
                    .greaterThan("bgRTC", HistoryUtils.offsetRTC(eventRTC,- 15 * 60))
                    .lessThanOrEqualTo("bgRTC", eventRTC)
                    .sort("eventDate", Sort.DESCENDING)
                    .findFirst();
        }

        if (record == null) {
            // look for a calibration
            record = realm.where(PumpHistoryBG.class)
                    .equalTo("pumpMAC", pumpMAC)
                    .equalTo("calibration", true)
                    .equalTo("calibrationFlag", false)
                    .greaterThanOrEqualTo("calibrationRTC", eventRTC)
                    .lessThan("calibrationRTC", HistoryUtils.offsetRTC(eventRTC, 20 * 60))
                    .sort("eventDate", Sort.ASCENDING)
                    .findFirst();
            if (record == null) {
                Log.d(TAG, "*new* bg");
                record = realm.createObject(PumpHistoryBG.class);
                record.pumpMAC = pumpMAC;
            } else {
                Log.d(TAG, "*update* add bg to calibration");
            }
            record.eventDate = eventDate;
            record.bgDate = eventDate;
            record.bgRTC = eventRTC;
            record.bgOffset = eventOFFSET;
            record.bg = bg;
            record.bgUnits = bgUnits;
            record.bgSource = bgSource;
            record.serial = serial;
        }

        if (entered && !record.entered) {
            Log.d(TAG, "*update* entered");
            record.entered = true;
            record.eventDate = eventDate;
            record.bgDate = eventDate;
            record.bgRTC = eventRTC;
            record.bgOffset = eventOFFSET;
            record.key = HistoryUtils.key("BG", eventRTC);
            pumpHistorySender.setSenderREQ(record);
        }

        if (!record.bgContext.contains(String.valueOf(bgContext))) {
            Log.d(TAG, "*update* bgContext: " + PumpHistoryParser.BG_CONTEXT.convert(bgContext).name() + " calibrationFlag: " + calibrationFlag);
            record.bgContext.add(String.valueOf(bgContext));
            record.calibrationFlag |= calibrationFlag;
        }
    }

    public static void calibration(
            PumpHistorySender pumpHistorySender, Realm realm, long pumpMAC,
            Date eventDate, int eventRTC, int eventOFFSET,
            double calFactor,
            int bgTarget) {

        // failed calibration
        if (calFactor == 0) return;

        PumpHistoryBG record = realm.where(PumpHistoryBG.class)
                .equalTo("pumpMAC", pumpMAC)
                .equalTo("calibrationRTC", eventRTC)
                .findFirst();
        if (record == null) {
            // look for a bg
            record = realm.where(PumpHistoryBG.class)
                    .equalTo("pumpMAC", pumpMAC)
                    .greaterThan("bgRTC", HistoryUtils.offsetRTC(eventRTC, - 20 * 60))
                    .lessThanOrEqualTo("bgRTC", eventRTC)
                    .equalTo("calibrationFlag", true)
                    .equalTo("calibration", false)
                    .findFirst();
            if (record == null) {
                Log.d(TAG, "*new*" + " calibration");
                record = realm.createObject(PumpHistoryBG.class);
                record.pumpMAC = pumpMAC;
                record.eventDate = eventDate;
            } else {
                Log.d(TAG, "*update*"  + " bg with calibration");
                if (record.entered) pumpHistorySender.setSenderREQ(record);
            }
            record.calibration = true;
            record.calibrationDate = eventDate;
            record.calibrationRTC = eventRTC;
            record.calibrationOFFSET = eventOFFSET;
            record.calibrationFactor = calFactor;
            record.calibrationTarget = bgTarget;

            // update calibrations for the sensor
            // find the sensor
            RealmResults<PumpHistoryMisc> results1 = realm.where(PumpHistoryMisc.class)
                    .equalTo("pumpMAC", pumpMAC)
                    .greaterThan("eventDate", new Date(eventDate.getTime() - 8 * 24 * 60 * 60000L))
                    .lessThan("eventDate", eventDate)
                    .equalTo("recordtype", PumpHistoryMisc.RECORDTYPE.CHANGE_SENSOR.value())
                    .sort("eventDate", Sort.DESCENDING)
                    .findAll();

            if (results1.size() > 0) {

                // find the sensor date range
                RealmResults<PumpHistoryMisc> results2 = realm.where(PumpHistoryMisc.class)
                        .equalTo("pumpMAC", pumpMAC)
                        .lessThan("eventDate", new Date(eventDate.getTime() + 8 * 24 * 60 * 60000L))
                        .greaterThan("eventDate", eventDate)
                        .equalTo("recordtype", PumpHistoryMisc.RECORDTYPE.CHANGE_SENSOR.value())
                        .sort("eventDate", Sort.ASCENDING)
                        .findAll();

                Date begin = results1.first().getEventDate();
                Date end = eventDate;

                if (results2.size() > 0)
                    end = results2.first().getEventDate();

                // find all the calibrations for this sensor
                RealmResults<PumpHistoryBG> results3 = realm.where(PumpHistoryBG.class)
                        .equalTo("pumpMAC", pumpMAC)
                        .greaterThan("eventDate", begin)
                        .lessThan("eventDate", end)
                        .equalTo("calibration", true)
                        .sort("eventDate", Sort.ASCENDING)
                        .findAll();

                if (results3.size() > 0) {
                    byte[] calibrations = new byte[results3.size()];
                    for (int i = 0; i < results3.size(); i++) {
                        calibrations[i] = (byte) (results3.get(i).calibrationFactor * 10);
                    }
                    results1.first().setCalibrations(calibrations);
                    pumpHistorySender.setSenderREQ(results1.first());
                }

            }

        }
    }

    @Override
    public String getSenderREQ() {
        return senderREQ;
    }

    @Override
    public void setSenderREQ(String senderREQ) {
        this.senderREQ = senderREQ;
    }

    @Override
    public String getSenderACK() {
        return senderACK;
    }

    @Override
    public void setSenderACK(String senderACK) {
        this.senderACK = senderACK;
    }

    @Override
    public String getSenderDEL() {
        return senderDEL;
    }

    @Override
    public void setSenderDEL(String senderDEL) {
        this.senderDEL = senderDEL;
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
    public String getKey() {
        return key;
    }

    @Override
    public void setKey(String key) {
        this.key = key;
    }

    @Override
    public long getPumpMAC() {
        return pumpMAC;
    }

    @Override
    public void setPumpMAC(long pumpMAC) {
        this.pumpMAC = pumpMAC;
    }

    public int getBgRTC() {
        return bgRTC;
    }

    public int getBgOffset() {
        return bgOffset;
    }

    public Date getBgDate() {
        return bgDate;
    }

    public byte getBgUnits() {
        return bgUnits;
    }

    public byte getBgSource() {
        return bgSource;
    }

    public int getBg() {
        return bg;
    }

    public String getSerial() {
        return serial;
    }

    public boolean isCalibrationFlag() {
        return calibrationFlag;
    }

    public boolean isCalibration() {
        return calibration;
    }

    public Date getCalibrationDate() {
        return calibrationDate;
    }

    public int getCalibrationRTC() {
        return calibrationRTC;
    }

    public int getCalibrationOFFSET() {
        return calibrationOFFSET;
    }

    public double getCalibrationFactor() {
        return calibrationFactor;
    }

    public int getCalibrationTarget() {
        return calibrationTarget;
    }

    public RealmList<String> getBgContext() {
        return bgContext;
    }
}
