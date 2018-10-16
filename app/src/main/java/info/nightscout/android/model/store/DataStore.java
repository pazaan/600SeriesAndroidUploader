package info.nightscout.android.model.store;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Date;

import info.nightscout.android.R;
import info.nightscout.android.medtronic.service.MedtronicCnlService;
import io.realm.RealmObject;
import io.realm.annotations.Index;

public class DataStore extends RealmObject {
    @Index
    private long timestamp;

    // do not send cgm/pump backfill data prior to this date
    // used to stop overwriting older NS entries
    // user option to override (we clear old data from NS to stop multiple entries and refresh using keys)
    private Date nightscoutLimitDate = null;
    private long nightscoutCgmCleanFrom;
    private long nightscoutPumpCleanFrom;

    private boolean nightscoutUpload = false;
    private String nightscoutURL = "";
    private String nightscoutSECRET = "";
    private long nightscoutReportTime = 0;
    private boolean nightscoutAvailable = false;
    private boolean nightscoutCareportal = false;

    private boolean requestProfile = false;
    private boolean requestPumpHistory = false;
    private boolean requestCgmHistory = false;
    private boolean requestEstimate = false;

    private String lastStatReport;

    private int pumpCgmNA;

    private int commsSuccess;
    private int commsError;
    private int commsConnectError;
    private int commsSignalError;
    private int commsSgvSuccess;
    private int pumpLostSensorError;
    private int pumpClockError;
    private int pumpBatteryError;

    // user preferences

    private boolean mmolxl;
    private boolean mmolxlDecimals;
    private long pollInterval;
    private long lowBatPollInterval;
    private boolean doublePollOnPumpAway;

    private boolean enableXdripPlusUpload;
    private boolean xdripPlusUploadAvailable;

    private boolean sysEnableCgmHistory;
    private int sysCgmHistoryDays;
    private boolean sysEnablePumpHistory;
    private int sysPumpHistoryDays;
    private int sysPumpHistoryFrequency;
    private boolean sysEnableEstimateSGV;
    private boolean sysEnableClashProtect;
    private boolean sysEnablePollOverride;
    private long sysPollGracePeriod;
    private long sysPollRecoveryPeriod;
    private long sysPollWarmupPeriod;
    private long sysPollErrorRetry;
    private long sysPollOldSgvRetry;
    private boolean sysEnableWait500ms;
    private boolean sysEnableUsbPermissionDialog;

    private boolean dbgEnableExtendedErrors;
    private boolean dbgEnableUploadErrors;

    private boolean nsEnableTreatments;
    private boolean nsEnableHistorySync;
    private boolean nsEnableFingerBG;
    private boolean nsEnableCalibrationInfo;
    private boolean nsEnableSensorChange;
    private boolean nsEnableReservoirChange;
    private boolean nsEnableInsulinChange;
    private int nsInsulinChangeThreshold;
    private boolean nsEnableBatteryChange;
    private boolean nsEnableLifetimes;
    private boolean nsEnableProfileUpload;
    private boolean nsEnableProfileSingle;
    private boolean nsEnableProfileOffset;
    private int nsProfileDefault;
    private float nsActiveInsulinTime;
    private boolean nsEnablePatternChange;
    private int nsGramsPerExchange;
    private boolean nsGramsPerExchangeChanged = false;
    private boolean nsEnableInsertBGasCGM;
    private boolean nsEnableAlarms;
    private boolean nsAlarmExtended;
    private boolean nsAlarmCleared;
    private boolean nsEnableSystemStatus;
    private int nsAlarmTTL;
    private boolean nsEnableDailyTotals;
    private boolean nsEnableFormatHTML;

    private boolean urchinEnable;
    private int urchinBasalPeriod;
    private int urchinBasalScale;
    private boolean urchinBolusGraph;
    private boolean urchinBolusTags;
    private int urchinBolusPop;
    private int urchinTimeStyle;
    private int urchinDurationStyle;
    private int urchinUnitsStyle;
    private int urchinBatteyStyle;
    private int urchinConcatenateStyle;
    private String urchinCustomText1;
    private String urchinCustomText2;
    private byte[] urchinStatusLayout;

    private String nameBasalPattern1;
    private String nameBasalPattern2;
    private String nameBasalPattern3;
    private String nameBasalPattern4;
    private String nameBasalPattern5;
    private String nameBasalPattern6;
    private String nameBasalPattern7;
    private String nameBasalPattern8;
    private String nameTempBasalPreset1;
    private String nameTempBasalPreset2;
    private String nameTempBasalPreset3;
    private String nameTempBasalPreset4;
    private String nameTempBasalPreset5;
    private String nameTempBasalPreset6;
    private String nameTempBasalPreset7;
    private String nameTempBasalPreset8;
    private String nameBolusPreset1;
    private String nameBolusPreset2;
    private String nameBolusPreset3;
    private String nameBolusPreset4;
    private String nameBolusPreset5;
    private String nameBolusPreset6;
    private String nameBolusPreset7;
    private String nameBolusPreset8;

    private boolean nameBasalPatternChanged = false;

    private boolean pushoverEnable;
    private String pushoverAPItoken;
    private String pushoverUSERtoken;

    private boolean pushoverValidated;
    private boolean pushoverError;
    private String pushoverAPItokenCheck;
    private String pushoverUSERtokenCheck;
    private long pushoverAppLimit;
    private long pushoverAppRemaining;
    private long pushoverAppReset;

    private boolean pushoverEnableOnHigh;
    private String pushoverPriorityOnHigh;
    private String pushoverSoundOnHigh;
    private boolean pushoverEnableOnLow;
    private String pushoverPriorityOnLow;
    private String pushoverSoundOnLow;
    private boolean pushoverEnableBeforeHigh;
    private String pushoverPriorityBeforeHigh;
    private String pushoverSoundBeforeHigh;
    private boolean pushoverEnableBeforeLow;
    private String pushoverPriorityBeforeLow;
    private String pushoverSoundBeforeLow;
    private boolean pushoverEnableAutoModeExit;
    private String pushoverPriorityAutoModeExit;
    private String pushoverSoundAutoModeExit;

    private boolean pushoverEnablePumpEmergency;
    private String pushoverPriorityPumpEmergency;
    private String pushoverSoundPumpEmergency;
    private boolean pushoverEnablePumpActionable;
    private String pushoverPriorityPumpActionable;
    private String pushoverSoundPumpActionable;
    private boolean pushoverEnablePumpInformational;
    private String pushoverPriorityPumpInformational;
    private String pushoverSoundPumpInformational;
    private boolean pushoverEnablePumpReminder;
    private String pushoverPriorityPumpReminder;
    private String pushoverSoundPumpReminder;

