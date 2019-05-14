package info.nightscout.android.history;

import android.util.Pair;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.android.PumpAlert;
import info.nightscout.android.model.medtronicNg.PumpHistoryAlarm;
import info.nightscout.android.model.medtronicNg.PumpHistoryBG;
import info.nightscout.android.model.medtronicNg.PumpHistoryBasal;
import info.nightscout.android.model.medtronicNg.PumpHistoryBolus;
import info.nightscout.android.model.medtronicNg.PumpHistoryCGM;
import info.nightscout.android.model.medtronicNg.PumpHistoryDaily;
import info.nightscout.android.model.medtronicNg.PumpHistoryInterface;
import info.nightscout.android.model.medtronicNg.PumpHistoryLoop;
import info.nightscout.android.model.medtronicNg.PumpHistoryMarker;
import info.nightscout.android.model.medtronicNg.PumpHistoryMisc;
import info.nightscout.android.model.medtronicNg.PumpHistoryPattern;
import info.nightscout.android.model.medtronicNg.PumpHistoryProfile;
import info.nightscout.android.model.medtronicNg.PumpHistorySystem;
import info.nightscout.android.model.store.DataStore;

public class PumpHistorySender {
    private static final String TAG = PumpHistorySender.class.getSimpleName();

    // sender ID's should be unique, be wary that these are used in string 'contains' code statements
    public static final String SENDER_ID_NIGHTSCOUT = "NS";
    public static final String SENDER_ID_XDRIP = "XD";
    public static final String SENDER_ID_PUSHOVER = "PO";

    private List<Sender> senders = new ArrayList<>();

    public PumpHistorySender() { }

