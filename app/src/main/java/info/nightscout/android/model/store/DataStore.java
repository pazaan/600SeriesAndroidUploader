package info.nightscout.android.model.store;

import java.util.Date;

import io.realm.RealmObject;
import io.realm.annotations.Index;

public class DataStore extends RealmObject {
    @Index
    private long timestamp;

    //private boolean debug_bump;

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

    private int pumpCgmNA;

    private int commsSuccess;
    private int commsError;
    private int commsConnectError;
    private int commsSignalError;
    private int commsSgvSuccess;
    private int pumpLostSensorError;
    private int pumpClockError;
    private int pumpBatteryError;

    private boolean mmolxl;
    private boolean mmolxlDecimals;
    private long pollInterval;
    private long lowBatPollInterval;
    private boolean doublePollOnPumpAway;

    private boolean sysEnableCgmHistory;
    private int sysCgmHistoryDays;
    private boolean sysEnablePumpHistory;
    private int sysPumpHistoryDays;
    private int sysPumpHistoryFrequency;
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
    private boolean nsEnableCalibrationInfoNow;
    private boolean nsEnableSensorChange;
    private boolean nsEnableReservoirChange;
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

    public DataStore() {
        this.timestamp = new Date().getTime();
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

    public boolean isMmolxl() {
        return mmolxl;
    }

    public void setMmolxl(boolean mmolxl) {
        this.mmolxl = mmolxl;
    }

    public boolean isMmolxlDecimals() {
        return mmolxlDecimals;
    }

    public void setMmolxlDecimals(boolean mmolxlDecimals) {
        this.mmolxlDecimals = mmolxlDecimals;
    }

    public long getPollInterval() {
        return pollInterval;
    }

    public void setPollInterval(long pollInterval) {
        this.pollInterval = pollInterval;
    }

    public long getLowBatPollInterval() {
        return lowBatPollInterval;
    }

    public void setLowBatPollInterval(long lowBatPollInterval) {
        this.lowBatPollInterval = lowBatPollInterval;
    }

    public boolean isDoublePollOnPumpAway() {
        return doublePollOnPumpAway;
    }

    public void setDoublePollOnPumpAway(boolean doublePollOnPumpAway) {
        this.doublePollOnPumpAway = doublePollOnPumpAway;
    }

    public boolean isSysEnableCgmHistory() {
        return sysEnableCgmHistory;
    }

    public void setSysEnableCgmHistory(boolean sysEnableCgmHistory) {
        this.sysEnableCgmHistory = sysEnableCgmHistory;
    }

    public int getSysCgmHistoryDays() {
        return sysCgmHistoryDays;
    }

    public void setSysCgmHistoryDays(int sysCgmHistoryDays) {
        this.sysCgmHistoryDays = sysCgmHistoryDays;
    }

    public boolean isSysEnablePumpHistory() {
        return sysEnablePumpHistory;
    }

    public void setSysEnablePumpHistory(boolean sysEnablePumpHistory) {
        this.sysEnablePumpHistory = sysEnablePumpHistory;
    }

    public int getSysPumpHistoryDays() {
        return sysPumpHistoryDays;
    }

    public void setSysPumpHistoryDays(int sysPumpHistoryDays) {
        this.sysPumpHistoryDays = sysPumpHistoryDays;
    }

    public int getSysPumpHistoryFrequency() {
        return sysPumpHistoryFrequency;
    }

    public void setSysPumpHistoryFrequency(int sysPumpHistoryFrequency) {
        this.sysPumpHistoryFrequency = sysPumpHistoryFrequency;
    }

    public boolean isSysEnableClashProtect() {
        return sysEnableClashProtect;
    }

    public void setSysEnableClashProtect(boolean sysEnableClashProtect) {
        this.sysEnableClashProtect = sysEnableClashProtect;
    }

    public boolean isSysEnablePollOverride() {
        return sysEnablePollOverride;
    }

    public void setSysEnablePollOverride(boolean sysEnablePollOverride) {
        this.sysEnablePollOverride = sysEnablePollOverride;
    }

    public long getSysPollGracePeriod() {
        return sysPollGracePeriod;
    }

    public void setSysPollGracePeriod(long sysPollGracePeriod) {
        this.sysPollGracePeriod = sysPollGracePeriod;
    }

    public long getSysPollRecoveryPeriod() {
        return sysPollRecoveryPeriod;
    }

    public void setSysPollRecoveryPeriod(long sysPollRecoveryPeriod) {
        this.sysPollRecoveryPeriod = sysPollRecoveryPeriod;
    }

    public long getSysPollWarmupPeriod() {
        return sysPollWarmupPeriod;
    }

    public void setSysPollWarmupPeriod(long sysPollWarmupPeriod) {
        this.sysPollWarmupPeriod = sysPollWarmupPeriod;
    }

    public long getSysPollErrorRetry() {
        return sysPollErrorRetry;
    }

    public void setSysPollErrorRetry(long sysPollErrorRetry) {
        this.sysPollErrorRetry = sysPollErrorRetry;
    }

    public long getSysPollOldSgvRetry() {
        return sysPollOldSgvRetry;
    }

    public void setSysPollOldSgvRetry(long sysPollOldSgvRetry) {
        this.sysPollOldSgvRetry = sysPollOldSgvRetry;
    }

    public boolean isSysEnableWait500ms() {
        return sysEnableWait500ms;
    }

    public void setSysEnableWait500ms(boolean sysEnableWait500ms) {
        this.sysEnableWait500ms = sysEnableWait500ms;
    }

    public boolean isSysEnableUsbPermissionDialog() {
        return sysEnableUsbPermissionDialog;
    }

    public void setSysEnableUsbPermissionDialog(boolean sysEnableUsbPermissionDialog) {
        this.sysEnableUsbPermissionDialog = sysEnableUsbPermissionDialog;
    }

    public boolean isDbgEnableExtendedErrors() {
        return dbgEnableExtendedErrors;
    }

    public void setDbgEnableExtendedErrors(boolean dbgEnableExtendedErrors) {
        this.dbgEnableExtendedErrors = dbgEnableExtendedErrors;
    }

    public boolean isDbgEnableUploadErrors() {
        return dbgEnableUploadErrors;
    }

    public void setDbgEnableUploadErrors(boolean dbgEnableUploadErrors) {
        this.dbgEnableUploadErrors = dbgEnableUploadErrors;
    }

    public boolean isNsEnableTreatments() {
        return nsEnableTreatments;
    }

    public void setNsEnableTreatments(boolean nsEnableTreatments) {
        this.nsEnableTreatments = nsEnableTreatments;
    }

    public boolean isNsEnableHistorySync() {
        return nsEnableHistorySync;
    }

    public void setNsEnableHistorySync(boolean nsEnableHistorySync) {
        this.nsEnableHistorySync = nsEnableHistorySync;
    }

    public boolean isNsEnableFingerBG() {
        return nsEnableFingerBG;
    }

    public void setNsEnableFingerBG(boolean nsEnableFingerBG) {
        this.nsEnableFingerBG = nsEnableFingerBG;
    }

    public boolean isNsEnableCalibrationInfo() {
        return nsEnableCalibrationInfo;
    }

    public void setNsEnableCalibrationInfo(boolean nsEnableCalibrationInfo) {
        this.nsEnableCalibrationInfo = nsEnableCalibrationInfo;
    }

    public boolean isNsEnableCalibrationInfoNow() {
        return nsEnableCalibrationInfoNow;
    }

    public void setNsEnableCalibrationInfoNow(boolean nsEnableCalibrationInfoNow) {
        this.nsEnableCalibrationInfoNow = nsEnableCalibrationInfoNow;
    }

    public boolean isNsEnableSensorChange() {
        return nsEnableSensorChange;
    }

    public void setNsEnableSensorChange(boolean nsEnableSensorChange) {
        this.nsEnableSensorChange = nsEnableSensorChange;
    }

    public boolean isNsEnableReservoirChange() {
        return nsEnableReservoirChange;
    }

    public void setNsEnableReservoirChange(boolean nsEnableReservoirChange) {
        this.nsEnableReservoirChange = nsEnableReservoirChange;
    }

    public boolean isNsEnableBatteryChange() {
        return nsEnableBatteryChange;
    }

    public void setNsEnableBatteryChange(boolean nsEnableBatteryChange) {
        this.nsEnableBatteryChange = nsEnableBatteryChange;
    }

    public boolean isNsEnableLifetimes() {
        return nsEnableLifetimes;
    }

    public void setNsEnableLifetimes(boolean nsEnableLifetimes) {
        this.nsEnableLifetimes = nsEnableLifetimes;
    }

    public boolean isNsEnableProfileUpload() {
        return nsEnableProfileUpload;
    }

    public void setNsEnableProfileUpload(boolean nsEnableProfileUpload) {
        this.nsEnableProfileUpload = nsEnableProfileUpload;
    }

    public boolean isNsEnableProfileSingle() {
        return nsEnableProfileSingle;
    }

    public void setNsEnableProfileSingle(boolean nsEnableProfileSingle) {
        this.nsEnableProfileSingle = nsEnableProfileSingle;
    }

    public boolean isNsEnableProfileOffset() {
        return nsEnableProfileOffset;
    }

    public void setNsEnableProfileOffset(boolean nsEnableProfileGroups) {
        this.nsEnableProfileOffset = nsEnableProfileGroups;
    }

    public int getNsProfileDefault() {
        return nsProfileDefault;
    }

    public void setNsProfileDefault(int nsProfileDefault) {
        this.nsProfileDefault = nsProfileDefault;
    }

    public float getNsActiveInsulinTime() {
        return nsActiveInsulinTime;
    }

    public void setNsActiveInsulinTime(float nsActiveInsulinTime) {
        this.nsActiveInsulinTime = nsActiveInsulinTime;
    }

    public boolean isNsEnablePatternChange() {
        return nsEnablePatternChange;
    }

    public void setNsEnablePatternChange(boolean nsEnablePatternChange) {
        this.nsEnablePatternChange = nsEnablePatternChange;
    }

    public int getNsGramsPerExchange() {
        return nsGramsPerExchange;
    }

    public void setNsGramsPerExchange(int nsGramsPerExchange) {
        if (this.nsGramsPerExchange != 0 && this.nsGramsPerExchange != nsGramsPerExchange)
            nsGramsPerExchangeChanged = true;
        this.nsGramsPerExchange = nsGramsPerExchange;
    }

    public boolean isNsGramsPerExchangeChanged() {
        return nsGramsPerExchangeChanged;
    }

    public void setNsGramsPerExchangeChanged(boolean nsGramsPerExchangeChanged) {
        this.nsGramsPerExchangeChanged = nsGramsPerExchangeChanged;
    }

    public boolean isNsEnableInsertBGasCGM() {
        return nsEnableInsertBGasCGM;
    }

    public void setNsEnableInsertBGasCGM(boolean nsEnableInsertBGasCGM) {
        this.nsEnableInsertBGasCGM = nsEnableInsertBGasCGM;
    }

    public boolean isUrchinEnable() {
        return urchinEnable;
    }

    public void setUrchinEnable(boolean urchinEnable) {
        this.urchinEnable = urchinEnable;
    }

    public int getUrchinBasalPeriod() {
        return urchinBasalPeriod;
    }

    public void setUrchinBasalPeriod(int urchinBasalPeriod) {
        this.urchinBasalPeriod = urchinBasalPeriod;
    }

    public int getUrchinBasalScale() {
        return urchinBasalScale;
    }

    public void setUrchinBasalScale(int urchinBasalScale) {
        this.urchinBasalScale = urchinBasalScale;
    }

    public boolean isUrchinBolusGraph() {
        return urchinBolusGraph;
    }

    public void setUrchinBolusGraph(boolean urchinBolusGraph) {
        this.urchinBolusGraph = urchinBolusGraph;
    }

    public boolean isUrchinBolusTags() {
        return urchinBolusTags;
    }

    public void setUrchinBolusTags(boolean urchinBolusTags) {
        this.urchinBolusTags = urchinBolusTags;
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

    public void setUrchinTimeStyle(int urchinTimeStyle) {
        this.urchinTimeStyle = urchinTimeStyle;
    }

    public int getUrchinDurationStyle() {
        return urchinDurationStyle;
    }

    public void setUrchinDurationStyle(int urchinDurationStyle) {
        this.urchinDurationStyle = urchinDurationStyle;
    }

    public int getUrchinUnitsStyle() {
        return urchinUnitsStyle;
    }

    public void setUrchinUnitsStyle(int urchinUnitsStyle) {
        this.urchinUnitsStyle = urchinUnitsStyle;
    }

    public int getUrchinBatteyStyle() {
        return urchinBatteyStyle;
    }

    public void setUrchinBatteyStyle(int urchinBatteyStyle) {
        this.urchinBatteyStyle = urchinBatteyStyle;
    }

    public int getUrchinConcatenateStyle() {
        return urchinConcatenateStyle;
    }

    public void setUrchinConcatenateStyle(int urchinConcatenateStyle) {
        this.urchinConcatenateStyle = urchinConcatenateStyle;
    }

    public String getUrchinCustomText1() {
        return urchinCustomText1;
    }

    public void setUrchinCustomText1(String urchinCustomText1) {
        this.urchinCustomText1 = urchinCustomText1;
    }

    public String getUrchinCustomText2() {
        return urchinCustomText2;
    }

    public void setUrchinCustomText2(String urchinCustomText2) {
        this.urchinCustomText2 = urchinCustomText2;
    }

    public byte[] getUrchinStatusLayout() {
        return urchinStatusLayout;
    }

    public void setUrchinStatusLayout(byte[] urchinStatusLayout) {
        this.urchinStatusLayout = urchinStatusLayout;
    }

    public String getNameBasalPattern1() {
        return nameBasalPattern1;
    }

    public void setNameBasalPattern1(String nameBasalPattern1) {
        if (this.nameBasalPattern1 != null && !this.nameBasalPattern1.equals(nameBasalPattern1))
            nameBasalPatternChanged = true;
        this.nameBasalPattern1 = nameBasalPattern1;
    }

    public String getNameBasalPattern2() {
        return nameBasalPattern2;
    }

    public void setNameBasalPattern2(String nameBasalPattern2) {
        if (this.nameBasalPattern2 != null && !this.nameBasalPattern2.equals(nameBasalPattern2))
            nameBasalPatternChanged = true;
        this.nameBasalPattern2 = nameBasalPattern2;
    }

    public String getNameBasalPattern3() {
        return nameBasalPattern3;
    }

    public void setNameBasalPattern3(String nameBasalPattern3) {
        if (this.nameBasalPattern3 != null && !this.nameBasalPattern3.equals(nameBasalPattern3))
            nameBasalPatternChanged = true;
        this.nameBasalPattern3 = nameBasalPattern3;
    }

    public String getNameBasalPattern4() {
        return nameBasalPattern4;
    }

    public void setNameBasalPattern4(String nameBasalPattern4) {
        if (this.nameBasalPattern4 != null && !this.nameBasalPattern4.equals(nameBasalPattern4))
            nameBasalPatternChanged = true;
        this.nameBasalPattern4 = nameBasalPattern4;
    }

    public String getNameBasalPattern5() {
        return nameBasalPattern5;
    }

    public void setNameBasalPattern5(String nameBasalPattern5) {
        if (this.nameBasalPattern5 != null && !this.nameBasalPattern5.equals(nameBasalPattern5))
            nameBasalPatternChanged = true;
        this.nameBasalPattern5 = nameBasalPattern5;
    }

    public String getNameBasalPattern6() {
        return nameBasalPattern6;
    }

    public void setNameBasalPattern6(String nameBasalPattern6) {
        if (this.nameBasalPattern6 != null && !this.nameBasalPattern6.equals(nameBasalPattern6))
            nameBasalPatternChanged = true;
        this.nameBasalPattern6 = nameBasalPattern6;
    }

    public String getNameBasalPattern7() {
        return nameBasalPattern7;
    }

    public void setNameBasalPattern7(String nameBasalPattern7) {
        if (this.nameBasalPattern7 != null && !this.nameBasalPattern7.equals(nameBasalPattern7))
            nameBasalPatternChanged = true;
        this.nameBasalPattern7 = nameBasalPattern7;
    }

    public String getNameBasalPattern8() {
        return nameBasalPattern8;
    }

    public void setNameBasalPattern8(String nameBasalPattern8) {
        if (this.nameBasalPattern8 != null && !this.nameBasalPattern8.equals(nameBasalPattern8))
            nameBasalPatternChanged = true;
        this.nameBasalPattern8 = nameBasalPattern8;
    }

    public String getNameTempBasalPreset1() {
        return nameTempBasalPreset1;
    }

    public void setNameTempBasalPreset1(String nameTempBasalPreset1) {
        this.nameTempBasalPreset1 = nameTempBasalPreset1;
    }

    public String getNameTempBasalPreset2() {
        return nameTempBasalPreset2;
    }

    public void setNameTempBasalPreset2(String nameTempBasalPreset2) {
        this.nameTempBasalPreset2 = nameTempBasalPreset2;
    }

    public String getNameTempBasalPreset3() {
        return nameTempBasalPreset3;
    }

    public void setNameTempBasalPreset3(String nameTempBasalPreset3) {
        this.nameTempBasalPreset3 = nameTempBasalPreset3;
    }

    public String getNameTempBasalPreset4() {
        return nameTempBasalPreset4;
    }

    public void setNameTempBasalPreset4(String nameTempBasalPreset4) {
        this.nameTempBasalPreset4 = nameTempBasalPreset4;
    }

    public String getNameTempBasalPreset5() {
        return nameTempBasalPreset5;
    }

    public void setNameTempBasalPreset5(String nameTempBasalPreset5) {
        this.nameTempBasalPreset5 = nameTempBasalPreset5;
    }

    public String getNameTempBasalPreset6() {
        return nameTempBasalPreset6;
    }

    public void setNameTempBasalPreset6(String nameTempBasalPreset6) {
        this.nameTempBasalPreset6 = nameTempBasalPreset6;
    }

    public String getNameTempBasalPreset7() {
        return nameTempBasalPreset7;
    }

    public void setNameTempBasalPreset7(String nameTempBasalPreset7) {
        this.nameTempBasalPreset7 = nameTempBasalPreset7;
    }

    public String getNameTempBasalPreset8() {
        return nameTempBasalPreset8;
    }

    public void setNameTempBasalPreset8(String nameTempBasalPreset8) {
        this.nameTempBasalPreset8 = nameTempBasalPreset8;
    }

    public String getNameBolusPreset1() {
        return nameBolusPreset1;
    }

    public void setNameBolusPreset1(String nameBolusPreset1) {
        this.nameBolusPreset1 = nameBolusPreset1;
    }

    public String getNameBolusPreset2() {
        return nameBolusPreset2;
    }

    public void setNameBolusPreset2(String nameBolusPreset2) {
        this.nameBolusPreset2 = nameBolusPreset2;
    }

    public String getNameBolusPreset3() {
        return nameBolusPreset3;
    }

    public void setNameBolusPreset3(String nameBolusPreset3) {
        this.nameBolusPreset3 = nameBolusPreset3;
    }

    public String getNameBolusPreset4() {
        return nameBolusPreset4;
    }

    public void setNameBolusPreset4(String nameBolusPreset4) {
        this.nameBolusPreset4 = nameBolusPreset4;
    }

    public String getNameBolusPreset5() {
        return nameBolusPreset5;
    }

    public void setNameBolusPreset5(String nameBolusPreset5) {
        this.nameBolusPreset5 = nameBolusPreset5;
    }

    public String getNameBolusPreset6() {
        return nameBolusPreset6;
    }

    public void setNameBolusPreset6(String nameBolusPreset6) {
        this.nameBolusPreset6 = nameBolusPreset6;
    }

    public String getNameBolusPreset7() {
        return nameBolusPreset7;
    }

    public void setNameBolusPreset7(String nameBolusPreset7) {
        this.nameBolusPreset7 = nameBolusPreset7;
    }

    public String getNameBolusPreset8() {
        return nameBolusPreset8;
    }

    public void setNameBolusPreset8(String nameBolusPreset8) {
        this.nameBolusPreset8 = nameBolusPreset8;
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
}
