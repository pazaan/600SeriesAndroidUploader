package info.nightscout.android.model.medtronicNg;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.android.history.MessageItem;
import info.nightscout.android.history.NightscoutItem;
import info.nightscout.android.history.PumpHistoryParser;
import info.nightscout.android.history.PumpHistorySender;
import info.nightscout.api.EntriesEndpoints;
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
    private String senderREQ = "";
    @Index
    private String senderACK = "";
    @Index
    private String senderDEL = "";

    @Index
    private Date eventDate;

    private String key; // unique identifier for nightscout, key = "ID" + RTC as 8 char hex ie. "CGM6A23C5AA"

    private boolean history = false; // history or status? we add these initially as polled from the status message and fill in extra details during history pulls

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

    private boolean estimate;
    private byte estimateQuality;

    @Override
    public List<NightscoutItem> nightscout(PumpHistorySender pumpHistorySender, String senderID) {
        List<NightscoutItem> nightscoutItems = new ArrayList<>();

        int sgvNS = sgv;
        if (sgvNS == 0) {
            if (PumpHistoryParser.CGM_EXCEPTION.SENSOR_CAL_NEEDED.equals(sensorException))
                sgvNS = NS_ERROR.SENSOR_NOT_CALIBRATED.value;
            else if (PumpHistoryParser.CGM_EXCEPTION.SENSOR_CHANGE_SENSOR_ERROR.equals(sensorException))
                sgvNS = NS_ERROR.SENSOR_NOT_ACTIVE.value;
            else if (PumpHistoryParser.CGM_EXCEPTION.SENSOR_END_OF_LIFE.equals(sensorException))
                sgvNS = NS_ERROR.SENSOR_NOT_ACTIVE.value;
        }

        if (sgvNS > 0) {
            NightscoutItem nightscoutItem = new NightscoutItem();
            nightscoutItems.add(nightscoutItem);
            EntriesEndpoints.Entry entry = nightscoutItem.ack(senderACK.contains(senderID)).entry();

            entry.setKey600(key);
            entry.setType("sgv");
            entry.setDate(eventDate.getTime());
            entry.setDateString(eventDate.toString());

            entry.setSgv(sgvNS);

            if (estimate)
                entry.setDirection(NS_TREND.NONE.string()); // setting the trend to NONE in NS shows symbol: <">
            else if (cgmTrend != null)
                entry.setDirection(NS_TREND.valueOf(cgmTrend).string());
        }

        return nightscoutItems;
    }

    public static void cgmFromHistory(PumpHistorySender pumpHistorySender, Realm realm, Date eventDate, int eventRTC, int eventOFFSET,
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

        PumpHistoryCGM record = realm.where(PumpHistoryCGM.class)
                .equalTo("cgmRTC", eventRTC)
                .findFirst();
        if (record == null) {
            // create new entry
            record = realm.createObject(PumpHistoryCGM.class);
            record.key = String.format("CGM%08X", eventRTC);
            record.history = true;
            record.eventDate = eventDate;
            record.cgmRTC = eventRTC;
            record.cgmOFFSET = eventOFFSET;
            record.sgv = sgv;
            record.isig = isig;
            record.vctr = vctr;
            record.sensorStatus = sensorStatus;
            record.readingStatus = readingStatus;
            record.rateOfChange = rateOfChange;
            record.backfilledData = backfilledData;
            record.settingsChanged = settingsChanged;
            record.noisyData = noisyData;
            record.discardData = discardData;
            record.sensorError = sensorError;
            record.sensorException = sensorException;
            if (sgv > 0) pumpHistorySender.setSenderREQ(record);

        } else if (!record.history) {
            // update the entry
            record.history = true;
            record.isig = isig;
            record.vctr = vctr;
            record.sensorStatus = sensorStatus;
            record.readingStatus = readingStatus;
            record.rateOfChange = rateOfChange;
            record.backfilledData = backfilledData;
            record.settingsChanged = settingsChanged;
            record.noisyData = noisyData;
            record.discardData = discardData;
            record.sensorError = sensorError;
            record.sensorException = sensorException;
        }
    }

    public static void cgmFromStatus(PumpHistorySender pumpHistorySender, Realm realm, Date eventDate, int eventRTC, int eventOFFSET,
                           int sgv,
                           byte sensorException,
                           String trend) {

        PumpHistoryCGM record = realm.where(PumpHistoryCGM.class)
                .equalTo("cgmRTC", eventRTC)
                .findFirst();
        if (record == null) {
            // create new entry
            record = realm.createObject(PumpHistoryCGM.class);
            record.key = String.format("CGM%08X", eventRTC);
            record.history = false;
            record.eventDate = eventDate;
            record.cgmRTC = eventRTC;
            record.cgmOFFSET = eventOFFSET;
            record.sgv = sgv;
            record.sensorException = sensorException;
            record.cgmTrend = trend;
            pumpHistorySender.setSenderREQ(record);
        }
    }

    public enum NS_TREND {
        NONE("NONE"),
        DOUBLE_UP("DoubleUp"),
        SINGLE_UP("SingleUp"),
        FOURTY_FIVE_UP("FortyFiveUp"),
        FLAT("Flat"),
        FOURTY_FIVE_DOWN("FortyFiveDown"),
        SINGLE_DOWN("SingleDown"),
        DOUBLE_DOWN("DoubleDown"),
        NOT_COMPUTABLE("NOT COMPUTABLE"),
        RATE_OUT_OF_RANGE("RATE OUT OF RANGE"),
        NOT_SET("NONE");

        private String string;

        NS_TREND(String string) {
            this.string = string;
        }

        public String string() {
            return this.string;
        }
    }

    public enum NS_ERROR {
        SENSOR_NOT_ACTIVE(1, "?SN"),
        MINIMAL_DEVIATION(2, "?MD"),
        NO_ANTENNA(3, "?NA"),
        SENSOR_NOT_CALIBRATED(5, "?NC"),
        COUNTS_DEVIATION(6, "?CD"),
        ABSOLUTE_DEVIATION(9, "?AD"),
        POWER_DEVIATION(10, "???"),
        BAD_RF(12, "?RF");

        private int value;
        private String string;

        NS_ERROR(int value, String string) {
            this.value = value;
            this.string = string;
        }

        public int value() {
            return this.value;
        }

        public String string() {
            return this.string;
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

    public boolean isHistory() {
        return history;
    }

    public int getCgmRTC() {
        return cgmRTC;
    }

    public int getCgmOFFSET() {
        return cgmOFFSET;
    }

    public int getSgv() {
        return sgv;
    }

    public double getIsig() {
        return isig;
    }

    public double getVctr() {
        return vctr;
    }

    public double getRateOfChange() {
        return rateOfChange;
    }

    public byte getSensorStatus() {
        return sensorStatus;
    }

    public byte getReadingStatus() {
        return readingStatus;
    }

    public byte getSensorException() {
        return sensorException;
    }

    public boolean isBackfilledData() {
        return backfilledData;
    }

    public boolean isSettingsChanged() {
        return settingsChanged;
    }

    public boolean isNoisyData() {
        return noisyData;
    }

    public boolean isDiscardData() {
        return discardData;
    }

    public boolean isSensorError() {
        return sensorError;
    }

    public String getCgmTrend() {
        return cgmTrend;
    }

    public void setSgv(int sgv) {
        this.sgv = sgv;
    }

    public void setHistory(boolean history) {
        this.history = history;
    }

    public boolean isEstimate() {
        return estimate;
    }

    public void setEstimate(boolean estimate) {
        this.estimate = estimate;
    }

    public void setEstimateQuality(byte estimateQuality) {
        this.estimateQuality = estimateQuality;
    }

    public byte getEstimateQuality() {
        return estimateQuality;
    }
}
