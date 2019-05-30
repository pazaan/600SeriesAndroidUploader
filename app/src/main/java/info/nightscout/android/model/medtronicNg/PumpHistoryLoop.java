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
import info.nightscout.android.upload.nightscout.TreatmentsEndpoints;
import info.nightscout.android.utils.FormatKit;
import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.annotations.Ignore;
import io.realm.annotations.Index;

/**
 * Created by Pogman on 17.1.18.
 */

public class PumpHistoryLoop extends RealmObject implements PumpHistoryInterface {
    @Ignore
    private static final String TAG = PumpHistoryLoop.class.getSimpleName();

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

    private int bolusRef = -1;
    private double deliveredAmount;

    private byte transitionValue;
    private byte transitionReason;

    private byte basalPattern;

    public enum RECORDTYPE {
        MICROBOLUS(1),
        TRANSITION_IN(2),
        TRANSITION_OUT(3),
        RESTART_BASAL(4),
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

            case MICROBOLUS:
                // Synthesize Closed Loop Microboluses into Temp Basals
                treatment = HistoryUtils.nightscoutTreatment(nightscoutItems, this, senderID);
                treatment.setEventType("Temp Basal");
                treatment.setDuration((float) 5);
                treatment.setAbsolute((float) (deliveredAmount * 12));
                treatment.setNotes(String.format("%s %s",
                        FormatKit.getInstance().getString(R.string.text__microbolus),
                        FormatKit.getInstance().formatAsInsulin(deliveredAmount, 3)));
                break;

            case TRANSITION_IN:
                treatment = HistoryUtils.nightscoutTreatment(nightscoutItems, this, senderID);
                treatment.setEventType("Profile Switch");
                treatment.setProfile("Auto Mode");
                treatment.setNotes(String.format("%s: %s (%s)",
                        FormatKit.getInstance().getString(R.string.text__Auto_Mode),
                        FormatKit.getInstance().getString(R.string.text__active),
                        PumpHistoryParser.CL_TRANSITION_REASON.convert(transitionReason).string()));
                break;

            case RESTART_BASAL:
                treatment = HistoryUtils.nightscoutTreatment(nightscoutItems, this, senderID);
                treatment.setEventType("Profile Switch");
                treatment.setProfile(pumpHistorySender.getList(senderID, PumpHistorySender.SENDEROPT.BASAL_PATTERN, basalPattern - 1));
                treatment.setNotes(String.format("%s: %s (%s)",
                        FormatKit.getInstance().getString(R.string.text__Auto_Mode),
                        FormatKit.getInstance().getString(R.string.text__stopped),
                        PumpHistoryParser.CL_TRANSITION_REASON.convert(transitionReason).string()));
                break;
        }

