package info.nightscout.android.model.medtronicNg;

import android.util.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.android.history.MessageItem;
import info.nightscout.android.history.NightscoutItem;
import info.nightscout.android.history.PumpHistorySender;
import info.nightscout.android.utils.FormatKit;
import info.nightscout.api.TreatmentsEndpoints;
import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.Sort;
import io.realm.annotations.Ignore;
import io.realm.annotations.Index;

public class PumpHistorySystem extends RealmObject implements PumpHistoryInterface {
    @Ignore
    private static final String TAG = PumpHistorySystem.class.getSimpleName();

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
    private int status;

    private Date updateDate;
    private int updateCount;

    private int timespan;
    private int timespanPre;
    private int eventspan;

    private RealmList<String> data;

    public enum STATUS {
        DEBUG(1),
        MESSAGE(2),
        NIGHTSCOUT(3),

        CNL_PLUGGED(10),
        CNL_UNPLUGGED(11),
        CNL_USB_ERROR(12),

        COMMS_PUMP_LOST(20),
        COMMS_PUMP_CONNECTED(21),
        COMMS_PUMP_LOW_BATTERY_MODE(22),

        UPLOADER_BATTERY(30),
        UPLOADER_BATTERY_LOW(31),
        UPLOADER_BATTERY_VERYLOW(32),
        UPLOADER_BATTERY_FULL(33),

        PUMP_DEVICE_ERROR(40),
        PUMP_NO_SGV(41),
        PUMP_LOST_SENSOR(42),

        NA(-1);

        private int value;

        STATUS(int value) {
            this.value = value;
        }

        public int value() {
            return this.value;
        }

        public boolean equals(int value) {
            return this.value == value;
        }

        public static STATUS convert(int value) {
            for (STATUS status : STATUS.values())
                if (status.value == value) return status;
            return STATUS.NA;
        }
    }

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

        String message = makeMessage(pumpHistorySender, senderID);
        if (message.equals("")) return nightscoutItems;

        NightscoutItem nightscoutItem = new NightscoutItem();
        nightscoutItems.add(nightscoutItem);
        TreatmentsEndpoints.Treatment treatment = nightscoutItem.ack(senderACK.contains(senderID)).treatment();

        treatment.setKey600(key);
        treatment.setCreated_at(eventDate);

        String notes;

        switch (STATUS.convert(status)) {
            case DEBUG:
                treatment.setEventType("Note");
                notes = "Debug: " + message;
                break;
            default:
                treatment.setEventType("Announcement");
                treatment.setAnnouncement(true);
                notes = "System Status: " + message;
        }

        treatment.setNotes(notes);

