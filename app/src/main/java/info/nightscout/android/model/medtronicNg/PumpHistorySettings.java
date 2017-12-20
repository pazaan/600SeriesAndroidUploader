package info.nightscout.android.model.medtronicNg;

import android.util.Log;

import java.util.Date;
import java.util.List;

import info.nightscout.android.model.store.DataStore;
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
    private Date eventDate;

    @Index
    private boolean uploadREQ = false;
    private boolean uploadACK = false;

    private boolean xdripREQ = false;
    private boolean xdripACK = false;

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
    public List nightscout(DataStore dataStore) { return null; }

    public static void change(Realm realm, Date eventDate, int eventRTC, int eventOFFSET,
                            int eventType) {

        PumpHistorySettings object = realm.where(PumpHistorySettings.class)
                .equalTo("settingsType", eventType)
                .equalTo("settingsRTC", eventRTC)
                .findFirst();
        if (object == null) {
            Log.d(TAG, "*new*" + " settings change: " + eventType);
            object = realm.createObject(PumpHistorySettings.class);
            object.setEventDate(eventDate);
            object.setSettingsRTC(eventRTC);
            object.setSettingsOFFSET(eventOFFSET);
            object.setSettingsType(eventType);
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

    public int getSettingsType() {
        return settingsType;
    }

    public void setSettingsType(int settingsType) {
        this.settingsType = settingsType;
    }

    public int getSettingsRTC() {
        return settingsRTC;
    }

    public void setSettingsRTC(int settingsRTC) {
        this.settingsRTC = settingsRTC;
    }

    public int getSettingsOFFSET() {
        return settingsOFFSET;
    }

    public void setSettingsOFFSET(int settingsOFFSET) {
        this.settingsOFFSET = settingsOFFSET;
    }

    public byte[] getBasalPaterns() {
        return basalPaterns;
    }

    public void setBasalPaterns(byte[] basalPaterns) {
        this.basalPaterns = basalPaterns;
    }

    public byte[] getCarbRatios() {
        return carbRatios;
    }

    public void setCarbRatios(byte[] carbRatios) {
        this.carbRatios = carbRatios;
    }

    public byte[] getSensitivity() {
        return sensitivity;
    }

    public void setSensitivity(byte[] sensitivity) {
        this.sensitivity = sensitivity;
    }

    public byte[] getTargets() {
        return targets;
    }

    public void setTargets(byte[] targets) {
        this.targets = targets;
    }
}