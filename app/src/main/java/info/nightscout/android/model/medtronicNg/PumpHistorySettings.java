package info.nightscout.android.model.medtronicNg;

import android.util.Log;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.Sort;
import io.realm.annotations.Ignore;
import io.realm.annotations.Index;

/**
 * Created by John on 8.11.17.
 */

public class PumpHistorySettings extends RealmObject implements PumpHistory {
    @Ignore
    private static final String TAG = PumpHistorySettings.class.getSimpleName();

    @Index
    private Date eventDate;
    @Index
    private Date eventEndDate; // event deleted when this is stale

    private boolean uploadREQ = false;
    private boolean uploadACK = false;
    private boolean xdripREQ = false;
    private boolean xdripACK = false;

    private String key; // unique identifier for nightscout, key = "ID" + RTC as 8 char hex ie. "CGM6A23C5AA"

    private int eventType; // change type: basals/carbs/sensitivity/targets
    private int eventRTC;
    private int eventOFFSET;

    private byte[] basalPaterns;
    private byte[] carbRatios;
    private byte[] sensitivity;
    private byte[] targets;

    @Override
    public List Nightscout() { return new ArrayList(); }

    public static void stale(Realm realm, Date date) {
        final RealmResults results = realm.where(PumpHistorySettings.class)
                .lessThan("eventDate", date)
                .findAll();
        if (results.size() > 0) {
            Log.d(TAG, "deleting " + results.size() + " records from realm");
            realm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    results.deleteAllFromRealm();
                }
            });
        }
    }

    public static void records(Realm realm) {
        DateFormat dateFormatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US);
        final RealmResults<PumpHistorySettings> results = realm.where(PumpHistorySettings.class)
                .findAllSorted("eventDate", Sort.ASCENDING);
        Log.d(TAG, "records: " + results.size() + (results.size() > 0 ? " start: " + dateFormatter.format(results.first().getEventDate()) + " end: " + dateFormatter.format(results.last().getEventDate()) : ""));
    }

    public static void change(Realm realm, Date eventDate, int eventRTC, int eventOFFSET,
                            int eventType) {

        PumpHistorySettings object = realm.where(PumpHistorySettings.class)
                .equalTo("eventType", eventType)
                .equalTo("eventRTC", eventRTC)
                .findFirst();
        if (object == null) {
            Log.d(TAG, "*new*" + " settings change: " + eventType);
            object = realm.createObject(PumpHistorySettings.class);
            object.setEventDate(eventDate);
            object.setEventEndDate(eventDate);
            object.setEventRTC(eventRTC);
            object.setEventOFFSET(eventOFFSET);
            object.setEventType(eventType);
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
    public Date getEventEndDate() {
        return eventEndDate;
    }

    @Override
    public void setEventEndDate(Date eventEndDate) {
        this.eventEndDate = eventEndDate;
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

    public int getEventType() {
        return eventType;
    }

    public void setEventType(int eventType) {
        this.eventType = eventType;
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