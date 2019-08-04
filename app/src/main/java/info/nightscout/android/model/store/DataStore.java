package info.nightscout.android.model.store;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Date;

import info.nightscout.android.R;
import info.nightscout.android.utils.FormatKit;
import io.realm.RealmObject;

public class DataStore extends RealmObject {

    private long initTimestamp;
    private long startupTimestamp;
    private long cnlUnplugTimestamp;
    private long cnlPlugTimestamp;
    private long cnlLimiterTimestamp;

    private boolean nightscoutInitCleanup;
    private long nightscoutCgmCleanFrom;
    private long nightscoutPumpCleanFrom;
    private long nightscoutAlwaysUpdateTimestamp; // items for upload will always update prior to this time

    private boolean nightscoutUpload;
    private String nightscoutURL = "";
    private String nightscoutSECRET = "";
    private long nightscoutReportTime;
    private boolean nightscoutAvailable;
    private boolean nightscoutCareportal;
    private boolean nightscoutUseQuery;
    private boolean nightscoutUseProfile;

    private boolean requestProfile;
    private boolean requestPumpHistory;
    private boolean requestCgmHistory;
    private boolean requestEstimate;
    private boolean requestIsig;

    private boolean reportIsigAvailable;
    private long reportIsigTimestamp;

    private boolean prefsProcessed;

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

    private boolean resendPumpHistoryBasal;
    private boolean resendPumpHistoryBolus;
    private boolean resendPumpHistoryBG;
    private boolean resendPumpHistoryMisc;
    private boolean resendPumpHistoryAlarm;
    private boolean resendPumpHistorySystem;
    private boolean resendPumpHistoryDaily;
    private boolean resendPumpHistoryPattern;
    private boolean resendPumpHistoryBolusExChanged;

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
    private boolean sysEnableEstimateSGVeol;
    private boolean sysEnableEstimateSGVerror;
    private boolean sysEnableReportISIG;
    private int sysReportISIGinclude;
    private int sysReportISIGminimum;
    private int sysReportISIGnewsensor;
    private boolean sysEnableClashProtect;
    private int sysRssiAllowConnect;
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
    private boolean nsEnableBasalTreatments;
    private boolean nsEnableBolusTreatments;
    private String nsEnteredBy;
    private boolean nsEnableDeviceStatus;
    private boolean nsEnableDevicePUMP;
    private String nsDeviceName;
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
    private boolean nsEnableInsertBGasCGM;
    private boolean nsEnableAlarms;
    private boolean nsAlarmExtended;
    private boolean nsAlarmCleared;

    private boolean nsEnableSystemStatus;
    private boolean nsSystemStatusUsbErrors;
    private boolean nsSystemStatusConnection;
    private boolean nsSystemStatusBatteryLow;
    private boolean nsSystemStatusBatteryCharged;

    private int nsAlarmTTL;
    private boolean nsEnableDailyTotals;
    private boolean nsEnableFormatHTML;
    private boolean nsEnableMedtronicTrendStyle;

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

    private boolean pushoverValidated;
    private boolean pushoverError;
    private String pushoverAPItokenCheck;
    private String pushoverUSERtokenCheck;
    private long pushoverAppLimit;
    private long pushoverAppRemaining;
    private long pushoverAppReset;

    private boolean pushoverEnable;
    private String pushoverAPItoken;
    private String pushoverUSERtoken;

    private boolean pushoverEnablePriorityOverride;
    private String pushoverPriorityOverride;
    private boolean pushoverEnableSoundOverride;
    private String pushoverSoundOverride;

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

    private boolean pushoverEnableAutoModeActive;
    private String pushoverPriorityAutoModeActive;
    private String pushoverSoundAutoModeActive;
    private boolean pushoverEnableAutoModeStop;
    private String pushoverPriorityAutoModeStop;
    private String pushoverSoundAutoModeStop;
    private boolean pushoverEnableAutoModeExit;
    private String pushoverPriorityAutoModeExit;
    private String pushoverSoundAutoModeExit;
    private boolean pushoverEnableAutoModeMinMax;
    private String pushoverPriorityAutoModeMinMax;
    private String pushoverSoundAutoModeMinMax;

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
    private boolean pushoverEnableUploaderUsbErrors;
    private String pushoverPriorityUploaderUsbErrors;
    private String pushoverSoundUploaderUsbErrors;
    private boolean pushoverEnableUploaderStatus;
    private String pushoverPriorityUploaderStatus;
    private String pushoverSoundUploaderStatus;
    private boolean pushoverEnableUploaderStatusConnection;
    private boolean pushoverEnableUploaderStatusEstimate;
    private boolean pushoverEnableUploaderBattery;
    private String pushoverPriorityUploaderBattery;
    private String pushoverSoundUploaderBattery;
    private boolean pushoverEnableBatteryLow;
    private boolean pushoverEnableBatteryCharged;

    private boolean pushoverEnableInfoExtended;
    private boolean pushoverEnableTitleTime;
    private boolean pushoverEnableTitleText;
    private String pushoverTitleText;

    private String pushoverEmergencyRetry;
    private String pushoverEmergencyExpire;

    private boolean pushoverEnableCleared;
    private boolean pushoverEnableClearedAcknowledged;
    private String pushoverPriorityCleared;
    private String pushoverSoundCleared;

    private boolean pushoverEnableSilenced;
    private boolean pushoverEnableSilencedOverride;
    private String pushoverPrioritySilenced;
    private String pushoverSoundSilenced;

    private boolean pushoverEnableBackfillOnStart;
    private int pushoverBackfillPeriod;
    private int pushoverBackfillLimiter;
    private boolean pushoverEnableBackfillOverride;
    private int pushoverBackfillOverrideAge;
    private String pushoverPriorityBackfill;
    private String pushoverSoundBackfill;

