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
 * Created by Pogman on 17.1.18.
 */

public class PumpHistoryLoop extends RealmObject implements PumpHistoryInterface {
    @Ignore
    private static final String TAG = PumpHistoryLoop.class.getSimpleName();

    //private boolean debug_bump;

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

    private int bolusRef = -1;
    private double deliveredAmount;

    private boolean loopMode = false;
    private boolean loopActive = false;
    private byte loopStatus;
    private byte loopPattern;

    @Override
    public List nightscout(DataStore dataStore) {
        List<UploadItem> uploadItems = new ArrayList<>();

        if (dataStore.isNsEnableTreatments()) {

            UploadItem uploadItem = new UploadItem();
            uploadItems.add(uploadItem);
            TreatmentsEndpoints.Treatment treatment = uploadItem.ack(uploadACK).treatment();

            String notes = "";

            if (loopMode) {
                treatment.setKey600(key);
                treatment.setCreated_at(eventDate);
                treatment.setEventType("Profile Switch");

                if (loopActive) {
                    notes = "Auto Mode: active";

                    treatment.setProfile("Auto Mode");
                    treatment.setNotes(notes);

                } else if (loopPattern != 0) {
                    String patternName = dataStore.getNameBasalPattern(loopPattern);
                    notes = "Auto Mode: stopped";

                    treatment.setProfile(patternName);
                    treatment.setNotes(notes);
                }

            } else {

                // Synthesize Closed Loop Microboluses into Temp Basals

                notes = "microbolus " + deliveredAmount + "U";

                //notes += " [DEBUG: ref=" + bolusRef + " del=" + deliveredAmount + " " + String.format("%08X", eventRTC) + "]";

                treatment.setEventType("Temp Basal");
                treatment.setKey600(key);
                treatment.setDuration((float) 5);
                treatment.setNotes(notes);

                treatment.setCreated_at(eventDate);
                treatment.setAbsolute((float) (deliveredAmount * 12));
            }
        }

        return uploadItems;
    }

    public static void microbolus(Realm realm, Date eventDate, int eventRTC, int eventOFFSET,
                                  int bolusRef,
                                  double deliveredAmount) {

        PumpHistoryLoop object = realm.where(PumpHistoryLoop.class)
                .equalTo("eventRTC", eventRTC)
                .equalTo("loopMode", false)
                .findFirst();

        if (object == null) {
            Log.d(TAG, "*new*" + " Ref: " + bolusRef + " create new microbolus event");
            object = realm.createObject(PumpHistoryLoop.class);

            object.setEventDate(eventDate);
            object.setBolusRef(bolusRef);

            object.setEventRTC(eventRTC);
            object.setEventOFFSET(eventOFFSET);
            object.setDeliveredAmount(deliveredAmount);

            object.setLoopActive(true);

            object.setKey("MB" + String.format("%08X", eventRTC));
            object.setUploadREQ(true);
        }
    }

    public static void mode(Realm realm, Date eventDate, int eventRTC, int eventOFFSET,
                            boolean loopActive,
                            byte loopStatus) {

        PumpHistoryLoop object = realm.where(PumpHistoryLoop.class)
                .equalTo("eventRTC", eventRTC)
                .equalTo("loopMode", true)
                .findFirst();

        if (object == null) {
            Log.d(TAG, "*new*" + "loop mode event");
            object = realm.createObject(PumpHistoryLoop.class);

            object.setEventDate(eventDate);
            object.setLoopMode(true);

            object.setEventRTC(eventRTC);
            object.setEventOFFSET(eventOFFSET);
            object.setLoopActive(loopActive);
            object.setLoopStatus(loopStatus);

            object.setKey("LOOP" + String.format("%08X", eventRTC));
            if (loopActive) object.setUploadREQ(true);
        }
    }

    public static void basal(Realm realm, Date eventDate, int eventRTC, int eventOFFSET,
                             byte pattern) {

        PumpHistoryLoop object = realm.where(PumpHistoryLoop.class)
                .equalTo("loopMode", true)
                .equalTo("loopActive", false)
                .greaterThan("eventRTC", eventRTC - (5 * 60))
                .lessThanOrEqualTo("eventRTC", eventRTC)
                .findFirst();

        if (object != null) {

            object = realm.where(PumpHistoryLoop.class)
                    .equalTo("loopPattern", pattern)
                    .equalTo("eventRTC", eventRTC)
                    .findFirst();

            if (object == null) {
                Log.d(TAG, "*new*" + "loop restart basal pattern");

                object = realm.createObject(PumpHistoryLoop.class);

                object.setEventDate(eventDate);
                object.setLoopMode(true);
                object.setLoopActive(false);
                object.setEventRTC(eventRTC);
                object.setEventOFFSET(eventOFFSET);
                object.setLoopPattern(pattern);
                object.setKey("LOOP" + String.format("%08X", eventRTC));
                object.setUploadREQ(true);
            }
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

    public int getBolusRef() {
        return bolusRef;
    }

    public void setBolusRef(int bolusRef) {
        this.bolusRef = bolusRef;
    }

    public double getDeliveredAmount() {
        return deliveredAmount;
    }

    public void setDeliveredAmount(double deliveredAmount) {
        this.deliveredAmount = deliveredAmount;
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

    public boolean isLoopMode() {
        return loopMode;
    }

    public void setLoopMode(boolean loopMode) {
        this.loopMode = loopMode;
    }

    public boolean isLoopActive() {
        return loopActive;
    }

    public void setLoopActive(boolean loopActive) {
        this.loopActive = loopActive;
    }

    public byte getLoopStatus() {
        return loopStatus;
    }

    public void setLoopStatus(byte loopStatus) {
        this.loopStatus = loopStatus;
    }

    public byte getLoopPattern() {
        return loopPattern;
    }

    public void setLoopPattern(byte loopPattern) {
        this.loopPattern = loopPattern;
    }
}