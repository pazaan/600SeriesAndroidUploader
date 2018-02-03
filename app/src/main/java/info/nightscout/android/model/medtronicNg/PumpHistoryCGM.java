package info.nightscout.android.model.medtronicNg;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.android.medtronic.PumpHistoryParser;
import info.nightscout.android.model.store.DataStore;
import info.nightscout.api.EntriesEndpoints;
import info.nightscout.api.UploadItem;
import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.annotations.Ignore;
import io.realm.annotations.Index;

/**
 * Created by Pogman on 19.10.17.
 */

public class PumpHistoryCGM extends RealmObject implements PumpHistoryInterface {
    @Ignore
    private static final String TAG = PumpHistoryCGM.class.getSimpleName();

    @Index
    private Date eventDate;

    @Index
    private boolean uploadREQ = false;
    private boolean uploadACK = false;

    private boolean xdripREQ = false;
    private boolean xdripACK = false; // TODO - refactor xdrip service

    private String key; // unique identifier for nightscout, key = "ID" + RTC as 8 char hex ie. "CGM6A23C5AA"

    private boolean history = false; // update or status? we add these initially as polled from status and fill in extra details during update pulls

    @Index
    private int cgmRTC;
    private int cgmOFFSET;

    @Index
    private int sgv;

    private double isig;
    private double vctr;
    private double rateOfChange;
    private byte sensorStatus;
    private byte readingStatus;
    private byte sensorException;

    private boolean backfilledData;
    private boolean settingsChanged;
    private boolean noisyData;
    private boolean discardData;
    private boolean sensorError;

    private String cgmTrend; // only available when added via the status message

    @Override
    public List nightscout(DataStore dataStore) {
        List<UploadItem> uploadItems = new ArrayList<>();

        int sgvNS = sgv;
        if (sgvNS == 0) {
            if (sensorException == 2) // SENSOR_CAL_NEEDED
                sgvNS = 5; // nightscout: '?NC' SENSOR_NOT_CALIBRATED
            else if (sensorException == 5) // SENSOR_CHANGE_SENSOR_ERROR
                sgvNS = 1; // nightscout: '?SN' SENSOR_NOT_ACTIVE
            else if (sensorException == 6) // SENSOR_END_OF_LIFE
                sgvNS = 1; // nightscout: '?SN' SENSOR_NOT_ACTIVE
        }

        if (sgvNS > 0) {
            UploadItem uploadItem = new UploadItem();
            uploadItems.add(uploadItem);
            EntriesEndpoints.Entry entry = uploadItem.ack(uploadACK).entry();

            entry.setKey600(key);
            entry.setType("sgv");
            entry.setDate(eventDate.getTime());
            entry.setDateString(eventDate.toString());

            entry.setSgv(sgvNS);
            if (cgmTrend != null)
                entry.setDirection(PumpHistoryParser.TextEN.valueOf("NS_TREND_" + cgmTrend).getText());
        }

        return uploadItems;
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
                .equalTo("cgmRTC", eventRTC)
                .findFirst();
        if (object == null) {
            // create new entry
            object = realm.createObject(PumpHistoryCGM.class);
            object.setKey("CGM" + String.format("%08X", eventRTC));
            object.setHistory(true);
            object.setEventDate(eventDate);
            object.setCgmRTC(eventRTC);
            object.setCgmOFFSET(eventOFFSET);
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

    public static void cgm(Realm realm, Date eventDate, int eventRTC, int eventOFFSET,
                           int sgv,
                           byte sensorException,
                           String trend) {

        PumpHistoryCGM object = realm.where(PumpHistoryCGM.class)
                .equalTo("cgmRTC", eventRTC)
                .findFirst();
        if (object == null) {
            // create new entry
            object = realm.createObject(PumpHistoryCGM.class);
            object.setKey("CGM" + String.format("%08X", eventRTC));
            object.setHistory(false);
            object.setEventDate(eventDate);
            object.setCgmRTC(eventRTC);
            object.setCgmOFFSET(eventOFFSET);
            object.setSgv(sgv);
            object.setSensorException(sensorException);
            object.setCgmTrend(trend);
            object.setUploadREQ(true);
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

    public int getCgmRTC() {
        return cgmRTC;
    }

    public void setCgmRTC(int cgmRTC) {
        this.cgmRTC = cgmRTC;
    }

    public int getCgmOFFSET() {
        return cgmOFFSET;
    }

    public void setCgmOFFSET(int cgmOFFSET) {
        this.cgmOFFSET = cgmOFFSET;
    }

    public boolean isHistory() {
        return history;
    }

    public void setHistory(boolean history) {
        this.history = history;
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
