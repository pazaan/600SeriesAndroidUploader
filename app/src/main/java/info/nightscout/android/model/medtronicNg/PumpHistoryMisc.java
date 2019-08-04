package info.nightscout.android.model.medtronicNg;

import android.util.Log;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import info.nightscout.android.R;
import info.nightscout.android.history.HistoryUtils;
import info.nightscout.android.history.MessageItem;
import info.nightscout.android.history.PumpHistoryParser;
import info.nightscout.android.history.PumpHistorySender;
import info.nightscout.android.utils.FormatKit;
import info.nightscout.android.upload.nightscout.TreatmentsEndpoints;
import info.nightscout.android.history.NightscoutItem;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.Sort;
import io.realm.annotations.Ignore;
import io.realm.annotations.Index;

/**
 * Created by Pogman on 26.10.17.
 */

public class PumpHistoryMisc extends RealmObject implements PumpHistoryInterface {
    @Ignore
    private static final String TAG = PumpHistoryMisc.class.getSimpleName();
    @Ignore
    private static final int LIFETIMES_TOTAL = 5;

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
    private int eventRTC;
    private int eventOFFSET;

    @Index
    private byte recordtype;

    private byte[] lifetimes = new byte[LIFETIMES_TOTAL << 1];
    private byte[] calibrations;
    private double delivered;
    private double remaining;

    public enum RECORDTYPE {
        CHANGE_SENSOR(1),
        CHANGE_BATTERY(2),
        CHANGE_CANNULA(3),
        CHANGE_INSULIN(4),
        NA(-1);

        private int value;

        RECORDTYPE(int value) {
            this.value = value;
        }

        public byte value() {
            return (byte) this.value;
        }

        public boolean equals(int value) {
            return this.value == value;
        }

        public static RECORDTYPE convert(byte value) {
            for (RECORDTYPE recordtype : RECORDTYPE.values())
                if (recordtype.value == value) return recordtype;
            return RECORDTYPE.NA;
        }
    }

    @Override
    public List<NightscoutItem> nightscout(PumpHistorySender pumpHistorySender, String senderID) {
        List<NightscoutItem> nightscoutItems = new ArrayList<>();

        String type;
        String notes;
        String formatSeperator = pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.FORMAT_HTML) ? " <br>" : " ";

