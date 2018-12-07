package info.nightscout.android.model.medtronicNg;

import android.util.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.android.PumpAlert;
import info.nightscout.android.history.HistoryUtils;
import info.nightscout.android.history.MessageItem;
import info.nightscout.android.history.NightscoutItem;
import info.nightscout.android.history.PumpHistorySender;
import info.nightscout.android.medtronic.exception.IntegrityException;
import info.nightscout.android.utils.FormatKit;
import info.nightscout.android.upload.nightscout.TreatmentsEndpoints;
import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.Sort;
import io.realm.annotations.Ignore;
import io.realm.annotations.Index;

/**
 * Created by Pogman on 15.04.18.
 */

public class PumpHistoryAlarm extends RealmObject implements PumpHistoryInterface {
    @Ignore
    private static final String TAG = PumpHistoryAlarm.class.getSimpleName();

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
    private boolean alarmed;
    @Index
    private int alarmedRTC;
    private int alarmedOFFSET;
    private Date alarmedDate;

    @Index
    private boolean cleared;
    @Index
    private int clearedRTC;
    private int clearedOFFSET;
    private Date clearedDate;

    @Index
    private boolean silenced;

    @Index
    private int faultNumber;

    private byte notificationMode;
    private boolean extraData;
    private boolean alarmHistory;
    private byte[] alarmData;

    @Override
    public List<NightscoutItem> nightscout(PumpHistorySender pumpHistorySender, String senderID) {
        List<NightscoutItem> nightscoutItems = new ArrayList<>();

        if (HistoryUtils.nightscoutTTL(nightscoutItems,this, senderID))
            return nightscoutItems;

        if (silenced && !pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.ALARM_SILENCED))
            return nightscoutItems;

