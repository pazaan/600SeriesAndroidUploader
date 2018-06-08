package info.nightscout.android.model.medtronicNg;

import android.util.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.android.R;
import info.nightscout.android.history.MessageItem;
import info.nightscout.android.history.NightscoutItem;
import info.nightscout.android.history.PumpHistoryParser;
import info.nightscout.android.history.PumpHistorySender;
import info.nightscout.android.utils.FormatKit;
import info.nightscout.api.TreatmentsEndpoints;
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

        NightscoutItem nightscoutItem = new NightscoutItem();
        TreatmentsEndpoints.Treatment treatment = nightscoutItem.ack(senderACK.contains(senderID)).treatment();

        switch (RECORDTYPE.convert(recordtype)) {

            case FOOD:
                treatment.setEventType("Carb Correction");
                treatment.setKey600(key);
                treatment.setCreated_at(eventDate);

                // conversion for unit type
                double carbInputAsGrams;
                String exchanges;
                double gramsPerExchange = Double.parseDouble(pumpHistorySender.senderVar(senderID, PumpHistorySender.SENDEROPT.GRAMS_PER_EXCHANGE, "15"));
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
                        "Event Marker",
                        "Food",
                        FormatKit.getInstance().getString(R.string.carb),
                        FormatKit.getInstance().formatAsGrams(carbInputAsGrams),
                        exchanges));

                nightscoutItems.add(nightscoutItem);
                break;

            case EXERCISE:
                treatment.setEventType("Exercise");
                treatment.setKey600(key);
                treatment.setCreated_at(eventDate);
                treatment.setDuration((float) duration);
                nightscoutItems.add(nightscoutItem);
                break;

            case INJECTION:
                treatment.setEventType("Correction Bolus");
                treatment.setKey600(key);
                treatment.setCreated_at(eventDate);
                treatment.setInsulin((float) insulin);
                treatment.setNotes(String.format("%s: %s %s %s",
                        "Event Marker",
                        "Injection",
                        FormatKit.getInstance().getString(R.string.bolus),
                        FormatKit.getInstance().formatAsInsulin(insulin)));
                nightscoutItems.add(nightscoutItem);
                break;

            case OTHER:
                treatment.setEventType("Question");
                treatment.setKey600(key);
                treatment.setCreated_at(eventDate);
                treatment.setNotes(String.format("%s: %s",
                        "Event Marker",
                        "Other"));
                nightscoutItems.add(nightscoutItem);
                break;

            default:
        }

        return nightscoutItems;
    }

    @Override
    public List<MessageItem> message(PumpHistorySender pumpHistorySender, String senderID) {
        List<MessageItem> messageItems = new ArrayList<>();

        String title = "Event Marker";
        String message = "";

        switch (RECORDTYPE.convert(recordtype)) {

            case FOOD:
                // conversion for unit type
                double carbInputAsGrams;
                String exchanges;
                double gramsPerExchange = Double.parseDouble(pumpHistorySender.senderVar(senderID, PumpHistorySender.SENDEROPT.GRAMS_PER_EXCHANGE, "15"));
                if (PumpHistoryParser.CARB_UNITS.EXCHANGES.equals(carbUnits)) {
                    carbInputAsGrams = gramsPerExchange * carbInput;
                    exchanges = String.format(", %s",
                            FormatKit.getInstance().formatAsExchanges(carbInput));
                } else {
                    carbInputAsGrams = carbInput;
                    exchanges = "";
                }
                message = String.format("%s %s %s%s",
                        "Food",
                        FormatKit.getInstance().getString(R.string.carb),
                        FormatKit.getInstance().formatAsGrams(carbInputAsGrams),
                        exchanges);
                break;

            case EXERCISE:
                message = String.format("%s %s %s",
                        "Exercise",
                        FormatKit.getInstance().getString(R.string.duration),
                        FormatKit.getInstance().formatMinutesAsHM(duration));
                break;

            case INJECTION:
                message = String.format("%s %s %s",
                        "Injection",
                        FormatKit.getInstance().getString(R.string.bolus),
                        FormatKit.getInstance().formatAsInsulin(insulin));
                break;

            case OTHER:
                message = String.format("%s",
                        "Other");
                break;

            default:
                return messageItems;
        }

        messageItems.add(new MessageItem()
                .key(key)
                .date(eventDate)
                .clock(FormatKit.getInstance().formatAsClock(eventDate))
                .title(title)
                .message(message));

        return messageItems;
    }

    public static void marker(PumpHistorySender pumpHistorySender, Realm realm, Date eventDate, int eventRTC, int eventOFFSET,
                              RECORDTYPE recordtype,
                              int duration,
                              byte carbUnits,
                              double carbInput,
                              double insulin) {

        PumpHistoryMarker markerRecord = realm.where(PumpHistoryMarker.class)
                .equalTo("eventRTC", eventRTC)
                .equalTo("recordtype", recordtype.value())
                .findFirst();

        if (markerRecord == null) {
            Log.d(TAG, "*new* recordtype: " + recordtype.name());
            markerRecord = realm.createObject(PumpHistoryMarker.class);
            markerRecord.recordtype = recordtype.value();
            markerRecord.eventDate = eventDate;
            markerRecord.eventRTC = eventRTC;
            markerRecord.eventOFFSET = eventOFFSET;
            markerRecord.duration = duration;
            markerRecord.carbUnits = carbUnits;
            markerRecord.carbInput = carbInput;
            markerRecord.insulin = insulin;
            markerRecord.key = String.format("MARK%08X", eventRTC);
            pumpHistorySender.senderREQ(markerRecord);
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
