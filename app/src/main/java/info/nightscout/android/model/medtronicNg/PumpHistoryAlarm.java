package info.nightscout.android.model.medtronicNg;

import android.util.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.android.history.MessageItem;
import info.nightscout.android.history.NightscoutItem;
import info.nightscout.android.history.PumpHistoryParser;
import info.nightscout.android.history.PumpHistorySender;
import info.nightscout.android.utils.ToolKit;
import info.nightscout.android.utils.FormatKit;
import info.nightscout.api.TreatmentsEndpoints;
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

        if (senderDEL.contains(senderID)) {
            Log.d(TAG, "TTL delete record");
            NightscoutItem nightscoutItem = new NightscoutItem();
            nightscoutItems.add(nightscoutItem);
            TreatmentsEndpoints.Treatment treatment = nightscoutItem.delete().treatment();
            treatment.setKey600(key);
            return nightscoutItems;
        }
/*
        if ((cleared && !pumpHistorySender.senderOpt(senderID, PumpHistorySender.SENDEROPT.ALARM_CLEARED))
                || (silenced && !pumpHistorySender.senderOpt(senderID, PumpHistorySender.SENDEROPT.ALARM_SILENCED))) {
            return nightscoutItems;
        }
*/
        if (silenced && !pumpHistorySender.senderOpt(senderID, PumpHistorySender.SENDEROPT.ALARM_SILENCED))
            return nightscoutItems;

        String[] alert = FormatKit.getInstance().getAlertString(faultNumber);

        int alertpriority = Integer.parseInt(alert[1]);
        if (alertpriority < Integer.parseInt(pumpHistorySender.senderVar(senderID, PumpHistorySender.SENDEROPT.ALARM_PRIORITY, "0")))
            return nightscoutItems;

        String notes = "";

        NightscoutItem nightscoutItem = new NightscoutItem();
        nightscoutItems.add(nightscoutItem);
        TreatmentsEndpoints.Treatment treatment = nightscoutItem.ack(senderACK.contains(senderID)).treatment();

        treatment.setKey600(key);
        treatment.setCreated_at(eventDate);
        treatment.setEventType("Announcement");
        treatment.setAnnouncement(true);

        String message = parseAlert(pumpHistorySender, senderID, alert[3]) + (alert[4].equals("?") ? "" : ". " + parseAlert(pumpHistorySender, senderID, alert[4]));

        if (alert[2].equals("1"))
            notes += "Pump Alert: " + message;
        else if (alert[2].equals("2"))
            notes += "Sensor Alert: " + message;
        else if (alert[2].equals("3"))
            notes += "Reminder: " + message;
        else
            notes += "Alert: " + message;

        if (silenced) {
            notes += " (silenced)";
        }
        if (cleared && pumpHistorySender.senderOpt(senderID, PumpHistorySender.SENDEROPT.ALARM_CLEARED)) {
            int duration = (int) ((clearedDate.getTime() - alarmedDate.getTime()) / 1000L);
            notes += " (cleared " + FormatKit.getInstance().formatSecondsAsDHMS(duration) + ")";
        }

        if (alert[2].equals("0") || pumpHistorySender.senderOpt(senderID, PumpHistorySender.SENDEROPT.ALARM_FAULTCODE)) {
            notes += " #" + faultNumber;
            if (pumpHistorySender.senderOpt(senderID, PumpHistorySender.SENDEROPT.ALARM_FAULTDATA)) {
                StringBuilder sb = new StringBuilder();
                if (extraData && alarmData != null)
                    for (byte b : alarmData) sb.append(String.format("%02X", b));
                notes += String.format(" [M:%s H:%s D:%s%s]", notificationMode, alarmHistory, extraData, sb.length() == 0 ? "" : " " + sb.toString());
            }
        }

        treatment.setNotes(notes);

        return nightscoutItems;
    }

    private String parseAlert(PumpHistorySender pumpHistorySender, String senderID, String alert) {
        String result = "";

        if (!alert.equals("?")) {
            try {
                String parts[] = alert.split("[{}]");

                for (String part : parts) {
                    String data[] = part.split(";");

                    if (data.length < 2) {
                        result += part;
                    } else {
                        int offset = Integer.parseInt(data[1]);
                        if (extraData && alarmData != null) {
                            switch (data[0]) {
                                case "dval":
                                    result += alarmData[offset];
                                    break;
                                case "dpat":
                                    result += pumpHistorySender.senderList(senderID, PumpHistorySender.SENDEROPT.BASAL_PATTERN, alarmData[offset] - 1);
                                    break;
                                case "dsgv":
                                    int sgv = ToolKit.read16BEtoInt(alarmData, offset);
                                    if (sgv < 0x0300) result += FormatKit.getInstance().formatAsGlucose(sgv, pumpHistorySender.senderOpt(senderID, PumpHistorySender.SENDEROPT.GLUCOSE_UNITS));
                                    break;
                                case "dclk":
                                    result += FormatKit.getInstance().formatAsClock(alarmData[offset], alarmData[offset + 1]);
                                    break;
                                case "dhrs":
                                    result += FormatKit.getInstance().formatAsHours(alarmData[offset], alarmData[offset + 1]);
                                    break;
                                case "dins":
                                    result += FormatKit.getInstance().formatAsInsulin(ToolKit.read32BEtoInt(alarmData, offset) / 10000.0);
                                    break;
                                case "dlst":
                                    result += data[alarmData[offset] + 2];
                                    break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing alert: " + alert);
                result = "[parse error]";
            }
        }

        return result;
    }

    @Override
    public List<MessageItem> message(PumpHistorySender pumpHistorySender, String senderID) {
        List<MessageItem> messageItems = new ArrayList<>();

        // check if already sent as it may re-trigger due to 'cleared' updates
        if (senderACK.contains(senderID)) return messageItems;

        if ((cleared && !pumpHistorySender.senderOpt(senderID, PumpHistorySender.SENDEROPT.ALARM_CLEARED))
                || (silenced && !pumpHistorySender.senderOpt(senderID, PumpHistorySender.SENDEROPT.ALARM_SILENCED))) {
            return messageItems;
        }

        String[] alert = FormatKit.getInstance().getAlertString(faultNumber);

        int alertpriority = Integer.parseInt(alert[1]);
        if (alertpriority < Integer.parseInt(pumpHistorySender.senderVar(senderID, PumpHistorySender.SENDEROPT.ALARM_PRIORITY, "0")))
            return messageItems;

        MessageItem.PRIORITY priority = MessageItem.PRIORITY.convert(alertpriority);

        String title;
        String message = parseAlert(pumpHistorySender, senderID, alert[3]);
        String extended = "";
        MessageItem.TYPE type;

        if (alert[2].equals("3")) {
            if (cleared) title = "Cleared Reminder";
            else title = "Reminder";
            type = MessageItem.TYPE.REMINDER;

        } else {

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

            if (alert[2].equals("1")) {
                if (cleared) title = "Cleared Alert";
                else if (silenced) title = "Silenced Alert";
                else title = "Pump Alert";
            } else if (alert[2].equals("2")) {
                if (cleared) title = "Cleared Alert";
                else if (silenced) title = "Silenced Alert";
                else title = "Sensor Alert";
            } else {
                title = "Alert";
            }
        }

        if (pumpHistorySender.senderOpt(senderID, PumpHistorySender.SENDEROPT.ALARM_EXTENDED))
            extended = parseAlert(pumpHistorySender, senderID, alert[4]);

        if (cleared) {
            int duration = (int) ((clearedDate.getTime() - alarmedDate.getTime()) / 1000L);
            extended += " (cleared " + FormatKit.getInstance().formatSecondsAsDHMS(duration) + ")";
        }

        if (silenced) {
            extended += " (silenced)";
        }

        messageItems.add(new MessageItem()
                .key(key)
                .type(type)
                .date(alarmedDate)
                .priority(priority)
                .clock(FormatKit.getInstance().formatAsClock(alarmedDate))
                .title(title)
                .message(message)
                .extended(extended)
                .cleared(cleared)
                .silenced(silenced));

        return messageItems;
    }

    public static void alarm(PumpHistorySender pumpHistorySender, Realm realm, Date eventDate, int eventRTC, int eventOFFSET,
                             int faultNumber,
                             byte notificationMode,
                             boolean alarmHistory,
                             boolean extraData,
                             byte[] alarmData) {

        PumpHistoryAlarm record;

        // check if pump re-raised/escalated an alarm that is already recorded in the history
        if (!alarmHistory && notificationMode == 3);

            // check if a silenced alarm notification
        else if (faultNumber == 110) {

            record = realm.where(PumpHistoryAlarm.class)
                    .equalTo("alarmedRTC", eventRTC)
                    .equalTo("alarmed", true)
                    .equalTo("silenced", false)
                    .findFirst();

            if (record != null) {
                Log.d(TAG, "*update*" + " alarm (silenced)");
                record.silenced = true;
                pumpHistorySender.senderREQ(record);
            }

        } else {

            record = realm.where(PumpHistoryAlarm.class)
                    .equalTo("alarmedRTC", eventRTC)
                    .equalTo("alarmed", true)
                    .equalTo("faultNumber", faultNumber)
                    .findFirst();

            if (record == null) {
                // look for a cleared alarm
                RealmResults<PumpHistoryAlarm> results = realm.where(PumpHistoryAlarm.class)
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
                record.key = String.format("ALARM%04X%08X", faultNumber, eventRTC);
                pumpHistorySender.senderREQ(record);
            }
        }
    }

    public static void cleared(PumpHistorySender pumpHistorySender, Realm realm, Date eventDate, int eventRTC, int eventOFFSET,
                               int faultNumber) {

        if (faultNumber == 110) return; // ignore cleared silenced alerts

        PumpHistoryAlarm record = realm.where(PumpHistoryAlarm.class)
                .equalTo("cleared", true)
                .equalTo("clearedRTC", eventRTC)
                .equalTo("faultNumber", faultNumber)
                .findFirst();

        if (record == null) {
            // look for a alarm
            RealmResults<PumpHistoryAlarm> results = realm.where(PumpHistoryAlarm.class)
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
                record.eventDate = eventDate;
                record.faultNumber = faultNumber;
            } else {
                Log.d(TAG, "*update*" + " alarm (cleared)");
                record = results.first();
                pumpHistorySender.senderREQ(record);
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