        return nightscoutItems;
    }

    @Override
    public List<MessageItem> message(PumpHistorySender sender, String senderID) {
        List<MessageItem> messageItems = new ArrayList<>();

        if (RECORDTYPE.TRANSITION_IN.equals(recordtype) && sender.isOpt(senderID, PumpHistorySender.SENDEROPT.AUTOMODE_ACTIVE)) {
            messageItems.add(new MessageItem()
                    .type(MessageItem.TYPE.AUTOMODE_ACTIVE)
                    .date(eventDate)
                    .clock(FormatKit.getInstance().formatAsClock(eventDate.getTime()))
                    .title(FormatKit.getInstance().getString(R.string.text__Auto_Mode))
                    .message(PumpHistoryParser.CL_TRANSITION_REASON.convert(transitionReason).string()));
        }

        else if (RECORDTYPE.RESTART_BASAL.equals(recordtype) && sender.isOpt(senderID, PumpHistorySender.SENDEROPT.AUTOMODE_STOP)) {
            messageItems.add(new MessageItem()
                    .type(MessageItem.TYPE.AUTOMODE_STOP)
                    .date(eventDate)
                    .clock(FormatKit.getInstance().formatAsClock(eventDate.getTime()))
                    .title(FormatKit.getInstance().getString(R.string.text__Auto_Mode))
                    .message(PumpHistoryParser.CL_TRANSITION_REASON.convert(transitionReason).string()));
        }

        return messageItems;
    }

    public static void microbolus(
            PumpHistorySender pumpHistorySender, Realm realm, long pumpMAC,
            Date eventDate, int eventRTC, int eventOFFSET,
            int bolusRef,
            double deliveredAmount) {

        //if (true) return;

        PumpHistoryLoop record = realm.where(PumpHistoryLoop.class)
                .equalTo("pumpMAC", pumpMAC)
                .equalTo("eventRTC", eventRTC)
                .equalTo("recordtype", RECORDTYPE.MICROBOLUS.value())
                .findFirst();

        if (record == null) {
            Log.d(TAG, "*new* microbolus ref: " + bolusRef);
            record = realm.createObject(PumpHistoryLoop.class);
            record.pumpMAC = pumpMAC;
            record.recordtype = RECORDTYPE.MICROBOLUS.value();
            record.eventDate = eventDate;
            record.eventRTC = eventRTC;
            record.eventOFFSET = eventOFFSET;
            record.bolusRef = bolusRef;
            record.deliveredAmount = deliveredAmount;
            record.key = HistoryUtils.key("MB", eventRTC);
            pumpHistorySender.setSenderREQ(record);
        }
    }

    public static void transition(
            PumpHistorySender pumpHistorySender, Realm realm, long pumpMAC,
            Date eventDate, int eventRTC, int eventOFFSET,
            byte transitionValue,
            byte transitionReason) {

        PumpHistoryLoop record;

        switch (PumpHistoryParser.CL_TRANSITION_VALUE.convert(transitionValue)) {

            case CL_INTO_ACTIVE:
                record = realm.where(PumpHistoryLoop.class)
                        .equalTo("pumpMAC", pumpMAC)
                        .equalTo("eventRTC", eventRTC)
                        .equalTo("recordtype", RECORDTYPE.TRANSITION_IN.value())
                        .findFirst();
                if (record == null) {
                    Log.d(TAG, "*new* loop transition in event");
                    record = realm.createObject(PumpHistoryLoop.class);
                    record.pumpMAC = pumpMAC;
                    record.recordtype = RECORDTYPE.TRANSITION_IN.value();
                    record.eventDate = eventDate;
                    record.eventRTC = eventRTC;
                    record.eventOFFSET = eventOFFSET;
                    record.transitionValue = transitionValue;
                    record.transitionReason = transitionReason;
                    record.key = HistoryUtils.key("LOOP", eventRTC);
                    pumpHistorySender.setSenderREQ(record);
                }
                break;

            case CL_OUT_OF_ACTIVE:
                record = realm.where(PumpHistoryLoop.class)
                        .equalTo("eventRTC", eventRTC)
                        .equalTo("pumpMAC", pumpMAC)
                        .equalTo("recordtype", RECORDTYPE.TRANSITION_OUT.value())
                        .findFirst();
                if (record == null) {
                    Log.d(TAG, "*new* loop transition out event");
                    record = realm.createObject(PumpHistoryLoop.class);
                    record.pumpMAC = pumpMAC;
                    record.recordtype = RECORDTYPE.TRANSITION_OUT.value();
                    record.eventDate = eventDate;
                    record.eventRTC = eventRTC;
                    record.eventOFFSET = eventOFFSET;
                    record.transitionValue = transitionValue;
                    record.transitionReason = transitionReason;
                    record.key = HistoryUtils.key("LOOP", eventRTC);
                }

        }
    }

    public static void basal(
            PumpHistorySender pumpHistorySender, Realm realm, long pumpMAC,
            Date eventDate, int eventRTC, int eventOFFSET,
            byte pattern) {

        PumpHistoryLoop transitionRecord = realm.where(PumpHistoryLoop.class)
                .equalTo("pumpMAC", pumpMAC)
                .equalTo("recordtype", RECORDTYPE.TRANSITION_OUT.value())
                .greaterThan("eventRTC", HistoryUtils.offsetRTC(eventRTC, - 5 * 60))
                .lessThanOrEqualTo("eventRTC", eventRTC)
                .findFirst();

        if (transitionRecord != null) {

            PumpHistoryLoop record = realm.where(PumpHistoryLoop.class)
                    .equalTo("pumpMAC", pumpMAC)
                    .equalTo("recordtype", RECORDTYPE.RESTART_BASAL.value())
                    .equalTo("eventRTC", eventRTC)
                    .findFirst();

            if (record == null) {
                Log.d(TAG, "*new* loop restart basal pattern");
                record = realm.createObject(PumpHistoryLoop.class);
                record.pumpMAC = pumpMAC;
                record.recordtype = RECORDTYPE.RESTART_BASAL.value();
                record.eventDate = eventDate;
                record.eventRTC = eventRTC;
                record.eventOFFSET = eventOFFSET;
                record.basalPattern = pattern;
                record.transitionValue = transitionRecord.transitionValue;
                record.transitionReason = transitionRecord.transitionReason;
                record.key = HistoryUtils.key("LOOP", eventRTC);
                pumpHistorySender.setSenderREQ(record);
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

    public byte getRecordtype() {
        return recordtype;
    }

    public int getEventRTC() {
        return eventRTC;
    }

    public int getEventOFFSET() {
        return eventOFFSET;
    }

    public int getBolusRef() {
        return bolusRef;
    }

    public double getDeliveredAmount() {
        return deliveredAmount;
    }

    public byte getTransitionValue() {
        return transitionValue;
    }

    public byte getTransitionReason() {
        return transitionReason;
    }

    public byte getBasalPattern() {
        return basalPattern;
    }
}