    public PumpHistorySender buildSenders(DataStore dataStore) {

        boolean treatments = dataStore.isNsEnableTreatments() & dataStore.isNightscoutCareportal();

        new Sender(SENDER_ID_NIGHTSCOUT)
                .add(PumpHistoryCGM.class)
                .add(PumpHistoryBolus.class, true, treatments)
                .add(PumpHistoryBasal.class, true, treatments)
                .add(PumpHistoryPattern.class, true, treatments)
                .add(PumpHistoryBG.class, true, treatments)
                .add(PumpHistoryProfile.class, true, dataStore.isNightscoutUseProfile() & dataStore.isNsEnableProfileUpload())
                .add(PumpHistoryMisc.class, true, treatments)
                .add(PumpHistoryMarker.class, true, treatments)
                .add(PumpHistoryLoop.class, true, treatments)
                .add(PumpHistoryDaily.class, true, treatments)
                .add(PumpHistoryAlarm.class, true, treatments)
                .add(PumpHistorySystem.class, true, treatments)

                .ttl(PumpHistoryAlarm.class, dataStore.isNsEnableAlarms() ? dataStore.getNsAlarmTTL() * 60 * 60000L : 0)
                .ttl(PumpHistorySystem.class, dataStore.isNsEnableSystemStatus() ? 24 * 60 * 60000L : 0)

                .limiter(dataStore.getNightscoutURL() != null && dataStore.getNightscoutURL().toLowerCase().contains("azure") ? 60 : 200)
                .process(180 * 24 * 60 * 60000L)

                .opt(SENDEROPT.TREATMENTS, treatments)
                .opt(SENDEROPT.BASAL, dataStore.isNsEnableBasalTreatments())
                .opt(SENDEROPT.BOLUS, dataStore.isNsEnableBolusTreatments())
                .opt(SENDEROPT.BASAL_PATTERN_CHANGE, dataStore.isNsEnablePatternChange())

                .opt(SENDEROPT.FORMAT_HTML, dataStore.isNsEnableFormatHTML())
                .opt(SENDEROPT.MEDTRONIC_TREND_STYLE, dataStore.isNsEnableMedtronicTrendStyle())
                .opt(SENDEROPT.GLUCOSE_UNITS, true)

                .opt(SENDEROPT.ALARM_FAULTCODE, false)
                .opt(SENDEROPT.ALARM_FAULTDATA, true)
                .opt(SENDEROPT.ALARM_CLEARED, dataStore.isNsAlarmCleared())
                .opt(SENDEROPT.ALARM_SILENCED, true)
                .opt(SENDEROPT.ALARM_EXTENDED, dataStore.isNsAlarmExtended())
                .var(SENDEROPT.ALARM_PRIORITY, Integer.toString(PumpAlert.PRIORITY.LOWEST.value()))

                .opt(SENDEROPT.SYSTEM_PUMP_DEVICE_ERROR, dataStore.isNsEnableSystemStatus())
                .opt(SENDEROPT.SYSTEM_CNL_USB_ERROR, dataStore.isNsEnableSystemStatus() & dataStore.isNsSystemStatusUsbErrors())
                .opt(SENDEROPT.SYSTEM_PUMP_LOST, dataStore.isNsEnableSystemStatus() & dataStore.isNsSystemStatusConnection())
                .var(SENDEROPT.SYSTEM_PUMP_LOST_AT, Integer.toString(30 * 60))
                .var(SENDEROPT.SYSTEM_PUMP_LOST_REPEAT, Integer.toString(30 * 60))
                .opt(SENDEROPT.SYSTEM_PUMP_CONNECTTED, dataStore.isNsEnableSystemStatus() & dataStore.isNsSystemStatusConnection())
                .var(SENDEROPT.SYSTEM_PUMP_CONNECTTED_AT, Integer.toString(30 * 60))
                .opt(SENDEROPT.SYSTEM_UPLOADER_BATTERY_LOW, dataStore.isNsEnableSystemStatus() & dataStore.isNsSystemStatusBatteryLow())
                .opt(SENDEROPT.SYSTEM_UPLOADER_BATTERY_VERY_LOW, dataStore.isNsEnableSystemStatus() & dataStore.isNsSystemStatusBatteryLow())
                .opt(SENDEROPT.SYSTEM_UPLOADER_BATTERY_CHARGED, dataStore.isNsEnableSystemStatus() & dataStore.isNsSystemStatusBatteryCharged())

                .opt(SENDEROPT.DAILY_TOTALS, dataStore.isNsEnableDailyTotals())
                .opt(SENDEROPT.BG_INFO, dataStore.isNsEnableFingerBG())
                .opt(SENDEROPT.CALIBRATION_INFO, dataStore.isNsEnableFingerBG() & dataStore.isNsEnableCalibrationInfo())
                .opt(SENDEROPT.MISC_LIFETIMES, dataStore.isNsEnableLifetimes())
                .opt(SENDEROPT.MISC_SENSOR, dataStore.isNsEnableSensorChange())
                .opt(SENDEROPT.MISC_BATTERY, dataStore.isNsEnableBatteryChange())
                .opt(SENDEROPT.MISC_CANNULA, dataStore.isNsEnableReservoirChange())
                .opt(SENDEROPT.MISC_INSULIN, dataStore.isNsEnableInsulinChange())
                .var(SENDEROPT.INSULIN_CHANGE_THRESHOLD, Integer.toString(dataStore.getNsInsulinChangeThreshold()))
                .var(SENDEROPT.CANNULA_CHANGE_THRESHOLD, Integer.toString(dataStore.getNsCannulaChangeThreshold()))

                .opt(SENDEROPT.INSERT_BG_AS_CGM, dataStore.isNsEnableFingerBG() & dataStore.isNsEnableInsertBGasCGM())

                .opt(SENDEROPT.PROFILE_OFFSET, dataStore.isNsEnableProfileOffset())
                .var(SENDEROPT.GRAMS_PER_EXCHANGE, Integer.toString(dataStore.getNsGramsPerExchange()))

                .var(SENDEROPT.DEVICE_NAME, dataStore.getNsDeviceName())
                .var(SENDEROPT.ENTERED_BY, dataStore.getNsEnteredBy())

                .list(SENDEROPT.BASAL_PATTERN, new String[]{dataStore.getNameBasalPattern1(), dataStore.getNameBasalPattern2(), dataStore.getNameBasalPattern3(), dataStore.getNameBasalPattern4(), dataStore.getNameBasalPattern5(), dataStore.getNameBasalPattern6(), dataStore.getNameBasalPattern7(), dataStore.getNameBasalPattern8()})
                .list(SENDEROPT.BASAL_PRESET, new String[]{dataStore.getNameTempBasalPreset1(), dataStore.getNameTempBasalPreset2(), dataStore.getNameTempBasalPreset3(), dataStore.getNameTempBasalPreset4(), dataStore.getNameTempBasalPreset5(), dataStore.getNameTempBasalPreset6(), dataStore.getNameTempBasalPreset7(), dataStore.getNameTempBasalPreset8()})
                .list(SENDEROPT.BOLUS_PRESET, new String[]{dataStore.getNameBolusPreset1(), dataStore.getNameBolusPreset2(), dataStore.getNameBolusPreset3(), dataStore.getNameBolusPreset4(), dataStore.getNameBolusPreset5(), dataStore.getNameBolusPreset6(), dataStore.getNameBolusPreset7(), dataStore.getNameBolusPreset8()});

        new Sender(SENDER_ID_XDRIP)
                .add(PumpHistoryCGM.class)
                .limiter(500)
                .process(7 * 24 * 60 * 60000L)
                .stale(7* 24 * 60 * 60000L);


        // Pushover backfill period
        long backfill = dataStore.getPushoverBackfillPeriod() * 60000L;
        long now = System.currentTimeMillis();
        long init = dataStore.getInitTimestamp();
        long startup = dataStore.getStartupTimestamp();
        long cnl = dataStore.getCnlLimiterTimestamp();
        if (now - init < backfill)
            backfill = now - init;
        if (dataStore.isPushoverEnableBackfillOnStart()
                && now - startup < backfill)
            backfill = now - startup;
        if (now - cnl < backfill)
            backfill = now - cnl;

        new Sender(SENDER_ID_PUSHOVER)
                .add(PumpHistoryBolus.class, dataStore.isPushoverEnable() & dataStore.isPushoverEnableBolus())
                .add(PumpHistoryBasal.class, dataStore.isPushoverEnable() & dataStore.isPushoverEnableBasal())
                .add(PumpHistoryPattern.class, dataStore.isPushoverEnable() & dataStore.isPushoverEnableBasal())
                .add(PumpHistoryBG.class, dataStore.isPushoverEnable() & (dataStore.isPushoverEnableBG() | dataStore.isPushoverEnableCalibration()))
                .add(PumpHistoryMisc.class, dataStore.isPushoverEnable() & dataStore.isPushoverEnableConsumables())
                .add(PumpHistoryLoop.class, dataStore.isPushoverEnable() & (dataStore.isPushoverEnableAutoModeActive() | dataStore.isPushoverEnableAutoModeStop()))
                .add(PumpHistoryDaily.class, dataStore.isPushoverEnable() & dataStore.isPushoverEnableDailyTotals())
                .add(PumpHistoryAlarm.class, dataStore.isPushoverEnable())
                .add(PumpHistorySystem.class, dataStore.isPushoverEnable())

                .limiter(10)
                .process(backfill)
                .stale(7* 24 * 60 * 60000L)

                .opt(SENDEROPT.GLUCOSE_UNITS, true)

                .opt(SENDEROPT.BG_INFO, dataStore.isPushoverEnableBG())
                .opt(SENDEROPT.CALIBRATION_INFO, dataStore.isPushoverEnableCalibration())
                .opt(SENDEROPT.MISC_LIFETIMES, dataStore.isPushoverLifetimeInfo())
                .opt(SENDEROPT.MISC_SENSOR, dataStore.isPushoverEnableConsumables())
                .opt(SENDEROPT.MISC_BATTERY, dataStore.isPushoverEnableConsumables())
                .opt(SENDEROPT.MISC_CANNULA, dataStore.isPushoverEnableConsumables() & dataStore.isNsEnableReservoirChange())
                .opt(SENDEROPT.MISC_INSULIN, dataStore.isPushoverEnableConsumables() & dataStore.isNsEnableInsulinChange())
                .var(SENDEROPT.INSULIN_CHANGE_THRESHOLD, Integer.toString(dataStore.getNsInsulinChangeThreshold()))
                .var(SENDEROPT.CANNULA_CHANGE_THRESHOLD, Integer.toString(dataStore.getNsCannulaChangeThreshold()))

                .opt(SENDEROPT.ALARM_CLEARED, dataStore.isPushoverEnableCleared())
                .opt(SENDEROPT.ALARM_CLEARED_ALWAYS_UPDATE, dataStore.isPushoverEnableClearedAcknowledged())
                .opt(SENDEROPT.ALARM_SILENCED, dataStore.isPushoverEnableSilenced())
                .opt(SENDEROPT.ALARM_EXTENDED, dataStore.isPushoverEnableInfoExtended())
                .var(SENDEROPT.ALARM_PRIORITY, Integer.toString(PumpAlert.PRIORITY.NORMAL.value()))

                .opt(SENDEROPT.AUTOMODE_ACTIVE, dataStore.isPushoverEnableAutoModeActive())
                .opt(SENDEROPT.AUTOMODE_STOP, dataStore.isPushoverEnableAutoModeStop())
                .opt(SENDEROPT.AUTOMODE_EXIT, dataStore.isPushoverEnableAutoModeExit())
                .opt(SENDEROPT.AUTOMODE_MINMAX, dataStore.isPushoverEnableAutoModeMinMax())

                .opt(SENDEROPT.SYSTEM_PUMP_DEVICE_ERROR, dataStore.isPushoverEnableUploaderPumpErrors())
                .opt(SENDEROPT.SYSTEM_CNL_USB_ERROR, dataStore.isPushoverEnableUploaderUsbErrors())
                .opt(SENDEROPT.SYSTEM_PUMP_CONNECTTED, dataStore.isPushoverEnableUploaderStatusConnection())
                .var(SENDEROPT.SYSTEM_PUMP_CONNECTTED_AT, Integer.toString(30 * 60))
                .opt(SENDEROPT.SYSTEM_PUMP_LOST, dataStore.isPushoverEnableUploaderStatusConnection())
                .var(SENDEROPT.SYSTEM_PUMP_LOST_AT, Integer.toString(30 * 60))
                .var(SENDEROPT.SYSTEM_PUMP_LOST_REPEAT, Integer.toString(30 * 60))
                .opt(SENDEROPT.SYSTEM_ESTIMATE, dataStore.isPushoverEnableUploaderStatusEstimate())
                .var(SENDEROPT.SYSTEM_ESTIMATE_AT, Integer.toString(15 * 60))
                .var(SENDEROPT.SYSTEM_ESTIMATE_REPEAT, Integer.toString(60 * 60))

                .opt(SENDEROPT.SYSTEM_UPLOADER_BATTERY_LOW, dataStore.isPushoverEnableUploaderBattery() & dataStore.isPushoverEnableBatteryLow())
                .opt(SENDEROPT.SYSTEM_UPLOADER_BATTERY_VERY_LOW, dataStore.isPushoverEnableUploaderBattery() & dataStore.isPushoverEnableBatteryLow())
                .opt(SENDEROPT.SYSTEM_UPLOADER_BATTERY_CHARGED, dataStore.isPushoverEnableUploaderBattery() & dataStore.isPushoverEnableBatteryCharged())

                .var(SENDEROPT.DEVICE_NAME, dataStore.getNsDeviceName())
                .var(SENDEROPT.ENTERED_BY, dataStore.getNsEnteredBy())

                .list(SENDEROPT.BASAL_PATTERN, new String[]{dataStore.getNameBasalPattern1(), dataStore.getNameBasalPattern2(), dataStore.getNameBasalPattern3(), dataStore.getNameBasalPattern4(), dataStore.getNameBasalPattern5(), dataStore.getNameBasalPattern6(), dataStore.getNameBasalPattern7(), dataStore.getNameBasalPattern8()})
                .list(SENDEROPT.BASAL_PRESET, new String[]{dataStore.getNameTempBasalPreset1(), dataStore.getNameTempBasalPreset2(), dataStore.getNameTempBasalPreset3(), dataStore.getNameTempBasalPreset4(), dataStore.getNameTempBasalPreset5(), dataStore.getNameTempBasalPreset6(), dataStore.getNameTempBasalPreset7(), dataStore.getNameTempBasalPreset8()})
                .list(SENDEROPT.BOLUS_PRESET, new String[]{dataStore.getNameBolusPreset1(), dataStore.getNameBolusPreset2(), dataStore.getNameBolusPreset3(), dataStore.getNameBolusPreset4(), dataStore.getNameBolusPreset5(), dataStore.getNameBolusPreset6(), dataStore.getNameBolusPreset7(), dataStore.getNameBolusPreset8()});

        return this;
    }