    private String pushoverSendToDevice;

    public DataStore() {
        this.initTimestamp = new Date().getTime();
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
        sysEnableEstimateSGVeol = getBoolean(c, p, R.string.key_sysEnableEstimateSGVeol, R.bool.default_sysEnableEstimateSGVeol);
        sysEnableEstimateSGVerror = getBoolean(c, p, R.string.key_sysEnableEstimateSGVerror, R.bool.default_sysEnableEstimateSGVerror);
        sysEnableReportISIG = getBoolean(c, p, R.string.key_sysEnableReportISIG, R.bool.default_sysEnableReportISIG);
        sysReportISIGinclude = getInt(c, p,R.string.key_sysReportISIGinclude, R.string.default_sysReportISIGinclude);
        sysReportISIGminimum = getInt(c, p,R.string.key_sysReportISIGminimum, R.string.default_sysReportISIGminimum);
        sysReportISIGnewsensor = getInt(c, p,R.string.key_sysReportISIGnewsensor, R.string.default_sysReportISIGnewsensor);
        sysEnableClashProtect = getBoolean(c, p, R.string.key_sysEnableClashProtect, R.bool.default_sysEnableClashProtect);
        sysRssiAllowConnect = getInt(c, p, R.string.key_sysRssiAllowConnect, R.string.default_sysRssiAllowConnect);
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

        // nightscout pref changes dynamic re-uploads / deletes
        if (prefsProcessed) {
            if (nsEnableBasalTreatments != getBoolean(c, p, R.string.key_nsEnableBasalTreatments, R.bool.default_nsEnableBasalTreatments)) {
                resendPumpHistoryBasal = true;
            }
            if (nsEnableBolusTreatments != getBoolean(c, p, R.string.key_nsEnableBolusTreatments, R.bool.default_nsEnableBolusTreatments)
                    || nsGramsPerExchange != getInt(c, p, R.string.key_nsGramsPerExchange, R.string.default_nsGramsPerExchange)
                    ) {
                resendPumpHistoryBolus = true;
            }
            if (nsEnableFingerBG != getBoolean(c, p, R.string.key_nsEnableFingerBG, R.bool.default_nsEnableFingerBG)
                    || nsEnableCalibrationInfo != getBoolean(c, p, R.string.key_nsEnableCalibrationInfo, R.bool.default_nsEnableCalibrationInfo)
                    ) {
                resendPumpHistoryBG = true;
                resendPumpHistoryMisc = true;
            }
            if (nsEnableInsertBGasCGM != getBoolean(c, p, R.string.key_nsEnableInsertBGasCGM, R.bool.default_nsEnableInsertBGasCGM)) {
                resendPumpHistoryBG = true;
            }
            if (nsEnableSensorChange != getBoolean(c, p, R.string.key_nsEnableSensorChange, R.bool.default_nsEnableSensorChange)
                    || nsEnableReservoirChange != getBoolean(c, p, R.string.key_nsEnableReservoirChange, R.bool.default_nsEnableReservoirChange)
                    || nsEnableInsulinChange != getBoolean(c, p, R.string.key_nsEnableInsulinChange, R.bool.default_nsEnableInsulinChange)
                    || nsEnableBatteryChange != getBoolean(c, p, R.string.key_nsEnableBatteryChange, R.bool.default_nsEnableBatteryChange)
                    || nsEnableLifetimes != getBoolean(c, p, R.string.key_nsEnableLifetimes, R.bool.default_nsEnableLifetimes)
                    || nsCannulaChangeThreshold != getInt(c, p, R.string.key_nsCannulaChangeThreshold, R.string.default_nsCannulaChangeThreshold)
                    || nsInsulinChangeThreshold != getInt(c, p, R.string.key_nsInsulinChangeThreshold, R.string.default_nsInsulinChangeThreshold)
                    ) {
                resendPumpHistoryMisc = true;
            }
            if (nsActiveInsulinTime != getFloat(c, p, R.string.key_nsActiveInsulinTime, R.string.default_nsActiveInsulinTime)
                    || nsProfileDefault != getInt(c, p, R.string.key_nsProfileDefault, R.string.default_nsProfileDefault)
                    ) {
                requestProfile = true;
            }
            if (nsAlarmExtended != getBoolean(c, p, R.string.key_nsAlarmExtended, R.bool.default_nsAlarmExtended)
                    || nsAlarmCleared != getBoolean(c, p, R.string.key_nsAlarmCleared, R.bool.default_nsAlarmCleared)
                    || nsSystemStatusUsbErrors != getBoolean(c, p, R.string.key_nsSystemStatusUsbErrors, R.bool.default_nsSystemStatusUsbErrors)
                    || nsSystemStatusConnection != getBoolean(c, p, R.string.key_nsSystemStatusConnection, R.bool.default_nsSystemStatusConnection)
                    || nsSystemStatusBatteryLow != getBoolean(c, p, R.string.key_nsSystemStatusBatteryLow, R.bool.default_nsSystemStatusBatteryLow)
                    || nsSystemStatusBatteryCharged != getBoolean(c, p, R.string.key_nsSystemStatusBatteryCharged, R.bool.default_nsSystemStatusBatteryCharged)
                    ) {
                resendPumpHistoryAlarm = true;
            }
            if (nsSystemStatusUsbErrors != getBoolean(c, p, R.string.key_nsSystemStatusUsbErrors, R.bool.default_nsSystemStatusUsbErrors)
                    || nsSystemStatusConnection != getBoolean(c, p, R.string.key_nsSystemStatusConnection, R.bool.default_nsSystemStatusConnection)
                    || nsSystemStatusBatteryLow != getBoolean(c, p, R.string.key_nsSystemStatusBatteryLow, R.bool.default_nsSystemStatusBatteryLow)
                    || nsSystemStatusBatteryCharged != getBoolean(c, p, R.string.key_nsSystemStatusBatteryCharged, R.bool.default_nsSystemStatusBatteryCharged)
                    ) {
                resendPumpHistorySystem = true;
            }
            if (nsEnableDailyTotals != getBoolean(c, p, R.string.key_nsEnableDailyTotals, R.bool.default_nsEnableDailyTotals)) {
                resendPumpHistoryDaily = true;
            }
            if (nsEnablePatternChange != getBoolean(c, p, R.string.key_nsEnablePatternChange, R.bool.default_nsEnablePatternChange)) {
                resendPumpHistoryPattern = true;
            }
            if (!nameBasalPattern1.equals(p.getString(c.getString(R.string.key_nameBasalPatternP1), ""))
                    || !nameBasalPattern2.equals(p.getString(c.getString(R.string.key_nameBasalPatternP2), ""))
                    || !nameBasalPattern3.equals(p.getString(c.getString(R.string.key_nameBasalPatternP3), ""))
                    || !nameBasalPattern4.equals(p.getString(c.getString(R.string.key_nameBasalPatternP4), ""))
                    || !nameBasalPattern5.equals(p.getString(c.getString(R.string.key_nameBasalPatternP5), ""))
                    || !nameBasalPattern6.equals(p.getString(c.getString(R.string.key_nameBasalPatternP6), ""))
                    || !nameBasalPattern7.equals(p.getString(c.getString(R.string.key_nameBasalPatternP7), ""))
                    || !nameBasalPattern8.equals(p.getString(c.getString(R.string.key_nameBasalPatternP8), ""))
                    ) {
                resendPumpHistoryPattern = true;
                requestProfile = true;
            }
            if (!nameTempBasalPreset1.equals(p.getString(c.getString(R.string.key_nameTempBasalPresetP1), ""))
                    || !nameTempBasalPreset2.equals(p.getString(c.getString(R.string.key_nameTempBasalPresetP2), ""))
                    || !nameTempBasalPreset3.equals(p.getString(c.getString(R.string.key_nameTempBasalPresetP3), ""))
                    || !nameTempBasalPreset4.equals(p.getString(c.getString(R.string.key_nameTempBasalPresetP4), ""))
                    || !nameTempBasalPreset5.equals(p.getString(c.getString(R.string.key_nameTempBasalPresetP5), ""))
                    || !nameTempBasalPreset6.equals(p.getString(c.getString(R.string.key_nameTempBasalPresetP6), ""))
                    || !nameTempBasalPreset7.equals(p.getString(c.getString(R.string.key_nameTempBasalPresetP7), ""))
                    || !nameTempBasalPreset8.equals(p.getString(c.getString(R.string.key_nameTempBasalPresetP8), ""))
                    ) {
                resendPumpHistoryBasal = true;
            }
            if (!nameBolusPreset1.equals(p.getString(c.getString(R.string.key_nameBolusPresetP1), ""))
                    || !nameBolusPreset2.equals(p.getString(c.getString(R.string.key_nameBolusPresetP2), ""))
                    || !nameBolusPreset3.equals(p.getString(c.getString(R.string.key_nameBolusPresetP3), ""))
                    || !nameBolusPreset4.equals(p.getString(c.getString(R.string.key_nameBolusPresetP4), ""))
                    || !nameBolusPreset5.equals(p.getString(c.getString(R.string.key_nameBolusPresetP5), ""))
                    || !nameBolusPreset6.equals(p.getString(c.getString(R.string.key_nameBolusPresetP6), ""))
                    || !nameBolusPreset7.equals(p.getString(c.getString(R.string.key_nameBolusPresetP7), ""))
                    || !nameBolusPreset8.equals(p.getString(c.getString(R.string.key_nameBolusPresetP8), ""))
                    ){
                resendPumpHistoryBolus = true;
            }
        }

        // nightscout
        nsEnableTreatments = getBoolean(c, p, R.string.key_nsEnableTreatments, R.bool.default_nsEnableTreatments);
        nsEnableBasalTreatments = getBoolean(c, p, R.string.key_nsEnableBasalTreatments, R.bool.default_nsEnableBasalTreatments);
        nsEnableBolusTreatments = getBoolean(c, p, R.string.key_nsEnableBolusTreatments, R.bool.default_nsEnableBolusTreatments);
        nsEnteredBy = getString(c, p, R.string.key_nsEnteredBy, R.string.default_nsEnteredBy);
        nsEnableDeviceStatus = getBoolean(c, p, R.string.key_nsEnableDeviceStatus, R.bool.default_nsEnableDeviceStatus);
        nsEnableDevicePUMP = getBoolean(c, p, R.string.key_nsEnableDevicePUMP, R.bool.default_nsEnableDevicePUMP);
        nsDeviceName = getString(c, p, R.string.key_nsDeviceName, R.string.default_nsDeviceName);

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
        nsSystemStatusUsbErrors = getBoolean(c, p, R.string.key_nsSystemStatusUsbErrors, R.bool.default_nsSystemStatusUsbErrors);
        nsSystemStatusConnection = getBoolean(c, p, R.string.key_nsSystemStatusConnection, R.bool.default_nsSystemStatusConnection);
        nsSystemStatusBatteryLow = getBoolean(c, p, R.string.key_nsSystemStatusBatteryLow, R.bool.default_nsSystemStatusBatteryLow);
        nsSystemStatusBatteryCharged = getBoolean(c, p, R.string.key_nsSystemStatusBatteryCharged, R.bool.default_nsSystemStatusBatteryCharged);
        nsAlarmTTL = getInt(c, p, R.string.key_nsAlarmTTL, R.string.default_nsAlarmTTL);
        nsEnableDailyTotals = getBoolean(c, p, R.string.key_nsEnableDailyTotals, R.bool.default_nsEnableDailyTotals);
        nsEnableFormatHTML = getBoolean(c, p, R.string.key_nsEnableFormatHTML, R.bool.default_nsEnableFormatHTML);
        nsEnableMedtronicTrendStyle = getBoolean(c, p, R.string.key_nsEnableMedtronicTrendStyle, R.bool.default_nsEnableMedtronicTrendStyle);
        nsGramsPerExchange = getInt(c, p, R.string.key_nsGramsPerExchange, R.string.default_nsGramsPerExchange);

        nameBasalPattern1 = p.getString(c.getString(R.string.key_nameBasalPatternP1), "");
        nameBasalPattern2 = p.getString(c.getString(R.string.key_nameBasalPatternP2), "");
        nameBasalPattern3 = p.getString(c.getString(R.string.key_nameBasalPatternP3), "");
        nameBasalPattern4 = p.getString(c.getString(R.string.key_nameBasalPatternP4), "");
        nameBasalPattern5 = p.getString(c.getString(R.string.key_nameBasalPatternP5), "");
        nameBasalPattern6 = p.getString(c.getString(R.string.key_nameBasalPatternP6), "");
        nameBasalPattern7 = p.getString(c.getString(R.string.key_nameBasalPatternP7), "");
        nameBasalPattern8 = p.getString(c.getString(R.string.key_nameBasalPatternP8), "");
        nameTempBasalPreset1 = p.getString(c.getString(R.string.key_nameTempBasalPresetP1), "");
        nameTempBasalPreset2 = p.getString(c.getString(R.string.key_nameTempBasalPresetP2), "");
        nameTempBasalPreset3 = p.getString(c.getString(R.string.key_nameTempBasalPresetP3), "");
        nameTempBasalPreset4 = p.getString(c.getString(R.string.key_nameTempBasalPresetP4), "");
        nameTempBasalPreset5 = p.getString(c.getString(R.string.key_nameTempBasalPresetP5), "");
        nameTempBasalPreset6 = p.getString(c.getString(R.string.key_nameTempBasalPresetP6), "");
        nameTempBasalPreset7 = p.getString(c.getString(R.string.key_nameTempBasalPresetP7), "");
        nameTempBasalPreset8 = p.getString(c.getString(R.string.key_nameTempBasalPresetP8), "");
        nameBolusPreset1 = p.getString(c.getString(R.string.key_nameBolusPresetP1), "");
        nameBolusPreset2 = p.getString(c.getString(R.string.key_nameBolusPresetP2), "");
        nameBolusPreset3 = p.getString(c.getString(R.string.key_nameBolusPresetP3), "");
        nameBolusPreset4 = p.getString(c.getString(R.string.key_nameBolusPresetP4), "");
        nameBolusPreset5 = p.getString(c.getString(R.string.key_nameBolusPresetP5), "");
        nameBolusPreset6 = p.getString(c.getString(R.string.key_nameBolusPresetP6), "");
        nameBolusPreset7 = p.getString(c.getString(R.string.key_nameBolusPresetP7), "");
        nameBolusPreset8 = p.getString(c.getString(R.string.key_nameBolusPresetP8), "");

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
                    c.getString(R.string.default_urchinStatusLayout_Empty)));
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

        pushoverEnablePriorityOverride = getBoolean(c, p, R.string.key_pushoverEnablePriorityOverride, R.bool.default_pushoverEnablePriorityOverride);
        pushoverPriorityOverride = getString(c, p, R.string.key_pushoverPriorityOverride, R.string.default_pushoverPriorityOverride);
        pushoverEnableSoundOverride = getBoolean(c, p, R.string.key_pushoverEnableSoundOverride, R.bool.default_pushoverEnableSoundOverride);
        pushoverSoundOverride = getString(c, p, R.string.key_pushoverSoundOverride, R.string.default_pushoverSoundOverride);

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

        pushoverEnableAutoModeActive = getBoolean(c, p, R.string.key_pushoverEnableAutoModeActive, R.bool.default_pushoverEnableAutoModeActive);
        pushoverPriorityAutoModeActive = getString(c, p, R.string.key_pushoverPriorityAutoModeActive, R.string.default_pushoverPriorityAutoModeActive);
        pushoverSoundAutoModeActive = getString(c, p, R.string.key_pushoverSoundAutoModeActive, R.string.default_pushoverSoundAutoModeActive);
        pushoverEnableAutoModeStop = getBoolean(c, p, R.string.key_pushoverEnableAutoModeStop, R.bool.default_pushoverEnableAutoModeStop);
        pushoverPriorityAutoModeStop = getString(c, p, R.string.key_pushoverPriorityAutoModeStop, R.string.default_pushoverPriorityAutoModeStop);
        pushoverSoundAutoModeStop = getString(c, p, R.string.key_pushoverSoundAutoModeStop, R.string.default_pushoverSoundAutoModeStop);
        pushoverEnableAutoModeExit = getBoolean(c, p, R.string.key_pushoverEnableAutoModeExit, R.bool.default_pushoverEnableAutoModeExit);
        pushoverPriorityAutoModeExit = getString(c, p, R.string.key_pushoverPriorityAutoModeExit, R.string.default_pushoverPriorityAutoModeExit);
        pushoverSoundAutoModeExit = getString(c, p, R.string.key_pushoverSoundAutoModeExit, R.string.default_pushoverSoundAutoModeExit);
        pushoverEnableAutoModeMinMax = getBoolean(c, p, R.string.key_pushoverEnableAutoModeMinMax, R.bool.default_pushoverEnableAutoModeMinMax);
        pushoverPriorityAutoModeMinMax = getString(c, p, R.string.key_pushoverPriorityAutoModeMinMax, R.string.default_pushoverPriorityAutoModeMinMax);
        pushoverSoundAutoModeMinMax = getString(c, p, R.string.key_pushoverSoundAutoModeMinMax, R.string.default_pushoverSoundAutoModeMinMax);

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
        pushoverEnableUploaderUsbErrors = getBoolean(c, p, R.string.key_pushoverEnableUploaderUsbErrors, R.bool.default_pushoverEnableUploaderUsbErrors);
        pushoverPriorityUploaderUsbErrors = getString(c, p, R.string.key_pushoverPriorityUploaderUsbErrors, R.string.default_pushoverPriorityUploaderUsbErrors);
        pushoverSoundUploaderUsbErrors = getString(c, p, R.string.key_pushoverSoundUploaderUsbErrors, R.string.default_pushoverSoundUploaderUsbErrors);
        pushoverEnableUploaderStatus = getBoolean(c, p, R.string.key_pushoverEnableUploaderStatus, R.bool.default_pushoverEnableUploaderStatus);
        pushoverPriorityUploaderStatus = getString(c, p, R.string.key_pushoverPriorityUploaderStatus, R.string.default_pushoverPriorityUploaderStatus);
        pushoverSoundUploaderStatus = getString(c, p, R.string.key_pushoverSoundUploaderStatus, R.string.default_pushoverSoundUploaderStatus);
        pushoverEnableUploaderStatusConnection = getBoolean(c, p, R.string.key_pushoverEnableUploaderStatusConnection, R.bool.default_pushoverEnableUploaderStatusConnection);
        pushoverEnableUploaderStatusEstimate = getBoolean(c, p, R.string.key_pushoverEnableUploaderStatusEstimate, R.bool.default_pushoverEnableUploaderStatusEstimate);
        pushoverEnableUploaderBattery = getBoolean(c, p, R.string.key_pushoverEnableUploaderBattery, R.bool.default_pushoverEnableUploaderBattery);
        pushoverPriorityUploaderBattery = getString(c, p, R.string.key_pushoverPriorityUploaderBattery, R.string.default_pushoverPriorityUploaderBattery);
        pushoverSoundUploaderBattery = getString(c, p, R.string.key_pushoverSoundUploaderBattery, R.string.default_pushoverSoundUploaderBattery);
        pushoverEnableBatteryLow = getBoolean(c, p, R.string.key_pushoverEnableBatteryLow, R.bool.default_pushoverEnableBatteryLow);
        pushoverEnableBatteryCharged = getBoolean(c, p, R.string.key_pushoverEnableBatteryCharged, R.bool.default_pushoverEnableBatteryCharged);

        pushoverEnableInfoExtended = getBoolean(c, p, R.string.key_pushoverEnableInfoExtended, R.bool.default_pushoverEnableInfoExtended);
        pushoverEnableTitleTime = getBoolean(c, p, R.string.key_pushoverEnableTitleTime, R.bool.default_pushoverEnableTitleTime);
        pushoverEnableTitleText = getBoolean(c, p, R.string.key_pushoverEnableTitleText, R.bool.default_pushoverEnableTitleText);
        pushoverTitleText = getString(c, p, R.string.key_pushoverTitleText, R.string.default_pushoverTitleText);

        pushoverEmergencyRetry = getString(c, p, R.string.key_pushoverEmergencyRetry, R.string.default_pushoverEmergencyRetry);
        pushoverEmergencyExpire = getString(c, p, R.string.key_pushoverEmergencyExpire, R.string.default_pushoverEmergencyExpire);

        pushoverEnableCleared = getBoolean(c, p, R.string.key_pushoverEnableCleared, R.bool.default_pushoverEnableCleared);
        pushoverEnableClearedAcknowledged = getBoolean(c, p, R.string.key_pushoverEnableClearedAcknowledged, R.bool.default_pushoverEnableClearedAcknowledged);
        pushoverPriorityCleared = getString(c, p, R.string.key_pushoverPriorityCleared, R.string.default_pushoverPriorityCleared);
        pushoverSoundCleared = getString(c, p, R.string.key_pushoverSoundCleared, R.string.default_pushoverSoundCleared);

        pushoverEnableSilenced = getBoolean(c, p, R.string.key_pushoverEnableSilenced, R.bool.default_pushoverEnableSilenced);
        pushoverEnableSilencedOverride = getBoolean(c, p, R.string.key_pushoverEnableSilencedOverride, R.bool.default_pushoverEnableSilencedOverride);
        pushoverPrioritySilenced = getString(c, p, R.string.key_pushoverPrioritySilenced, R.string.default_pushoverPrioritySilenced);
        pushoverSoundSilenced = getString(c, p, R.string.key_pushoverSoundSilenced, R.string.default_pushoverSoundSilenced);

        pushoverEnableBackfillOnStart = getBoolean(c, p, R.string.key_pushoverEnableBackfillOnStart, R.bool.default_pushoverEnableBackfillOnStart);
        pushoverBackfillPeriod = getInt(c, p, R.string.key_pushoverBackfillPeriod, R.string.default_pushoverBackfillPeriod);
        pushoverBackfillLimiter = getInt(c, p, R.string.key_pushoverBackfillLimiter, R.string.default_pushoverBackfillLimiter);
        pushoverEnableBackfillOverride = getBoolean(c, p, R.string.key_pushoverEnableBackfillOverride, R.bool.default_pushoverEnableBackfillOverride);
        pushoverBackfillOverrideAge = getInt(c, p, R.string.key_pushoverBackfillOverrideAge, R.string.default_pushoverBackfillOverrideAge);
        pushoverPriorityBackfill = getString(c, p, R.string.key_pushoverPriorityBackfill, R.string.default_pushoverPriorityBackfill);
        pushoverSoundBackfill = getString(c, p, R.string.key_pushoverSoundBackfill, R.string.default_pushoverSoundBackfill);

        pushoverSendToDevice = getString(c, p, R.string.key_pushoverSendToDevice, R.string.default_pushoverSendToDevice);

        prefsProcessed = true;
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

    public String getLastStatReport() {
        return lastStatReport;
    }

    public void setLastStatReport(String lastStatReport) {
        this.lastStatReport = lastStatReport;
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

    public boolean isNightscoutInitCleanup() {
        return nightscoutInitCleanup;
    }

    public void setNightscoutInitCleanup(boolean nightscoutInitCleanup) {
        this.nightscoutInitCleanup = nightscoutInitCleanup;
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

    public boolean isNightscoutUseQuery() {
        return nightscoutUseQuery;
    }

    public void setNightscoutUseQuery(boolean nightscoutUseQuery) {
        this.nightscoutUseQuery = nightscoutUseQuery;
    }

    public boolean isNightscoutUseProfile() {
        return nightscoutUseProfile;
    }

    public void setNightscoutUseProfile(boolean nightscoutUseProfile) {
        this.nightscoutUseProfile = nightscoutUseProfile;
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

    public boolean isSysEnableEstimateSGVeol() {
        return sysEnableEstimateSGVeol;
    }

    public boolean isSysEnableEstimateSGVerror() {
        return sysEnableEstimateSGVerror;
    }

    public boolean isSysEnableReportISIG() {
        return sysEnableReportISIG;
    }

    public int getSysReportISIGinclude() {
        return sysReportISIGinclude;
    }

    public int getSysReportISIGminimum() {
        return sysReportISIGminimum;
    }

    public int getSysReportISIGnewsensor() {
        return sysReportISIGnewsensor;
    }

    public boolean isSysEnableClashProtect() {
        return sysEnableClashProtect;
    }

    public int getSysRssiAllowConnect() {
        return sysRssiAllowConnect;
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

    public boolean isResendPumpHistoryBasal() {
        return resendPumpHistoryBasal;
    }

    public void setResendPumpHistoryBasal(boolean resendPumpHistoryBasal) {
        this.resendPumpHistoryBasal = resendPumpHistoryBasal;
    }

    public boolean isResendPumpHistoryBolus() {
        return resendPumpHistoryBolus;
    }

    public void setResendPumpHistoryBolus(boolean resendPumpHistoryBolus) {
        this.resendPumpHistoryBolus = resendPumpHistoryBolus;
    }

    public boolean isResendPumpHistoryBG() {
        return resendPumpHistoryBG;
    }

    public void setResendPumpHistoryBG(boolean resendPumpHistoryBG) {
        this.resendPumpHistoryBG = resendPumpHistoryBG;
    }

    public boolean isResendPumpHistoryMisc() {
        return resendPumpHistoryMisc;
    }

    public void setResendPumpHistoryMisc(boolean resendPumpHistoryMisc) {
        this.resendPumpHistoryMisc = resendPumpHistoryMisc;
    }

    public boolean isResendPumpHistoryAlarm() {
        return resendPumpHistoryAlarm;
    }

    public void setResendPumpHistoryAlarm(boolean resendPumpHistoryAlarm) {
        this.resendPumpHistoryAlarm = resendPumpHistoryAlarm;
    }

    public boolean isResendPumpHistorySystem() {
        return resendPumpHistorySystem;
    }

    public void setResendPumpHistorySystem(boolean resendPumpHistorySystem) {
        this.resendPumpHistorySystem = resendPumpHistorySystem;
    }

    public boolean isResendPumpHistoryDaily() {
        return resendPumpHistoryDaily;
    }

    public void setResendPumpHistoryDaily(boolean resendPumpHistoryDaily) {
        this.resendPumpHistoryDaily = resendPumpHistoryDaily;
    }

    public boolean isResendPumpHistoryPattern() {
        return resendPumpHistoryPattern;
    }

    public void setResendPumpHistoryPattern(boolean resendPumpHistoryPattern) {
        this.resendPumpHistoryPattern = resendPumpHistoryPattern;
    }

    public boolean isResendPumpHistoryBolusExChanged() {
        return resendPumpHistoryBolusExChanged;
    }

    public void setResendPumpHistoryBolusExChanged(boolean resendPumpHistoryBolusExChanged) {
        this.resendPumpHistoryBolusExChanged = resendPumpHistoryBolusExChanged;
    }

    public boolean isNsEnableTreatments() {
        return nsEnableTreatments;
    }

    public boolean isNsEnableBasalTreatments() {
        return nsEnableBasalTreatments;
    }

    public boolean isNsEnableBolusTreatments() {
        return nsEnableBolusTreatments;
    }

    public String getNsEnteredBy() {
        return nsEnteredBy;
    }

    public boolean isNsEnableDeviceStatus() {
        return nsEnableDeviceStatus;
    }

    public boolean isNsEnableDevicePUMP() {
        return nsEnableDevicePUMP;
    }

    public String getNsDeviceName() {
        return nsDeviceName;
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

    public boolean isNsSystemStatusUsbErrors() {
        return nsSystemStatusUsbErrors;
    }

    public boolean isNsSystemStatusConnection() {
        return nsSystemStatusConnection;
    }

    public boolean isNsSystemStatusBatteryLow() {
        return nsSystemStatusBatteryLow;
    }

    public boolean isNsSystemStatusBatteryCharged() {
        return nsSystemStatusBatteryCharged;
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

    public boolean isNsEnableMedtronicTrendStyle() {
        return nsEnableMedtronicTrendStyle;
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
        return nameBasalPattern1.isEmpty() ?
                FormatKit.getInstance().getString(R.string.default_nameBasalPatternP1) : nameBasalPattern1;
    }

    public String getNameBasalPattern2() {
        return nameBasalPattern2.isEmpty() ?
                FormatKit.getInstance().getString(R.string.default_nameBasalPatternP2) : nameBasalPattern2;
    }

    public String getNameBasalPattern3() {
        return nameBasalPattern3.isEmpty() ?
                FormatKit.getInstance().getString(R.string.default_nameBasalPatternP3) : nameBasalPattern3;
    }

    public String getNameBasalPattern4() {
        return nameBasalPattern4.isEmpty() ?
                FormatKit.getInstance().getString(R.string.default_nameBasalPatternP4) : nameBasalPattern4;
    }

    public String getNameBasalPattern5() {
        return nameBasalPattern5.isEmpty() ?
                FormatKit.getInstance().getString(R.string.default_nameBasalPatternP5) : nameBasalPattern5;
    }

    public String getNameBasalPattern6() {
        return nameBasalPattern6.isEmpty() ?
                FormatKit.getInstance().getString(R.string.default_nameBasalPatternP6) : nameBasalPattern6;
    }

    public String getNameBasalPattern7() {
        return nameBasalPattern7.isEmpty() ?
                FormatKit.getInstance().getString(R.string.default_nameBasalPatternP7) : nameBasalPattern7;
    }

    public String getNameBasalPattern8() {
        return nameBasalPattern8.isEmpty() ?
                FormatKit.getInstance().getString(R.string.default_nameBasalPatternP8) : nameBasalPattern8;
    }

    public String getNameTempBasalPreset1() {
        return nameTempBasalPreset1.isEmpty() ?
                FormatKit.getInstance().getString(R.string.default_nameTempBasalPresetP1) : nameTempBasalPreset1;
    }

    public String getNameTempBasalPreset2() {
        return nameTempBasalPreset2.isEmpty() ?
                FormatKit.getInstance().getString(R.string.default_nameTempBasalPresetP2) : nameTempBasalPreset2;
    }

    public String getNameTempBasalPreset3() {
        return nameTempBasalPreset3.isEmpty() ?
                FormatKit.getInstance().getString(R.string.default_nameTempBasalPresetP3) : nameTempBasalPreset3;
    }

    public String getNameTempBasalPreset4() {
        return nameTempBasalPreset4.isEmpty() ?
                FormatKit.getInstance().getString(R.string.default_nameTempBasalPresetP4) : nameTempBasalPreset4;
    }

    public String getNameTempBasalPreset5() {
        return nameTempBasalPreset5.isEmpty() ?
                FormatKit.getInstance().getString(R.string.default_nameTempBasalPresetP5) : nameTempBasalPreset5;
    }

    public String getNameTempBasalPreset6() {
        return nameTempBasalPreset6.isEmpty() ?
                FormatKit.getInstance().getString(R.string.default_nameTempBasalPresetP6) : nameTempBasalPreset6;
    }

    public String getNameTempBasalPreset7() {
        return nameTempBasalPreset7.isEmpty() ?
                FormatKit.getInstance().getString(R.string.default_nameTempBasalPresetP7) : nameTempBasalPreset7;
    }

    public String getNameTempBasalPreset8() {
        return nameTempBasalPreset8.isEmpty() ?
                FormatKit.getInstance().getString(R.string.default_nameTempBasalPresetP8) : nameTempBasalPreset8;
    }

    public String getNameBolusPreset1() {
        return nameBolusPreset1.isEmpty() ?
                FormatKit.getInstance().getString(R.string.default_nameBolusPresetP1) : nameBolusPreset1;
    }

    public String getNameBolusPreset2() {
        return nameBolusPreset2.isEmpty() ?
                FormatKit.getInstance().getString(R.string.default_nameBolusPresetP2) : nameBolusPreset2;
    }

    public String getNameBolusPreset3() {
        return nameBolusPreset3.isEmpty() ?
                FormatKit.getInstance().getString(R.string.default_nameBolusPresetP3) : nameBolusPreset3;
    }

    public String getNameBolusPreset4() {
        return nameBolusPreset4.isEmpty() ?
                FormatKit.getInstance().getString(R.string.default_nameBolusPresetP4) : nameBolusPreset4;
    }

    public String getNameBolusPreset5() {
        return nameBolusPreset5.isEmpty() ?
                FormatKit.getInstance().getString(R.string.default_nameBolusPresetP5) : nameBolusPreset5;
    }

    public String getNameBolusPreset6() {
        return nameBolusPreset6.isEmpty() ?
                FormatKit.getInstance().getString(R.string.default_nameBolusPresetP6) : nameBolusPreset6;
    }

    public String getNameBolusPreset7() {
        return nameBolusPreset7.isEmpty() ?
                FormatKit.getInstance().getString(R.string.default_nameBolusPresetP7) : nameBolusPreset7;
    }

    public String getNameBolusPreset8() {
        return nameBolusPreset8.isEmpty() ?
                FormatKit.getInstance().getString(R.string.default_nameBolusPresetP8) : nameBolusPreset8;
    }

    public String getNameBasalPattern(int value) {
        switch (value) {
            case 1: return getNameBasalPattern1();
            case 2: return getNameBasalPattern2();
            case 3: return getNameBasalPattern3();
            case 4: return getNameBasalPattern4();
            case 5: return getNameBasalPattern5();
            case 6: return getNameBasalPattern6();
            case 7: return getNameBasalPattern7();
            case 8: return getNameBasalPattern8();
        }
        return "";
    }

    public String getNameTempBasalPreset(int value) {
        switch (value) {
            case 1: return getNameTempBasalPreset1();
            case 2: return getNameTempBasalPreset2();
            case 3: return getNameTempBasalPreset3();
            case 4: return getNameTempBasalPreset4();
            case 5: return getNameTempBasalPreset5();
            case 6: return getNameTempBasalPreset6();
            case 7: return getNameTempBasalPreset7();
            case 8: return getNameTempBasalPreset8();
        }
        return "";
    }

    public String getNameBolusPreset(int value) {
        switch (value) {
            case 1: return getNameBolusPreset1();
            case 2: return getNameBolusPreset2();
            case 3: return getNameBolusPreset3();
            case 4: return getNameBolusPreset4();
            case 5: return getNameBolusPreset5();
            case 6: return getNameBolusPreset6();
            case 7: return getNameBolusPreset7();
            case 8: return getNameBolusPreset8();
        }
        return "";
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

    public boolean isPushoverEnableAutoModeActive() {
        return pushoverEnableAutoModeActive;
    }

    public String getPushoverPriorityAutoModeActive() {
        return pushoverPriorityAutoModeActive;
    }

    public String getPushoverSoundAutoModeActive() {
        return pushoverSoundAutoModeActive;
    }

    public boolean isPushoverEnableAutoModeStop() {
        return pushoverEnableAutoModeStop;
    }

    public String getPushoverPriorityAutoModeStop() {
        return pushoverPriorityAutoModeStop;
    }

    public String getPushoverSoundAutoModeStop() {
        return pushoverSoundAutoModeStop;
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

    public boolean isPushoverEnableAutoModeMinMax() {
        return pushoverEnableAutoModeMinMax;
    }

    public String getPushoverPriorityAutoModeMinMax() {
        return pushoverPriorityAutoModeMinMax;
    }

    public String getPushoverSoundAutoModeMinMax() {
        return pushoverSoundAutoModeMinMax;
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

    public boolean isPushoverEnableUploaderUsbErrors() {
        return pushoverEnableUploaderUsbErrors;
    }

    public String getPushoverPriorityUploaderUsbErrors() {
        return pushoverPriorityUploaderUsbErrors;
    }

    public String getPushoverSoundUploaderUsbErrors() {
        return pushoverSoundUploaderUsbErrors;
    }

    public boolean isPushoverEnableUploaderStatus() {
        return pushoverEnableUploaderStatus;
    }

    public String getPushoverPriorityUploaderStatus() {
        return pushoverPriorityUploaderStatus;
    }

    public String getPushoverSoundUploaderStatus() {
        return pushoverSoundUploaderStatus;
    }

    public boolean isPushoverEnableUploaderStatusConnection() {
        return pushoverEnableUploaderStatusConnection;
    }

    public boolean isPushoverEnableUploaderStatusEstimate() {
        return pushoverEnableUploaderStatusEstimate;
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

    public boolean isPushoverEnableInfoExtended() {
        return pushoverEnableInfoExtended;
    }

    public boolean isPushoverEnableTitleTime() {
        return pushoverEnableTitleTime;
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

    public boolean isPushoverEnableTitleText() {
        return pushoverEnableTitleText;
    }

    public String getPushoverTitleText() {
        return pushoverTitleText;
    }

    public boolean isPushoverEnableClearedAcknowledged() {
        return pushoverEnableClearedAcknowledged;
    }

    public boolean isPushoverEnableSilencedOverride() {
        return pushoverEnableSilencedOverride;
    }

    public boolean isPushoverEnableBackfillOnStart() {
        return pushoverEnableBackfillOnStart;
    }

    public int getPushoverBackfillPeriod() {
        return pushoverBackfillPeriod;
    }

    public int getPushoverBackfillLimiter() {
        return pushoverBackfillLimiter;
    }

    public boolean isPushoverEnableBackfillOverride() {
        return pushoverEnableBackfillOverride;
    }

    public int getPushoverBackfillOverrideAge() {
        return pushoverBackfillOverrideAge;
    }

    public String getPushoverPriorityBackfill() {
        return pushoverPriorityBackfill;
    }

    public String getPushoverSoundBackfill() {
        return pushoverSoundBackfill;
    }

    public String getPushoverSendToDevice() {
        return pushoverSendToDevice;
    }

    public long getInitTimestamp() {
        return initTimestamp;
    }

    public void setInitTimestamp(long initTimestamp) {
        this.initTimestamp = initTimestamp;
    }

    public long getStartupTimestamp() {
        return startupTimestamp;
    }

    public void setStartupTimestamp(long startupTimestamp) {
        this.startupTimestamp = startupTimestamp;
    }

    public long getCnlUnplugTimestamp() {
        return cnlUnplugTimestamp;
    }

    public void setCnlUnplugTimestamp(long cnlUnplugTimestamp) {
        this.cnlUnplugTimestamp = cnlUnplugTimestamp;
    }

    public long getCnlPlugTimestamp() {
        return cnlPlugTimestamp;
    }

    public void setCnlPlugTimestamp(long cnlPlugTimestamp) {
        this.cnlPlugTimestamp = cnlPlugTimestamp;
    }

    public long getCnlLimiterTimestamp() {
        return cnlLimiterTimestamp;
    }

    public void setCnlLimiterTimestamp(long cnlLimiterTimestamp) {
        this.cnlLimiterTimestamp = cnlLimiterTimestamp;
    }

    public long getReportIsigTimestamp() {
        return reportIsigTimestamp;
    }

    public void setReportIsigTimestamp(long reportIsigTimestamp) {
        this.reportIsigTimestamp = reportIsigTimestamp;
    }

    public boolean isReportIsigAvailable() {
        return reportIsigAvailable;
    }

    public void setReportIsigAvailable(boolean reportIsigAvailable) {
        this.reportIsigAvailable = reportIsigAvailable;
    }
}