package info.nightscout.android.model.medtronicNg;

import android.util.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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

public class PumpHistoryBasal extends RealmObject implements PumpHistoryInterface {
    @Ignore
    private static final String TAG = PumpHistoryBasal.class.getSimpleName();

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

    @Index
    private boolean completed;
    @Index
    private boolean canceled;

    private byte preset;
    private byte type;
    private int percentageOfRate;
    private double rate;
    private int programmedDuration;
    private int completedDuration;

    private byte suspendReason;
    private byte resumeReason;

    public enum RECORDTYPE {
        PROGRAMMED(1),
        COMPLETED(2),
        SUSPEND(3),
        RESUME(4),
        NA(-1);

        private int value;

        RECORDTYPE(int value) {
            this.value = value;
        }

        public byte value() {
            return (byte) this.value;
        }

        public boolean equals(byte value) {
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

        if (!pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.BASAL)) {
            HistoryUtils.nightscoutDeleteTreatment(nightscoutItems, this, senderID);
            return nightscoutItems;
        }

        TreatmentsEndpoints.Treatment treatment;

        switch (RECORDTYPE.convert(recordtype)) {

            case SUSPEND:
                treatment = HistoryUtils.nightscoutTreatment(nightscoutItems, this, senderID);
                treatment.setEventType("Temp Basal");
                treatment.setDuration((float) programmedDuration);
                treatment.setAbsolute((float) 0);

                treatment.setNotes(String.format("%s: %s",
                        FormatKit.getInstance().getString(R.string.pump_suspend__pump_suspended_insulin_delivery),
                        PumpHistoryParser.SUSPEND_REASON.convert(suspendReason).string()));
                break;

            case RESUME:
                treatment = HistoryUtils.nightscoutTreatment(nightscoutItems, this, senderID);
                treatment.setEventType("Temp Basal");
                treatment.setDuration((float) programmedDuration);

                treatment.setNotes(String.format("%s: %s",
                        FormatKit.getInstance().getString(R.string.pump_resume__pump_resumed_insulin_delivery),
                        PumpHistoryParser.RESUME_REASON.convert(resumeReason).string()));

                // temp still in progress after resume?
                if (programmedDuration > 0) {
                    if (PumpHistoryParser.TEMP_BASAL_TYPE.PERCENT.equals(type))
                        treatment.setPercent((float) (percentageOfRate - 100));
                    else
                        treatment.setAbsolute((float) rate);
                }
                break;

            case PROGRAMMED:
                treatment = HistoryUtils.nightscoutTreatment(nightscoutItems, this, senderID);
                treatment.setEventType("Temp Basal");
                treatment.setDuration((float) programmedDuration);

                if (PumpHistoryParser.TEMP_BASAL_TYPE.PERCENT.equals(type))
                    treatment.setPercent((float) (percentageOfRate - 100));
                else
                    treatment.setAbsolute((float) rate);

                treatment.setNotes(String.format("%s: %s %s %s%s",
                        FormatKit.getInstance().getString(R.string.text__Temp_Basal),
                        PumpHistoryParser.TEMP_BASAL_TYPE.PERCENT.equals(type) ?
                                FormatKit.getInstance().formatAsPercent(percentageOfRate) :
                                FormatKit.getInstance().formatAsInsulin(rate),
                        FormatKit.getInstance().getString(R.string.text__duration),
                        FormatKit.getInstance().formatMinutesAsHM(programmedDuration),
                        PumpHistoryParser.TEMP_BASAL_PRESET.TEMP_BASAL_PRESET_0.equals(preset) ? "" :
                                String.format(" [%s]", pumpHistorySender.getList(senderID, PumpHistorySender.SENDEROPT.BASAL_PRESET, preset - 1))));
                break;

            case COMPLETED:
                if (canceled) {
                    treatment = HistoryUtils.nightscoutTreatment(nightscoutItems, this, senderID);
                    treatment.setEventType("Temp Basal");
                    treatment.setDuration((float) 0);

                    treatment.setNotes(String.format("%s: %s, %s %s %s",
                            FormatKit.getInstance().getString(R.string.text__Temp_Basal),
                            FormatKit.getInstance().getString(R.string.text__cancelled),
                            PumpHistoryParser.TEMP_BASAL_TYPE.PERCENT.equals(type) ?
                                    FormatKit.getInstance().formatAsPercent(percentageOfRate) :
                                    FormatKit.getInstance().formatAsInsulin(rate),
                            FormatKit.getInstance().getString(R.string.text__duration),
                            FormatKit.getInstance().formatMinutesAsHM(completedDuration)));
                }
                break;
        }

        return nightscoutItems;
    }

