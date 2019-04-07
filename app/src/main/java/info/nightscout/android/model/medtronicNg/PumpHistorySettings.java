package info.nightscout.android.model.medtronicNg;

import android.util.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.android.history.MessageItem;
import info.nightscout.android.history.NightscoutItem;
import info.nightscout.android.history.PumpHistorySender;
import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.annotations.Ignore;
import io.realm.annotations.Index;

/**
 * Created by Pogman on 8.11.17.
 */

public class PumpHistorySettings extends RealmObject implements PumpHistoryInterface {
    @Ignore
    private static final String TAG = PumpHistorySettings.class.getSimpleName();

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

    private int settingsType; // change type: basals/carbs/sensitivity/targets

    @Index
    private int settingsRTC;
    private int settingsOFFSET;

    private byte[] basalPaterns;
    private byte[] carbRatios;
    private byte[] sensitivity;
    private byte[] targets;

    @Override
    public List<NightscoutItem> nightscout(PumpHistorySender pumpHistorySender, String senderID) { return new ArrayList<>(); }

    public static void change(
            PumpHistorySender pumpHistorySender, Realm realm, long pumpMAC,
            Date eventDate, int eventRTC, int eventOFFSET,
            int eventType) {

        PumpHistorySettings record = realm.where(PumpHistorySettings.class)
                .equalTo("pumpMAC", pumpMAC)
                .equalTo("settingsType", eventType)
                .equalTo("settingsRTC", eventRTC)
                .findFirst();
        if (record == null) {
            Log.d(TAG, "*new*" + " settings change: " + eventType);
            record = realm.createObject(PumpHistorySettings.class);
            record.pumpMAC = pumpMAC;
            record.eventDate = eventDate;
            record.settingsRTC = eventRTC;
            record.settingsOFFSET = eventOFFSET;
            record.settingsType = eventType;
        }
    }

    @Override
    public List<MessageItem> message(PumpHistorySender pumpHistorySender, String senderID) {return new ArrayList<>();}

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

    public int getSettingsType() {
        return settingsType;
    }

    public int getSettingsRTC() {
        return settingsRTC;
    }

    public int getSettingsOFFSET() {
        return settingsOFFSET;
    }

    public byte[] getBasalPaterns() {
        return basalPaterns;
    }

    public byte[] getCarbRatios() {
        return carbRatios;
    }

    public byte[] getSensitivity() {
        return sensitivity;
    }

    public byte[] getTargets() {
        return targets;
    }
}