        PumpAlert pumpAlert = new PumpAlert()
                .record(this)
                .units(pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.GLUCOSE_UNITS))
                .build();

        if (pumpAlert.getPriority() < Integer.parseInt(pumpHistorySender.getVar(senderID, PumpHistorySender.SENDEROPT.ALARM_PRIORITY, "0")))
            return nightscoutItems;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s: %s",
                pumpAlert.getTitle(),
                pumpAlert.getCode() == 0 | pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.ALARM_EXTENDED)
                        ? pumpAlert.getComplete() : pumpAlert.getMessage()));

        if (pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.ALARM_SILENCED))
            sb.append(pumpAlert.getSilenced(true));

        if (pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.ALARM_CLEARED))
            sb.append(pumpAlert.getCleared(true));

        if (pumpAlert.getCode() == 0 || pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.ALARM_FAULTCODE))
            sb.append(pumpAlert.getInfo(true));

        TreatmentsEndpoints.Treatment treatment = HistoryUtils.nightscoutTreatment(nightscoutItems, this, senderID);
        treatment.setEventType("Announcement");
        treatment.setAnnouncement(true);
        treatment.setNotes(sb.toString());

        return nightscoutItems;
    }

    @Override
    public List<MessageItem> message(PumpHistorySender pumpHistorySender, String senderID) {
        List<MessageItem> messageItems = new ArrayList<>();

        // check if already sent as it may re-trigger due to 'cleared' updates
        if (senderACK.contains(senderID)) return messageItems;

        if ((cleared && !pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.ALARM_CLEARED))
                || (silenced && !pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.ALARM_SILENCED))) {
            return messageItems;
        }

        PumpAlert pumpAlert = new PumpAlert()
                .record(this)
                .units(pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.GLUCOSE_UNITS))
                .build();

        if (pumpAlert.getCode() == 0
                || pumpAlert.getPriority() < Integer.parseInt(pumpHistorySender.getVar(senderID, PumpHistorySender.SENDEROPT.ALARM_PRIORITY, "0")))
            return messageItems;

        MessageItem.TYPE type;
        if (pumpAlert.getType() == PumpAlert.TYPE.REMINDER)
            type = MessageItem.TYPE.REMINDER;
        else
            switch (faultNumber) {
                case 816:
                    type = MessageItem.TYPE.ALERT_ON_HIGH;
                    break;
                case 802:
                case 803:
                    type = MessageItem.TYPE.ALERT_ON_LOW;
                    break;
                case 817:
                    type = MessageItem.TYPE.ALERT_BEFORE_HIGH;
                    break;
                case 805:
                    type = MessageItem.TYPE.ALERT_BEFORE_LOW;
                    break;
                case 820:
                case 821:
                case 823:
                case 824:
                    type = MessageItem.TYPE.ALERT_AUTOMODE_EXIT;
                    break;
                default:
                    type = MessageItem.TYPE.ALERT;
            }

        messageItems.add(new MessageItem()
                .type(type)
                .date(alarmedDate)
                .priority(MessageItem.PRIORITY.convert(pumpAlert.getPriority()))
                .clock(FormatKit.getInstance().formatAsClock(alarmedDate.getTime()).replace(" ", ""))
                .title(pumpAlert.getTitleAlt())
                .message(pumpAlert.getMessage())
                .extended(String.format("%s%s%s",
                        pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.ALARM_EXTENDED)
                                ? pumpAlert.getExtended() : "",
                        pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.ALARM_CLEARED)
                                ? pumpAlert.getCleared(true) : "",
                        pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.ALARM_SILENCED)
                                ? pumpAlert.getSilenced(true) : ""))
                .cleared(cleared)
                .silenced(silenced));

        return messageItems;
    }

    public static void alarm(
            PumpHistorySender pumpHistorySender, Realm realm, long pumpMAC,
            Date eventDate, int eventRTC, int eventOFFSET,
            int faultNumber,
            byte notificationMode,
            boolean alarmHistory,
            boolean extraData,
            byte[] alarmData) throws IntegrityException {

        PumpHistoryAlarm record;

        // check if pump re-raised/escalated an alarm that is already recorded in the history
        if (!alarmHistory && notificationMode == 3) {
            return;
        }

        // check if a silenced alarm notification
        else if (faultNumber == 110) {

            record = realm.where(PumpHistoryAlarm.class)
                    .equalTo("pumpMAC", pumpMAC)
                    .equalTo("alarmedRTC", eventRTC)
                    .equalTo("alarmed", true)
                    .equalTo("silenced", false)
                    .findFirst();

            if (record != null) {
                Log.d(TAG, "*update*" + " alarm (silenced)");
                record.silenced = true;
                pumpHistorySender.setSenderREQ(record);
            }
        }

        else {

            record = realm.where(PumpHistoryAlarm.class)
                    .equalTo("pumpMAC", pumpMAC)
                    .equalTo("alarmedRTC", eventRTC)
                    .equalTo("alarmed", true)
                    .equalTo("faultNumber", faultNumber)
                    .findFirst();

            if (record == null) {
                // look for a cleared alarm
                RealmResults<PumpHistoryAlarm> results = realm.where(PumpHistoryAlarm.class)
                        .equalTo("pumpMAC", pumpMAC)
                        .equalTo("alarmed", false)
                        .equalTo("cleared", true)
                        .equalTo("faultNumber", faultNumber)
                        .greaterThanOrEqualTo("clearedRTC", eventRTC)
                        .lessThan("clearedRTC", eventRTC + 12 * 60 * 60)
                        .sort("clearedDate", Sort.ASCENDING)
                        .findAll();
                if (results.size() == 0) {
                    Log.d(TAG, "*new*" + " alarm");
                    record = realm.createObject(PumpHistoryAlarm.class);
                    record.pumpMAC = pumpMAC;
                } else {
                    Log.d(TAG, "*update*" + " alarm");
                    record = results.first();
                }

                record.eventDate = eventDate;

                record.alarmedDate = eventDate;
                record.alarmedRTC = eventRTC;
                record.alarmedOFFSET = eventOFFSET;
                record.alarmed = true;

                record.faultNumber = faultNumber;
                record.notificationMode = notificationMode;
                record.alarmHistory = alarmHistory;

                record.extraData = extraData;
                if (extraData && alarmData != null) record.alarmData = alarmData;

                // key composed of 2 byte faultNumber and 4 byte eventRTC due to multiple alerts at the same time
                record.key = HistoryUtils.key("ALARM", (short) faultNumber, eventRTC);
                pumpHistorySender.setSenderREQ(record);
            }

            else HistoryUtils.integrity(record, eventDate);
        }
    }

    public static void cleared(
            PumpHistorySender pumpHistorySender, Realm realm, long pumpMAC,
            Date eventDate, int eventRTC, int eventOFFSET,
            int faultNumber) {

        if (faultNumber == 110) return; // ignore cleared silenced alerts

        PumpHistoryAlarm record = realm.where(PumpHistoryAlarm.class)
                .equalTo("pumpMAC", pumpMAC)
                .equalTo("cleared", true)
                .equalTo("clearedRTC", eventRTC)
                .equalTo("faultNumber", faultNumber)
                .findFirst();

        if (record == null) {
            // look for a alarm
            RealmResults<PumpHistoryAlarm> results = realm.where(PumpHistoryAlarm.class)
                    .equalTo("pumpMAC", pumpMAC)
                    .equalTo("alarmed", true)
                    .equalTo("cleared", false)
                    .equalTo("silenced", false)
                    .equalTo("faultNumber", faultNumber)
                    .lessThanOrEqualTo("alarmedRTC", eventRTC)
                    .greaterThan("alarmedRTC", eventRTC - 12 * 60 * 60)
                    //.sort("alarmedDate", Sort.DESCENDING)
                    .sort("alarmedDate", Sort.ASCENDING)
                    .findAll();
            if (results.size() == 0) {
                Log.d(TAG, "*new*" + " alarm (cleared)");
                record = realm.createObject(PumpHistoryAlarm.class);
                record.pumpMAC = pumpMAC;
                record.eventDate = eventDate;
                record.faultNumber = faultNumber;
            } else {
                Log.d(TAG, "*update*" + " alarm (cleared)");
                record = results.first();
                pumpHistorySender.setSenderREQ(record);
            }

            record.clearedDate = eventDate;
            record.clearedRTC = eventRTC;
            record.clearedOFFSET = eventOFFSET;
            record.cleared = true;
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

    public boolean isAlarmed() {
        return alarmed;
    }

    public int getAlarmedRTC() {
        return alarmedRTC;
    }

    public int getAlarmedOFFSET() {
        return alarmedOFFSET;
    }

    public Date getAlarmedDate() {
        return alarmedDate;
    }

    public boolean isCleared() {
        return cleared;
    }

    public int getClearedRTC() {
        return clearedRTC;
    }

    public int getClearedOFFSET() {
        return clearedOFFSET;
    }

    public Date getClearedDate() {
        return clearedDate;
    }

    public boolean isSilenced() {
        return silenced;
    }

    public int getFaultNumber() {
        return faultNumber;
    }

    public byte getNotificationMode() {
        return notificationMode;
    }

    public boolean isExtraData() {
        return extraData;
    }

    public boolean isAlarmHistory() {
        return alarmHistory;
    }

    public byte[] getAlarmData() {
        return alarmData;
    }
}