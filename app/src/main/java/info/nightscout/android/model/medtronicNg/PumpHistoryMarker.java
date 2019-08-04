package info.nightscout.android.model.medtronicNg;

import android.util.Log;

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
import info.nightscout.android.upload.nightscout.TreatmentsEndpoints;
import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.annotations.Ignore;
import io.realm.annotations.Index;

public class PumpHistoryMarker extends RealmObject implements PumpHistoryInterface {
    @Ignore
    private static final String TAG = PumpHistoryMarker.class.getSimpleName();

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
    private byte recordtype;

    @Index
    private int eventRTC;
    private int eventOFFSET;

    private int duration;
    private byte carbUnits;
    private double carbInput;
    private double insulin;

    public enum RECORDTYPE {
        FOOD(1),
        EXERCISE(2),
        INJECTION(3),
        OTHER(4),
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

        public static RECORDTYPE convert(int value) {
            for (RECORDTYPE recordtype : RECORDTYPE.values())
                if (recordtype.value == value) return recordtype;
            return RECORDTYPE.NA;
        }
    }

    @Override
    public List<NightscoutItem> nightscout(PumpHistorySender pumpHistorySender, String senderID) {
        List<NightscoutItem> nightscoutItems = new ArrayList<>();
        TreatmentsEndpoints.Treatment treatment;

        switch (RECORDTYPE.convert(recordtype)) {

            case FOOD:
                treatment = HistoryUtils.nightscoutTreatment(nightscoutItems, this, senderID);
                treatment.setEventType("Carb Correction");

                // conversion for unit type
                double carbInputAsGrams;
                String exchanges;
                double gramsPerExchange = Double.parseDouble(pumpHistorySender.getVar(senderID, PumpHistorySender.SENDEROPT.GRAMS_PER_EXCHANGE, "15"));
                if (PumpHistoryParser.CARB_UNITS.EXCHANGES.equals(carbUnits)) {
                    carbInputAsGrams = gramsPerExchange * carbInput;
                    exchanges = String.format(", %s",
                            FormatKit.getInstance().formatAsExchanges(carbInput));
                } else {
                    carbInputAsGrams = carbInput;
                    exchanges = "";
                }
                treatment.setCarbs((float) carbInputAsGrams);

                treatment.setNotes(String.format("%s: %s %s %s%s",
                        FormatKit.getInstance().getString(R.string.event_marker__heading),
                        FormatKit.getInstance().getString(R.string.event_marker__food),
                        FormatKit.getInstance().getString(R.string.text__carb),
                        FormatKit.getInstance().formatAsGrams(carbInputAsGrams),
                        exchanges));
                break;

            case EXERCISE:
                treatment = HistoryUtils.nightscoutTreatment(nightscoutItems, this, senderID);
                treatment.setEventType("Exercise");
                treatment.setDuration((float) duration);
                treatment.setNotes(String.format("%s: %s",
                        FormatKit.getInstance().getString(R.string.event_marker__heading),
                        FormatKit.getInstance().getString(R.string.event_marker__exercise)));
                break;

            case INJECTION:
                treatment = HistoryUtils.nightscoutTreatment(nightscoutItems, this, senderID);
                treatment.setEventType("Correction Bolus");
                treatment.setInsulin((float) insulin);
                treatment.setNotes(String.format("%s: %s %s %s",
                        FormatKit.getInstance().getString(R.string.event_marker__heading),
                        FormatKit.getInstance().getString(R.string.event_marker__injection),
                        FormatKit.getInstance().getString(R.string.text__bolus),
                        FormatKit.getInstance().formatAsInsulin(insulin)));
                break;

            case OTHER:
                treatment = HistoryUtils.nightscoutTreatment(nightscoutItems, this, senderID);
                treatment.setEventType("Question");
                treatment.setNotes(String.format("%s: %s",
                        FormatKit.getInstance().getString(R.string.event_marker__heading),
                        FormatKit.getInstance().getString(R.string.event_marker__other)));
                break;

            default:
        }

        return nightscoutItems;
    }

    @Override
    public List<MessageItem> message(PumpHistorySender pumpHistorySender, String senderID) {
        List<MessageItem> messageItems = new ArrayList<>();

        String title = FormatKit.getInstance().getString(R.string.event_marker__heading);
        String message = "";

        switch (RECORDTYPE.convert(recordtype)) {

            case FOOD:
                // conversion for unit type
                double carbInputAsGrams;
                String exchanges;
                double gramsPerExchange = Double.parseDouble(pumpHistorySender.getVar(senderID, PumpHistorySender.SENDEROPT.GRAMS_PER_EXCHANGE, "15"));
                if (PumpHistoryParser.CARB_UNITS.EXCHANGES.equals(carbUnits)) {
                    carbInputAsGrams = gramsPerExchange * carbInput;
                    exchanges = String.format(", %s",
                            FormatKit.getInstance().formatAsExchanges(carbInput));
                } else {
                    carbInputAsGrams = carbInput;
                    exchanges = "";
                }
                message = String.format("%s %s %s%s",
                        FormatKit.getInstance().getString(R.string.event_marker__food),
                        FormatKit.getInstance().getString(R.string.text__carb),
                        FormatKit.getInstance().formatAsGrams(carbInputAsGrams),
                        exchanges);
                break;

            case EXERCISE:
                message = String.format("%s %s %s",
                        FormatKit.getInstance().getString(R.string.event_marker__exercise),
                        FormatKit.getInstance().getString(R.string.text__duration),
                        FormatKit.getInstance().formatMinutesAsHM(duration));
                break;

            case INJECTION:
                message = String.format("%s %s %s",
                        FormatKit.getInstance().getString(R.string.event_marker__injection),
                        FormatKit.getInstance().getString(R.string.text__bolus),
                        FormatKit.getInstance().formatAsInsulin(insulin));
                break;

            case OTHER:
                message = String.format("%s",
                        FormatKit.getInstance().getString(R.string.event_marker__other));
                break;

            default:
                return messageItems;
        }

        messageItems.add(new MessageItem()
                .date(eventDate)
                .clock(FormatKit.getInstance().formatAsClock(eventDate.getTime()))
                .title(title)
                .message(message));

        return messageItems;
    }

    public static void marker(
            PumpHistorySender pumpHistorySender, Realm realm, long pumpMAC,
            Date eventDate, int eventRTC, int eventOFFSET,
            RECORDTYPE recordtype,
            int duration,
            byte carbUnits,
            double carbInput,
            double insulin) {

        PumpHistoryMarker record = realm.where(PumpHistoryMarker.class)
                .equalTo("pumpMAC", pumpMAC)
                .equalTo("eventRTC", eventRTC)
                .equalTo("recordtype", recordtype.value())
                .findFirst();

        if (record == null) {
            Log.d(TAG, "*new* recordtype: " + recordtype.name());
            record = realm.createObject(PumpHistoryMarker.class);
            record.pumpMAC = pumpMAC;
            record.recordtype = recordtype.value();
            record.eventDate = eventDate;
            record.eventRTC = eventRTC;
            record.eventOFFSET = eventOFFSET;
            record.duration = duration;
            record.carbUnits = carbUnits;
            record.carbInput = carbInput;
            record.insulin = insulin;
            record.key = HistoryUtils.key("MARK", eventRTC);
            pumpHistorySender.setSenderREQ(record);
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

    public int getCarbUnits() {
        return carbUnits;
    }

    public double getCarbInput() {
        return carbInput;
    }

    public double getInsulin() {
        return insulin;
    }

    public int getDuration() {
        return duration;
    }
}