    public enum SENDEROPT {
        NA,
        TREATMENTS,
        BASAL,
        BASAL_PATTERN_CHANGE,
        BOLUS,
        FORMAT_HTML,
        MEDTRONIC_TREND_STYLE,
        GLUCOSE_UNITS,

        ALARM_CLEARED,
        ALARM_CLEARED_ALWAYS_UPDATE,
        ALARM_SILENCED,
        ALARM_REPEATED,
        ALARM_EXTENDED,
        ALARM_PRIORITY,
        ALARM_FAULTCODE,
        ALARM_FAULTDATA,

        AUTOMODE_ACTIVE,
        AUTOMODE_STOP,
        AUTOMODE_EXIT,
        AUTOMODE_MINMAX,

        SYSTEM_UPLOADER_BATTERY_LOW,
        SYSTEM_UPLOADER_BATTERY_VERY_LOW,
        SYSTEM_UPLOADER_BATTERY_CHARGED,

        SYSTEM_UPLOADER_BATTERY,
        SYSTEM_CNL_USB_ERROR,
        SYSTEM_CNL_USB_ERROR_AT,
        SYSTEM_CNL_USB_ERROR_REPEAT,
        SYSTEM_PUMP_DEVICE_ERROR,
        SYSTEM_PUMP_DEVICE_ERROR_AT,
        SYSTEM_PUMP_DEVICE_ERROR_REPEAT,
        SYSTEM_PUMP_CONNECTTED,
        SYSTEM_PUMP_CONNECTTED_AT,
        SYSTEM_PUMP_LOST,
        SYSTEM_PUMP_LOST_AT,
        SYSTEM_PUMP_LOST_REPEAT,
        SYSTEM_ESTIMATE,
        SYSTEM_ESTIMATE_AT,
        SYSTEM_ESTIMATE_REPEAT,