    private boolean pushoverEnableBolus;
    private String pushoverPriorityBolus;
    private String pushoverSoundBolus;
    private boolean pushoverEnableBasal;
    private String pushoverPriorityBasal;
    private String pushoverSoundBasal;
    private boolean pushoverEnableSuspendResume;
    private String pushoverPrioritySuspendResume;
    private String pushoverSoundSuspendResume;
    private boolean pushoverEnableBG;
    private String pushoverPriorityBG;
    private String pushoverSoundBG;
    private boolean pushoverEnableCalibration;
    private String pushoverPriorityCalibration;
    private String pushoverSoundCalibration;
    private boolean pushoverEnableConsumables;
    private String pushoverPriorityConsumables;
    private String pushoverSoundConsumables;
    private boolean pushoverLifetimeInfo;
    private boolean pushoverEnableDailyTotals;
    private String pushoverPriorityDailyTotals;
    private String pushoverSoundDailyTotals;

    private boolean pushoverEnableUploaderPumpErrors;
    private String pushoverPriorityUploaderPumpErrors;
    private String pushoverSoundUploaderPumpErrors;
    private boolean pushoverEnableUploaderPumpConnection;
    private String pushoverPriorityUploaderPumpConnection;
    private String pushoverSoundUploaderPumpConnection;
    private boolean pushoverEnableUploaderBattery;
    private String pushoverPriorityUploaderBattery;
    private String pushoverSoundUploaderBattery;
    private boolean pushoverEnableBatteryLow;
    private boolean pushoverEnableBatteryCharged;

    private boolean pushoverEnableCleared;
    private String pushoverPriorityCleared;
    private String pushoverSoundCleared;
    private boolean pushoverEnableSilenced;
    private String pushoverPrioritySilenced;
    private String pushoverSoundSilenced;

    private boolean pushoverInfoExtended;
    private boolean pushoverTitleTime;
    private String pushoverEmergencyRetry;
    private String pushoverEmergencyExpire;

    private boolean pushoverEnablePriorityOverride;
    private String pushoverPriorityOverride;
    private boolean pushoverEnableSoundOverride;
    private String pushoverSoundOverride;

    private byte bump;

    public DataStore() {
        this.timestamp = new Date().getTime();
    }

