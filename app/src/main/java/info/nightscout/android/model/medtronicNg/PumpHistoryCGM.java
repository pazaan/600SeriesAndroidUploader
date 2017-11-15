package info.nightscout.android.model.medtronicNg;

import android.util.Log;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import info.nightscout.android.medtronic.PumpHistoryParser;
import info.nightscout.api.EntriesEndpoints;
import info.nightscout.api.TreatmentsEndpoints;
import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.Sort;
import io.realm.annotations.Ignore;
import io.realm.annotations.Index;
import io.realm.annotations.PrimaryKey;

/**
 * Created by John on 19.10.17.
 */

public class PumpHistoryCGM extends RealmObject implements PumpHistory {
    @Ignore
    private static final String TAG = PumpHistoryCGM.class.getSimpleName();

    @Index
    private Date eventDate;
    @Index
    private Date eventEndDate; // event deleted when this is stale

    private boolean uploadREQ = false;
    private boolean uploadACK = false;
    private boolean xdripREQ = false;
    private boolean xdripACK = false;

    private String key; // unique identifier for nightscout, key = "ID" + RTC as 8 char hex ie. "CGM6A23C5AA"

    private boolean history = false; // history or status? we add these initially as polled from status and fill in extra details during history pulls

    private boolean uploaded = false;
    private boolean xdrip = false;
    private int eventRTC;
    private int eventOFFSET;

    private int sgv;
    private double isig;
    private double vctr;
    private double rateOfChange;
    private byte sensorStatus;
    private byte readingStatus;

    private boolean backfilledData;
    private boolean settingsChanged;
    private boolean noisyData;
    private boolean discardData;
    private boolean sensorError;

    byte sensorException;

    private String cgmTrend;

    public List Nightscout() {
        List list = new ArrayList();

        EntriesEndpoints.Entry entry = new EntriesEndpoints.Entry();
        list.add("entry");
        list.add(uploadACK ? "update" : "new");
        list.add(entry);

        entry.setKey600(key);
        entry.setType("sgv");
        entry.setDate(eventDate.getTime());
        entry.setDateString(eventDate.toString());

        entry.setSgv(sgv);
        if (cgmTrend != null) entry.setDirection(PumpHistoryParser.TextEN.valueOf("NS_TREND_" + cgmTrend).getText());

        return list;
    }

    public static void stale(Realm realm, Date date) {
        final RealmResults results = realm.where(PumpHistoryCGM.class)
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
        final RealmResults<PumpHistoryCGM> results = realm.where(PumpHistoryCGM.class)
                .findAllSorted("eventDate", Sort.ASCENDING);
        Log.d(TAG, "records: " + results.size() + (results.size() > 0 ? " start: "+ dateFormatter.format(results.first().getEventDate()) + " end: " + dateFormatter.format(results.last().getEventDate()) : ""));
    }

    public static void event(Realm realm, Date eventDate, int eventRTC, int eventOFFSET,
                             int sgv,
                             double isig,
                             double vctr,
                             double rateOfChange,
                             byte sensorStatus,
                             byte readingStatus,
                             boolean backfilledData,
                             boolean settingsChanged,
                             boolean noisyData,
                             boolean discardData,
                             boolean sensorError,
                             byte sensorException) {

        PumpHistoryCGM object = realm.where(PumpHistoryCGM.class)
                .equalTo("eventRTC", eventRTC)
                .findFirst();
        if (object == null) {
            // create new entry
            object = realm.createObject(PumpHistoryCGM.class);
            object.setKey("CGM" + String.format("%08X", eventRTC));
            object.setHistory(true);
            object.setEventDate(eventDate);
            object.setEventEndDate(eventDate);
            object.setEventRTC(eventRTC);
            object.setEventOFFSET(eventOFFSET);
            object.setSgv(sgv);
            object.setIsig(isig);
            object.setVctr(vctr);
            object.setSensorStatus(sensorStatus);
            object.setReadingStatus(readingStatus);
            object.setRateOfChange(rateOfChange);
            object.setBackfilledData(backfilledData);
            object.setSettingsChanged(settingsChanged);
            object.setNoisyData(noisyData);
            object.setDiscardData(discardData);
            object.setSensorError(sensorError);
            object.setSensorException(sensorException);
            if (sgv > 0) object.setUploadREQ(true);
        } else if (!object.isHistory()) {
            // update the entry
            object.setHistory(true);
            object.setIsig(isig);
            object.setVctr(vctr);
            object.setSensorStatus(sensorStatus);
            object.setReadingStatus(readingStatus);
            object.setRateOfChange(rateOfChange);
            object.setBackfilledData(backfilledData);
            object.setSettingsChanged(settingsChanged);
            object.setNoisyData(noisyData);
            object.setDiscardData(discardData);
            object.setSensorError(sensorError);
            object.setSensorException(sensorException);
        }
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

    public boolean isHistory() {
        return history;
    }

    public void setHistory(boolean history) {
        this.history = history;
    }

    public boolean isUploaded() {
        return uploaded;
    }

    public void setUploaded(boolean uploaded) {
        this.uploaded = uploaded;
    }

    public boolean isXdrip() {
        return xdrip;
    }

    public void setXdrip(boolean xdrip) {
        this.xdrip = xdrip;
    }

    public int getSgv() {
        return sgv;
    }

    public void setSgv(int sgv) {
        this.sgv = sgv;
    }

    public double getIsig() {
        return isig;
    }

    public void setIsig(double isig) {
        this.isig = isig;
    }

    public double getVctr() {
        return vctr;
    }

    public void setVctr(double vctr) {
        this.vctr = vctr;
    }

    public double getRateOfChange() {
        return rateOfChange;
    }

    public void setRateOfChange(double rateOfChange) {
        this.rateOfChange = rateOfChange;
    }

    public byte getSensorStatus() {
        return sensorStatus;
    }

    public void setSensorStatus(byte sensorStatus) {
        this.sensorStatus = sensorStatus;
    }

    public byte getReadingStatus() {
        return readingStatus;
    }

    public void setReadingStatus(byte readingStatus) {
        this.readingStatus = readingStatus;
    }

    public boolean isBackfilledData() {
        return backfilledData;
    }

    public void setBackfilledData(boolean backfilledData) {
        this.backfilledData = backfilledData;
    }

    public boolean isSettingsChanged() {
        return settingsChanged;
    }

    public void setSettingsChanged(boolean settingsChanged) {
        this.settingsChanged = settingsChanged;
    }

    public boolean isNoisyData() {
        return noisyData;
    }

    public void setNoisyData(boolean noisyData) {
        this.noisyData = noisyData;
    }

    public boolean isDiscardData() {
        return discardData;
    }

    public void setDiscardData(boolean discardData) {
        this.discardData = discardData;
    }

    public boolean isSensorError() {
        return sensorError;
    }

    public void setSensorError(boolean sensorError) {
        this.sensorError = sensorError;
    }

    public byte getSensorException() {
        return sensorException;
    }

    public void setSensorException(byte sensorException) {
        this.sensorException = sensorException;
    }

    public String getCgmTrend() {
        return cgmTrend;
    }

    public void setCgmTrend(String cgmTrend) {
        this.cgmTrend = cgmTrend;
    }
}