    @Override
    public List<MessageItem> message(PumpHistorySender sender, String senderID) {
        List<MessageItem> messageItems = new ArrayList<>();

        String title;
        String message;
        MessageItem.TYPE type;

        switch (RECORDTYPE.convert(recordtype)) {

            case SUSPEND:
                // skip suspend messages due to consumable changes
                if (PumpHistoryParser.SUSPEND_REASON.ALARM_SUSPEND.equals(suspendReason) ||
                        PumpHistoryParser.SUSPEND_REASON.SET_CHANGE_SUSPEND.equals(suspendReason)) return messageItems;

                type = MessageItem.TYPE.SUSPEND;
                title = FormatKit.getInstance().getString(R.string.text__Pump_Suspend);
                message = PumpHistoryParser.SUSPEND_REASON.convert(suspendReason).string();
                break;

            case RESUME:
                // skip resume messages due to consumable changes
                if (PumpHistoryParser.RESUME_REASON.USER_CLEARS_ALARM.equals(resumeReason) ||
                        PumpHistoryParser.SUSPEND_REASON.ALARM_SUSPEND.equals(suspendReason) ||
                        PumpHistoryParser.SUSPEND_REASON.SET_CHANGE_SUSPEND.equals(suspendReason)) return messageItems;

                type = MessageItem.TYPE.RESUME;
                title = FormatKit.getInstance().getString(R.string.text__Pump_Resume);
                message = PumpHistoryParser.RESUME_REASON.convert(resumeReason).string();
                break;

            case PROGRAMMED:
                type = MessageItem.TYPE.BASAL;
                title = FormatKit.getInstance().getString(R.string.text__Basal);
                message = String.format("%s %s %s %s%s",
                        FormatKit.getInstance().getString(R.string.text__Temp),
                        PumpHistoryParser.TEMP_BASAL_TYPE.PERCENT.equals(this.type) ?
                                FormatKit.getInstance().formatAsPercent(percentageOfRate) :
                                FormatKit.getInstance().formatAsInsulin(rate),
                        FormatKit.getInstance().getString(R.string.text__duration),
                        FormatKit.getInstance().formatMinutesAsHM(programmedDuration),
                        completed && !canceled ? String.format(" (%s)",
                                FormatKit.getInstance().getString(R.string.text__completed)) : "");
                break;

            case COMPLETED:
                if (!canceled) return messageItems;
                type = MessageItem.TYPE.BASAL;
                title = FormatKit.getInstance().getString(R.string.text__Basal);
                message = String.format("%s %s %s %s %s",
                        FormatKit.getInstance().getString(R.string.text__Temp),
                        FormatKit.getInstance().getString(R.string.text__cancelled),
                        PumpHistoryParser.TEMP_BASAL_TYPE.PERCENT.equals(this.type) ?
                                FormatKit.getInstance().formatAsPercent(percentageOfRate) :
                                FormatKit.getInstance().formatAsInsulin(rate),
                        FormatKit.getInstance().getString(R.string.text__duration),
                        FormatKit.getInstance().formatMinutesAsHM(completedDuration));
                break;

            default:
                return messageItems;
        }

        messageItems.add(new MessageItem()
                .type(type)
                .date(eventDate)
                .clock(FormatKit.getInstance().formatAsClock(eventDate.getTime()))
                .title(title)
                .message(message));

        return messageItems;
    }