    public void copyPrefs(Context context, SharedPreferences sharedPreferences)
    {
        mmolxl = sharedPreferences.getBoolean("mmolxl", false);
        mmolxlDecimals = sharedPreferences.getBoolean("mmolDecimals", false);
        pollInterval = Long.parseLong(sharedPreferences.getString("pollInterval",
                Long.toString(MedtronicCnlService.POLL_PERIOD_MS)));
        lowBatPollInterval = Long.parseLong(sharedPreferences.getString("lowBatPollInterval",
                Long.toString(MedtronicCnlService.LOW_BATTERY_POLL_PERIOD_MS)));
        doublePollOnPumpAway = sharedPreferences.getBoolean("doublePollOnPumpAway", false);

        nightscoutUpload = sharedPreferences.getBoolean("EnableRESTUpload", false);
        nightscoutURL = sharedPreferences.getString(context.getString(R.string.preference_nightscout_url), "");
        nightscoutSECRET = sharedPreferences.getString(context.getString(R.string.preference_api_secret), "YOURAPISECRET");

        enableXdripPlusUpload = sharedPreferences.getBoolean(context.getString(R.string.preference_enable_xdrip_plus), false);

        // system
        sysEnableCgmHistory = sharedPreferences.getBoolean("sysEnableCgmHistory", true);
        sysCgmHistoryDays = Integer.parseInt(sharedPreferences.getString("sysCgmHistoryDays", "7"));
        sysEnablePumpHistory = sharedPreferences.getBoolean("sysEnablePumpHistory", true);
        sysPumpHistoryDays = Integer.parseInt(sharedPreferences.getString("sysPumpHistoryDays", "7"));
        sysPumpHistoryFrequency = Integer.parseInt(sharedPreferences.getString("sysPumpHistoryFrequency", "90"));
        sysEnableEstimateSGV = sharedPreferences.getBoolean("sysEnableEstimateSGV", false);
        sysEnableClashProtect = sharedPreferences.getBoolean("sysEnableClashProtect", true);
        sysEnablePollOverride = sharedPreferences.getBoolean("sysEnablePollOverride", false);
        sysPollGracePeriod = Long.parseLong(sharedPreferences.getString("sysPollGracePeriod", "30000"));
        sysPollRecoveryPeriod = Long.parseLong(sharedPreferences.getString("sysPollRecoveryPeriod", "90000"));
        sysPollWarmupPeriod = Long.parseLong(sharedPreferences.getString("sysPollWarmupPeriod", "90000"));
        sysPollErrorRetry = Long.parseLong(sharedPreferences.getString("sysPollErrorRetry", "90000"));
        sysPollOldSgvRetry = Long.parseLong(sharedPreferences.getString("sysPollOldSgvRetry", "90000"));
        sysEnableWait500ms = sharedPreferences.getBoolean("sysEnableWait500ms", false);
        sysEnableUsbPermissionDialog = sharedPreferences.getBoolean("sysEnableUsbPermissionDialog", false);

        // debug
        dbgEnableExtendedErrors = sharedPreferences.getBoolean("dbgEnableExtendedErrors", false);
        dbgEnableUploadErrors = sharedPreferences.getBoolean("dbgEnableUploadErrors", true);

        // nightscout
        nsEnableTreatments = sharedPreferences.getBoolean("nsEnableTreatments", true);
        nsEnableHistorySync = sharedPreferences.getBoolean("nsEnableHistorySync", false);
        nsEnableFingerBG = sharedPreferences.getBoolean("nsEnableFingerBG", true);
        nsEnableCalibrationInfo = sharedPreferences.getBoolean("nsEnableCalibrationInfo", false);
        nsEnableSensorChange = sharedPreferences.getBoolean("nsEnableSensorChange", true);
        nsEnableReservoirChange = sharedPreferences.getBoolean("nsEnableReservoirChange", true);
        nsEnableInsulinChange = sharedPreferences.getBoolean("nsEnableInsulinChange", false);
        nsInsulinChangeThreshold = Integer.parseInt(sharedPreferences.getString("nsInsulinChangeThreshold", "0"));
        nsEnableBatteryChange = sharedPreferences.getBoolean("nsEnableBatteryChange", true);
        nsEnableLifetimes = sharedPreferences.getBoolean("nsEnableLifetimes", false);
        nsEnableProfileUpload = sharedPreferences.getBoolean("nsEnableProfileUpload", true);
        nsEnableProfileSingle = sharedPreferences.getBoolean("nsEnableProfileSingle", true);
        nsEnableProfileOffset = sharedPreferences.getBoolean("nsEnableProfileOffset", true);
        nsProfileDefault = Integer.parseInt(sharedPreferences.getString("nsProfileDefault", "0"));
        nsActiveInsulinTime = Float.parseFloat(sharedPreferences.getString("nsActiveInsulinTime", "3"));
        nsEnablePatternChange = sharedPreferences.getBoolean("nsEnablePatternChange", true);
        nsEnableInsertBGasCGM = sharedPreferences.getBoolean("nsEnableInsertBGasCGM", false);
        nsEnableAlarms = sharedPreferences.getBoolean("nsEnableAlarms", false);
        nsAlarmExtended = sharedPreferences.getBoolean("nsAlarmExtended", true);
        nsAlarmCleared = sharedPreferences.getBoolean("nsAlarmCleared", true);
        nsEnableSystemStatus = sharedPreferences.getBoolean("nsEnableSystemStatus", false);
        nsAlarmTTL = Integer.parseInt(sharedPreferences.getString("nsAlarmTTL", "24"));
        nsEnableDailyTotals = sharedPreferences.getBoolean("nsEnableDailyTotals", true);
        nsEnableFormatHTML = sharedPreferences.getBoolean("nsEnableFormatHTML", true);

        int nsGramsPerExchange = Integer.parseInt(sharedPreferences.getString("nsGramsPerExchange", "15"));
        if (this.nsGramsPerExchange != 0 && this.nsGramsPerExchange != nsGramsPerExchange)
            nsGramsPerExchangeChanged = true;
        this.nsGramsPerExchange = nsGramsPerExchange;

        nameBasalPattern1 = checkBasalPattern(nameBasalPattern1, sharedPreferences.getString("nameBasalPattern1", context.getString(R.string.BASAL_PATTERN_1)));
        nameBasalPattern2 = checkBasalPattern(nameBasalPattern2, sharedPreferences.getString("nameBasalPattern2", context.getString(R.string.BASAL_PATTERN_2)));
        nameBasalPattern3 = checkBasalPattern(nameBasalPattern3, sharedPreferences.getString("nameBasalPattern3", context.getString(R.string.BASAL_PATTERN_3)));
        nameBasalPattern4 = checkBasalPattern(nameBasalPattern4, sharedPreferences.getString("nameBasalPattern4", context.getString(R.string.BASAL_PATTERN_4)));
        nameBasalPattern5 = checkBasalPattern(nameBasalPattern5, sharedPreferences.getString("nameBasalPattern5", context.getString(R.string.BASAL_PATTERN_5)));
        nameBasalPattern6 = checkBasalPattern(nameBasalPattern6, sharedPreferences.getString("nameBasalPattern6", context.getString(R.string.BASAL_PATTERN_6)));
        nameBasalPattern7 = checkBasalPattern(nameBasalPattern7, sharedPreferences.getString("nameBasalPattern7", context.getString(R.string.BASAL_PATTERN_7)));
        nameBasalPattern8 = checkBasalPattern(nameBasalPattern8, sharedPreferences.getString("nameBasalPattern8", context.getString(R.string.BASAL_PATTERN_8)));
        nameTempBasalPreset1 = sharedPreferences.getString("nameTempBasalPreset1", context.getString(R.string.TEMP_BASAL_PRESET_0));
        nameTempBasalPreset2 = sharedPreferences.getString("nameTempBasalPreset2", context.getString(R.string.TEMP_BASAL_PRESET_1));
        nameTempBasalPreset3 = sharedPreferences.getString("nameTempBasalPreset3", context.getString(R.string.TEMP_BASAL_PRESET_2));
        nameTempBasalPreset4 = sharedPreferences.getString("nameTempBasalPreset4", context.getString(R.string.TEMP_BASAL_PRESET_3));
        nameTempBasalPreset5 = sharedPreferences.getString("nameTempBasalPreset5", context.getString(R.string.TEMP_BASAL_PRESET_4));
        nameTempBasalPreset6 = sharedPreferences.getString("nameTempBasalPreset6", context.getString(R.string.TEMP_BASAL_PRESET_5));
        nameTempBasalPreset7 = sharedPreferences.getString("nameTempBasalPreset7", context.getString(R.string.TEMP_BASAL_PRESET_6));
        nameTempBasalPreset8 = sharedPreferences.getString("nameTempBasalPreset8", context.getString(R.string.TEMP_BASAL_PRESET_7));
        nameBolusPreset1 = sharedPreferences.getString("nameBolusPreset1", context.getString(R.string.BOLUS_PRESET_0));
        nameBolusPreset2 = sharedPreferences.getString("nameBolusPreset2", context.getString(R.string.BOLUS_PRESET_1));
        nameBolusPreset3 = sharedPreferences.getString("nameBolusPreset3", context.getString(R.string.BOLUS_PRESET_2));
        nameBolusPreset4 = sharedPreferences.getString("nameBolusPreset4", context.getString(R.string.BOLUS_PRESET_3));
        nameBolusPreset5 = sharedPreferences.getString("nameBolusPreset5", context.getString(R.string.BOLUS_PRESET_4));
        nameBolusPreset6 = sharedPreferences.getString("nameBolusPreset6", context.getString(R.string.BOLUS_PRESET_5));
        nameBolusPreset7 = sharedPreferences.getString("nameBolusPreset7", context.getString(R.string.BOLUS_PRESET_6));
        nameBolusPreset8 = sharedPreferences.getString("nameBolusPreset8", context.getString(R.string.BOLUS_PRESET_7));

        // urchin
        urchinEnable = sharedPreferences.getBoolean("urchinEnable", false);
        urchinBasalPeriod = Integer.parseInt(sharedPreferences.getString("urchinBasalPeriod", "23"));
        urchinBasalScale = Integer.parseInt(sharedPreferences.getString("urchinBasalScale", "0"));
        urchinBolusGraph = sharedPreferences.getBoolean("urchinBolusGraph", false);
        urchinBolusTags = sharedPreferences.getBoolean("urchinBolusTags", false);
        urchinBolusPop = Integer.parseInt(sharedPreferences.getString("urchinBolusPop", "0"));
        urchinTimeStyle = Integer.parseInt(sharedPreferences.getString("urchinTimeStyle", "1"));
        urchinDurationStyle = Integer.parseInt(sharedPreferences.getString("urchinDurationStyle", "1"));
        urchinUnitsStyle = Integer.parseInt(sharedPreferences.getString("urchinUnitsStyle", "1"));
        urchinBatteyStyle = Integer.parseInt(sharedPreferences.getString("urchinBatteyStyle", "1"));
        urchinConcatenateStyle = Integer.parseInt(sharedPreferences.getString("urchinConcatenateStyle", "2"));
        urchinCustomText1 = sharedPreferences.getString("urchinCustomText1", "");
        urchinCustomText2 = sharedPreferences.getString("urchinCustomText2", "");

        int count = 20;
        byte[] urchinStatusLayout = new byte[count];
        for (int i=0; i < count; i++) {
            urchinStatusLayout[i] = Byte.parseByte(sharedPreferences.getString("urchinStatusLayout" + (i + 1), "0"));
        }
        this.urchinStatusLayout = urchinStatusLayout;

        // pushover
        pushoverEnable = sharedPreferences.getBoolean("pushoverEnable", false);
        pushoverAPItoken = sharedPreferences.getString("pushoverAPItoken", "");
        pushoverUSERtoken = sharedPreferences.getString("pushoverUSERtoken", "");

        if (!pushoverEnable) {
            // will force a validation check when pushover re-enabled
            pushoverAPItokenCheck = "";
            pushoverUSERtokenCheck = "";
        }

        pushoverEnableOnHigh = sharedPreferences.getBoolean("pushoverEnableOnHigh", true);
        pushoverPriorityOnHigh = sharedPreferences.getString("pushoverPriorityOnHigh", "emergency");
        pushoverSoundOnHigh = sharedPreferences.getString("pushoverSoundOnHigh", "persistent");
        pushoverEnableOnLow = sharedPreferences.getBoolean("pushoverEnableOnLow", true);
        pushoverPriorityOnLow = sharedPreferences.getString("pushoverPriorityOnLow", "emergency");
        pushoverSoundOnLow = sharedPreferences.getString("pushoverSoundOnLow", "persistent");
        pushoverEnableBeforeHigh = sharedPreferences.getBoolean("pushoverEnableBeforeHigh", true);
        pushoverPriorityBeforeHigh = sharedPreferences.getString("pushoverPriorityBeforeHigh", "high");
        pushoverSoundBeforeHigh = sharedPreferences.getString("pushoverSoundBeforeHigh", "updown");
        pushoverEnableBeforeLow = sharedPreferences.getBoolean("pushoverEnableBeforeLow", true);
        pushoverPriorityBeforeLow = sharedPreferences.getString("pushoverPriorityBeforeLow", "high");
        pushoverSoundBeforeLow = sharedPreferences.getString("pushoverSoundBeforeLow", "updown");
        pushoverEnableAutoModeExit = sharedPreferences.getBoolean("pushoverEnableAutoModeExit", true);
        pushoverPriorityAutoModeExit = sharedPreferences.getString("pushoverPriorityAutoModeExit", "high");
        pushoverSoundAutoModeExit = sharedPreferences.getString("pushoverSoundAutoModeExit", "updown");

        pushoverEnablePumpEmergency = sharedPreferences.getBoolean("pushoverEnablePumpEmergency", true);
        pushoverPriorityPumpEmergency = sharedPreferences.getString("pushoverPriorityPumpEmergency", "emergency");
        pushoverSoundPumpEmergency = sharedPreferences.getString("pushoverSoundPumpEmergency", "persistent");
        pushoverEnablePumpActionable = sharedPreferences.getBoolean("pushoverEnablePumpActionable", true);
        pushoverPriorityPumpActionable = sharedPreferences.getString("pushoverPriorityPumpActionable", "high");
        pushoverSoundPumpActionable = sharedPreferences.getString("pushoverSoundPumpActionable", "updown");
        pushoverEnablePumpInformational = sharedPreferences.getBoolean("pushoverEnablePumpInformational", true);
        pushoverPriorityPumpInformational = sharedPreferences.getString("pushoverPriorityPumpInformational", "normal");
        pushoverSoundPumpInformational = sharedPreferences.getString("pushoverSoundPumpInformational", "bike");
        pushoverEnablePumpReminder = sharedPreferences.getBoolean("pushoverEnablePumpReminder", true);
        pushoverPriorityPumpReminder = sharedPreferences.getString("pushoverPriorityPumpReminder", "normal");
        pushoverSoundPumpReminder = sharedPreferences.getString("pushoverSoundPumpReminder", "tugboat");

        pushoverEnableBolus = sharedPreferences.getBoolean("pushoverEnableBolus", true);
        pushoverPriorityBolus = sharedPreferences.getString("pushoverPriorityBolus", "normal");
        pushoverSoundBolus = sharedPreferences.getString("pushoverSoundBolus", "classical");
        pushoverEnableBasal = sharedPreferences.getBoolean("pushoverEnableBasal", true);
        pushoverPriorityBasal = sharedPreferences.getString("pushoverPriorityBasal", "normal");
        pushoverSoundBasal = sharedPreferences.getString("pushoverSoundBasal", "pianobar");
        pushoverEnableSuspendResume = sharedPreferences.getBoolean("pushoverEnableSuspendResume", true);
        pushoverPrioritySuspendResume = sharedPreferences.getString("pushoverPrioritySuspendResume", "normal");
        pushoverSoundSuspendResume = sharedPreferences.getString("pushoverSoundSuspendResume", "gamelan");
        pushoverEnableBG = sharedPreferences.getBoolean("pushoverEnableBG", true);
        pushoverPriorityBG = sharedPreferences.getString("pushoverPriorityBG", "normal");
        pushoverSoundBG = sharedPreferences.getString("pushoverSoundBG", "bike");
        pushoverEnableCalibration = sharedPreferences.getBoolean("pushoverEnableCalibration", false);
        pushoverPriorityCalibration = sharedPreferences.getString("pushoverPriorityCalibration", "normal");
        pushoverSoundCalibration = sharedPreferences.getString("pushoverSoundCalibration", "bike");
        pushoverEnableConsumables = sharedPreferences.getBoolean("pushoverEnableConsumables", true);
        pushoverPriorityConsumables = sharedPreferences.getString("pushoverPriorityConsumables", "normal");
        pushoverSoundConsumables = sharedPreferences.getString("pushoverSoundConsumables", "bike");
        pushoverLifetimeInfo = sharedPreferences.getBoolean("pushoverLifetimeInfo", false);
        pushoverEnableDailyTotals = sharedPreferences.getBoolean("pushoverEnableDailyTotals", true);
        pushoverPriorityDailyTotals = sharedPreferences.getString("pushoverPriorityDailyTotals", "normal");
        pushoverSoundDailyTotals = sharedPreferences.getString("pushoverSoundDailyTotals", "bike");

        pushoverEnableUploaderPumpErrors = sharedPreferences.getBoolean("pushoverEnableUploaderPumpErrors", true);
        pushoverPriorityUploaderPumpErrors = sharedPreferences.getString("pushoverPriorityUploaderPumpErrors", "emergency");
        pushoverSoundUploaderPumpErrors = sharedPreferences.getString("pushoverSoundUploaderPumpErrors", "persistent");
        pushoverEnableUploaderPumpConnection = sharedPreferences.getBoolean("pushoverEnableUploaderPumpConnection", true);
        pushoverPriorityUploaderPumpConnection = sharedPreferences.getString("pushoverPriorityUploaderPumpConnection", "normal");
        pushoverSoundUploaderPumpConnection = sharedPreferences.getString("pushoverSoundUploaderPumpConnection", "bike");
        pushoverEnableUploaderBattery = sharedPreferences.getBoolean("pushoverEnableUploaderBattery", true);
        pushoverPriorityUploaderBattery = sharedPreferences.getString("pushoverPriorityUploaderBattery", "normal");
        pushoverSoundUploaderBattery = sharedPreferences.getString("pushoverSoundUploaderBattery", "bike");
        pushoverEnableBatteryLow = sharedPreferences.getBoolean("pushoverEnableBatteryLow", true);
        pushoverEnableBatteryCharged = sharedPreferences.getBoolean("pushoverEnableBatteryCharged", true);

        pushoverEnableCleared = sharedPreferences.getBoolean("pushoverEnableCleared", true);
        pushoverPriorityCleared = sharedPreferences.getString("pushoverPriorityCleared", "low");
        pushoverSoundCleared = sharedPreferences.getString("pushoverSoundCleared", "none");
        pushoverEnableSilenced = sharedPreferences.getBoolean("pushoverEnableSilenced", false);
        pushoverPrioritySilenced = sharedPreferences.getString("pushoverPrioritySilenced", "normal");
        pushoverSoundSilenced = sharedPreferences.getString("pushoverSoundSilenced", "none");

        pushoverEnablePriorityOverride = sharedPreferences.getBoolean("pushoverEnablePriorityOverride", false);
        pushoverPriorityOverride = sharedPreferences.getString("pushoverPriorityOverride", "-2");
        pushoverEnableSoundOverride = sharedPreferences.getBoolean("pushoverEnableSoundOverride", false);
        pushoverSoundOverride = sharedPreferences.getString("pushoverSoundOverride", "none");

        pushoverInfoExtended = sharedPreferences.getBoolean("pushoverInfoExtended", true);
        pushoverTitleTime = sharedPreferences.getBoolean("pushoverTitleTime", true);
        pushoverEmergencyRetry = sharedPreferences.getString("pushoverEmergencyRetry", "30");
        pushoverEmergencyExpire = sharedPreferences.getString("pushoverEmergencyExpire", "3600");

    }