        MISC_SENSOR,
        MISC_BATTERY,
        MISC_CANNULA,
        MISC_INSULIN,
        MISC_LIFETIMES,

        DAILY_TOTALS,

        BG_INFO,
        CALIBRATION_INFO,
        INSERT_BG_AS_CGM,
        INSULIN_CHANGE_THRESHOLD,
        CANNULA_CHANGE_THRESHOLD,

        PROFILE_OFFSET,

        GRAMS_PER_EXCHANGE,

        DEVICE_NAME,
        ENTERED_BY,

        BASAL_PATTERN,
        BASAL_PRESET,
        BOLUS_PRESET
    }

    public class Sender {

        private List<String> active = new ArrayList<>();
        private List<String> request = new ArrayList<>();

        private List<Pair<String, Long>> ttl = new ArrayList<>();

        private List<SENDEROPT> senderopts = new ArrayList<>();
        private List<Pair<SENDEROPT, String>> sendervars = new ArrayList<>();
        private List<Pair<SENDEROPT, String[]>> senderlists = new ArrayList<>();

        private String id = "";
        private long stale = 0;
        private long process = 0;
        private int limiter = 0;

        private Sender (String id) {
            this.id = id;
            senders.add(this);
        }

        private Sender limiter(int limiter) {
            this.limiter = limiter;
            return this;
        }

