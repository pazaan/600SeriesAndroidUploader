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
import io.realm.RealmResults;
import io.realm.Sort;
import io.realm.annotations.Ignore;
import io.realm.annotations.Index;

/**
 * Created by Pogman on 26.10.17.
 */

public class PumpHistoryMisc extends RealmObject implements PumpHistoryInterface {
    @Ignore
    private static final String TAG = PumpHistoryMisc.class.getSimpleName();

    @Index
    private Date eventDate;

    @Index
    private boolean uploadREQ = false;
    private boolean uploadACK = false;

    private boolean xdripREQ = false;
    private boolean xdripACK = false;

    private String key; // unique identifier for nightscout, key = "ID" + RTC as 8 char hex ie. "CGM6A23C5AA"

    @Index
    private int itemRTC;
    private int itemOFFSET;

    private int item; // change sensor = 1, change battery = 2, change cannula = 3
    private int lifetime;

    @Override
    public List nightscout(DataStore dataStore) {
        List<UploadItem> uploadItems = new ArrayList<>();

        if (dataStore.isNsEnableTreatments()) {
            String type = null;
            String notes = "";

            if (item == 1 && dataStore.isNsEnableSensorChange()) {
                type = "Sensor Start";
                notes += "Sensor changed";
            } else if (item == 2 && dataStore.isNsEnableBatteryChange()) {
                type = "Note";
                notes += "Pump battery changed";
            } else if (item == 3 && dataStore.isNsEnableReservoirChange()) {
                type = "Site Change";
                notes += "Reservoir changed";
            }
            if (lifetime > 0 && dataStore.isNsEnableLifetimes())
                notes += " (lifetime " + (lifetime / 1440) + " days " + ((lifetime % 1440) / 60) + " hours)";

            if (type != null) {
                UploadItem uploadItem = new UploadItem();
                uploadItems.add(uploadItem);
                TreatmentsEndpoints.Treatment treatment = uploadItem.ack(uploadACK).treatment();

                treatment.setKey600(key);
                treatment.setCreated_at(eventDate);
                treatment.setEventType(type);
                treatment.setNotes(notes);
            }
        }

        return uploadItems;
    }

    public static void item(Realm realm, Date eventDate, int eventRTC, int eventOFFSET,
                                   int item) {

        PumpHistoryMisc object = realm.where(PumpHistoryMisc.class)
                .equalTo("item", item)
                .equalTo("itemRTC", eventRTC)
                .findFirst();
        if (object == null) {
            Log.d(TAG, "*new*" + " item: " + item);
            object = realm.createObject(PumpHistoryMisc.class);
            object.setEventDate(eventDate);
            object.setKey("MISC" + String.format("%08X", eventRTC));
            object.setItemRTC(eventRTC);
            object.setItemOFFSET(eventOFFSET);
            object.setItem(item);
            object.setUploadREQ(true);

            // lifetime for items
            RealmResults<PumpHistoryMisc> results = realm.where(PumpHistoryMisc.class)
                    .equalTo("item", item)
                    .findAllSorted("eventDate", Sort.DESCENDING);
            if (results.size() > 1) {
                for (int i = 1; i < results.size(); i++) {
                    int lifetime = (int) ((results.get(i - 1).getEventDate().getTime() - results.get(i).getEventDate().getTime()) / 60000L);
                    // lifetime may need updating due to new datapoint
                    if (results.get(i).getLifetime() != lifetime) {
                        results.get(i).setLifetime(lifetime);
                        results.get(i).setUploadREQ(true);
                    }
                }
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

    public int getItemRTC() {
        return itemRTC;
    }

    public void setItemRTC(int itemRTC) {
        this.itemRTC = itemRTC;
    }

    public int getItemOFFSET() {
        return itemOFFSET;
    }

    public void setItemOFFSET(int itemOFFSET) {
        this.itemOFFSET = itemOFFSET;
    }

    public int getItem() {
        return item;
    }

    public void setItem(int item) {
        this.item = item;
    }

    public int getLifetime() {
        return lifetime;
    }

    public void setLifetime(int lifetime) {
        this.lifetime = lifetime;
    }
}