    private String checkBasalPattern(String oldName, String newName) {
        if (oldName != null && !oldName.equals(newName))
            nameBasalPatternChanged = true;
        return newName;
    }

    public String getLastStatReport() {
        return lastStatReport;
    }

    public void setLastStatReport(String lastStatReport) {
        this.lastStatReport = lastStatReport;
    }

    public Date getNightscoutLimitDate() {
        return nightscoutLimitDate;
    }

    public void setNightscoutLimitDate(Date nightscoutLimitDate) {
        this.nightscoutLimitDate = nightscoutLimitDate;
    }

    public long getNightscoutCgmCleanFrom() {
        return nightscoutCgmCleanFrom;
    }

    public void setNightscoutCgmCleanFrom(long nightscoutCgmCleanFrom) {
        this.nightscoutCgmCleanFrom = nightscoutCgmCleanFrom;
    }

    public long getNightscoutPumpCleanFrom() {
        return nightscoutPumpCleanFrom;
    }

    public void setNightscoutPumpCleanFrom(long nightscoutPumpCleanFrom) {
        this.nightscoutPumpCleanFrom = nightscoutPumpCleanFrom;
    }

    public boolean isNightscoutUpload() {
        return nightscoutUpload;
    }

    public void setNightscoutUpload(boolean nightscoutUpload) {
        this.nightscoutUpload = nightscoutUpload;
    }