        private Sender stale(long time) {
            return this;
        }

        private Sender process(long time) {
            this.process = time;
            return this;
        }

        private Sender ttl(Class clazz, long time) {
            this.ttl.add(Pair.create(clazz.getSimpleName(), time));
            return this;
        }

        private Sender add(Class clazz) {
            this.active.add(clazz.getSimpleName());
            this.request.add(clazz.getSimpleName());
            return this;
        }

        private Sender add(Class clazz, boolean active) {
            if (active) this.active.add(clazz.getSimpleName());
            if (active) this.request.add(clazz.getSimpleName());
            return this;
        }

        private Sender add(Class clazz, boolean active, boolean request) {
            if (active) this.active.add(clazz.getSimpleName());
            if (active & request) this.request.add(clazz.getSimpleName());
            return this;
        }

        private Sender opt(SENDEROPT senderopt, boolean enable) {
            if (enable) this.senderopts.add(senderopt);
            return this;
        }

        private Sender var(SENDEROPT senderopt, String string) {
            this.sendervars.add(Pair.create(senderopt, string));
            return this;
        }

        private Sender list(SENDEROPT senderopt, String[] strings) {
            this.senderlists.add(Pair.create(senderopt, strings));
            return this;
        }

        public List<String> getRequest() {
            return request;
        }