    public static void programmed(
            PumpHistorySender pumpHistorySender, Realm realm, long pumpMAC,
            Date eventDate, int eventRTC, int eventOFFSET,
            byte preset,
            byte type,
            double rate,
            int percentageOfRate,
            int duration) {

        PumpHistoryBasal programmedRecord = realm.where(PumpHistoryBasal.class)
                .equalTo("pumpMAC", pumpMAC)
                .equalTo("recordtype", RECORDTYPE.PROGRAMMED.value())
                .equalTo("eventRTC", eventRTC)
                .findFirst();
        if (programmedRecord == null) {
            Log.d(TAG, "*new* temp basal programmed");
            programmedRecord = realm.createObject(PumpHistoryBasal.class);
            programmedRecord.pumpMAC = pumpMAC;
            programmedRecord.recordtype = RECORDTYPE.PROGRAMMED.value();
            programmedRecord.eventDate = eventDate;
            programmedRecord.eventRTC = eventRTC;
            programmedRecord.eventOFFSET = eventOFFSET;
            programmedRecord.programmedDuration = duration;
            programmedRecord.preset = preset;
            programmedRecord.type = type;
            programmedRecord.rate = rate;
            programmedRecord.percentageOfRate = percentageOfRate;
            programmedRecord.completed = false;
            programmedRecord.canceled = false;
            programmedRecord.key = HistoryUtils.key("BASAL", eventRTC);
            pumpHistorySender.setSenderREQ(programmedRecord);

            // look for a corresponding completed temp basal
            PumpHistoryBasal completedRecord = realm.where(PumpHistoryBasal.class)
                    .equalTo("pumpMAC", pumpMAC)
                    .equalTo("recordtype", RECORDTYPE.COMPLETED.value())
                    .equalTo("completed", false)
                    .greaterThan("eventRTC", eventRTC)
                    .lessThan("eventRTC", HistoryUtils.offsetRTC(eventRTC, (duration + 1) * 60))
                    .findFirst();
            if (completedRecord != null) {
                boolean canceled = completedRecord.canceled;
                int completedDuration = canceled ? (int) Math.round((double) (completedRecord.eventRTC - eventRTC) / 60) : duration;
                Log.d(TAG, "*update* temp basal completed (from programed) canceled=" + canceled + " completedDuration=" + completedDuration);

                programmedRecord.completedDuration = completedDuration;
                programmedRecord.canceled = canceled;
                programmedRecord.completed = true;

                completedRecord.completedDuration = completedDuration;
                completedRecord.completed = true;
                // only run completed senders when a temp basal is canceled
                if (canceled) pumpHistorySender.setSenderREQ(completedRecord);
            }

        }
    }

    public static void completed(
            PumpHistorySender pumpHistorySender, Realm realm, long pumpMAC,
            Date eventDate, int eventRTC, int eventOFFSET,
            byte preset,
            byte type,
            double rate,
            int percentageOfRate,
            int duration,
            boolean canceled) {

        PumpHistoryBasal completedRecord = realm.where(PumpHistoryBasal.class)
                .equalTo("pumpMAC", pumpMAC)
                .equalTo("recordtype", RECORDTYPE.COMPLETED.value())
                .equalTo("eventRTC", eventRTC)
                .findFirst();
        if (completedRecord == null) {
            Log.d(TAG, "*new* temp basal completed");
            completedRecord = realm.createObject(PumpHistoryBasal.class);
            completedRecord.pumpMAC = pumpMAC;
            completedRecord.recordtype = RECORDTYPE.COMPLETED.value();
            completedRecord.eventDate = eventDate;
            completedRecord.eventRTC = eventRTC;
            completedRecord.eventOFFSET = eventOFFSET;
            completedRecord.programmedDuration = duration;
            completedRecord.preset = preset;
            completedRecord.type = type;
            completedRecord.rate = rate;
            completedRecord.percentageOfRate = percentageOfRate;
            completedRecord.completed = false;
            completedRecord.canceled = canceled;
            completedRecord.key = HistoryUtils.key("BASAL", eventRTC);

            // look for a corresponding programmed temp basal
            PumpHistoryBasal programmedRecord = realm.where(PumpHistoryBasal.class)
                    .equalTo("pumpMAC", pumpMAC)
                    .equalTo("recordtype", RECORDTYPE.PROGRAMMED.value())
                    .equalTo("completed", false)
                    .greaterThan("eventRTC", HistoryUtils.offsetRTC(eventRTC, - (duration + 1) * 60))
                    .lessThan("eventRTC", eventRTC)
                    .findFirst();
            if (programmedRecord != null) {
                int completedDuration = canceled ? (int) Math.round((double) (eventRTC - programmedRecord.eventRTC) / 60) : duration;
                Log.d(TAG, "*update* temp basal programed (from completed) canceled=" + canceled + " completedDuration=" + completedDuration);

                programmedRecord.completedDuration = completedDuration;
                programmedRecord.canceled = canceled;
                programmedRecord.completed = true;

                completedRecord.completedDuration = completedDuration;
                completedRecord.completed = true;
                // only run completed senders when a temp basal is canceled
                if (canceled) pumpHistorySender.setSenderREQ(completedRecord);
            }
        }
    }

    public static void suspend(
            PumpHistorySender pumpHistorySender, Realm realm, long pumpMAC,
            Date eventDate, int eventRTC, int eventOFFSET,
            byte reason) {

        PumpHistoryBasal suspendRecord = realm.where(PumpHistoryBasal.class)
                .equalTo("pumpMAC", pumpMAC)
                .equalTo("recordtype", RECORDTYPE.SUSPEND.value())
                .equalTo("eventRTC", eventRTC)
                .findFirst();
        if (suspendRecord == null) {
            Log.d(TAG, "*new* suspend basal");
            suspendRecord = realm.createObject(PumpHistoryBasal.class);
            suspendRecord.pumpMAC = pumpMAC;
            suspendRecord.recordtype = RECORDTYPE.SUSPEND.value();
            suspendRecord.eventDate = eventDate;
            suspendRecord.eventRTC = eventRTC;
            suspendRecord.eventOFFSET = eventOFFSET;
            suspendRecord.suspendReason = reason;
            suspendRecord.programmedDuration = 24 * 60;
            suspendRecord.completed = false;
            suspendRecord.key = HistoryUtils.key("SUSPEND", eventRTC);
            pumpHistorySender.setSenderREQ(suspendRecord);
        }
    }