    public String getNightscoutURL() {
        return nightscoutURL;
    }

    public void setNightscoutURL(String nightscoutURL) {
        this.nightscoutURL = nightscoutURL;
    }

    public String getNightscoutSECRET() {
        return nightscoutSECRET;
    }

    public void setNightscoutSECRET(String nightscoutSECRET) {
        this.nightscoutSECRET = nightscoutSECRET;
    }

    public long getNightscoutReportTime() {
        return nightscoutReportTime;
    }

    public void setNightscoutReportTime(long nightscoutReportTime) {
        this.nightscoutReportTime = nightscoutReportTime;
    }

    public boolean isNightscoutAvailable() {
        return nightscoutAvailable;
    }

    public void setNightscoutAvailable(boolean nightscoutAvailable) {
        this.nightscoutAvailable = nightscoutAvailable;
    }

    public boolean isNightscoutCareportal() {
        return nightscoutCareportal;
    }

    public void setNightscoutCareportal(boolean nightscoutCareportal) {
        this.nightscoutCareportal = nightscoutCareportal;
    }

    public boolean isRequestProfile() {
        return requestProfile;
    }

    public void setRequestProfile(boolean requestProfile) {
        this.requestProfile = requestProfile;
    }

    public boolean isRequestPumpHistory() {
        return requestPumpHistory;
    }

    public void setRequestPumpHistory(boolean requestPumpHistory) {
        this.requestPumpHistory = requestPumpHistory;
    }

    public boolean isRequestCgmHistory() {
        return requestCgmHistory;
    }

    public void setRequestCgmHistory(boolean requestCgmHistory) {
        this.requestCgmHistory = requestCgmHistory;
    }

    public boolean isRequestEstimate() {
        return requestEstimate;
    }

    public void setRequestEstimate(boolean requestEstimate) {
        this.requestEstimate = requestEstimate;
    }

    public int getPumpCgmNA() {
        return pumpCgmNA;
    }

    public void setPumpCgmNA(int pumpCgmNA) {
        this.pumpCgmNA = pumpCgmNA;
    }

    public int getCommsSuccess() {
        return commsSuccess;
    }

    public void setCommsSuccess(int commsSuccess) {
        this.commsSuccess = commsSuccess;
    }

    public int getCommsError() {
        return commsError;
    }

    public void setCommsError(int commsError) {
        this.commsError = commsError;
    }

    public int getCommsConnectError() {
        return commsConnectError;
    }

    public void setCommsConnectError(int commsConnectError) {
        this.commsConnectError = commsConnectError;
    }

    public int getCommsSignalError() {
        return commsSignalError;
    }

    public void setCommsSignalError(int commsSignalError) {
        this.commsSignalError = commsSignalError;
    }

    public int getCommsSgvSuccess() {
        return commsSgvSuccess;
    }

    public void setCommsSgvSuccess(int commsSgvSuccess) {
        this.commsSgvSuccess = commsSgvSuccess;
    }

    public int getPumpLostSensorError() {
        return pumpLostSensorError;
    }