        public List<String> getActive() {
            return active;
        }

        public List<Pair<String, Long>> getTtl() {
            return ttl;
        }

        public long getProcess() {
            return process;
        }

        public int getLimiter() {
            return limiter;
        }
    }

    public Sender getSender(String senderID) {
        for (Sender sender : senders) {
            if (sender.id.equals(senderID)) return sender;
        }
        return null;
    }

    public boolean isOpt(String senderID, SENDEROPT senderopt) {
        for (Sender sender : senders) {
            if (sender.id.equals(senderID) && sender.senderopts.contains(senderopt)) return true;
        }
        return false;
    }

    public String getVar(String senderID, SENDEROPT senderopt) {
        return getVar(senderID, senderopt, "");
    }

    public String getVar(String senderID, SENDEROPT senderopt, String defaultValue) {
        for (Sender sender : senders) {
            if (sender.id.equals(senderID)) {
                for (Pair<SENDEROPT, String> tupple : sender.sendervars) {
                    if (tupple.first == senderopt) return tupple.second;
                }
                break;
            }
        }
        return defaultValue;
    }

    public String getList(String senderID, SENDEROPT senderopt, int index) {
        return getList(senderID,senderopt, index, "");
    }

    public String getList(String senderID, SENDEROPT senderopt, int index, String defaultValue) {
        for (Sender sender : senders) {
            if (sender.id.equals(senderID)) {
                for (Pair<SENDEROPT, String[]> tupple : sender.senderlists) {
                    if (tupple.first == senderopt) return tupple.second[index];
                }
                break;
            }
        }
        return defaultValue;
    }

    // REQ/ACK setters must only be used as part of a open Realm transaction

    // set history record REQ for all associated senders
    public void setSenderREQ(PumpHistoryInterface record) {
        String req = record.getSenderREQ();
        String db = record.getClass().getSuperclass().getSimpleName();

        for (Sender sender : senders) {
            if (sender.active.contains(db) && !req.contains(sender.id))
                req = req.concat(sender.id);
        }

        record.setSenderREQ(req);
    }

    // set history record ACK for all associated senders
    public void setSenderACK(PumpHistoryInterface record) {
        String ack = record.getSenderACK();
        String db = record.getClass().getSuperclass().getSimpleName();

        for (Sender sender : senders) {
            if (sender.active.contains(db) && !ack.contains(sender.id))
                ack = ack.concat(sender.id);
        }

        record.setSenderACK(ack);
    }

}
