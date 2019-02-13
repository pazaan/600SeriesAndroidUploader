package info.nightscout.android.model.store;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Date;

import info.nightscout.android.R;
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
    private long nightscoutAlwaysUpdateTimestamp; // items for upload will always update prior to this time

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
    private boolean requestIsig = false;

    private String lastStatReport;

    private int pumpCgmNA;

    private int commsSuccess;
    private int commsError;
    private int commsConnectError;
    private int commsSignalError;
    private int commsCgmSuccess;
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
    private boolean sysEnableReportISIG;
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
    private int nsCannulaChangeThreshold;
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
    private boolean nsEnableUploaderBatteryFull;
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

    public DataStore() {
        this.timestamp = new Date().getTime();
    }

    public void copyPrefs(Context c, SharedPreferences p)
    {
        mmolxl = getBoolean(c, p, R.string.key_mmolxl, R.bool.default_mmolxl);
        mmolxlDecimals = getBoolean(c, p, R.string.key_mmolDecimals, R.bool.default_mmolDecimals);
        pollInterval = getLong(c, p, R.string.key_pollInterval, R.string.default_pollInterval);
        lowBatPollInterval = getLong(c, p, R.string.key_lowBatPollInterval, R.string.default_lowBatPollInterval);
        doublePollOnPumpAway = getBoolean(c, p, R.string.key_doublePollOnPumpAway, R.bool.default_doublePollOnPumpAway);

        nightscoutUpload = getBoolean(c, p, R.string.key_EnableRESTUpload, R.bool.default_EnableRESTUpload);
        nightscoutURL = getString(c, p, R.string.key_nightscoutURL, R.string.default_nightscoutURL);
        nightscoutSECRET = getString(c, p, R.string.key_nightscoutSECRET, R.string.default_nightscoutSECRET);

        enableXdripPlusUpload = getBoolean(c, p, R.string.key_enableXdripPlusUpload, R.bool.default_enableXdripPlusUpload);

        // system

        sysEnableCgmHistory = getBoolean(c, p, R.string.key_sysEnableCgmHistory, R.bool.default_sysEnableCgmHistory);
        sysCgmHistoryDays = getInt(c, p,R.string.key_sysCgmHistoryDays, R.string.default_sysCgmHistoryDays);
        sysEnablePumpHistory = getBoolean(c, p, R.string.key_sysEnablePumpHistory, R.bool.default_sysEnablePumpHistory);
        sysPumpHistoryDays = getInt(c, p, R.string.key_sysPumpHistoryDays, R.string.default_sysPumpHistoryDays);
        sysPumpHistoryFrequency = getInt(c, p, R.string.key_sysPumpHistoryFrequency, R.string.default_sysPumpHistoryFrequency);
        sysEnableEstimateSGV = getBoolean(c, p, R.string.key_sysEnableEstimateSGV, R.bool.default_sysEnableEstimateSGV);
        sysEnableReportISIG = getBoolean(c, p, R.string.key_sysEnableReportISIG, R.bool.default_sysEnableReportISIG);
        sysEnableClashProtect = getBoolean(c, p, R.string.key_sysEnableClashProtect, R.bool.default_sysEnableClashProtect);
        sysEnablePollOverride = getBoolean(c, p, R.string.key_sysEnablePollOverride, R.bool.default_sysEnablePollOverride);
        sysPollGracePeriod = getLong(c, p, R.string.key_sysPollGracePeriod, R.string.default_sysPollGracePeriod);
        sysPollRecoveryPeriod = getLong(c, p, R.string.key_sysPollRecoveryPeriod, R.string.default_sysPollRecoveryPeriod);
        sysPollWarmupPeriod = getLong(c, p, R.string.key_sysPollWarmupPeriod, R.string.default_sysPollWarmupPeriod);
        sysPollErrorRetry = getLong(c, p, R.string.key_sysPollErrorRetry, R.string.default_sysPollErrorRetry);
        sysPollOldSgvRetry = getLong(c, p, R.string.key_sysPollOldSgvRetry, R.string.default_sysPollOldSgvRetry);
        sysEnableWait500ms = getBoolean(c, p, R.string.key_sysEnableWait500ms, R.bool.default_sysEnableWait500ms);
        sysEnableUsbPermissionDialog = getBoolean(c, p, R.string.key_sysEnableUsbPermissionDialog, R.bool.default_sysEnableUsbPermissionDialog);

        // debug
        dbgEnableExtendedErrors = getBoolean(c, p, R.string.key_dbgEnableExtendedErrors, R.bool.default_dbgEnableExtendedErrors);
        dbgEnableUploadErrors = getBoolean(c, p, R.string.key_dbgEnableUploadErrors, R.bool.default_dbgEnableUploadErrors);

        // nightscout
        nsEnableTreatments = getBoolean(c, p, R.string.key_nsEnableTreatments, R.bool.default_nsEnableTreatments);
        nsEnableHistorySync = getBoolean(c, p, R.string.key_nsEnableHistorySync, R.bool.default_nsEnableHistorySync);
        nsEnableFingerBG = getBoolean(c, p, R.string.key_nsEnableFingerBG, R.bool.default_nsEnableFingerBG);
        nsEnableCalibrationInfo = getBoolean(c, p, R.string.key_nsEnableCalibrationInfo, R.bool.default_nsEnableCalibrationInfo);
        nsEnableSensorChange = getBoolean(c, p, R.string.key_nsEnableSensorChange, R.bool.default_nsEnableSensorChange);
        nsEnableReservoirChange = getBoolean(c, p, R.string.key_nsEnableReservoirChange, R.bool.default_nsEnableReservoirChange);
        nsEnableInsulinChange = getBoolean(c, p, R.string.key_nsEnableInsulinChange, R.bool.default_nsEnableInsulinChange);
        nsCannulaChangeThreshold = getInt(c, p, R.string.key_nsCannulaChangeThreshold, R.string.default_nsCannulaChangeThreshold);
        nsInsulinChangeThreshold = getInt(c, p, R.string.key_nsInsulinChangeThreshold, R.string.default_nsInsulinChangeThreshold);
        nsEnableBatteryChange = getBoolean(c, p, R.string.key_nsEnableBatteryChange, R.bool.default_nsEnableBatteryChange);
        nsEnableLifetimes = getBoolean(c, p, R.string.key_nsEnableLifetimes, R.bool.default_nsEnableLifetimes);
        nsEnableProfileUpload = getBoolean(c, p, R.string.key_nsEnableProfileUpload, R.bool.default_nsEnableProfileUpload);
        nsEnableProfileSingle = getBoolean(c, p, R.string.key_nsEnableProfileSingle, R.bool.default_nsEnableProfileSingle);
        nsEnableProfileOffset = getBoolean(c, p, R.string.key_nsEnableProfileOffset, R.bool.default_nsEnableProfileOffset);
        nsProfileDefault = getInt(c, p, R.string.key_nsProfileDefault, R.string.default_nsProfileDefault);
        nsActiveInsulinTime = getFloat(c, p, R.string.key_nsActiveInsulinTime, R.string.default_nsActiveInsulinTime);
        nsEnablePatternChange = getBoolean(c, p, R.string.key_nsEnablePatternChange, R.bool.default_nsEnablePatternChange);
        nsEnableInsertBGasCGM = getBoolean(c, p, R.string.key_nsEnableInsertBGasCGM, R.bool.default_nsEnableInsertBGasCGM);
        nsEnableAlarms = getBoolean(c, p, R.string.key_nsEnableAlarms, R.bool.default_nsEnableAlarms);
        nsAlarmExtended = getBoolean(c, p, R.string.key_nsAlarmExtended, R.bool.default_nsAlarmExtended);
        nsAlarmCleared = getBoolean(c, p, R.string.key_nsAlarmCleared, R.bool.default_nsAlarmCleared);
        nsEnableSystemStatus = getBoolean(c, p, R.string.key_nsEnableSystemStatus, R.bool.default_nsEnableSystemStatus);
        nsEnableUploaderBatteryFull = getBoolean(c, p, R.string.key_nsEnableUploaderBatteryFull, R.bool.default_nsEnableUploaderBatteryFull);
        nsAlarmTTL = getInt(c, p, R.string.key_nsAlarmTTL, R.string.default_nsAlarmTTL);
        nsEnableDailyTotals = getBoolean(c, p, R.string.key_nsEnableDailyTotals, R.bool.default_nsEnableDailyTotals);
        nsEnableFormatHTML = getBoolean(c, p, R.string.key_nsEnableFormatHTML, R.bool.default_nsEnableFormatHTML);

        int nsGramsPerExchange = getInt(c, p, R.string.key_nsGramsPerExchange, R.string.default_nsGramsPerExchange);
        if (this.nsGramsPerExchange != 0 && this.nsGramsPerExchange != nsGramsPerExchange)
            nsGramsPerExchangeChanged = true;
        this.nsGramsPerExchange = nsGramsPerExchange;

        nameBasalPattern1 = checkBasalPattern(nameBasalPattern1, getString(c, p, R.string.key_nameBasalPattern1, R.string.default_nameBasalPattern1));
        nameBasalPattern2 = checkBasalPattern(nameBasalPattern2, getString(c, p, R.string.key_nameBasalPattern2, R.string.default_nameBasalPattern2));
        nameBasalPattern3 = checkBasalPattern(nameBasalPattern3, getString(c, p, R.string.key_nameBasalPattern3, R.string.default_nameBasalPattern3));
        nameBasalPattern4 = checkBasalPattern(nameBasalPattern4, getString(c, p, R.string.key_nameBasalPattern4, R.string.default_nameBasalPattern4));
        nameBasalPattern5 = checkBasalPattern(nameBasalPattern5, getString(c, p, R.string.key_nameBasalPattern5, R.string.default_nameBasalPattern5));
        nameBasalPattern6 = checkBasalPattern(nameBasalPattern6, getString(c, p, R.string.key_nameBasalPattern6, R.string.default_nameBasalPattern6));
        nameBasalPattern7 = checkBasalPattern(nameBasalPattern7, getString(c, p, R.string.key_nameBasalPattern7, R.string.default_nameBasalPattern7));
        nameBasalPattern8 = checkBasalPattern(nameBasalPattern8, getString(c, p, R.string.key_nameBasalPattern8, R.string.default_nameBasalPattern8));
        nameTempBasalPreset1 = getString(c, p, R.string.key_nameTempBasalPreset1, R.string.default_nameTempBasalPreset1);
        nameTempBasalPreset2 = getString(c, p, R.string.key_nameTempBasalPreset2, R.string.default_nameTempBasalPreset2);
        nameTempBasalPreset3 = getString(c, p, R.string.key_nameTempBasalPreset3, R.string.default_nameTempBasalPreset3);
        nameTempBasalPreset4 = getString(c, p, R.string.key_nameTempBasalPreset4, R.string.default_nameTempBasalPreset4);
        nameTempBasalPreset5 = getString(c, p, R.string.key_nameTempBasalPreset5, R.string.default_nameTempBasalPreset5);
        nameTempBasalPreset6 = getString(c, p, R.string.key_nameTempBasalPreset6, R.string.default_nameTempBasalPreset6);
        nameTempBasalPreset7 = getString(c, p, R.string.key_nameTempBasalPreset7, R.string.default_nameTempBasalPreset7);
        nameTempBasalPreset8 = getString(c, p, R.string.key_nameTempBasalPreset8, R.string.default_nameTempBasalPreset8);
        nameBolusPreset1 = getString(c, p, R.string.key_nameBolusPreset1, R.string.default_nameBolusPreset1);
        nameBolusPreset2 = getString(c, p, R.string.key_nameBolusPreset2, R.string.default_nameBolusPreset2);
        nameBolusPreset3 = getString(c, p, R.string.key_nameBolusPreset3, R.string.default_nameBolusPreset3);
        nameBolusPreset4 = getString(c, p, R.string.key_nameBolusPreset4, R.string.default_nameBolusPreset4);
        nameBolusPreset5 = getString(c, p, R.string.key_nameBolusPreset5, R.string.default_nameBolusPreset5);
        nameBolusPreset6 = getString(c, p, R.string.key_nameBolusPreset6, R.string.default_nameBolusPreset6);
        nameBolusPreset7 = getString(c, p, R.string.key_nameBolusPreset7, R.string.default_nameBolusPreset7);
        nameBolusPreset8 = getString(c, p, R.string.key_nameBolusPreset8, R.string.default_nameBolusPreset8);

        // urchin
        urchinEnable = getBoolean(c, p, R.string.key_urchinEnable, R.bool.default_urchinEnable);
        urchinBasalPeriod = getInt(c, p, R.string.key_urchinBasalPeriod, R.string.default_urchinBasalPeriod);
        urchinBasalScale = getInt(c, p, R.string.key_urchinBasalScale, R.string.default_urchinBasalScale);
        urchinBolusGraph = getBoolean(c, p, R.string.key_urchinBolusGraph, R.bool.default_urchinBolusGraph);
        urchinBolusTags = getBoolean(c, p, R.string.key_urchinBolusTags, R.bool.default_urchinBolusTags);
        urchinBolusPop = getInt(c, p, R.string.key_urchinBolusPop, R.string.default_urchinBolusPop);
        urchinTimeStyle = getInt(c, p, R.string.key_urchinTimeStyle, R.string.default_urchinTimeStyle);
        urchinDurationStyle = getInt(c, p, R.string.key_urchinDurationStyle, R.string.default_urchinDurationStyle);
        urchinUnitsStyle = getInt(c, p, R.string.key_urchinUnitsStyle, R.string.default_urchinUnitsStyle);
        urchinBatteyStyle = getInt(c, p, R.string.key_urchinBatteyStyle, R.string.default_urchinBatteyStyle);
        urchinConcatenateStyle = getInt(c, p, R.string.key_urchinConcatenateStyle, R.string.default_urchinConcatenateStyle);
        urchinCustomText1 = getString(c, p, R.string.key_urchinCustomText1, R.string.default_urchinCustomText1);
        urchinCustomText2 = getString(c, p, R.string.key_urchinCustomText2, R.string.default_urchinCustomText2);

        int count = 35;
        byte[] urchinStatusLayout = new byte[count];
        for (int i=0; i < count; i++) {
            urchinStatusLayout[i] = (byte) Integer.parseInt(p.getString(
                    c.getString(R.string.key_urchinStatusLayout) + (i + 1),
                    c.getString(R.string.default_urchinStatusLayout)));
        }
        this.urchinStatusLayout = urchinStatusLayout;

        // pushover
        pushoverEnable = getBoolean(c, p, R.string.key_pushoverEnable, R.bool.default_pushoverEnable);
        pushoverAPItoken = getString(c, p, R.string.key_pushoverAPItoken, R.string.default_pushoverAPItoken);
        pushoverUSERtoken = getString(c, p, R.string.key_pushoverUSERtoken, R.string.default_pushoverUSERtoken);

        if (!pushoverEnable) {
            // will force a validation check when pushover re-enabled
            pushoverAPItokenCheck = "";
            pushoverUSERtokenCheck = "";
        }

        pushoverEnableOnHigh = getBoolean(c, p, R.string.key_pushoverEnableOnHigh, R.bool.default_pushoverEnableOnHigh);
        pushoverPriorityOnHigh = getString(c, p, R.string.key_pushoverPriorityOnHigh, R.string.default_pushoverPriorityOnHigh);
        pushoverSoundOnHigh = getString(c, p, R.string.key_pushoverSoundOnHigh, R.string.default_pushoverSoundOnHigh);
        pushoverEnableOnLow = getBoolean(c, p, R.string.key_pushoverEnableOnLow, R.bool.default_pushoverEnableOnLow);
        pushoverPriorityOnLow = getString(c, p, R.string.key_pushoverPriorityOnLow, R.string.default_pushoverPriorityOnLow);
        pushoverSoundOnLow = getString(c, p, R.string.key_pushoverSoundOnLow, R.string.default_pushoverSoundOnLow);
        pushoverEnableBeforeHigh = getBoolean(c, p, R.string.key_pushoverEnableBeforeHigh, R.bool.default_pushoverEnableBeforeHigh);
        pushoverPriorityBeforeHigh = getString(c, p, R.string.key_pushoverPriorityBeforeHigh, R.string.default_pushoverPriorityBeforeHigh);
        pushoverSoundBeforeHigh = getString(c, p, R.string.key_pushoverSoundBeforeHigh, R.string.default_pushoverSoundBeforeHigh);
        pushoverEnableBeforeLow = getBoolean(c, p, R.string.key_pushoverEnableBeforeLow, R.bool.default_pushoverEnableBeforeLow);
        pushoverPriorityBeforeLow = getString(c, p, R.string.key_pushoverPriorityBeforeLow, R.string.default_pushoverPriorityBeforeLow);
        pushoverSoundBeforeLow = getString(c, p, R.string.key_pushoverSoundBeforeLow, R.string.default_pushoverSoundBeforeLow);
        pushoverEnableAutoModeExit = getBoolean(c, p, R.string.key_pushoverEnableAutoModeExit, R.bool.default_pushoverEnableAutoModeExit);
        pushoverPriorityAutoModeExit = getString(c, p, R.string.key_pushoverPriorityAutoModeExit, R.string.default_pushoverPriorityAutoModeExit);
        pushoverSoundAutoModeExit = getString(c, p, R.string.key_pushoverSoundAutoModeExit, R.string.default_pushoverSoundAutoModeExit);

        pushoverEnablePumpEmergency = getBoolean(c, p, R.string.key_pushoverEnablePumpEmergency, R.bool.default_pushoverEnablePumpEmergency);
        pushoverPriorityPumpEmergency = getString(c, p, R.string.key_pushoverPriorityPumpEmergency, R.string.default_pushoverPriorityPumpEmergency);
        pushoverSoundPumpEmergency = getString(c, p, R.string.key_pushoverSoundPumpEmergency, R.string.default_pushoverSoundPumpEmergency);
        pushoverEnablePumpActionable = getBoolean(c, p, R.string.key_pushoverEnablePumpActionable, R.bool.default_pushoverEnablePumpActionable);
        pushoverPriorityPumpActionable = getString(c, p, R.string.key_pushoverPriorityPumpActionable, R.string.default_pushoverPriorityPumpActionable);
        pushoverSoundPumpActionable = getString(c, p, R.string.key_pushoverSoundPumpActionable, R.string.default_pushoverSoundPumpActionable);
        pushoverEnablePumpInformational = getBoolean(c, p, R.string.key_pushoverEnablePumpInformational, R.bool.default_pushoverEnablePumpInformational);
        pushoverPriorityPumpInformational = getString(c, p, R.string.key_pushoverPriorityPumpInformational, R.string.default_pushoverPriorityPumpInformational);
        pushoverSoundPumpInformational = getString(c, p, R.string.key_pushoverSoundPumpInformational, R.string.default_pushoverSoundPumpInformational);
        pushoverEnablePumpReminder = getBoolean(c, p, R.string.key_pushoverEnablePumpReminder, R.bool.default_pushoverEnablePumpReminder);
        pushoverPriorityPumpReminder = getString(c, p, R.string.key_pushoverPriorityPumpReminder, R.string.default_pushoverPriorityPumpReminder);
        pushoverSoundPumpReminder = getString(c, p, R.string.key_pushoverSoundPumpReminder, R.string.default_pushoverSoundPumpReminder);

        pushoverEnableBolus = getBoolean(c, p, R.string.key_pushoverEnableBolus, R.bool.default_pushoverEnableBolus);
        pushoverPriorityBolus = getString(c, p, R.string.key_pushoverPriorityBolus, R.string.default_pushoverPriorityBolus);
        pushoverSoundBolus = getString(c, p, R.string.key_pushoverSoundBolus, R.string.default_pushoverSoundBolus);
        pushoverEnableBasal = getBoolean(c, p, R.string.key_pushoverEnableBasal, R.bool.default_pushoverEnableBasal);
        pushoverPriorityBasal = getString(c, p, R.string.key_pushoverPriorityBasal, R.string.default_pushoverPriorityBasal);
        pushoverSoundBasal = getString(c, p, R.string.key_pushoverSoundBasal, R.string.default_pushoverSoundBasal);
        pushoverEnableSuspendResume = getBoolean(c, p, R.string.key_pushoverEnableSuspendResume, R.bool.default_pushoverEnableSuspendResume);
        pushoverPrioritySuspendResume = getString(c, p, R.string.key_pushoverPrioritySuspendResume, R.string.default_pushoverPrioritySuspendResume);
        pushoverSoundSuspendResume = getString(c, p, R.string.key_pushoverSoundSuspendResume, R.string.default_pushoverSoundSuspendResume);
        pushoverEnableBG = getBoolean(c, p, R.string.key_pushoverEnableBG, R.bool.default_pushoverEnableBG);
        pushoverPriorityBG = getString(c, p, R.string.key_pushoverPriorityBG, R.string.default_pushoverPriorityBG);
        pushoverSoundBG = getString(c, p, R.string.key_pushoverSoundBG, R.string.default_pushoverSoundBG);
        pushoverEnableCalibration = getBoolean(c, p, R.string.key_pushoverEnableCalibration, R.bool.default_pushoverEnableCalibration);
        pushoverPriorityCalibration = getString(c, p, R.string.key_pushoverPriorityCalibration, R.string.default_pushoverPriorityCalibration);
        pushoverSoundCalibration = getString(c, p, R.string.key_pushoverSoundCalibration, R.string.default_pushoverSoundCalibration);
        pushoverEnableConsumables = getBoolean(c, p, R.string.key_pushoverEnableConsumables, R.bool.default_pushoverEnableConsumables);
        pushoverPriorityConsumables = getString(c, p, R.string.key_pushoverPriorityConsumables, R.string.default_pushoverPriorityConsumables);
        pushoverSoundConsumables = getString(c, p, R.string.key_pushoverSoundConsumables, R.string.default_pushoverSoundConsumables);
        pushoverLifetimeInfo = getBoolean(c, p, R.string.key_pushoverLifetimeInfo, R.bool.default_pushoverLifetimeInfo);
        pushoverEnableDailyTotals = getBoolean(c, p, R.string.key_pushoverEnableDailyTotals, R.bool.default_pushoverEnableDailyTotals);
        pushoverPriorityDailyTotals = getString(c, p, R.string.key_pushoverPriorityDailyTotals, R.string.default_pushoverPriorityDailyTotals);
        pushoverSoundDailyTotals = getString(c, p, R.string.key_pushoverSoundDailyTotals, R.string.default_pushoverSoundDailyTotals);

        pushoverEnableUploaderPumpErrors = getBoolean(c, p, R.string.key_pushoverEnableUploaderPumpErrors, R.bool.default_pushoverEnableUploaderPumpErrors);
        pushoverPriorityUploaderPumpErrors = getString(c, p, R.string.key_pushoverPriorityUploaderPumpErrors, R.string.default_pushoverPriorityUploaderPumpErrors);
        pushoverSoundUploaderPumpErrors = getString(c, p, R.string.key_pushoverSoundUploaderPumpErrors, R.string.default_pushoverSoundUploaderPumpErrors);
        pushoverEnableUploaderPumpConnection = getBoolean(c, p, R.string.key_pushoverEnableUploaderPumpConnection, R.bool.default_pushoverEnableUploaderPumpConnection);
        pushoverPriorityUploaderPumpConnection = getString(c, p, R.string.key_pushoverPriorityUploaderPumpConnection, R.string.default_pushoverPriorityUploaderPumpConnection);
        pushoverSoundUploaderPumpConnection = getString(c, p, R.string.key_pushoverSoundUploaderPumpConnection, R.string.default_pushoverSoundUploaderPumpConnection);
        pushoverEnableUploaderBattery = getBoolean(c, p, R.string.key_pushoverEnableUploaderBattery, R.bool.default_pushoverEnableUploaderBattery);
        pushoverPriorityUploaderBattery = getString(c, p, R.string.key_pushoverPriorityUploaderBattery, R.string.default_pushoverPriorityUploaderBattery);
        pushoverSoundUploaderBattery = getString(c, p, R.string.key_pushoverSoundUploaderBattery, R.string.default_pushoverSoundUploaderBattery);
        pushoverEnableBatteryLow = getBoolean(c, p, R.string.key_pushoverEnableBatteryLow, R.bool.default_pushoverEnableBatteryLow);
        pushoverEnableBatteryCharged = getBoolean(c, p, R.string.key_pushoverEnableBatteryCharged, R.bool.default_pushoverEnableBatteryCharged);

        pushoverEnableCleared = getBoolean(c, p, R.string.key_pushoverEnableCleared, R.bool.default_pushoverEnableCleared);
        pushoverPriorityCleared = getString(c, p, R.string.key_pushoverPriorityCleared, R.string.default_pushoverPriorityCleared);
        pushoverSoundCleared = getString(c, p, R.string.key_pushoverSoundCleared, R.string.default_pushoverSoundCleared);
        pushoverEnableSilenced = getBoolean(c, p, R.string.key_pushoverEnableSilenced, R.bool.default_pushoverEnableSilenced);
        pushoverPrioritySilenced = getString(c, p, R.string.key_pushoverPrioritySilenced, R.string.default_pushoverPrioritySilenced);
        pushoverSoundSilenced = getString(c, p, R.string.key_pushoverSoundSilenced, R.string.default_pushoverSoundSilenced);

        pushoverEnablePriorityOverride = getBoolean(c, p, R.string.key_pushoverEnablePriorityOverride, R.bool.default_pushoverEnablePriorityOverride);
        pushoverPriorityOverride = getString(c, p, R.string.key_pushoverPriorityOverride, R.string.default_pushoverPriorityOverride);
        pushoverEnableSoundOverride = getBoolean(c, p, R.string.key_pushoverEnableSoundOverride, R.bool.default_pushoverEnableSoundOverride);
        pushoverSoundOverride = getString(c, p, R.string.key_pushoverSoundOverride, R.string.default_pushoverSoundOverride);

        pushoverInfoExtended = getBoolean(c, p, R.string.key_pushoverInfoExtended, R.bool.default_pushoverInfoExtended);
        pushoverTitleTime = getBoolean(c, p, R.string.key_pushoverTitleTime, R.bool.default_pushoverTitleTime);
        pushoverEmergencyRetry = getString(c, p, R.string.key_pushoverEmergencyRetry, R.string.default_pushoverEmergencyRetry);
        pushoverEmergencyExpire = getString(c, p, R.string.key_pushoverEmergencyExpire, R.string.default_pushoverEmergencyExpire);

    }

    private Boolean getBoolean(Context c, SharedPreferences p, int k, int d) {
        return p.getBoolean(c.getString(k), c.getResources().getBoolean(d));
    }

    private int getInt(Context c, SharedPreferences p, int k, int d) {
        return Integer.parseInt(p.getString(c.getString(k), c.getString(d)));
    }

    private long getLong(Context c, SharedPreferences p, int k, int d) {
        return Long.parseLong(p.getString(c.getString(k), c.getString(d)));
    }

    private float getFloat(Context c, SharedPreferences p, int k, int d) {
        return Float.parseFloat(p.getString(c.getString(k), c.getString(d)));
    }

    private String getString(Context c, SharedPreferences p, int k, int d) {
        return p.getString(c.getString(k), c.getString(d));
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

    public long getNightscoutAlwaysUpdateTimestamp() {
        return nightscoutAlwaysUpdateTimestamp;
    }

    public void setNightscoutAlwaysUpdateTimestamp(long nightscoutAlwaysUpdateTimestamp) {
        this.nightscoutAlwaysUpdateTimestamp = nightscoutAlwaysUpdateTimestamp;
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

    public boolean isRequestIsig() {
        return requestIsig;
    }

    public void setRequestIsig(boolean requestIsig) {
        this.requestIsig = requestIsig;
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

    public int getCommsCgmSuccess() {
        return commsCgmSuccess;
    }

    public void setCommsCgmSuccess(int commsCgmSuccess) {
        this.commsCgmSuccess = commsCgmSuccess;
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
        this.commsCgmSuccess = 0;
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

    public boolean isSysEnableReportISIG() {
        return sysEnableReportISIG;
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

    public int getNsCannulaChangeThreshold() {
        return nsCannulaChangeThreshold;
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
    public boolean isNsEnableUploaderBatteryFull() {
        return nsEnableUploaderBatteryFull;
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

/*
        sysEnableCgmHistory = p.getBoolean("sysEnableCgmHistory", Boolean.parseBoolean(c.getString(R.string.default_sysEnableCgmHistory)));
        sysCgmHistoryDays = Integer.parseInt(p.getString("sysCgmHistoryDays", c.getString(R.string.default_sysCgmHistoryDays)));
        sysEnablePumpHistory = p.getBoolean("sysEnablePumpHistory", Boolean.parseBoolean(c.getString(R.string.default_sysEnablePumpHistory)));
        sysPumpHistoryDays = Integer.parseInt(p.getString("sysPumpHistoryDays", c.getString(R.string.default_sysPumpHistoryDays)));
        sysPumpHistoryFrequency = Integer.parseInt(p.getString("sysPumpHistoryFrequency", c.getString(R.string.default_sysPumpHistoryFrequency)));
        sysEnableEstimateSGV = p.getBoolean("sysEnableEstimateSGV", Boolean.parseBoolean(c.getString(R.string.default_sysEnableEstimateSGV)));
        sysEnableReportISIG = p.getBoolean("sysEnableReportISIG", Boolean.parseBoolean(c.getString(R.string.default_sysEnableReportISIG)));
        sysEnableClashProtect = p.getBoolean("sysEnableClashProtect", Boolean.parseBoolean(c.getString(R.string.default_sysEnableClashProtect)));
        sysEnablePollOverride = p.getBoolean("sysEnablePollOverride", Boolean.parseBoolean(c.getString(R.string.default_sysEnablePollOverride)));
        sysPollGracePeriod = Long.parseLong(p.getString("sysPollGracePeriod", c.getString(R.string.default_sysPollGracePeriod)));
        sysPollRecoveryPeriod = Long.parseLong(p.getString("sysPollRecoveryPeriod", c.getString(R.string.default_sysPollRecoveryPeriod)));
        sysPollWarmupPeriod = Long.parseLong(p.getString("sysPollWarmupPeriod", c.getString(R.string.default_sysPollWarmupPeriod)));
        sysPollErrorRetry = Long.parseLong(p.getString("sysPollErrorRetry", c.getString(R.string.default_sysPollErrorRetry)));
        sysPollOldSgvRetry = Long.parseLong(p.getString("sysPollOldSgvRetry", c.getString(R.string.default_sysPollOldSgvRetry)));
        sysEnableWait500ms = p.getBoolean("sysEnableWait500ms", Boolean.parseBoolean(c.getString(R.string.default_sysEnableWait500ms)));
        sysEnableUsbPermissionDialog = p.getBoolean("sysEnableUsbPermissionDialog", Boolean.parseBoolean(c.getString(R.string.default_sysEnableUsbPermissionDialog)));
// debug
        dbgEnableExtendedErrors = p.getBoolean("dbgEnableExtendedErrors", false);
                dbgEnableUploadErrors = p.getBoolean("dbgEnableUploadErrors", true);

                // nightscout
                nsEnableTreatments = p.getBoolean("nsEnableTreatments", true);
                nsEnableHistorySync = p.getBoolean("nsEnableHistorySync", false);
                nsEnableFingerBG = p.getBoolean("nsEnableFingerBG", true);
                nsEnableCalibrationInfo = p.getBoolean("nsEnableCalibrationInfo", false);
                nsEnableSensorChange = p.getBoolean("nsEnableSensorChange", true);
                nsEnableReservoirChange = p.getBoolean("nsEnableReservoirChange", true);
                nsEnableInsulinChange = p.getBoolean("nsEnableInsulinChange", false);
                nsCannulaChangeThreshold = Integer.parseInt(p.getString("nsCannulaChangeThreshold", "0"));
                nsInsulinChangeThreshold = Integer.parseInt(p.getString("nsInsulinChangeThreshold", "0"));
                nsEnableBatteryChange = p.getBoolean("nsEnableBatteryChange", true);
                nsEnableLifetimes = p.getBoolean("nsEnableLifetimes", false);
                nsEnableProfileUpload = p.getBoolean("nsEnableProfileUpload", true);
                nsEnableProfileSingle = p.getBoolean("nsEnableProfileSingle", true);
                nsEnableProfileOffset = p.getBoolean("nsEnableProfileOffset", true);
                nsProfileDefault = Integer.parseInt(p.getString("nsProfileDefault", "0"));
                nsActiveInsulinTime = Float.parseFloat(p.getString("nsActiveInsulinTime", "3"));
                nsEnablePatternChange = p.getBoolean("nsEnablePatternChange", true);
                nsEnableInsertBGasCGM = p.getBoolean("nsEnableInsertBGasCGM", false);
                nsEnableAlarms = p.getBoolean("nsEnableAlarms", false);
                nsAlarmExtended = p.getBoolean("nsAlarmExtended", true);
                nsAlarmCleared = p.getBoolean("nsAlarmCleared", true);
                nsEnableSystemStatus = p.getBoolean("nsEnableSystemStatus", false);
                nsAlarmTTL = Integer.parseInt(p.getString("nsAlarmTTL", "24"));
                nsEnableDailyTotals = p.getBoolean("nsEnableDailyTotals", true);
                nsEnableFormatHTML = p.getBoolean("nsEnableFormatHTML", true);

                int nsGramsPerExchange = Integer.parseInt(p.getString("nsGramsPerExchange", "15"));
                if (this.nsGramsPerExchange != 0 && this.nsGramsPerExchange != nsGramsPerExchange)
                nsGramsPerExchangeChanged = true;
                this.nsGramsPerExchange = nsGramsPerExchange;

                nameBasalPattern1 = checkBasalPattern(nameBasalPattern1, p.getString("nameBasalPattern1", c.getString(R.string.BASAL_PATTERN_1)));
                nameBasalPattern2 = checkBasalPattern(nameBasalPattern2, p.getString("nameBasalPattern2", c.getString(R.string.BASAL_PATTERN_2)));
                nameBasalPattern3 = checkBasalPattern(nameBasalPattern3, p.getString("nameBasalPattern3", c.getString(R.string.BASAL_PATTERN_3)));
                nameBasalPattern4 = checkBasalPattern(nameBasalPattern4, p.getString("nameBasalPattern4", c.getString(R.string.BASAL_PATTERN_4)));
                nameBasalPattern5 = checkBasalPattern(nameBasalPattern5, p.getString("nameBasalPattern5", c.getString(R.string.BASAL_PATTERN_5)));
                nameBasalPattern6 = checkBasalPattern(nameBasalPattern6, p.getString("nameBasalPattern6", c.getString(R.string.BASAL_PATTERN_6)));
                nameBasalPattern7 = checkBasalPattern(nameBasalPattern7, p.getString("nameBasalPattern7", c.getString(R.string.BASAL_PATTERN_7)));
                nameBasalPattern8 = checkBasalPattern(nameBasalPattern8, p.getString("nameBasalPattern8", c.getString(R.string.BASAL_PATTERN_8)));
                nameTempBasalPreset1 = p.getString("nameTempBasalPreset1", c.getString(R.string.TEMP_BASAL_PRESET_0));
                nameTempBasalPreset2 = p.getString("nameTempBasalPreset2", c.getString(R.string.TEMP_BASAL_PRESET_1));
                nameTempBasalPreset3 = p.getString("nameTempBasalPreset3", c.getString(R.string.TEMP_BASAL_PRESET_2));
                nameTempBasalPreset4 = p.getString("nameTempBasalPreset4", c.getString(R.string.TEMP_BASAL_PRESET_3));
                nameTempBasalPreset5 = p.getString("nameTempBasalPreset5", c.getString(R.string.TEMP_BASAL_PRESET_4));
                nameTempBasalPreset6 = p.getString("nameTempBasalPreset6", c.getString(R.string.TEMP_BASAL_PRESET_5));
                nameTempBasalPreset7 = p.getString("nameTempBasalPreset7", c.getString(R.string.TEMP_BASAL_PRESET_6));
                nameTempBasalPreset8 = p.getString("nameTempBasalPreset8", c.getString(R.string.TEMP_BASAL_PRESET_7));
                nameBolusPreset1 = p.getString("nameBolusPreset1", c.getString(R.string.BOLUS_PRESET_0));
                nameBolusPreset2 = p.getString("nameBolusPreset2", c.getString(R.string.BOLUS_PRESET_1));
                nameBolusPreset3 = p.getString("nameBolusPreset3", c.getString(R.string.BOLUS_PRESET_2));
                nameBolusPreset4 = p.getString("nameBolusPreset4", c.getString(R.string.BOLUS_PRESET_3));
                nameBolusPreset5 = p.getString("nameBolusPreset5", c.getString(R.string.BOLUS_PRESET_4));
                nameBolusPreset6 = p.getString("nameBolusPreset6", c.getString(R.string.BOLUS_PRESET_5));
                nameBolusPreset7 = p.getString("nameBolusPreset7", c.getString(R.string.BOLUS_PRESET_6));
                nameBolusPreset8 = p.getString("nameBolusPreset8", c.getString(R.string.BOLUS_PRESET_7));

                // urchin
                urchinEnable = p.getBoolean("urchinEnable", false);
                urchinBasalPeriod = Integer.parseInt(p.getString("urchinBasalPeriod", "23"));
                urchinBasalScale = Integer.parseInt(p.getString("urchinBasalScale", "0"));
                urchinBolusGraph = p.getBoolean("urchinBolusGraph", false);
                urchinBolusTags = p.getBoolean("urchinBolusTags", false);
                urchinBolusPop = Integer.parseInt(p.getString("urchinBolusPop", "0"));
                urchinTimeStyle = Integer.parseInt(p.getString("urchinTimeStyle", "1"));
                urchinDurationStyle = Integer.parseInt(p.getString("urchinDurationStyle", "1"));
                urchinUnitsStyle = Integer.parseInt(p.getString("urchinUnitsStyle", "1"));
                urchinBatteyStyle = Integer.parseInt(p.getString("urchinBatteyStyle", "1"));
                urchinConcatenateStyle = Integer.parseInt(p.getString("urchinConcatenateStyle", "2"));
                urchinCustomText1 = p.getString("urchinCustomText1", "");
                urchinCustomText2 = p.getString("urchinCustomText2", "");

                int count = 35;
                byte[] urchinStatusLayout = new byte[count];
                for (int i=0; i < count; i++) {
        urchinStatusLayout[i] = (byte) Integer.parseInt(p.getString("urchinStatusLayout" + (i + 1), "0"));
        }
        this.urchinStatusLayout = urchinStatusLayout;

        // pushover
        pushoverEnable = p.getBoolean("pushoverEnable", false);
        pushoverAPItoken = p.getString("pushoverAPItoken", "");
        pushoverUSERtoken = p.getString("pushoverUSERtoken", "");

        if (!pushoverEnable) {
        // will force a validation check when pushover re-enabled
        pushoverAPItokenCheck = "";
        pushoverUSERtokenCheck = "";
        }

        pushoverEnableOnHigh = p.getBoolean("pushoverEnableOnHigh", true);
        pushoverPriorityOnHigh = p.getString("pushoverPriorityOnHigh", "emergency");
        pushoverSoundOnHigh = p.getString("pushoverSoundOnHigh", "persistent");
        pushoverEnableOnLow = p.getBoolean("pushoverEnableOnLow", true);
        pushoverPriorityOnLow = p.getString("pushoverPriorityOnLow", "emergency");
        pushoverSoundOnLow = p.getString("pushoverSoundOnLow", "persistent");
        pushoverEnableBeforeHigh = p.getBoolean("pushoverEnableBeforeHigh", true);
        pushoverPriorityBeforeHigh = p.getString("pushoverPriorityBeforeHigh", "high");
        pushoverSoundBeforeHigh = p.getString("pushoverSoundBeforeHigh", "updown");
        pushoverEnableBeforeLow = p.getBoolean("pushoverEnableBeforeLow", true);
        pushoverPriorityBeforeLow = p.getString("pushoverPriorityBeforeLow", "high");
        pushoverSoundBeforeLow = p.getString("pushoverSoundBeforeLow", "updown");
        pushoverEnableAutoModeExit = p.getBoolean("pushoverEnableAutoModeExit", true);
        pushoverPriorityAutoModeExit = p.getString("pushoverPriorityAutoModeExit", "high");
        pushoverSoundAutoModeExit = p.getString("pushoverSoundAutoModeExit", "updown");

        pushoverEnablePumpEmergency = p.getBoolean("pushoverEnablePumpEmergency", true);
        pushoverPriorityPumpEmergency = p.getString("pushoverPriorityPumpEmergency", "emergency");
        pushoverSoundPumpEmergency = p.getString("pushoverSoundPumpEmergency", "persistent");
        pushoverEnablePumpActionable = p.getBoolean("pushoverEnablePumpActionable", true);
        pushoverPriorityPumpActionable = p.getString("pushoverPriorityPumpActionable", "high");
        pushoverSoundPumpActionable = p.getString("pushoverSoundPumpActionable", "updown");
        pushoverEnablePumpInformational = p.getBoolean("pushoverEnablePumpInformational", true);
        pushoverPriorityPumpInformational = p.getString("pushoverPriorityPumpInformational", "normal");
        pushoverSoundPumpInformational = p.getString("pushoverSoundPumpInformational", "bike");
        pushoverEnablePumpReminder = p.getBoolean("pushoverEnablePumpReminder", true);
        pushoverPriorityPumpReminder = p.getString("pushoverPriorityPumpReminder", "normal");
        pushoverSoundPumpReminder = p.getString("pushoverSoundPumpReminder", "tugboat");

        pushoverEnableBolus = p.getBoolean("pushoverEnableBolus", true);
        pushoverPriorityBolus = p.getString("pushoverPriorityBolus", "normal");
        pushoverSoundBolus = p.getString("pushoverSoundBolus", "classical");
        pushoverEnableBasal = p.getBoolean("pushoverEnableBasal", true);
        pushoverPriorityBasal = p.getString("pushoverPriorityBasal", "normal");
        pushoverSoundBasal = p.getString("pushoverSoundBasal", "pianobar");
        pushoverEnableSuspendResume = p.getBoolean("pushoverEnableSuspendResume", true);
        pushoverPrioritySuspendResume = p.getString("pushoverPrioritySuspendResume", "normal");
        pushoverSoundSuspendResume = p.getString("pushoverSoundSuspendResume", "gamelan");
        pushoverEnableBG = p.getBoolean("pushoverEnableBG", true);
        pushoverPriorityBG = p.getString("pushoverPriorityBG", "normal");
        pushoverSoundBG = p.getString("pushoverSoundBG", "bike");
        pushoverEnableCalibration = p.getBoolean("pushoverEnableCalibration", false);
        pushoverPriorityCalibration = p.getString("pushoverPriorityCalibration", "normal");
        pushoverSoundCalibration = p.getString("pushoverSoundCalibration", "bike");
        pushoverEnableConsumables = p.getBoolean("pushoverEnableConsumables", true);
        pushoverPriorityConsumables = p.getString("pushoverPriorityConsumables", "normal");
        pushoverSoundConsumables = p.getString("pushoverSoundConsumables", "bike");
        pushoverLifetimeInfo = p.getBoolean("pushoverLifetimeInfo", false);
        pushoverEnableDailyTotals = p.getBoolean("pushoverEnableDailyTotals", true);
        pushoverPriorityDailyTotals = p.getString("pushoverPriorityDailyTotals", "normal");
        pushoverSoundDailyTotals = p.getString("pushoverSoundDailyTotals", "bike");

        pushoverEnableUploaderPumpErrors = p.getBoolean("pushoverEnableUploaderPumpErrors", true);
        pushoverPriorityUploaderPumpErrors = p.getString("pushoverPriorityUploaderPumpErrors", "emergency");
        pushoverSoundUploaderPumpErrors = p.getString("pushoverSoundUploaderPumpErrors", "persistent");
        pushoverEnableUploaderPumpConnection = p.getBoolean("pushoverEnableUploaderPumpConnection", true);
        pushoverPriorityUploaderPumpConnection = p.getString("pushoverPriorityUploaderPumpConnection", "normal");
        pushoverSoundUploaderPumpConnection = p.getString("pushoverSoundUploaderPumpConnection", "bike");
        pushoverEnableUploaderBattery = p.getBoolean("pushoverEnableUploaderBattery", true);
        pushoverPriorityUploaderBattery = p.getString("pushoverPriorityUploaderBattery", "normal");
        pushoverSoundUploaderBattery = p.getString("pushoverSoundUploaderBattery", "bike");
        pushoverEnableBatteryLow = p.getBoolean("pushoverEnableBatteryLow", true);
        pushoverEnableBatteryCharged = p.getBoolean("pushoverEnableBatteryCharged", true);

        pushoverEnableCleared = p.getBoolean("pushoverEnableCleared", true);
        pushoverPriorityCleared = p.getString("pushoverPriorityCleared", "low");
        pushoverSoundCleared = p.getString("pushoverSoundCleared", "none");
        pushoverEnableSilenced = p.getBoolean("pushoverEnableSilenced", false);
        pushoverPrioritySilenced = p.getString("pushoverPrioritySilenced", "normal");
        pushoverSoundSilenced = p.getString("pushoverSoundSilenced", "none");

        pushoverEnablePriorityOverride = p.getBoolean("pushoverEnablePriorityOverride", false);
        pushoverPriorityOverride = p.getString("pushoverPriorityOverride", "-2");
        pushoverEnableSoundOverride = p.getBoolean("pushoverEnableSoundOverride", false);
        pushoverSoundOverride = p.getString("pushoverSoundOverride", "none");

        pushoverInfoExtended = p.getBoolean("pushoverInfoExtended", true);
        pushoverTitleTime = p.getBoolean("pushoverTitleTime", true);
        pushoverEmergencyRetry = p.getString("pushoverEmergencyRetry", "30");
        pushoverEmergencyExpire = p.getString("pushoverEmergencyExpire", "3600");

*/