    public void setPumpLostSensorError(int pumpLostSensorError) {
        this.pumpLostSensorError = pumpLostSensorError;
    }

    public int getPumpClockError() {
        return pumpClockError;
    }

    public void setPumpClockError(int pumpClockError) {
        this.pumpClockError = pumpClockError;
    }

    public int getPumpBatteryError() {
        return pumpBatteryError;
    }

    public void setPumpBatteryError(int pumpBatteryError) {
        this.pumpBatteryError = pumpBatteryError;
    }

    public void clearAllCommsErrors() {
        this.pumpCgmNA = 0;
        this.commsSuccess = 0;
        this.commsError = 0;
        this.commsConnectError = 0;
        this.commsSignalError = 0;
        this.commsSgvSuccess = 0;
        this.pumpLostSensorError = 0;
        this.pumpClockError = 0;
        this.pumpBatteryError = 0;
    }

    public boolean isEnableXdripPlusUpload() {
        return enableXdripPlusUpload;
    }

    public boolean isXdripPlusUploadAvailable() {
        return xdripPlusUploadAvailable;
    }

    public void setXdripPlusUploadAvailable(boolean xdripPlusUploadAvailable) {
        this.xdripPlusUploadAvailable = xdripPlusUploadAvailable;
    }

    public boolean isMmolxl() {
        return mmolxl;
    }

    public boolean isMmolxlDecimals() {
        return mmolxlDecimals;
    }

    public long getPollInterval() {
        return pollInterval;
    }

    public long getLowBatPollInterval() {
        return lowBatPollInterval;
    }

    public boolean isDoublePollOnPumpAway() {
        return doublePollOnPumpAway;
    }

    public boolean isSysEnableCgmHistory() {
        return sysEnableCgmHistory;
    }

    public int getSysCgmHistoryDays() {
        return sysCgmHistoryDays;
    }

    public boolean isSysEnablePumpHistory() {
        return sysEnablePumpHistory;
    }

    public int getSysPumpHistoryDays() {
        return sysPumpHistoryDays;
    }

    public int getSysPumpHistoryFrequency() {
        return sysPumpHistoryFrequency;
    }

    public boolean isSysEnableEstimateSGV() {
        return sysEnableEstimateSGV;
    }

    public boolean isSysEnableClashProtect() {
        return sysEnableClashProtect;
    }

    public boolean isSysEnablePollOverride() {
        return sysEnablePollOverride;
    }

    public long getSysPollGracePeriod() {
        return sysPollGracePeriod;
    }

    public long getSysPollRecoveryPeriod() {
        return sysPollRecoveryPeriod;
    }

    public long getSysPollWarmupPeriod() {
        return sysPollWarmupPeriod;
    }

    public long getSysPollErrorRetry() {
        return sysPollErrorRetry;
    }

    public long getSysPollOldSgvRetry() {
        return sysPollOldSgvRetry;
    }

    public boolean isSysEnableWait500ms() {
        return sysEnableWait500ms;
    }

    public boolean isSysEnableUsbPermissionDialog() {
        return sysEnableUsbPermissionDialog;
    }

    public boolean isDbgEnableExtendedErrors() {
        return dbgEnableExtendedErrors;
    }

    public boolean isDbgEnableUploadErrors() {
        return dbgEnableUploadErrors;
    }

    public boolean isNsEnableTreatments() {
        return nsEnableTreatments;
    }

    public boolean isNsEnableHistorySync() {
        return nsEnableHistorySync;
    }

    public boolean isNsEnableFingerBG() {
        return nsEnableFingerBG;
    }

    public boolean isNsEnableCalibrationInfo() {
        return nsEnableCalibrationInfo;
    }

    public boolean isNsEnableSensorChange() {
        return nsEnableSensorChange;
    }

    public boolean isNsEnableReservoirChange() {
        return nsEnableReservoirChange;
    }

    public boolean isNsEnableInsulinChange() {
        return nsEnableInsulinChange;
    }

    public int getNsInsulinChangeThreshold() {
        return nsInsulinChangeThreshold;
    }

    public boolean isNsEnableBatteryChange() {
        return nsEnableBatteryChange;
    }

    public boolean isNsEnableLifetimes() {
        return nsEnableLifetimes;
    }

    public boolean isNsEnableProfileUpload() {
        return nsEnableProfileUpload;
    }

    public boolean isNsEnableProfileSingle() {
        return nsEnableProfileSingle;
    }

    public boolean isNsEnableProfileOffset() {
        return nsEnableProfileOffset;
    }

    public int getNsProfileDefault() {
        return nsProfileDefault;
    }

    public float getNsActiveInsulinTime() {
        return nsActiveInsulinTime;
    }

    public boolean isNsEnablePatternChange() {
        return nsEnablePatternChange;
    }

    public int getNsGramsPerExchange() {
        return nsGramsPerExchange;
    }

    public void setNsGramsPerExchangeChanged(boolean nsGramsPerExchangeChanged) {
        this.nsGramsPerExchangeChanged = nsGramsPerExchangeChanged;
    }

    public boolean isNsGramsPerExchangeChanged() {
        return nsGramsPerExchangeChanged;
    }

    public boolean isNsEnableInsertBGasCGM() {
        return nsEnableInsertBGasCGM;
    }

    public boolean isNsEnableAlarms() {
        return nsEnableAlarms;
    }

    public boolean isNsAlarmExtended() {
        return nsAlarmExtended;
    }

    public boolean isNsAlarmCleared() {
        return nsAlarmCleared;
    }

    public boolean isNsEnableSystemStatus() {
        return nsEnableSystemStatus;
    }

    public int getNsAlarmTTL() {
        return nsAlarmTTL;
    }

    public boolean isNsEnableDailyTotals() {
        return nsEnableDailyTotals;
    }

    public boolean isNsEnableFormatHTML() {
        return nsEnableFormatHTML;
    }

    public boolean isUrchinEnable() {
        return urchinEnable;
    }

    public int getUrchinBasalPeriod() {
        return urchinBasalPeriod;
    }

    public int getUrchinBasalScale() {
        return urchinBasalScale;
    }

    public boolean isUrchinBolusGraph() {
        return urchinBolusGraph;
    }

    public boolean isUrchinBolusTags() {
        return urchinBolusTags;
    }

    public int getUrchinBolusPop() {
        return urchinBolusPop;
    }

    public void setUrchinBolusPop(int urchinBolusPop) {
        this.urchinBolusPop = urchinBolusPop;
    }

    public int getUrchinTimeStyle() {
        return urchinTimeStyle;
    }

    public int getUrchinDurationStyle() {
        return urchinDurationStyle;
    }

    public int getUrchinUnitsStyle() {
        return urchinUnitsStyle;
    }

