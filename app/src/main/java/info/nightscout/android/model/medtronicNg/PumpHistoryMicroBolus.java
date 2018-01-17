package info.nightscout.android.model.medtronicNg;

import android.util.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import info.nightscout.android.medtronic.PumpHistoryParser;
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

public class PumpHistoryMicroBolus extends RealmObject implements PumpHistoryInterface {
    @Ignore
    private static final String TAG = PumpHistoryMicroBolus.class.getSimpleName();

    @Index
    private Date eventDate;

    @Index
    private boolean uploadREQ = false;
    private boolean uploadACK = false;

    private boolean xdripREQ = false;
    private boolean xdripACK = false;

    private String key; // unique identifier for nightscout, key = "ID" + RTC as 8 char hex ie. "CGM6A23C5AA"

    private int bolusRef;

    private double deliveredAmount;

    @Index
    private int deliveredRTC;
    private int deliveredOFFSET;
    private Date deliveredDate;

    @Override
    public List nightscout(DataStore dataStore) {
        List<UploadItem> uploadItems = new ArrayList<>();

        if (dataStore.isNsEnableTreatments()) {

            UploadItem uploadItem = new UploadItem();
            uploadItems.add(uploadItem);
            TreatmentsEndpoints.Treatment treatment = uploadItem.ack(uploadACK).treatment();

            String notes = "";

            // Synthesize Closed Loop Microboluses into Temp Basals

            notes = "microbolus " + deliveredAmount + "U";

            notes += " [DEBUG: ref=" + bolusRef + " del=" + deliveredAmount + " " + String.format("%08X", deliveredRTC) + "]";

            treatment.setEventType("Temp Basal");
            treatment.setKey600(key);
            treatment.setDuration((float) 5);
            treatment.setNotes(notes);

            treatment.setCreated_at(deliveredDate);
            treatment.setAbsolute((float) (deliveredAmount * 12));
        }

        return uploadItems;
    }

    public static void bolus(Realm realm, Date eventDate, int eventRTC, int eventOFFSET,
                             int bolusRef,
                             double deliveredAmount) {

        PumpHistoryMicroBolus object = realm.where(PumpHistoryMicroBolus.class)
                .equalTo("deliveredRTC", eventRTC)
                .findFirst();

        if (object == null) {
            Log.d(TAG, "*new*" + " Ref: " + bolusRef + " create new microbolus event");
            object = realm.createObject(PumpHistoryMicroBolus.class);

            object.setEventDate(eventDate);
            object.setBolusRef(bolusRef);

            object.setDeliveredDate(eventDate);
            object.setDeliveredRTC(eventRTC);
            object.setDeliveredOFFSET(eventOFFSET);
            object.setDeliveredAmount(deliveredAmount);

            object.setKey("MICROBOLUS" + String.format("%08X", eventRTC));
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

    public int getDeliveredRTC() {
        return deliveredRTC;
    }

    public void setDeliveredRTC(int deliveredRTC) {
        this.deliveredRTC = deliveredRTC;
    }

    public int getDeliveredOFFSET() {
        return deliveredOFFSET;
    }

    public void setDeliveredOFFSET(int deliveredOFFSET) {
        this.deliveredOFFSET = deliveredOFFSET;
    }

    public Date getDeliveredDate() {
        return deliveredDate;
    }

    public void setDeliveredDate(Date deliveredDate) {
        this.deliveredDate = deliveredDate;
    }
}