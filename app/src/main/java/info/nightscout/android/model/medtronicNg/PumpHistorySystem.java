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
import info.nightscout.android.medtronic.message.MessageUtils;
import info.nightscout.android.utils.FormatKit;
import info.nightscout.android.upload.nightscout.TreatmentsEndpoints;
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
    @Index
    private long pumpMAC;

    private String key; // unique identifier for nightscout, key = "ID" + RTC as 8 char hex ie. "CGM6A23C5AA"

    @Index
    private int status;

    private Date createDate;
    private Date startDate;
    private Date updateDate;
    private int updateCount;

    private int timespan;
    private int timespanPre;
    private int eventspan;
    private int eventspanPre;

    private RealmList<String> data;

    public enum STATUS {
        DEBUG(1),
        DEBUG_NIGHTSCOUT(2),
        DEBUG_MESSAGE(3),

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

        ESTIMATE_ACTIVE(500),

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

        if (HistoryUtils.nightscoutTTL(nightscoutItems,this, senderID))
            return nightscoutItems;

        String message = makeMessage(pumpHistorySender, senderID);
        if (message.equals("")) {
            if (senderACK.contains(senderID))
                HistoryUtils.nightscoutDeleteTreatment(nightscoutItems, this, senderID);
            return nightscoutItems;
        }

        TreatmentsEndpoints.Treatment treatment = HistoryUtils.nightscoutTreatment(nightscoutItems, this, senderID);
        treatment.setCreated_at(startDate);

        String notes;

        switch (STATUS.convert(status)) {
            case DEBUG:
            case DEBUG_NIGHTSCOUT:
                treatment.setEventType("Note");
                notes = "Debug: " + message;
                break;
            default:
                treatment.setEventType("Announcement");
                treatment.setAnnouncement(true);
                notes = String.format("%s: %s",
                        FormatKit.getInstance().getString(R.string.system_status__header),
                        message);
        }

        treatment.setNotes(notes);

        return nightscoutItems;
    }

    @Override
    public List<MessageItem> message(PumpHistorySender pumpHistorySender, String senderID) {
        List<MessageItem> messageItems = new ArrayList<>();

        String message = makeMessage(pumpHistorySender, senderID);
        if (message.equals("")) return messageItems;

        String title = FormatKit.getInstance().getString(R.string.system_status__header);

        MessageItem.PRIORITY priority = MessageItem.PRIORITY.NORMAL;
        MessageItem.TYPE type = MessageItem.TYPE.INFO;

        switch (STATUS.convert(status)) {
            case DEBUG:
            case DEBUG_MESSAGE:
                priority = MessageItem.PRIORITY.LOWEST;
                title = "Debug";
                break;
            case DEBUG_NIGHTSCOUT:
                return messageItems;
            case CNL_USB_ERROR:
            case PUMP_DEVICE_ERROR:
                type = MessageItem.TYPE.ALERT_UPLOADER_ERROR;
                priority = MessageItem.PRIORITY.EMERGENCY;
                break;
            case COMMS_PUMP_CONNECTED:
            case COMMS_PUMP_LOST:
            case COMMS_PUMP_LOW_BATTERY_MODE:
            case ESTIMATE_ACTIVE:
                type = MessageItem.TYPE.ALERT_UPLOADER_STATUS;
                priority = MessageItem.PRIORITY.NORMAL;
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
                .clock(FormatKit.getInstance().formatAsClock(updateDate.getTime()))
                .title(title)
                .message(message));

        return messageItems;
    }

    private String makeMessage(PumpHistorySender pumpHistorySender, String senderID) {

        String message = "";

        switch (STATUS.convert(status)) {
            case DEBUG:
            case DEBUG_NIGHTSCOUT:
            case DEBUG_MESSAGE:
                if (data != null && data.size() > 0) {
                    message = data.first();
                }
                break;

            case COMMS_PUMP_CONNECTED:
                if (pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.SYSTEM_PUMP_CONNECTTED) && eventspanAtRepeat(senderID,
                        Integer.parseInt(pumpHistorySender.getVar(senderID, PumpHistorySender.SENDEROPT.SYSTEM_PUMP_CONNECTTED_AT, "1800")),
                        0
                )) message = String.format("%s. %s %s %s%s",
                        FormatKit.getInstance().getString(R.string.system_status__pump_now_connected),
                        FormatKit.getInstance().getString(R.string.system_status__last_seen),
                        FormatKit.getInstance().formatMinutesAsDHM(eventspan / 60),
                        FormatKit.getInstance().getString(R.string.system_status__time_ago),
                        deviceName(pumpHistorySender, senderID));
                break;

            case COMMS_PUMP_LOST:
                if (pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.SYSTEM_PUMP_LOST) && timespanAtRepeat(senderID,
                        Integer.parseInt(pumpHistorySender.getVar(senderID, PumpHistorySender.SENDEROPT.SYSTEM_PUMP_LOST_AT, "1800")),
                        Integer.parseInt(pumpHistorySender.getVar(senderID, PumpHistorySender.SENDEROPT.SYSTEM_PUMP_LOST_REPEAT, "1800"))
                )) message = String.format("%s. %s %s %s%s",
                        FormatKit.getInstance().getString(R.string.system_status__could_not_communicate_with_the_pump),
                        FormatKit.getInstance().getString(R.string.system_status__last_seen),
                        FormatKit.getInstance().formatMinutesAsDHM(timespan / 60),
                        FormatKit.getInstance().getString(R.string.system_status__time_ago),
                        deviceName(pumpHistorySender, senderID));
                break;

            case CNL_USB_ERROR:
                if (pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.SYSTEM_CNL_USB_ERROR) && timespanAtRepeat(senderID,
                        Integer.parseInt(pumpHistorySender.getVar(senderID, PumpHistorySender.SENDEROPT.SYSTEM_CNL_USB_ERROR_AT, "0")),
                        Integer.parseInt(pumpHistorySender.getVar(senderID, PumpHistorySender.SENDEROPT.SYSTEM_CNL_USB_ERROR_REPEAT, "900"))
                )) message = String.format("%s%s",
                        FormatKit.getInstance().getString(R.string.system_status__usb_error),
                        deviceName(pumpHistorySender, senderID));
                break;

            case PUMP_DEVICE_ERROR:
                if (pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.SYSTEM_PUMP_DEVICE_ERROR) && timespanAtRepeat(senderID,
                        Integer.parseInt(pumpHistorySender.getVar(senderID, PumpHistorySender.SENDEROPT.SYSTEM_PUMP_DEVICE_ERROR_AT, "0")),
                        Integer.parseInt(pumpHistorySender.getVar(senderID, PumpHistorySender.SENDEROPT.SYSTEM_PUMP_DEVICE_ERROR_REPEAT, "600"))
                )) message = FormatKit.getInstance().getString(R.string.system_status__pump_device_error);
                break;

            case UPLOADER_BATTERY_LOW:
                if (pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.SYSTEM_UPLOADER_BATTERY_LOW) && hasData()) {
                    message = String.format("%s %s. %s%s",
                            FormatKit.getInstance().getString(R.string.system_status__uploader_battery_level),
                            FormatKit.getInstance().formatAsPercent(Integer.parseInt(data.first())),
                            FormatKit.getInstance().getString(R.string.system_status__uploader_battery_charge_soon),
                            deviceName(pumpHistorySender, senderID));
                }
                break;

            case UPLOADER_BATTERY_VERYLOW:
                if (pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.SYSTEM_UPLOADER_BATTERY_VERY_LOW) && hasData()) {
                    message = String.format("%s %s. %s%s",
                            FormatKit.getInstance().getString(R.string.system_status__uploader_battery_level),
                            FormatKit.getInstance().formatAsPercent(Integer.parseInt(data.first())),
                            FormatKit.getInstance().getString(R.string.system_status__uploader_battery_charge_soon),
                            deviceName(pumpHistorySender, senderID));
                }
                break;

            case UPLOADER_BATTERY_FULL:
                if (pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.SYSTEM_UPLOADER_BATTERY_CHARGED)) {
                    message =  String.format("%s%s",
                            FormatKit.getInstance().getString(R.string.system_status__uploader_battery_fully_charged),
                            deviceName(pumpHistorySender, senderID));
                }
                break;

            case UPLOADER_BATTERY:
                if (pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.SYSTEM_UPLOADER_BATTERY) && hasData()) {
                    message = String.format("%s %s%s",
                            FormatKit.getInstance().getString(R.string.system_status__uploader_battery_level),
                            FormatKit.getInstance().formatAsPercent(Integer.parseInt(data.first())),
                            deviceName(pumpHistorySender, senderID));
                }
                break;

            case ESTIMATE_ACTIVE:
                if (pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.SYSTEM_ESTIMATE) && timespanAtRepeat(senderID,
                        Integer.parseInt(pumpHistorySender.getVar(senderID, PumpHistorySender.SENDEROPT.SYSTEM_ESTIMATE_AT, "0")),
                        Integer.parseInt(pumpHistorySender.getVar(senderID, PumpHistorySender.SENDEROPT.SYSTEM_ESTIMATE_REPEAT, "3600"))
                )) message = String.format("%s (%s)",
                        FormatKit.getInstance().getString(R.string.system_status__estimate_active),
                        FormatKit.getInstance().formatMinutesAsDHM(timespan / 60));
                break;

            default:
        }

        return message;
    }

    private String deviceName(PumpHistorySender pumpHistorySender, String senderID) {
        String name = pumpHistorySender.getVar(senderID, PumpHistorySender.SENDEROPT.DEVICE_NAME, "");
        return name.length() == 0 ? "" : String.format(" (%s)", name);
    }

    private boolean timespanAtRepeat(String senderID, int at, int repeat) {
        Log.d(TAG, String.format("%s sender=%s timespan=%s pre=%s at=%s repeat=%s",
                STATUS.convert(status).name(),
                senderID,
                timespan,
                timespanPre,
                at,
                repeat
        ));
        return ((timespan >= at && (timespanPre < 0 || (at != 0 && timespanPre / at == 0)))
                || (repeat != 0 && timespanPre - at > 0 && (timespan - at) / repeat > (timespanPre - at) / repeat));
    }

    private boolean eventspanAtRepeat(String senderID, int at, int repeat) {
        Log.d(TAG, String.format("%s sender=%s eventspan=%s eventspanPre=%s at=%s repeat=%s",
                STATUS.convert(status).name(),
                senderID,
                eventspan,
                eventspanPre,
                at,
                repeat
        ));
        return ((eventspan >= at && (eventspanPre < 0 || (at != 0 && eventspanPre / at == 0)))
                || (repeat != 0 && eventspanPre - at > 0 && (eventspan - at) / repeat > (eventspanPre - at) / repeat));
    }

    private boolean hasData() {
        return data != null && data.size() > 0;
    }

    public static void event(PumpHistorySender pumpHistorySender, Realm realm,
                             Date eventDate,
                             Date startDate,
                             String key,
                             STATUS status,
                             RealmList<String> data
    ) {

        long now = System.currentTimeMillis();
        key = String.format("SYS%s:%s", status.value(), key);

        PumpHistorySystem record = realm.where(PumpHistorySystem.class)
                .equalTo("key", key)
                .equalTo("status", status.value())
                .findFirst();
        if (record == null) {
            Log.d(TAG, "*new* system status=" + status.name() + " key=" + key);
            record = realm.createObject(PumpHistorySystem.class);
            record.status = status.value();
            record.key = key;
            record.createDate = new Date(now);
            record.startDate = startDate;
            record.updateCount = 0;
            record.timespan = -1;
            record.eventspan = -1;
        } else {
            Log.d(TAG, "*update* system status=" + status.name() + " key=" + key);
        }

        record.eventDate = eventDate;
        record.updateDate = new Date(now);
        record.updateCount++;

        // data stack for incoming data, older data is shifted out
        if (record.data == null || data == null || record.data.size() == 0) {
            record.data = data;
        } else {
            for (String s : record.data) {
                if (data.size() >= 20) break;
                data.add(s);
            }
        }
        record.data = data;

        // current timespan
        int timespan = (int) ((record.eventDate.getTime() - record.startDate.getTime()) / 1000L);

        // find the timespan between events with the same status type
        int eventspan = 0;
        RealmResults<PumpHistorySystem> results = realm.where(PumpHistorySystem.class)
                .equalTo("status", status.value())
                .sort("eventDate", Sort.DESCENDING)
                .findAll();
        if (results.size() > 1) {
            eventspan = (int) ((record.eventDate.getTime() - results.get(1).getEventDate().getTime()) / 1000L);
        }

        // previous timespan between updates using the same key
        record.timespanPre = record.timespan;
        // current timespan between updates using the same key
        record.timespan = timespan;
        // previous timespan between this and last of the same status type
        record.eventspanPre = record.eventspan;
        // timespan between this and last of the same status type
        record.eventspan = eventspan;

        pumpHistorySender.setSenderREQ(record);
    }

    public static void debugParser(
            PumpHistorySender pumpHistorySender, Realm realm, long pumpMAC,
            Date eventDate, int eventRTC, int eventOFFSET,
            PumpHistoryParser.EventType eventType,
            byte[] eventData, int eventSize, int index) {

        index += 0x0B;
        eventSize -= 0x0B;
        String key = String.format("%08X", eventRTC);
        Date pumpdate = MessageUtils.decodeDateTime(eventRTC & 0xFFFFFFFFL, eventOFFSET);

        StringBuilder sb = new StringBuilder(String.format("[%s]<br>pumpDate: %s<br>size: %s key: %s<br>",
                eventType.name(),
                FormatKit.getInstance().formatAsYMDHMS(pumpdate.getTime()),
                eventSize,
                key
        ));

        for (int i = 0; i < eventSize; i++)
            sb.append(String.format("%02X", eventData[index++]));

        Log.d(TAG, "DEBUGPARSER: " + eventDate + sb.toString());

        event(pumpHistorySender, realm,
                eventDate,
                eventDate,
                key+":DEBUGPARSER",
                STATUS.DEBUG_NIGHTSCOUT,
                new RealmList<>(sb.toString()));
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
}