    public int getUrchinBatteyStyle() {
        return urchinBatteyStyle;
    }

    public int getUrchinConcatenateStyle() {
        return urchinConcatenateStyle;
    }

    public String getUrchinCustomText1() {
        return urchinCustomText1;
    }

    public String getUrchinCustomText2() {
        return urchinCustomText2;
    }

    public byte[] getUrchinStatusLayout() {
        return urchinStatusLayout;
    }

    public String getNameBasalPattern1() {
        return nameBasalPattern1;
    }

    public String getNameBasalPattern2() {
        return nameBasalPattern2;
    }

    public String getNameBasalPattern3() {
        return nameBasalPattern3;
    }

    public String getNameBasalPattern4() {
        return nameBasalPattern4;
    }

    public String getNameBasalPattern5() {
        return nameBasalPattern5;
    }

    public String getNameBasalPattern6() {
        return nameBasalPattern6;
    }

    public String getNameBasalPattern7() {
        return nameBasalPattern7;
    }

    public String getNameBasalPattern8() {
        return nameBasalPattern8;
    }

    public String getNameTempBasalPreset1() {
        return nameTempBasalPreset1;
    }

    public String getNameTempBasalPreset2() {
        return nameTempBasalPreset2;
    }

    public String getNameTempBasalPreset3() {
        return nameTempBasalPreset3;
    }

    public String getNameTempBasalPreset4() {
        return nameTempBasalPreset4;
    }

    public String getNameTempBasalPreset5() {
        return nameTempBasalPreset5;
    }

    public String getNameTempBasalPreset6() {
        return nameTempBasalPreset6;
    }

    public String getNameTempBasalPreset7() {
        return nameTempBasalPreset7;
    }

    public String getNameTempBasalPreset8() {
        return nameTempBasalPreset8;
    }

    public String getNameBolusPreset1() {
        return nameBolusPreset1;
    }

    public String getNameBolusPreset2() {
        return nameBolusPreset2;
    }

    public String getNameBolusPreset3() {
        return nameBolusPreset3;
    }

    public String getNameBolusPreset4() {
        return nameBolusPreset4;
    }

    public String getNameBolusPreset5() {
        return nameBolusPreset5;
    }

    public String getNameBolusPreset6() {
        return nameBolusPreset6;
    }

    public String getNameBolusPreset7() {
        return nameBolusPreset7;
    }

    public String getNameBolusPreset8() {
        return nameBolusPreset8;
    }

    public String getNameBasalPattern(int value) {
        switch (value) {
            case 1: return nameBasalPattern1;
            case 2: return nameBasalPattern2;
            case 3: return nameBasalPattern3;
            case 4: return nameBasalPattern4;
            case 5: return nameBasalPattern5;
            case 6: return nameBasalPattern6;
            case 7: return nameBasalPattern7;
            case 8: return nameBasalPattern8;
        }
        return "";
    }

    public String getNameTempBasalPreset(int value) {
        switch (value) {
            case 1: return nameTempBasalPreset1;
            case 2: return nameTempBasalPreset2;
            case 3: return nameTempBasalPreset3;
            case 4: return nameTempBasalPreset4;
            case 5: return nameTempBasalPreset5;
            case 6: return nameTempBasalPreset6;
            case 7: return nameTempBasalPreset7;
            case 8: return nameTempBasalPreset8;
        }
        return "";
    }

    public String getNameBolusPreset(int value) {
        switch (value) {
            case 1: return nameBolusPreset1;
            case 2: return nameBolusPreset2;
            case 3: return nameBolusPreset3;
            case 4: return nameBolusPreset4;
            case 5: return nameBolusPreset5;
            case 6: return nameBolusPreset6;
            case 7: return nameBolusPreset7;
            case 8: return nameBolusPreset8;
        }
        return "";
    }

    public boolean isNameBasalPatternChanged() {
        return nameBasalPatternChanged;
    }

    public void setNameBasalPatternChanged(boolean nameBasalPatternChanged) {
        this.nameBasalPatternChanged = nameBasalPatternChanged;
    }

    public boolean isPushoverEnable() {
        return pushoverEnable;
    }

    public String getPushoverAPItoken() {
        return pushoverAPItoken;
    }

    public void setPushoverAPItoken(String pushoverAPItoken) {
        this.pushoverAPItoken = pushoverAPItoken;
    }

    public String getPushoverUSERtoken() {
        return pushoverUSERtoken;
    }

    public void setPushoverUSERtoken(String pushoverUSERtoken) {
        this.pushoverUSERtoken = pushoverUSERtoken;
    }

    public boolean isPushoverValidated() {
        return pushoverValidated;
    }

    public void setPushoverValidated(boolean pushoverValidated) {
        this.pushoverValidated = pushoverValidated;
    }

    public boolean isPushoverError() {
        return pushoverError;
    }

    public void setPushoverError(boolean pushoverError) {
        this.pushoverError = pushoverError;
    }

    public String getPushoverAPItokenCheck() {
        return pushoverAPItokenCheck;
    }

    public void setPushoverAPItokenCheck(String pushoverAPItokenCheck) {
        this.pushoverAPItokenCheck = pushoverAPItokenCheck;
    }

    public String getPushoverUSERtokenCheck() {
        return pushoverUSERtokenCheck;
    }

    public void setPushoverUSERtokenCheck(String pushoverUSERtokenCheck) {
        this.pushoverUSERtokenCheck = pushoverUSERtokenCheck;
    }

    public long getPushoverAppLimit() {
        return pushoverAppLimit;
    }

    public void setPushoverAppLimit(long pushoverAppLimit) {
        this.pushoverAppLimit = pushoverAppLimit;
    }

    public long getPushoverAppRemaining() {
        return pushoverAppRemaining;
    }

    public void setPushoverAppRemaining(long pushoverAppRemaining) {
        this.pushoverAppRemaining = pushoverAppRemaining;
    }

    public long getPushoverAppReset() {
        return pushoverAppReset;
    }

    public void setPushoverAppReset(long pushoverAppReset) {
        this.pushoverAppReset = pushoverAppReset;
    }

    public boolean isPushoverEnableOnHigh() {
        return pushoverEnableOnHigh;
    }

    public String getPushoverPriorityOnHigh() {
        return pushoverPriorityOnHigh;
    }

    public String getPushoverSoundOnHigh() {
        return pushoverSoundOnHigh;
    }

    public boolean isPushoverEnableOnLow() {
        return pushoverEnableOnLow;
    }

    public String getPushoverPriorityOnLow() {
        return pushoverPriorityOnLow;
    }

    public String getPushoverSoundOnLow() {
        return pushoverSoundOnLow;
    }

    public boolean isPushoverEnableBeforeHigh() {
        return pushoverEnableBeforeHigh;
    }

