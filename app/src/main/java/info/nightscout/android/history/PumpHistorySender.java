package info.nightscout.android.history;

import android.util.Pair;

import java.util.ArrayList;
import java.util.List;

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

    private List<Sender> senders = new ArrayList<>();

    public PumpHistorySender() { }

    public PumpHistorySender buildSenders(DataStore dataStore) {

        boolean treatments = dataStore.isNsEnableTreatments() & dataStore.isNightscoutCareportal();

        new Sender("NS")
                .add(PumpHistoryCGM.class)
                .add(PumpHistoryBolus.class, true, treatments)
                .add(PumpHistoryBasal.class, true, treatments)
                .add(PumpHistoryPattern.class, true, treatments & dataStore.isNsEnablePatternChange())
                .add(PumpHistoryBG.class, true, treatments & dataStore.isNsEnableFingerBG())
                .add(PumpHistoryProfile.class, true, dataStore.isNsEnableProfileUpload())
                .add(PumpHistoryMisc.class, true, treatments)
                .add(PumpHistoryMarker.class, true, treatments)
                .add(PumpHistoryLoop.class, true, treatments)
                .add(PumpHistoryDaily.class, true, treatments & dataStore.isNsEnableDailyTotals())
                .add(PumpHistoryAlarm.class, true, treatments)
                .add(PumpHistorySystem.class, true, treatments)

                .ttl(PumpHistoryAlarm.class, dataStore.isNsEnableAlarms() ? dataStore.getNsAlarmTTL() * 60 * 60000L : 0)
                .ttl(PumpHistorySystem.class, dataStore.isNsEnableSystemStatus() ? dataStore.getNsAlarmTTL() * 60 * 60000L : 0)

                .limiter(2000)
                .process(dataStore.isNsEnableHistorySync() ? 180 * 24 * 60 * 60000L : System.currentTimeMillis() - dataStore.getNightscoutLimitDate().getTime())

                .opt(SENDEROPT.TREATMENTS, treatments)
                .opt(SENDEROPT.FORMAT_HTML, dataStore.isNsEnableFormatHTML())

                .opt(SENDEROPT.GLUCOSE_UNITS, true)

                .opt(SENDEROPT.ALARM_FAULTCODE, false)
                .opt(SENDEROPT.ALARM_FAULTDATA, true)
                .opt(SENDEROPT.ALARM_CLEARED, dataStore.isNsAlarmCleared())
                .opt(SENDEROPT.ALARM_SILENCED, true)
                .opt(SENDEROPT.ALARM_EXTENDED, dataStore.isNsAlarmExtended())
                .var(SENDEROPT.ALARM_PRIORITY, Integer.toString(MessageItem.PRIORITY.LOWEST.value()))

                .opt(SENDEROPT.SYSTEM_PUMP_DEVICE_ERROR, dataStore.isNsEnableSystemStatus())
                .opt(SENDEROPT.SYSTEM_CNL_USB_ERROR, dataStore.isNsEnableSystemStatus())
                /*
                makes lot of extra noise in NS when a user is in/out of range all day!
                .opt(SENDEROPT.SYSTEM_PUMP_CONNECTTED, dataStore.isNsEnableSystemStatus())
                .var(SENDEROPT.SYSTEM_PUMP_CONNECTTED_AT, Integer.toString(15 * 60))
                .opt(SENDEROPT.SYSTEM_PUMP_LOST, dataStore.isNsEnableSystemStatus())
                .var(SENDEROPT.SYSTEM_PUMP_LOST_AT, Integer.toString(15 * 60))
                .var(SENDEROPT.SYSTEM_PUMP_LOST_REPEAT, Integer.toString(30 * 60))
                 */

                .opt(SENDEROPT.SYSTEM_UPLOADER_BATTERY_LOW, dataStore.isNsEnableSystemStatus())
                .opt(SENDEROPT.SYSTEM_UPLOADER_BATTERY_VERY_LOW, dataStore.isNsEnableSystemStatus())
                .opt(SENDEROPT.SYSTEM_UPLOADER_BATTERY_CHARGED, dataStore.isNsEnableSystemStatus())

                .opt(SENDEROPT.BG_INFO, dataStore.isNsEnableFingerBG())
                .opt(SENDEROPT.CALIBRATION_INFO, dataStore.isNsEnableFingerBG() & dataStore.isNsEnableCalibrationInfo())
                .opt(SENDEROPT.MISC_LIFETIMES, dataStore.isNsEnableLifetimes())
                .opt(SENDEROPT.MISC_SENSOR, dataStore.isNsEnableSensorChange())
                .opt(SENDEROPT.MISC_BATTERY, dataStore.isNsEnableBatteryChange())
                .opt(SENDEROPT.MISC_CANNULA, dataStore.isNsEnableReservoirChange())
                .opt(SENDEROPT.MISC_INSULIN, dataStore.isNsEnableInsulinChange())
                .var(SENDEROPT.INSULIN_CLANGE_THRESHOLD, Integer.toString(dataStore.getNsInsulinChangeThreshold()))

                .opt(SENDEROPT.INSERT_BG_AS_CGM, dataStore.isNsEnableFingerBG() & dataStore.isNsEnableInsertBGasCGM())

                .opt(SENDEROPT.PROFILE_OFFSET, dataStore.isNsEnableProfileOffset())
                .var(SENDEROPT.GRAMS_PER_EXCHANGE, Integer.toString(dataStore.getNsGramsPerExchange()))

                .list(SENDEROPT.BASAL_PATTERN, new String[]{dataStore.getNameBasalPattern1(), dataStore.getNameBasalPattern2(), dataStore.getNameBasalPattern3(), dataStore.getNameBasalPattern4(), dataStore.getNameBasalPattern5(), dataStore.getNameBasalPattern6(), dataStore.getNameBasalPattern7(), dataStore.getNameBasalPattern8()})
                .list(SENDEROPT.BASAL_PRESET, new String[]{dataStore.getNameTempBasalPreset1(), dataStore.getNameTempBasalPreset2(), dataStore.getNameTempBasalPreset3(), dataStore.getNameTempBasalPreset4(), dataStore.getNameTempBasalPreset5(), dataStore.getNameTempBasalPreset6(), dataStore.getNameTempBasalPreset7(), dataStore.getNameTempBasalPreset8()})
                .list(SENDEROPT.BOLUS_PRESET, new String[]{dataStore.getNameBolusPreset1(), dataStore.getNameBolusPreset2(), dataStore.getNameBolusPreset3(), dataStore.getNameBolusPreset4(), dataStore.getNameBolusPreset5(), dataStore.getNameBolusPreset6(), dataStore.getNameBolusPreset7(), dataStore.getNameBolusPreset8()});

        new Sender("XD")
                .add(PumpHistoryCGM.class)
                .limiter(500)
                .process(7 * 24 * 60 * 60000L)
                .stale(7* 24 * 60 * 60000L);

        new Sender("PO")
                .add(PumpHistoryBolus.class, dataStore.isPushoverEnable() & dataStore.isPushoverEnableBolus())
                .add(PumpHistoryBasal.class, dataStore.isPushoverEnable() & dataStore.isPushoverEnableBasal())
                .add(PumpHistoryPattern.class, dataStore.isPushoverEnable() & dataStore.isPushoverEnableBasal())
                .add(PumpHistoryBG.class, dataStore.isPushoverEnable() & (dataStore.isPushoverEnableBG() | dataStore.isPushoverEnableCalibration()))
                .add(PumpHistoryMisc.class, dataStore.isPushoverEnable() & dataStore.isPushoverEnableConsumables())
                .add(PumpHistoryDaily.class, dataStore.isPushoverEnable() & dataStore.isPushoverEnableDailyTotals())
                .add(PumpHistoryAlarm.class, dataStore.isPushoverEnable())
                .add(PumpHistorySystem.class, dataStore.isPushoverEnable())

                .limiter(7)
                .process(4 * 60 * 60000L)
                .stale(7* 24 * 60 * 60000L)

                .opt(SENDEROPT.GLUCOSE_UNITS, true)

                .opt(SENDEROPT.BG_INFO, dataStore.isPushoverEnableBG())
                .opt(SENDEROPT.CALIBRATION_INFO, dataStore.isPushoverEnableCalibration())
                .opt(SENDEROPT.MISC_LIFETIMES, dataStore.isPushoverLifetimeInfo())
                .opt(SENDEROPT.MISC_SENSOR, dataStore.isPushoverEnableConsumables())
                .opt(SENDEROPT.MISC_BATTERY, dataStore.isPushoverEnableConsumables())
                .opt(SENDEROPT.MISC_CANNULA, dataStore.isPushoverEnableConsumables() & dataStore.isNsEnableReservoirChange())
                .opt(SENDEROPT.MISC_INSULIN, dataStore.isPushoverEnableConsumables() & dataStore.isNsEnableInsulinChange())
                .var(SENDEROPT.INSULIN_CLANGE_THRESHOLD, Integer.toString(dataStore.getNsInsulinChangeThreshold()))

                .opt(SENDEROPT.ALARM_CLEARED, dataStore.isPushoverEnableCleared())
                .opt(SENDEROPT.ALARM_SILENCED, dataStore.isPushoverEnableSilenced())
                .opt(SENDEROPT.ALARM_EXTENDED, dataStore.isPushoverInfoExtended())
                .var(SENDEROPT.ALARM_PRIORITY, Integer.toString(MessageItem.PRIORITY.NORMAL.value()))

                .opt(SENDEROPT.SYSTEM_PUMP_DEVICE_ERROR, dataStore.isPushoverEnableUploaderPumpErrors())
                .opt(SENDEROPT.SYSTEM_CNL_USB_ERROR, dataStore.isPushoverEnableUploaderPumpConnection())
                .opt(SENDEROPT.SYSTEM_PUMP_CONNECTTED, dataStore.isPushoverEnableUploaderPumpConnection())
                .var(SENDEROPT.SYSTEM_PUMP_CONNECTTED_AT, Integer.toString(15 * 60))
                .opt(SENDEROPT.SYSTEM_PUMP_LOST, dataStore.isPushoverEnableUploaderPumpConnection())
                .var(SENDEROPT.SYSTEM_PUMP_LOST_AT, Integer.toString(15 * 60))
                .var(SENDEROPT.SYSTEM_PUMP_LOST_REPEAT, Integer.toString(30 * 60))

                .opt(SENDEROPT.SYSTEM_UPLOADER_BATTERY_LOW, dataStore.isPushoverEnableUploaderBattery() & dataStore.isPushoverEnableBatteryLow())
                .opt(SENDEROPT.SYSTEM_UPLOADER_BATTERY_VERY_LOW, dataStore.isPushoverEnableUploaderBattery() & dataStore.isPushoverEnableBatteryLow())
                .opt(SENDEROPT.SYSTEM_UPLOADER_BATTERY_CHARGED, dataStore.isPushoverEnableUploaderBattery() & dataStore.isPushoverEnableBatteryCharged())

                .list(SENDEROPT.BASAL_PATTERN, new String[]{dataStore.getNameBasalPattern1(), dataStore.getNameBasalPattern2(), dataStore.getNameBasalPattern3(), dataStore.getNameBasalPattern4(), dataStore.getNameBasalPattern5(), dataStore.getNameBasalPattern6(), dataStore.getNameBasalPattern7(), dataStore.getNameBasalPattern8()})
                .list(SENDEROPT.BASAL_PRESET, new String[]{dataStore.getNameTempBasalPreset1(), dataStore.getNameTempBasalPreset2(), dataStore.getNameTempBasalPreset3(), dataStore.getNameTempBasalPreset4(), dataStore.getNameTempBasalPreset5(), dataStore.getNameTempBasalPreset6(), dataStore.getNameTempBasalPreset7(), dataStore.getNameTempBasalPreset8()})
                .list(SENDEROPT.BOLUS_PRESET, new String[]{dataStore.getNameBolusPreset1(), dataStore.getNameBolusPreset2(), dataStore.getNameBolusPreset3(), dataStore.getNameBolusPreset4(), dataStore.getNameBolusPreset5(), dataStore.getNameBolusPreset6(), dataStore.getNameBolusPreset7(), dataStore.getNameBolusPreset8()});
        ;

        return this;
    }

    public enum SENDEROPT {
        NA,
        TREATMENTS,
        FORMAT_HTML,
        GLUCOSE_UNITS,

        ALARM_CLEARED,
        ALARM_SILENCED,
        ALARM_EXTENDED,
        ALARM_PRIORITY,
        ALARM_FAULTCODE,
        ALARM_FAULTDATA,

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

        MISC_SENSOR,
        MISC_BATTERY,
        MISC_CANNULA,
        MISC_INSULIN,
        MISC_LIFETIMES,

        BG_INFO,
        CALIBRATION_INFO,
        INSERT_BG_AS_CGM,
        INSULIN_CLANGE_THRESHOLD,

        PROFILE_OFFSET,

        GRAMS_PER_EXCHANGE,

        BASAL_PATTERN,
        BASAL_PRESET,
        BOLUS_PRESET
    }

    public class Sender {

        List<String> active = new ArrayList<>();
        List<String> request = new ArrayList<>();

        List<Pair<String, Long>> ttl = new ArrayList<>();

        List<SENDEROPT> senderopts = new ArrayList<>();
        List<Pair<SENDEROPT, String>> sendervars = new ArrayList<>();
        List<Pair<SENDEROPT, String[]>> senderlists = new ArrayList<>();

        String id = "";
        long stale = 0;
        long process = 0;
        int limiter = 0;

        Sender (String id) {
            this.id = id;
            senders.add(this);
        }

        Sender limiter(int limiter) {
            this.limiter = limiter;
            return this;
        }

        public Sender stale(long time) {
            return this;
        }

        public Sender process(long time) {
            this.process = time;
            return this;
        }

        public Sender ttl(Class clazz, long time) {
            this.ttl.add(Pair.create(clazz.getSimpleName(), time));
            return this;
        }

        public Sender add(Class clazz) {
            this.active.add(clazz.getSimpleName());
            this.request.add(clazz.getSimpleName());
            return this;
        }

        public Sender add(Class clazz, boolean active) {
            if (active) this.active.add(clazz.getSimpleName());
            if (active) this.request.add(clazz.getSimpleName());
            return this;
        }

        public Sender add(Class clazz, boolean active, boolean request) {
            if (active) this.active.add(clazz.getSimpleName());
            if (active & request) this.request.add(clazz.getSimpleName());
            return this;
        }

        public Sender opt(SENDEROPT senderopt, boolean enable) {
            if (enable) this.senderopts.add(senderopt);
            return this;
        }

        public Sender var(SENDEROPT senderopt, String string) {
            this.sendervars.add(Pair.create(senderopt, string));
            return this;
        }

        public Sender list(SENDEROPT senderopt, String[] strings) {
            this.senderlists.add(Pair.create(senderopt, strings));
            return this;
        }
    }

    public Sender getSender(String senderID) {
        for (Sender sender : senders) {
            if (sender.id.equals(senderID)) return sender;
        }
        return null;
    }

    public boolean senderOpt(String senderID, SENDEROPT senderopt) {
        for (Sender sender : senders) {
            if (sender.id.equals(senderID) && sender.senderopts.contains(senderopt)) return true;
        }
        return false;
    }

    public String senderVar(String senderID, SENDEROPT senderopt) {
        return senderVar(senderID, senderopt, "");
    }

    public String senderVar(String senderID, SENDEROPT senderopt, String defaultValue) {
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

    public String senderList(String senderID, SENDEROPT senderopt, int index) {
        return senderList(senderID,senderopt, index, "");
    }

    public String senderList(String senderID, SENDEROPT senderopt, int index, String defaultValue) {
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

    // set history record REQ for all associated senders
    public void setSenderREQ(PumpHistoryInterface record) {
        String req = record.getSenderREQ();
        String db = record.getClass().getSuperclass().getSimpleName();

        for (Sender sender : senders) {
            if (sender.active.contains(db))
                if (!req.contains(sender.id)) req += sender.id;
        }

        record.setSenderREQ(req);
    }

    // set history record ACK for all associated senders
    public void setSenderACK(PumpHistoryInterface record) {
        String ack = record.getSenderACK();
        String db = record.getClass().getSuperclass().getSimpleName();

        for (Sender sender : senders) {
            if (sender.active.contains(db))
                if (!ack.contains(sender.id)) ack += sender.id;
        }

        record.setSenderACK(ack);
    }

}