    public static void resume(
            PumpHistorySender pumpHistorySender, Realm realm, long pumpMAC,
            Date eventDate, int eventRTC, int eventOFFSET,
            byte reason) {

        PumpHistoryBasal resumeRecord = realm.where(PumpHistoryBasal.class)
                .equalTo("pumpMAC", pumpMAC)
                .equalTo("recordtype", RECORDTYPE.RESUME.value())
                .equalTo("eventRTC", eventRTC)
                .findFirst();
        if (resumeRecord == null) {
            Log.d(TAG, "*new* resume basal");
            resumeRecord = realm.createObject(PumpHistoryBasal.class);
            resumeRecord.pumpMAC = pumpMAC;
            resumeRecord.recordtype = RECORDTYPE.RESUME.value();
            resumeRecord.eventDate = eventDate;
            resumeRecord.eventRTC = eventRTC;
            resumeRecord.eventOFFSET = eventOFFSET;
            resumeRecord.resumeReason = reason;
            resumeRecord.programmedDuration = 0;
            resumeRecord.key = HistoryUtils.key("RESUME", eventRTC);
            pumpHistorySender.setSenderREQ(resumeRecord);

            // look for corresponding suspend and update it's duration
            RealmResults<PumpHistoryBasal> suspendRecords = realm.where(PumpHistoryBasal.class)
                    .equalTo("pumpMAC", pumpMAC)
                    .equalTo("recordtype", RECORDTYPE.SUSPEND.value())
                    .equalTo("completed", false)
                    .greaterThan("eventRTC", HistoryUtils.offsetRTC(eventRTC, - 24 * 60 * 60))
                    .lessThan("eventRTC", eventRTC)
                    .sort("eventDate", Sort.DESCENDING)
                    .findAll();
            if (suspendRecords.size() > 0) {
                PumpHistoryBasal suspendRecord = suspendRecords.first();
                suspendRecord.completed = true;
                suspendRecord.completedDuration = (int) Math.round(((double) (eventRTC - suspendRecord.getEventRTC())) / 60);
                suspendRecord.resumeReason = reason;
                resumeRecord.suspendReason = suspendRecord.suspendReason;
            }

            // look for a temp that was in progress and resume temp with recalculated duration (for nightscout)
            RealmResults<PumpHistoryBasal> realmResults = realm.where(PumpHistoryBasal.class)
                    .equalTo("pumpMAC", pumpMAC)
                    .equalTo("recordtype", RECORDTYPE.PROGRAMMED.value())
                    .equalTo("completed", false)
                    .greaterThan("eventRTC", HistoryUtils.offsetRTC(eventRTC, - 24 * 60 * 60))
                    .lessThan("eventRTC", eventRTC)
                    .sort("eventDate", Sort.DESCENDING)
                    .findAll();
            if (realmResults.size() == 1) {
                PumpHistoryBasal programedRecord = realmResults.first();
                int remaining = (int) Math.round(((double) programedRecord.programmedDuration - ((double) (eventRTC - programedRecord.eventRTC) / 60)));
                if (remaining > 0) {
                    Log.d(TAG, "temp still in progress after resumed basal");
                    resumeRecord.programmedDuration = remaining;
                    resumeRecord.type = programedRecord.type;
                    resumeRecord.rate = programedRecord.rate;
                    resumeRecord.percentageOfRate = programedRecord.percentageOfRate;
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

    public byte getRecordtype() {
        return recordtype;
    }

    public int getEventRTC() {
        return eventRTC;
    }

    public int getEventOFFSET() {
        return eventOFFSET;
    }

    public byte getPreset() {
        return preset;
    }

    public boolean isCompleted() {
        return completed;
    }

    public boolean isCanceled() {
        return canceled;
    }

    public byte getType() {
        return type;
    }

    public int getPercentageOfRate() {
        return percentageOfRate;
    }

    public double getRate() {
        return rate;
    }

    public int getProgrammedDuration() {
        return programmedDuration;
    }

    public int getCompletedDuration() {
        return completedDuration;
    }

    public byte getSuspendReason() {
        return suspendReason;
    }

    public byte getResumeReason() {
        return resumeReason;
    }
}