    public String getPushoverPriorityBeforeHigh() {
        return pushoverPriorityBeforeHigh;
    }

    public String getPushoverSoundBeforeHigh() {
        return pushoverSoundBeforeHigh;
    }

    public boolean isPushoverEnableBeforeLow() {
        return pushoverEnableBeforeLow;
    }

    public String getPushoverPriorityBeforeLow() {
        return pushoverPriorityBeforeLow;
    }

    public String getPushoverSoundBeforeLow() {
        return pushoverSoundBeforeLow;
    }

    public boolean isPushoverEnableAutoModeExit() {
        return pushoverEnableAutoModeExit;
    }

    public String getPushoverPriorityAutoModeExit() {
        return pushoverPriorityAutoModeExit;
    }

    public String getPushoverSoundAutoModeExit() {
        return pushoverSoundAutoModeExit;
    }

    public boolean isPushoverEnablePumpEmergency() {
        return pushoverEnablePumpEmergency;
    }

    public String getPushoverPriorityPumpEmergency() {
        return pushoverPriorityPumpEmergency;
    }

    public String getPushoverSoundPumpEmergency() {
        return pushoverSoundPumpEmergency;
    }

    public boolean isPushoverEnablePumpActionable() {
        return pushoverEnablePumpActionable;
    }

    public String getPushoverPriorityPumpActionable() {
        return pushoverPriorityPumpActionable;
    }

    public String getPushoverSoundPumpActionable() {
        return pushoverSoundPumpActionable;
    }

    public boolean isPushoverEnablePumpInformational() {
        return pushoverEnablePumpInformational;
    }

    public String getPushoverPriorityPumpInformational() {
        return pushoverPriorityPumpInformational;
    }

    public String getPushoverSoundPumpInformational() {
        return pushoverSoundPumpInformational;
    }

    public boolean isPushoverEnablePumpReminder() {
        return pushoverEnablePumpReminder;
    }

    public String getPushoverPriorityPumpReminder() {
        return pushoverPriorityPumpReminder;
    }

    public String getPushoverSoundPumpReminder() {
        return pushoverSoundPumpReminder;
    }

    public boolean isPushoverEnableBolus() {
        return pushoverEnableBolus;
    }

    public String getPushoverPriorityBolus() {
        return pushoverPriorityBolus;
    }

    public String getPushoverSoundBolus() {
        return pushoverSoundBolus;
    }

    public boolean isPushoverEnableBasal() {
        return pushoverEnableBasal;
    }

    public String getPushoverPriorityBasal() {
        return pushoverPriorityBasal;
    }

    public String getPushoverSoundBasal() {
        return pushoverSoundBasal;
    }

    public boolean isPushoverEnableSuspendResume() {
        return pushoverEnableSuspendResume;
    }

    public String getPushoverPrioritySuspendResume() {
        return pushoverPrioritySuspendResume;
    }

    public String getPushoverSoundSuspendResume() {
        return pushoverSoundSuspendResume;
    }

    public boolean isPushoverEnableBG() {
        return pushoverEnableBG;
    }

    public String getPushoverPriorityBG() {
        return pushoverPriorityBG;
    }

    public String getPushoverSoundBG() {
        return pushoverSoundBG;
    }

    public boolean isPushoverEnableCalibration() {
        return pushoverEnableCalibration;
    }

    public String getPushoverPriorityCalibration() {
        return pushoverPriorityCalibration;
    }

    public String getPushoverSoundCalibration() {
        return pushoverSoundCalibration;
    }

    public boolean isPushoverEnableConsumables() {
        return pushoverEnableConsumables;
    }

    public String getPushoverPriorityConsumables() {
        return pushoverPriorityConsumables;
    }

    public String getPushoverSoundConsumables() {
        return pushoverSoundConsumables;
    }

    public boolean isPushoverLifetimeInfo() {
        return pushoverLifetimeInfo;
    }

    public boolean isPushoverEnableDailyTotals() {
        return pushoverEnableDailyTotals;
    }

    public String getPushoverPriorityDailyTotals() {
        return pushoverPriorityDailyTotals;
    }

    public String getPushoverSoundDailyTotals() {
        return pushoverSoundDailyTotals;
    }

    public boolean isPushoverEnableUploaderPumpErrors() {
        return pushoverEnableUploaderPumpErrors;
    }

    public String getPushoverPriorityUploaderPumpErrors() {
        return pushoverPriorityUploaderPumpErrors;
    }

    public String getPushoverSoundUploaderPumpErrors() {
        return pushoverSoundUploaderPumpErrors;
    }

    public boolean isPushoverEnableUploaderPumpConnection() {
        return pushoverEnableUploaderPumpConnection;
    }

    public String getPushoverPriorityUploaderPumpConnection() {
        return pushoverPriorityUploaderPumpConnection;
    }

    public String getPushoverSoundUploaderPumpConnection() {
        return pushoverSoundUploaderPumpConnection;
    }

    public boolean isPushoverEnableUploaderBattery() {
        return pushoverEnableUploaderBattery;
    }

    public String getPushoverPriorityUploaderBattery() {
        return pushoverPriorityUploaderBattery;
    }

    public String getPushoverSoundUploaderBattery() {
        return pushoverSoundUploaderBattery;
    }

    public boolean isPushoverEnableBatteryLow() {
        return pushoverEnableBatteryLow;
    }

    public boolean isPushoverEnableBatteryCharged() {
        return pushoverEnableBatteryCharged;
    }

    public boolean isPushoverEnableCleared() {
        return pushoverEnableCleared;
    }

    public String getPushoverPriorityCleared() {
        return pushoverPriorityCleared;
    }

    public String getPushoverSoundCleared() {
        return pushoverSoundCleared;
    }

    public boolean isPushoverEnableSilenced() {
        return pushoverEnableSilenced;
    }

    public String getPushoverPrioritySilenced() {
        return pushoverPrioritySilenced;
    }

    public String getPushoverSoundSilenced() {
        return pushoverSoundSilenced;
    }

    public boolean isPushoverInfoExtended() {
        return pushoverInfoExtended;
    }

    public boolean isPushoverTitleTime() {
        return pushoverTitleTime;
    }

    public String getPushoverEmergencyRetry() {
        return pushoverEmergencyRetry;
    }

    public String getPushoverEmergencyExpire() {
        return pushoverEmergencyExpire;
    }

    public boolean isPushoverEnablePriorityOverride() {
        return pushoverEnablePriorityOverride;
    }

    public String getPushoverPriorityOverride() {
        return pushoverPriorityOverride;
    }

    public boolean isPushoverEnableSoundOverride() {
        return pushoverEnableSoundOverride;
    }

    public String getPushoverSoundOverride() {
        return pushoverSoundOverride;
    }
}
