package info.nightscout.android.model.medtronicNg;

import android.util.Log;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.android.R;
import info.nightscout.android.history.MessageItem;
import info.nightscout.android.history.NightscoutItem;
import info.nightscout.android.history.PumpHistoryParser;
import info.nightscout.android.history.PumpHistorySender;
import info.nightscout.android.utils.FormatKit;
import info.nightscout.api.EntriesEndpoints;
import info.nightscout.api.TreatmentsEndpoints;
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

    private String key; // unique identifier for nightscout, key = "ID" + RTC as 8 char hex ie. "CGM6A23C5AA"

    @Index
    private int bgRTC;
    private int bgOffset;
    private Date bgDate;

    private byte bgUnits;
    private byte bgSource;
    private RealmList<Byte> bgContext;

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

        if (!calibration && !pumpHistorySender.senderOpt(senderID, PumpHistorySender.SENDEROPT.BG_INFO))
            return nightscoutItems;

        NightscoutItem nightscoutItem = new NightscoutItem();
        nightscoutItems.add(nightscoutItem);
        TreatmentsEndpoints.Treatment treatment = nightscoutItem.ack(senderACK.contains(senderID)).treatment();

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

        //for (int i : bgContext) notes += PumpHistoryParser.BG_CONTEXT.convert(i).name() + " | ";

        if (calibration && pumpHistorySender.senderOpt(senderID, PumpHistorySender.SENDEROPT.CALIBRATION_INFO)) {
            long seconds = (calibrationDate.getTime() - bgDate.getTime()) / 1000;
            notes += "CAL: ⋊ " + calibrationFactor + " (" + (seconds / 60) + "m" + (seconds % 60) + "s)";
            nightscoutItem.update();
        }

        if (!notes.equals("")) treatment.setNotes(notes);

        // insert BG reading as CGM chart entry
        if (pumpHistorySender.senderOpt(senderID, PumpHistorySender.SENDEROPT.INSERT_BG_AS_CGM)) {

            NightscoutItem nightscoutItem1 = new NightscoutItem();
            nightscoutItems.add(nightscoutItem1);
            EntriesEndpoints.Entry entry = nightscoutItem1.ack(senderACK.contains(senderID)).entry();

            entry.setKey600(key + "CGM");
            entry.setType("sgv");
            entry.setDate(bgDate.getTime());
            entry.setDateString(bgDate.toString());
            entry.setSgv(bg);
        }

        return nightscoutItems;
    }

    @Override
    public List<MessageItem> message(PumpHistorySender pumpHistorySender, String senderID) {
        List<MessageItem> messageItems = new ArrayList<>();

        String key = this.key;
        MessageItem.TYPE type;
        Date date;
        String title;
        String message;

        if (calibration && pumpHistorySender.senderOpt(senderID, PumpHistorySender.SENDEROPT.CALIBRATION_INFO)) {
            key = String.format("BG%08X", calibrationRTC);
            type = MessageItem.TYPE.CALIBRATION;
            date = calibrationDate;
            title = "Calibration";
            message = String.format("%s %s %s %s",
                    FormatKit.getInstance().getString(R.string.Factor),
                    calibrationFactor,
                    FormatKit.getInstance().getString(R.string.Target),
                    FormatKit.getInstance().formatAsGlucose(bg, pumpHistorySender.senderOpt(senderID, PumpHistorySender.SENDEROPT.GLUCOSE_UNITS)));

        } else if (!senderACK.contains(senderID) && pumpHistorySender.senderOpt(senderID, PumpHistorySender.SENDEROPT.BG_INFO)) {
            type = MessageItem.TYPE.BG;
            date = eventDate;
            title = "BG";
            message = String.format("%s %s",
                    FormatKit.getInstance().getString(R.string.Finger_BG),
                    FormatKit.getInstance().formatAsGlucose(bg, pumpHistorySender.senderOpt(senderID, PumpHistorySender.SENDEROPT.GLUCOSE_UNITS)));

        } else return messageItems;

        messageItems.add(new MessageItem()
                .key(key)
                .type(type)
                .date(date)
                .clock(FormatKit.getInstance().formatAsClock(date))
                .title(title)
                .message(message));

        return messageItems;
    }

    public static void bg(PumpHistorySender pumpHistorySender, Realm realm, Date eventDate, int eventRTC, int eventOFFSET,
                          boolean calibrationFlag,
                          int bg,
                          byte bgUnits,
                          byte bgSource,
                          byte bgContext,
                          String serial) {

        PumpHistoryBG record = realm.where(PumpHistoryBG.class)
                .equalTo("bg", bg)
                .greaterThan("bgRTC", eventRTC - 15 * 60)
                .lessThan("bgRTC", eventRTC + 15 * 60)
                .findFirst();

        if (record == null) {
            // look for a calibration
            record = realm.where(PumpHistoryBG.class)
                    .equalTo("calibration", true)
                    .equalTo("calibrationFlag", false)
                    .greaterThan("calibrationRTC", eventRTC)
                    .lessThan("calibrationRTC", eventRTC + 20 * 60)
                    .findFirst();
            if (record == null) {
                Log.d(TAG, "*new* bg");
                record = realm.createObject(PumpHistoryBG.class);
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
            record.bgContext = new RealmList<>();
            record.serial = serial;
            record.key = String.format("BG%08X", eventRTC);
            //record.key = String.format("BG%08X%02X", eventRTC, bgContext);
            pumpHistorySender.senderREQ(record);
        }

        if (!record.bgContext.contains(bgContext)) {
            Log.d(TAG, "*update* bgContext: " + PumpHistoryParser.BG_CONTEXT.convert(bgContext).name() + " calibrationFlag: " + calibrationFlag);
            record.bgContext.add(bgContext);
            record.calibrationFlag |= calibrationFlag;
        }
    }

    public static void calibration(PumpHistorySender pumpHistorySender, Realm realm, Date eventDate, int eventRTC, int eventOFFSET,
                                   double calFactor,
                                   int bgTarget) {

        // failed calibration
        if (calFactor == 0) return;

        PumpHistoryBG record = realm.where(PumpHistoryBG.class)
                .equalTo("calibrationRTC", eventRTC)
                .findFirst();
        if (record == null) {
            // look for a bg
            record = realm.where(PumpHistoryBG.class)
                    .greaterThan("bgRTC", eventRTC - 20 * 60)
                    .lessThan("bgRTC", eventRTC)
                    .equalTo("calibrationFlag", true)
                    .equalTo("calibration", false)
                    .findFirst();
            if (record == null) {
                Log.d(TAG, "*new*" + " calibration");
                record = realm.createObject(PumpHistoryBG.class);
                record.eventDate = eventDate;
            } else {
                Log.d(TAG, "*update*"  + " bg with calibration");
                pumpHistorySender.senderREQ(record);

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
                    .greaterThan("eventDate", new Date(eventDate.getTime() - 8 * 24 * 60 * 60000L))
                    .lessThan("eventDate", eventDate)
                    .equalTo("recordtype", PumpHistoryMisc.RECORDTYPE.CHANGE_SENSOR.value())
                    .sort("eventDate", Sort.DESCENDING)
                    .findAll();

            if (results1.size() > 0) {

                // find the sensor date range
                RealmResults<PumpHistoryMisc> results2 = realm.where(PumpHistoryMisc.class)
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
                    pumpHistorySender.senderREQ(results1.first());
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

    public RealmList<Byte> getBgContext() {
        return bgContext;
    }
}
