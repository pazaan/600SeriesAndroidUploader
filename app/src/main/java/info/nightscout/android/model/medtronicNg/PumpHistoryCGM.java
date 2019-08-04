package info.nightscout.android.model.medtronicNg;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.android.history.HistoryUtils;
import info.nightscout.android.history.MessageItem;
import info.nightscout.android.history.NightscoutItem;
import info.nightscout.android.history.PumpHistoryParser;
import info.nightscout.android.history.PumpHistorySender;
import info.nightscout.android.medtronic.exception.IntegrityException;
import info.nightscout.android.upload.nightscout.EntriesEndpoints;
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
    @Index
    private long pumpMAC;

    private String key; // unique identifier for nightscout, key = "ID" + RTC as 8 char hex ie. "CGM6A23C5AA"

    @Index
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

    @Index
    private byte sensorException;

    private boolean backfilledData;
    private boolean settingsChanged;
    @Index
    private boolean noisyData;
    @Index
    private boolean discardData;
    @Index
    private boolean sensorError;

    private String cgmTrend; // only available when added via the status message

    @Index
    private boolean estimate;

    @Override
    public List<NightscoutItem> nightscout(PumpHistorySender pumpHistorySender, String senderID) {
        List<NightscoutItem> nightscoutItems = new ArrayList<>();

        NS_TREND trend = cgmTrend == null ? null
                : pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.MEDTRONIC_TREND_STYLE)
                ? NS_TREND.valueOf(cgmTrend)
                : NS_TREND.valueOf(cgmTrend).dexcom();

        int sgv = this.sgv;

        if (!estimate || sgv == 0) {

            switch (PumpHistoryParser.CGM_EXCEPTION.convert(sensorException)) {
                case SENSOR_CAL_NEEDED:
                    trend = NS_TREND.NOT_COMPUTABLE;
                    sgv = NS_ERROR.SENSOR_NOT_CALIBRATED.value;
                    break;
                case SENSOR_CHANGE_CAL_ERROR:
                case SENSOR_CHANGE_SENSOR_ERROR:
                case SENSOR_END_OF_LIFE:
                    trend = NS_TREND.NOT_COMPUTABLE;
                    sgv = NS_ERROR.SENSOR_NOT_ACTIVE.value;
                    break;
                case SENSOR_READING_LOW:
                    trend = NS_TREND.RATE_OUT_OF_RANGE;
                    sgv = 40;
                    break;
                case SENSOR_READING_HIGH:
                    trend = NS_TREND.RATE_OUT_OF_RANGE;
                    sgv = 400;
                    break;
                case SENSOR_CAL_PENDING:
                case SENSOR_INIT:
                case SENSOR_TIME_UNKNOWN:
                case SENSOR_NOT_READY:
                case SENSOR_ERROR:
                    trend = NS_TREND.NOT_COMPUTABLE;
                    sgv = NS_ERROR.NO_ANTENNA.value;
                    break;
                default:
            }

        } else {
            // limit range for NS as it uses some values as flags
            if (sgv < 40) sgv = 40;
            else if (sgv > 500) sgv = 500;
            trend = NS_TREND.NONE; // setting the trend to NONE in NS shows symbol: <">
        }

        if (sgv > 0) {
            EntriesEndpoints.Entry entry = HistoryUtils.nightscoutEntry(nightscoutItems, this, senderID);
            entry.setType("sgv");
            entry.setSgv(sgv);
            if (trend != null) entry.setDirection(trend.string());
            if (estimate) nightscoutItems.get(0).update();
        }

        return nightscoutItems;
    }

    public static void cgmFromHistory(
            PumpHistorySender pumpHistorySender, Realm realm, long pumpMAC,
            Date eventDate, int eventRTC, int eventOFFSET,
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
            byte sensorException) throws IntegrityException {

        PumpHistoryCGM record = realm.where(PumpHistoryCGM.class)
                .equalTo("pumpMAC", pumpMAC)
                .equalTo("cgmRTC", eventRTC)
                .findFirst();

        if (record == null) {
            // create new entry
            record = realm.createObject(PumpHistoryCGM.class);
            record.pumpMAC = pumpMAC;
            record.key = HistoryUtils.key("CGM", eventRTC);
            record.history = true;
            record.eventDate = eventDate;
            record.cgmRTC = eventRTC;
            record.cgmOFFSET = eventOFFSET;
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
            sgv(record, sgv, null, sensorException);
            pumpHistorySender.setSenderREQ(record);
        }

        else {
            HistoryUtils.integrity(record, eventDate);
            if (!record.history) {
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
    }

    public static void cgmFromStatus(
            PumpHistorySender pumpHistorySender, Realm realm, long pumpMAC,
            Date eventDate, int eventRTC, int eventOFFSET,
            int sgv,
            byte sensorException,
            String trend) throws IntegrityException {

        PumpHistoryCGM record = realm.where(PumpHistoryCGM.class)
                .equalTo("pumpMAC", pumpMAC)
                .equalTo("cgmRTC", eventRTC)
                .findFirst();

        if (record == null) {
            // create new entry
            record = realm.createObject(PumpHistoryCGM.class);
            record.pumpMAC = pumpMAC;
            record.key = HistoryUtils.key("CGM", eventRTC);
            record.history = false;
            record.eventDate = eventDate;
            record.cgmRTC = eventRTC;
            record.cgmOFFSET = eventOFFSET;
            sgv(record, sgv, trend, sensorException);
            pumpHistorySender.setSenderREQ(record);
        }

        else HistoryUtils.integrity(record, eventDate);
    }

    private static void sgv(PumpHistoryCGM record, int sgv, String trend, byte sensorException) {
        // 600 pumps produce a exception for low/high readings but no actual sgv
        // it will show 'below 40 / 2.2' or 'above 400 / 22.2' on the pump
        // for continuity and graph visibility we set
        // reading low: <= 40 mg/dl (2.2 mmol) as 40
        // reading high: >= 400 mg/dl (22.2 mmol) as 400
        if (sgv == 0) {
            switch (PumpHistoryParser.CGM_EXCEPTION.convert(sensorException)) {
                case SENSOR_READING_LOW:
                    trend = NS_TREND.RATE_OUT_OF_RANGE.name();
                    sgv = 40;
                    break;
                case SENSOR_READING_HIGH:
                    trend = NS_TREND.RATE_OUT_OF_RANGE.name();
                    sgv = 400;
                    break;
            }
        }
        record.sgv = sgv;
        record.cgmTrend = trend;
        record.sensorException = sensorException;
    }

    public enum NS_TREND {
        NONE("NONE"),
        TRIPLE_UP("TripleUp"),
        DOUBLE_UP("DoubleUp"),
        SINGLE_UP("SingleUp"),
        FOURTY_FIVE_UP("FortyFiveUp"),
        FLAT("Flat"),
        FOURTY_FIVE_DOWN("FortyFiveDown"),
        SINGLE_DOWN("SingleDown"),
        DOUBLE_DOWN("DoubleDown"),
        TRIPLE_DOWN("TripleDown"),
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

        public NS_TREND dexcom() {
            switch (this) {
                case TRIPLE_DOWN:
                    return DOUBLE_DOWN;
                case DOUBLE_DOWN:
                    return SINGLE_DOWN;
                case SINGLE_DOWN:
                    return FOURTY_FIVE_DOWN;
                case SINGLE_UP:
                    return FOURTY_FIVE_UP;
                case DOUBLE_UP:
                    return SINGLE_UP;
                case TRIPLE_UP:
                    return DOUBLE_UP;
                default:
                    return this;
            }
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

    @Override
    public long getPumpMAC() {
        return pumpMAC;
    }

    @Override
    public void setPumpMAC(long pumpMAC) {
        this.pumpMAC = pumpMAC;
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
}