        if (RECORDTYPE.CHANGE_SENSOR.equals(recordtype)
                && pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.MISC_SENSOR)) {
            type = "Sensor Change";
            notes = String.format("%s: %s",
                    FormatKit.getInstance().getString(R.string.text__Pump),
                    FormatKit.getInstance().getString(R.string.text__new_sensor_started));
        }
        else if (RECORDTYPE.CHANGE_BATTERY.equals(recordtype)
                && pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.MISC_BATTERY)) {
            type = "Pump Battery Change";
            notes = String.format("%s: %s",
                    FormatKit.getInstance().getString(R.string.text__Pump),
                    FormatKit.getInstance().getString(R.string.text__battery_inserted));
        }
        else if (RECORDTYPE.CHANGE_CANNULA.equals(recordtype)
                && pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.MISC_CANNULA)) {
            double threshold = Double.parseDouble(pumpHistorySender.getVar(senderID, PumpHistorySender.SENDEROPT.CANNULA_CHANGE_THRESHOLD, "0")) / 1000;
            // only send a CAGE event when primed amount exceeds threshold
            if (delivered < threshold) return nightscoutItems;
            type = "Site Change";
            notes = String.format("%s: %s%s%s %s (%s)",
                    FormatKit.getInstance().getString(R.string.text__Pump),
                    FormatKit.getInstance().getString(R.string.text__fill_cannula),
                    formatSeperator,
                    FormatKit.getInstance().getString(R.string.text__Prime),
                    FormatKit.getInstance().formatAsInsulin(delivered),
                    FormatKit.getInstance().formatAsInsulin(remaining));
        }
        else if (RECORDTYPE.CHANGE_INSULIN.equals(recordtype)
                && pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.MISC_INSULIN)) {
            // only send a IAGE event when insulin reservoir in pump exceeds threshold
            int threshold = Integer.parseInt(pumpHistorySender.getVar(senderID, PumpHistorySender.SENDEROPT.INSULIN_CHANGE_THRESHOLD, "0"));
            if (threshold == 1) threshold = 140; // 1.8ml Reservoir
            else if (threshold == 2) threshold = 240; // 3.0ml Reservoir
            if (remaining < threshold) return nightscoutItems;
            type = "Insulin Change";
            notes = String.format("%s: %s%s%s %s (%s)",
                    FormatKit.getInstance().getString(R.string.text__Pump),
                    FormatKit.getInstance().getString(R.string.text__new_reservoir),
                    formatSeperator,
                    FormatKit.getInstance().getString(R.string.text__Prime),
                    FormatKit.getInstance().formatAsInsulin(delivered),
                    FormatKit.getInstance().formatAsInsulin(remaining));
        }
        else {
            HistoryUtils.nightscoutDeleteTreatment(nightscoutItems, this, senderID);
            return nightscoutItems;
        }

        String lifetimes = pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.MISC_LIFETIMES) ? formatLifetimes() : "";

        if (RECORDTYPE.CHANGE_SENSOR.equals(recordtype)
                && pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.MISC_SENSOR)
                && pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.CALIBRATION_INFO)
                && pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.FORMAT_HTML)
                && calibrations != null) {
            notes = FormatKit.getInstance().asMongoDBIndexKeySafe(lifetimes + formatCalibrations());
        } else if (!lifetimes.equals("")) {
            notes += formatSeperator + lifetimes;
        }

        TreatmentsEndpoints.Treatment treatment = HistoryUtils.nightscoutTreatment(nightscoutItems, this, senderID);
        treatment.setEventType(type);
        treatment.setNotes(notes);

        return nightscoutItems;
    }

    @Override
    public List<MessageItem> message(PumpHistorySender pumpHistorySender, String senderID) {
        List<MessageItem> messageItems = new ArrayList<>();

        // check if already sent as it may re-trigger due to lifetime/calibration updates
        if (senderACK.contains(senderID)) return messageItems;

        String message;
        String title;

        if (RECORDTYPE.CHANGE_SENSOR.equals(recordtype)
                && pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.MISC_SENSOR)) {
            title = FormatKit.getInstance().getString(R.string.text__Sensor_Change);
        }
        else if (RECORDTYPE.CHANGE_BATTERY.equals(recordtype)
                && pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.MISC_BATTERY)) {
            title = FormatKit.getInstance().getString(R.string.text__Battery_Change);
        }
        else if (RECORDTYPE.CHANGE_CANNULA.equals(recordtype)
                && pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.MISC_CANNULA)) {
            double threshold = Double.parseDouble(pumpHistorySender.getVar(senderID, PumpHistorySender.SENDEROPT.CANNULA_CHANGE_THRESHOLD, "0")) / 1000;
            // only send a CAGE event when primed amount exceeds threshold
            if (delivered < threshold) return messageItems;
            title = FormatKit.getInstance().getString(R.string.text__Cannula_Change);
        }
        else if (RECORDTYPE.CHANGE_INSULIN.equals(recordtype)
                && pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.MISC_INSULIN)) {
            // only send a IAGE event when insulin reservoir in pump exceeds threshold
            int threshold = Integer.parseInt(pumpHistorySender.getVar(senderID, PumpHistorySender.SENDEROPT.INSULIN_CHANGE_THRESHOLD, "0"));
            if (threshold == 1) threshold = 140; // 1.8ml Reservoir
            else if (threshold == 2) threshold = 240; // 3.0ml Reservoir
            if (remaining < threshold) return messageItems;
            title = FormatKit.getInstance().getString(R.string.text__Insulin_Change);
        }
        else
            return messageItems;

        if (pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.MISC_LIFETIMES))
            message = formatLifetimes();
        else
            message = "...";

        messageItems.add(new MessageItem()
                .type(MessageItem.TYPE.CONSUMABLE)
                .date(eventDate)
                .clock(FormatKit.getInstance().formatAsClock(eventDate.getTime()))
                .title(title)
                .message(message));

        return messageItems;
    }

    private String formatLifetimes() {
        StringBuilder sb = new StringBuilder(
                String.format("%s ",
                        FormatKit.getInstance().getString(R.string.text__Lifetimes)));

        for (int lt = 0; lt < LIFETIMES_TOTAL; lt++) {
            byte days = lifetimes[lt << 1];
            byte hours = lifetimes[(lt << 1) + 1];
            if (lt > 0) sb.append(" ");
            sb.append((days | hours) == 0 ? "---" : FormatKit.getInstance().formatHoursAsDH((days * 24) + hours));
        }

        return sb.toString();
    }

    private String formatCalibrations() {

        // MongoDB Index Key Limit
        // The total size of an index entry, which can include structural overhead depending on the BSON type,
        // must be less than 1024 bytes.

        // This is enough space for around 17 formatted data points
        // When there are more then this, a log function is used to select points preferring the most recent

        int style = 1;

        int segMin = 10;
        int segMax = 17;

        double fontMin = 50;
        double fontMax = 65;

        int height = 60;
        double border = 0.5;

        double segWidth;
        String css;

        if (style == 1) {
            segWidth = 6;
            fontMin = 55;
            fontMax = 68;
            css = ".wr{height:%spx;position:relative;border:1px solid#aaa;background-color:#fff;text-align:center}" +
                    ".wr div{width:%s%%;position:absolute;background-color:#bbb;color:#000;height:2px}";
        } else if (style == 2) {
            segWidth = 8;
            fontMin = 55;
            fontMax = 70;
            css = ".wr{height:%spx;position:relative;border:1px solid#aaa;background-color:#fff;text-align:center}" +
                    ".wr div{width:%s%%;position:absolute;background-color:#aaa2;color:#000;line-height:120%%;border-radius:10px}";
        } else {
            segWidth = 7;
            css = ".wr{height:%spx;position:relative;border:1px solid#aaa;background-color:#fff;text-align:center}" +
                    ".wr div{width:%s%%;position:absolute;background-color:#ddd;color:#000;bottom:0}";
        }

        int total = calibrations.length;

        int seg = total < segMin ? segMin : (total > segMax ? segMax : total);
        int count = total < seg ? total : seg;

        double xadj = ((((100.0 - (segMin * segWidth)) / segMin) / 2) / (segMax - segMin)) * (segMax - seg);

        double ymax = 100 - (border * 2);
        double xmax = 100 - ((border + xadj) * 2);
        double xpos = border + xadj;
        double xadd = (xmax - segWidth) / (seg - 1);
        double font = fontMax - (((fontMax - fontMin) / (segMax - segMin)) * (seg - segMin));

        double scale = 1.5;
        double smid = 50;
        double vmin = 22;
        double vmax = 97;
        double vmid = 42;

        DecimalFormat dfFacLocal = new DecimalFormat("0", DecimalFormatSymbols.getInstance(Locale.getDefault()));
        dfFacLocal.setMinimumFractionDigits(0);
        dfFacLocal.setMaximumFractionDigits(1);

        DecimalFormat dfFac = new DecimalFormat("0", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        dfFac.setMinimumFractionDigits(0);
        dfFac.setMaximumFractionDigits(1);

        DecimalFormat dfNum = new DecimalFormat("0");
        dfNum.setMinimumFractionDigits(0);
        dfNum.setMaximumFractionDigits(0);

        StringBuilder parts = new StringBuilder();

        double v, r, l, p;

        for (int i = 0; i < count; i++) {

            l = Math.log10((i + 1) * (100 / seg));
            p = i + ((l * (total - count)) / 2);

            v = calibrations[(int) Math.round(p)] & 0xFF; // cal factor as 8bit div 10
            if (v > 100) v = 10 * (int) (v / 10); // keep cal factors > 10 as integers due to html space limitations

            r = vmid + ((v - smid) * scale);
            r = r < vmin ? vmin : (r > vmax ? vmax : r);

            parts.append(String.format("<div style='left:%s%%;top:%s%%'>%s</div>",
                    dfFac.format(xpos),
                    dfNum.format(100 - ((ymax * r) / 100)),
                    dfFacLocal.format(v / 10)));

            xpos += xadd;
        }

        return String.format("<style>" + css + "</style><div class='wr'style='font-size:%s%%'>%s</div>",
                dfNum.format(height),
                dfFac.format(segWidth),
                dfNum.format(font),
                parts.toString());
    }

    public static PumpHistoryMisc item(
            PumpHistorySender pumpHistorySender, Realm realm, long pumpMAC,
            Date eventDate, int eventRTC, int eventOFFSET,
            RECORDTYPE recordtype) {

        PumpHistoryMisc record = realm.where(PumpHistoryMisc.class)
                .equalTo("pumpMAC", pumpMAC)
                .equalTo("recordtype", recordtype.value())
                .equalTo("eventRTC", eventRTC)
                .findFirst();

        if (record == null) {
            Log.d(TAG, "*new* recordtype: " + recordtype.name());
            record = realm.createObject(PumpHistoryMisc.class);
            record.pumpMAC = pumpMAC;

            record.eventDate = eventDate;
            record.key = HistoryUtils.key("MISC", eventRTC);
            pumpHistorySender.setSenderREQ(record);

            record.eventRTC = eventRTC;
            record.eventOFFSET = eventOFFSET;
            record.recordtype = recordtype.value();

            calcLifetimes(pumpHistorySender, realm, pumpMAC, recordtype);

            return record;
        }

        return null;
    }

    private static void calcLifetimes(PumpHistorySender pumpHistorySender, Realm realm, long pumpMAC, RECORDTYPE recordtype) {

        // lifetime for items
        RealmResults<PumpHistoryMisc> realmResults = realm.where(PumpHistoryMisc.class)
                .equalTo("pumpMAC", pumpMAC)
                .equalTo("recordtype", recordtype.value())
                .sort("eventDate", Sort.DESCENDING)
                .findAll();

        if (realmResults.size() > 1) {

            for (int i = 1; i < realmResults.size(); i++) {

                long lifetime = (realmResults.get(i - 1).eventDate.getTime() - realmResults.get(i).eventDate.getTime()) / 60000L;

                byte days = (byte) (lifetime / 1440);
                byte hours = (byte) ((lifetime % 1440) / 60);

                for (int lt = 0; lt < LIFETIMES_TOTAL && i - lt >= 0; lt++) {
                    byte[] lifetimes = realmResults.get(i - lt).lifetimes;

                    if (lifetimes[lt << 1] != days || lifetimes[(lt << 1) + 1] != hours) {
                        lifetimes[lt << 1] = days;
                        lifetimes[(lt << 1) + 1] = hours;
                        realmResults.get(i - lt).lifetimes = lifetimes;
                        pumpHistorySender.setSenderREQ(realmResults.get(i - lt));
                    }
                }

            }
        }
    }

    /*
    Sensor Change
    A delay in responding to a 'new sensor' will create 2 history items with the 1st being
    the time inserted and the 2nd at the time responded (user selects start as new)
    NS prefers to show the 1st even though the 2nd is more recent.
    *** workaround ***
    Filter 'Sensor Change' events and use the recorded history item again if less the 60 mins old.
    */

    public static void sensor(
            PumpHistorySender pumpHistorySender, Realm realm, long pumpMAC,
            Date eventDate, int eventRTC, int eventOFFSET,
            RECORDTYPE recordtype) {

        PumpHistoryMisc record = realm.where(PumpHistoryMisc.class)
                .equalTo("pumpMAC", pumpMAC)
                .equalTo("recordtype", RECORDTYPE.CHANGE_SENSOR.value())
                .equalTo("eventRTC", eventRTC)
                .findFirst();

        if (record == null && (realm.where(PumpHistoryMisc.class)
                .equalTo("pumpMAC", pumpMAC)
                .equalTo("recordtype", RECORDTYPE.CHANGE_SENSOR.value())
                .greaterThan("eventDate", new Date(eventDate.getTime() - 60 * 60000L))
                .lessThan("eventDate", new Date(eventDate.getTime() + 60 * 60000L))
                .findAll()).size() == 0)
        {
            Log.d(TAG, "*new* recordtype: " + recordtype.name());
            record = realm.createObject(PumpHistoryMisc.class);
            record.pumpMAC = pumpMAC;

            record.eventDate = eventDate;
            record.key = HistoryUtils.key("MISC", eventRTC);
            pumpHistorySender.setSenderREQ(record);

            record.eventRTC = eventRTC;
            record.eventOFFSET = eventOFFSET;
            record.recordtype = recordtype.value();

            calcLifetimes(pumpHistorySender, realm, pumpMAC, recordtype);
        }
    }

    public static void cannula(
            PumpHistorySender pumpHistorySender, Realm realm, long pumpMAC,
            Date eventDate, int eventRTC, int eventOFFSET,
            byte type,
            double delivered,
            double remaining) {

        if (PumpHistoryParser.CANNULA_FILL_TYPE.CANULLA_FILL.equals(type)
                && delivered >= 0) {
            PumpHistoryMisc record = PumpHistoryMisc.item(
                    pumpHistorySender, realm, pumpMAC,
                    eventDate, eventRTC, eventOFFSET,
                    RECORDTYPE.CHANGE_CANNULA);
            if (record != null) {
                record.delivered = delivered;
                record.remaining = remaining;
            }

        } else if (PumpHistoryParser.CANNULA_FILL_TYPE.TUBING_FILL.equals(type)
                && delivered + remaining >= 0) {
            PumpHistoryMisc record = PumpHistoryMisc.item(
                    pumpHistorySender, realm, pumpMAC,
                    eventDate, eventRTC, eventOFFSET,
                    RECORDTYPE.CHANGE_INSULIN);
            if (record != null) {
                record.delivered = delivered;
                record.remaining = remaining;
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

    public int getEventRTC() {
        return eventRTC;
    }

    public int getEventOFFSET() {
        return eventOFFSET;
    }

    public byte getRecordtype() {
        return recordtype;
    }

    public byte[] getLifetimes() {
        return lifetimes;
    }

    public double getDelivered() {
        return delivered;
    }

    public double getRemaining() {
        return remaining;
    }

    public byte[] getCalibrations() {
        return calibrations;
    }

    public void setCalibrations(byte[] calibrations) {
        this.calibrations = calibrations;
    }
}