package info.nightscout.android.model.medtronicNg;

import android.util.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.android.R;
import info.nightscout.android.history.HistoryUtils;
import info.nightscout.android.history.MessageItem;
import info.nightscout.android.history.PumpHistorySender;
import info.nightscout.android.utils.FormatKit;
import info.nightscout.android.upload.nightscout.TreatmentsEndpoints;
import info.nightscout.android.history.NightscoutItem;
import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.annotations.Ignore;
import io.realm.annotations.Index;

/**
 * Created by Pogman on 24.1.18.
 */

public class PumpHistoryPattern extends RealmObject implements PumpHistoryInterface {
    @Ignore
    private static final String TAG = PumpHistoryPattern.class.getSimpleName();

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
    private byte oldPatternNumber;
    private byte newPatternNumber;

    @Override
    public List<NightscoutItem> nightscout(PumpHistorySender pumpHistorySender, String senderID) {
        List<NightscoutItem> nightscoutItems = new ArrayList<>();

        if (!pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.BASAL_PATTERN_CHANGE)) {
            HistoryUtils.nightscoutDeleteTreatment(nightscoutItems, this, senderID);
            return nightscoutItems;
        }

        TreatmentsEndpoints.Treatment treatment = HistoryUtils.nightscoutTreatment(nightscoutItems, this, senderID);
        treatment.setEventType("Profile Switch");

        String oldName = pumpHistorySender.getList(senderID, PumpHistorySender.SENDEROPT.BASAL_PATTERN, oldPatternNumber - 1);
        String newName = pumpHistorySender.getList(senderID, PumpHistorySender.SENDEROPT.BASAL_PATTERN, newPatternNumber - 1);

        treatment.setProfile(newName);
        treatment.setNotes(String.format("%s: %s. %s: %s.",
                FormatKit.getInstance().getString(R.string.text__basal_pattern),
                newName,
                FormatKit.getInstance().getString(R.string.text__previous_basal_pattern),
                oldName));

        return nightscoutItems;
    }

    @Override
    public List<MessageItem> message(PumpHistorySender pumpHistorySender, String senderID) {
        List<MessageItem> messageItems = new ArrayList<>();

        String title = FormatKit.getInstance().getString(R.string.text__Basal);

        String message = String.format("%s: %s. %s: %s.",
                FormatKit.getInstance().getString(R.string.text__basal_pattern),
                pumpHistorySender.getList(senderID, PumpHistorySender.SENDEROPT.BASAL_PATTERN, newPatternNumber - 1),
                FormatKit.getInstance().getString(R.string.text__previous_basal_pattern),
                pumpHistorySender.getList(senderID, PumpHistorySender.SENDEROPT.BASAL_PATTERN, oldPatternNumber - 1));

        messageItems.add(new MessageItem()
                .type(MessageItem.TYPE.BASAL)
                .date(eventDate)
                .clock(FormatKit.getInstance().formatAsClock(eventDate.getTime()))
                .title(title)
                .message(message));

        return messageItems;
    }

    public static void pattern(
            PumpHistorySender pumpHistorySender, Realm realm, long pumpMAC,
            Date eventDate, int eventRTC, int eventOFFSET,
            byte oldPatternNumber,
            byte newPatternNumber) {

        PumpHistoryPattern record = realm.where(PumpHistoryPattern.class)
                .equalTo("pumpMAC", pumpMAC)
                .equalTo("eventRTC", eventRTC)
                .findFirst();
        if (record == null) {
            Log.d(TAG, "*new*" + " basal pattern switch");
            // create new entry
            record = realm.createObject(PumpHistoryPattern.class);
            record.pumpMAC = pumpMAC;
            record.key = HistoryUtils.key("PRO", eventRTC);
            record.eventDate = eventDate;
            record.eventRTC = eventRTC;
            record.eventOFFSET = eventOFFSET;
            record.oldPatternNumber = oldPatternNumber;
            record.newPatternNumber = newPatternNumber;
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

    public byte getOldPatternNumber() {
        return oldPatternNumber;
    }

    public byte getNewPatternNumber() {
        return newPatternNumber;
    }
}
