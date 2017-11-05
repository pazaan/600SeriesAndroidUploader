package info.nightscout.android.model.medtronicNg;

import android.util.Log;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import info.nightscout.api.EntriesEndpoints;
import info.nightscout.api.TreatmentsEndpoints;
import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.Sort;
import io.realm.annotations.Ignore;
import io.realm.annotations.Index;

/**
 * Created by John on 26.10.17.
 */

public class PumpHistoryMisc extends RealmObject implements PumpHistory {
    @Ignore
    private static final String TAG = PumpHistoryMisc.class.getSimpleName();

    @Index
    private Date eventDate;
    @Index
    private Date eventEndDate; // event deleted when this is stale

    private boolean uploadREQ = false;
    private boolean uploadACK = false;
    private boolean xdripREQ = false;
    private boolean xdripACK = false;

    private String key; // unique identifier for nightscout, key = "ID" + RTC as 8 char hex ie. "CGM6A23C5AA"

    private int eventRTC;
    private int eventOFFSET;

    private int item; // change sensor = 1, change battery = 2, change cannula = 3

    @Override
    public List Nightscout() {
        Log.d(TAG, "*history* MISC do da thing! " + "item: " + item);

        List list = new ArrayList();
        list.add("treatment");
        if (uploadACK) list.add("update"); else list.add("new");
        TreatmentsEndpoints.Treatment treatment = new TreatmentsEndpoints.Treatment();
        list.add(treatment);

        treatment.setKey600(key);
        treatment.setCreated_at(eventDate);
        if (item == 1) {
            treatment.setEventType("Sensor Start");
            treatment.setNotes("Sensor changed");
        } else if (item == 2) {
            treatment.setEventType("Note");
            treatment.setNotes("Pump battery changed");
        } else if (item == 3) {
            treatment.setEventType("Site Change");
            treatment.setNotes("Reservoir changed");
        } else {
            treatment.setEventType("Note");
            treatment.setNotes("Unknown event");
        }

        return list;
    }

    public static void stale(Realm realm, Date date) {
        final RealmResults results = realm.where(PumpHistoryMisc.class)
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
        final RealmResults<PumpHistoryMisc> results = realm.where(PumpHistoryMisc.class)
                .findAllSorted("eventDate", Sort.ASCENDING);
        Log.d(TAG, "records: " + results.size() + (results.size() > 0 ? " start: " + dateFormatter.format(results.first().getEventDate()) + " end: " + dateFormatter.format(results.last().getEventDate()) : ""));
    }

    public static void item(Realm realm, Date eventDate, int eventRTC, int eventOFFSET,
                                   int item) {

        PumpHistoryMisc object = realm.where(PumpHistoryMisc.class)
                .equalTo("item", item)
                .equalTo("eventRTC", eventRTC)
                .findFirst();
        if (object == null) {
            Log.d(TAG, "*new*" + " item: " + item);
            object = realm.createObject(PumpHistoryMisc.class);
            object.setEventDate(eventDate);
            object.setEventEndDate(eventDate);
            object.setKey("MISC" + String.format("%08X", eventRTC));
            object.setEventRTC(eventRTC);
            object.setEventOFFSET(eventOFFSET);
            object.setItem(item);
            if (item == 1 || item == 2 || item == 3)
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

    public int getItem() {
        return item;
    }

    public void setItem(int item) {
        this.item = item;
    }
}