        return nightscoutItems;
    }

    @Override
    public List<MessageItem> message(PumpHistorySender pumpHistorySender, String senderID) {
        List<MessageItem> messageItems = new ArrayList<>();

        String message = makeMessage(pumpHistorySender, senderID);
        if (message.equals("")) return messageItems;

        String title = "System Status";

        MessageItem.PRIORITY priority = MessageItem.PRIORITY.NORMAL;
        MessageItem.TYPE type = MessageItem.TYPE.INFO;

        switch (STATUS.convert(status)) {
            case DEBUG:
                priority = MessageItem.PRIORITY.LOWEST;
                title = "Debug";
                break;
            case CNL_USB_ERROR:
            case PUMP_DEVICE_ERROR:
                type = MessageItem.TYPE.ALERT_UPLOADER_ERROR;
                priority = MessageItem.PRIORITY.EMERGENCY;
                break;
            case COMMS_PUMP_CONNECTED:
            case COMMS_PUMP_LOST:
            case COMMS_PUMP_LOW_BATTERY_MODE:
                type = MessageItem.TYPE.ALERT_UPLOADER_CONNECTION;
                priority = MessageItem.PRIORITY.HIGH;
                break;
            case UPLOADER_BATTERY:
            case UPLOADER_BATTERY_FULL:
            case UPLOADER_BATTERY_LOW:
            case UPLOADER_BATTERY_VERYLOW:
                type = MessageItem.TYPE.ALERT_UPLOADER_BATTERY;
                priority = MessageItem.PRIORITY.HIGH;
                break;
        }

        messageItems.add(new MessageItem()
                .date(updateDate)
                .type(type)
                .priority(priority)
                .clock(FormatKit.getInstance().formatAsClock(updateDate))
                .title(title)
                .message(message));

        return messageItems;
    }

    private String makeMessage(PumpHistorySender pumpHistorySender, String senderID) {

        String message = "";

        switch (STATUS.convert(status)) {
            case DEBUG:
                if (data != null && data.size() > 0) {
                    message = data.first();
                }
                break;

            case COMMS_PUMP_CONNECTED:
                if (pumpHistorySender.senderOpt(senderID, PumpHistorySender.SENDEROPT.SYSTEM_PUMP_CONNECTTED)) {
                    int at = Integer.parseInt(pumpHistorySender.senderVar(senderID, PumpHistorySender.SENDEROPT.SYSTEM_PUMP_CONNECTTED_AT, "900"));
                    Log.d(TAG, STATUS.convert(status).name() + " sender=" + senderID + " eventspan=" + eventspan + " timespan=" + timespan + " pre=" + timespanPre + " at=" + at);
                    if (eventspan >= at) {
                        message = String.format("%s. %s %s %s",
                                "Pump now connected",
                                "Last seen",
                                FormatKit.getInstance().formatMinutesAsDHM(eventspan / 60),
                                "ago");
                    }
                }
                break;

            case COMMS_PUMP_LOST:
                if (pumpHistorySender.senderOpt(senderID, PumpHistorySender.SENDEROPT.SYSTEM_PUMP_LOST)) {
                    int at = Integer.parseInt(pumpHistorySender.senderVar(senderID, PumpHistorySender.SENDEROPT.SYSTEM_PUMP_LOST_AT, "900"));
                    int repeat = Integer.parseInt(pumpHistorySender.senderVar(senderID, PumpHistorySender.SENDEROPT.SYSTEM_PUMP_LOST_REPEAT, "900"));
                    Log.d(TAG, STATUS.convert(status).name() + " sender=" + senderID + " eventspan=" + eventspan + " timespan=" + timespan + " pre=" + timespanPre + " at=" + at + " repeat=" + repeat);
                    if ((timespan >= at && timespanPre / (at > 0 ? at : 1) == 0)
                            || (repeat != 0 && timespanPre - at > 0 && (timespan - at) / repeat > (timespanPre - at) / repeat)) {
                        message = String.format("%s. %s %s %s",
                                "Could not communicate with the pump",
                                "Last seen",
                                FormatKit.getInstance().formatMinutesAsDHM(timespan / 60),
                                "ago");
                    }
                }
                break;

            case CNL_USB_ERROR:
                if (pumpHistorySender.senderOpt(senderID, PumpHistorySender.SENDEROPT.SYSTEM_CNL_USB_ERROR)) {
                    int at = Integer.parseInt(pumpHistorySender.senderVar(senderID, PumpHistorySender.SENDEROPT.SYSTEM_CNL_USB_ERROR_AT, "0"));
                    int repeat = Integer.parseInt(pumpHistorySender.senderVar(senderID, PumpHistorySender.SENDEROPT.SYSTEM_CNL_USB_ERROR_REPEAT, "900"));
                    Log.d(TAG, STATUS.convert(status).name() + " sender=" + senderID + " eventspan=" + eventspan + " timespan=" + timespan + " pre=" + timespanPre + " at=" + at + " repeat=" + repeat);
                    if ((timespan >= at && timespanPre / (at > 0 ? at : 1) == 0)
                            || (repeat != 0 && timespanPre - at > 0 && (timespan - at) / repeat > (timespanPre - at) / repeat)) {
                        message = String.format("%s %s.",
                                "Contour Next Link",
                                "USB Error");
                    }
                }
                break;

            case PUMP_DEVICE_ERROR:
                if (pumpHistorySender.senderOpt(senderID, PumpHistorySender.SENDEROPT.SYSTEM_PUMP_DEVICE_ERROR)) {
                    int at = Integer.parseInt(pumpHistorySender.senderVar(senderID, PumpHistorySender.SENDEROPT.SYSTEM_PUMP_DEVICE_ERROR_AT, "0"));
                    int repeat = Integer.parseInt(pumpHistorySender.senderVar(senderID, PumpHistorySender.SENDEROPT.SYSTEM_PUMP_DEVICE_ERROR_REPEAT, "600"));
                    if ((timespan >= at && timespanPre / (at > 0 ? at : 1) == 0)
                            || (repeat != 0 && timespanPre - at > 0 && (timespan - at) / repeat > (timespanPre - at) / repeat)) {
                        message = String.format("%s. %s.",
                                "Pump has a device error",
                                "No data can be read until this error has been cleared on the Pump");
                    }
                }
                break;

            case UPLOADER_BATTERY_LOW:
                if (pumpHistorySender.senderOpt(senderID, PumpHistorySender.SENDEROPT.SYSTEM_UPLOADER_BATTERY_LOW)
                        && data != null && data.size() > 0) {
                    message = String.format("%s %s. %s",
                            "Uploader battery at",
                            FormatKit.getInstance().formatAsPercent(Integer.parseInt(data.first())),
                            "Charge your uploader device soon");
                }
                break;

            case UPLOADER_BATTERY_VERYLOW:
                if (pumpHistorySender.senderOpt(senderID, PumpHistorySender.SENDEROPT.SYSTEM_UPLOADER_BATTERY_VERY_LOW)
                        && data != null && data.size() > 0) {
                    message = String.format("%s %s. %s",
                            "Uploader battery at",
                            FormatKit.getInstance().formatAsPercent(Integer.parseInt(data.first())),
                            "Charge your uploader device soon");
                }
                break;

            case UPLOADER_BATTERY_FULL:
                if (pumpHistorySender.senderOpt(senderID, PumpHistorySender.SENDEROPT.SYSTEM_UPLOADER_BATTERY_CHARGED)) {
                    message =  "Uploader battery fully charged";
                }
                break;

            case UPLOADER_BATTERY:
                if (pumpHistorySender.senderOpt(senderID, PumpHistorySender.SENDEROPT.SYSTEM_UPLOADER_BATTERY)
                        && data != null && data.size() > 0) {
                    message = String.format("%s %s.",
                            "Uploader battery at",
                            FormatKit.getInstance().formatAsPercent(Integer.parseInt(data.first())));
                }
                break;

            default:
        }

        return message;
    }

    public static void event(PumpHistorySender pumpHistorySender, Realm realm,
                             Date eventDate,
                             String key,
                             STATUS status,
                             RealmList<String> data
    ) {

        //if (true) return;
        key = "SYS" + key;

        PumpHistorySystem record = realm.where(PumpHistorySystem.class)
                .equalTo("key", key)
                .equalTo("status", status.value())
                .findFirst();
        if (record == null) {
            Log.d(TAG, "*new* system status=" + status.name() + " key=" + key);
            record = realm.createObject(PumpHistorySystem.class);
            record.key = key;
            record.updateCount = 0;
            record.status = status.value();

        } else {
            Log.d(TAG, "*update* system status=" + status.name() + " key=" + key);
        }

        Date updateDate = new Date(System.currentTimeMillis());

        record.eventDate = eventDate;
        record.updateDate = updateDate;
        record.updateCount++;

        if (record.data == null || data == null || record.data.size() == 0) {
            record.data = data;
        } else {
            for (String s : record.data) {
                if (data.size() >= 20) break;
                data.add(s);
            }
        }

        record.data = data;

        int timespan = (int) ((updateDate.getTime() - eventDate.getTime()) / 1000L);

        int eventspan = 0;
        RealmResults<PumpHistorySystem> results = realm.where(PumpHistorySystem.class)
                .equalTo("status", status.value())
                .sort("eventDate", Sort.DESCENDING)
                .findAll();
        if (results.size() > 1) {
            eventspan = (int) ((eventDate.getTime() - results.get(1).getEventDate().getTime()) / 1000L);
        }

        record.timespanPre = record.timespan;
        record.timespan = timespan;
        record.eventspan = eventspan;

        pumpHistorySender.setSenderREQ(record);
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
}