package info.nightscout.android.model.medtronicNg;

import android.util.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.android.model.store.DataStore;
import info.nightscout.api.TreatmentsEndpoints;
import info.nightscout.api.UploadItem;
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
    private Date eventDate;

    @Index
    private boolean uploadREQ = false;
    private boolean uploadACK = false;

    private boolean xdripREQ = false;
    private boolean xdripACK = false;

    private String key; // unique identifier for nightscout, key = "ID" + RTC as 8 char hex ie. "CGM6A23C5AA"

    @Index
    private int eventRTC;
    private int eventOFFSET;
    private int oldPatternNumber;
    private int newPatternNumber;

    @Override
    public List nightscout(DataStore dataStore) {
        List<UploadItem> uploadItems = new ArrayList<>();

        if (dataStore.isNsEnableTreatments() && dataStore.isNsEnablePatternChange()) {

            UploadItem uploadItem = new UploadItem();
            uploadItems.add(uploadItem);
            TreatmentsEndpoints.Treatment treatment = uploadItem.ack(uploadACK).treatment();

            treatment.setKey600(key);
            treatment.setCreated_at(eventDate);
            treatment.setEventType("Profile Switch");

            String oldName = dataStore.getNameBasalPattern(oldPatternNumber);
            String newName = dataStore.getNameBasalPattern(newPatternNumber);

            treatment.setProfile(newName);
            treatment.setNotes("Changed profile from " + oldName + " to " + newName);
        }

        return uploadItems;
    }

    public static void change(Realm realm, Date eventDate, int eventRTC, int eventOFFSET,
                              int oldPatternNumber,
                              int newPatternNumber) {

        PumpHistoryPattern object = realm.where(PumpHistoryPattern.class)
                .equalTo("eventRTC", eventRTC)
                .findFirst();
        if (object == null) {
            Log.d(TAG, "*new*" + " basal pattern switch");
            // create new entry
            object = realm.createObject(PumpHistoryPattern.class);
            object.setKey("PRO" + String.format("%08X", eventRTC));
            object.setEventDate(eventDate);
            object.setEventRTC(eventRTC);
            object.setEventOFFSET(eventOFFSET);
            object.setOldPatternNumber(oldPatternNumber);
            object.setNewPatternNumber(newPatternNumber);
            object.setUploadREQ(true);
        }
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
    public boolean isUploadREQ() {
        return uploadREQ;
    }

    @Override
    public void setUploadREQ(boolean uploadREQ) {
        this.uploadREQ = uploadREQ;
    }

    @Override
    public boolean isUploadACK() {
        return uploadACK;
    }

    @Override
    public void setUploadACK(boolean uploadACK) {
        this.uploadACK = uploadACK;
    }

    @Override
    public boolean isXdripREQ() {
        return xdripREQ;
    }

    @Override
    public void setXdripREQ(boolean xdripREQ) {
        this.xdripREQ = xdripREQ;
    }

    @Override
    public boolean isXdripACK() {
        return xdripACK;
    }

    @Override
    public void setXdripACK(boolean xdripACK) {
        this.xdripACK = xdripACK;
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

    public void setEventRTC(int eventRTC) {
        this.eventRTC = eventRTC;
    }

    public int getEventOFFSET() {
        return eventOFFSET;
    }

    public void setEventOFFSET(int eventOFFSET) {
        this.eventOFFSET = eventOFFSET;
    }

    public int getOldPatternNumber() {
        return oldPatternNumber;
    }

    public void setOldPatternNumber(int oldPatternNumber) {
        this.oldPatternNumber = oldPatternNumber;
    }

    public int getNewPatternNumber() {
        return newPatternNumber;
    }

    public void setNewPatternNumber(int newPatternNumber) {
        this.newPatternNumber = newPatternNumber;
    }
}
