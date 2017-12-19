package info.nightscout.android.model.store;

import java.util.Date;

import io.realm.RealmObject;
import io.realm.annotations.Index;

public class DataStore extends RealmObject {
    @Index
    private long timestamp;

    // do not send cgm/pump backfill data prior to this date
    // used to stop overwriting older NS entries
    // user option to override (we clear old data from NS to stop multiple entries and refresh using keys)
    private Date NightscoutLimitDate = null;
    private long NightscoutCgmCleanFrom;
    private long NightscoutPumpCleanFrom;

    private boolean RequestProfile = false;
    private boolean RequestPumpHistory = false;
    private boolean RequestCgmHistory = false;

    private int PumpCgmNA;

    private int CommsSuccess;
    private int CommsError;
    private int CommsConnectError;
    private int CommsSignalError;
    private int CommsSgvSuccess;
    private int PumpLostSensorError;
    private int PumpClockError;
    private int PumpBatteryError;

    private boolean mmolxl;
    private boolean mmolxlDecimals;
    private long pollInterval;
    private long lowBatPollInterval;
    private boolean doublePollOnPumpAway;

    private boolean sysEnableCgmHistory;
    private int sysCgmHistoryDays;
    private boolean sysEnablePumpHistory;
    private int sysPumpHistoryDays;
    private boolean sysEnableClashProtect;
    private boolean sysEnablePollOverride;
    private long sysPollGracePeriod;
    private long sysPollRecoveryPeriod;
    private long sysPollWarmupPeriod;
    private long sysPollErrorRetry;
    private long sysPollOldSgvRetry;
    private boolean sysEnableWait500ms;

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

    public DataStore() {
        this.timestamp = new Date().getTime();
    }

    public Date getNightscoutLimitDate() {
        return NightscoutLimitDate;
    }

    public void setNightscoutLimitDate(Date nightscoutLimitDate) {
        NightscoutLimitDate = nightscoutLimitDate;
    }

    public long getNightscoutCgmCleanFrom() {
        return NightscoutCgmCleanFrom;
    }

    public void setNightscoutCgmCleanFrom(long nightscoutCgmCleanFrom) {
        NightscoutCgmCleanFrom = nightscoutCgmCleanFrom;
    }

    public long getNightscoutPumpCleanFrom() {
        return NightscoutPumpCleanFrom;
    }

    public void setNightscoutPumpCleanFrom(long nightscoutPumpCleanFrom) {
        NightscoutPumpCleanFrom = nightscoutPumpCleanFrom;
    }

    public boolean isRequestProfile() {
        return RequestProfile;
    }

    public void setRequestProfile(boolean requestProfile) {
        RequestProfile = requestProfile;
    }

    public boolean isRequestPumpHistory() {
        return RequestPumpHistory;
    }

    public void setRequestPumpHistory(boolean requestPumpHistory) {
        RequestPumpHistory = requestPumpHistory;
    }

    public boolean isRequestCgmHistory() {
        return RequestCgmHistory;
    }

    public void setRequestCgmHistory(boolean requestCgmHistory) {
        RequestCgmHistory = requestCgmHistory;
    }

    public int getPumpCgmNA() {
        return PumpCgmNA;
    }

    public void setPumpCgmNA(int pumpCgmNA) {
        PumpCgmNA = pumpCgmNA;
    }

    public int getCommsSuccess() {
        return CommsSuccess;
    }

    public void setCommsSuccess(int commsSuccess) {
        CommsSuccess = commsSuccess;
    }

    public int getCommsError() {
        return CommsError;
    }

    public void setCommsError(int commsError) {
        CommsError = commsError;
    }

    public int getCommsConnectError() {
        return CommsConnectError;
    }

    public void setCommsConnectError(int commsConnectError) {
        CommsConnectError = commsConnectError;
    }

    public int getCommsSignalError() {
        return CommsSignalError;
    }

    public void setCommsSignalError(int commsSignalError) {
        CommsSignalError = commsSignalError;
    }

    public int getCommsSgvSuccess() {
        return CommsSgvSuccess;
    }

    public void setCommsSgvSuccess(int commsSgvSuccess) {
        CommsSgvSuccess = commsSgvSuccess;
    }

    public int getPumpLostSensorError() {
        return PumpLostSensorError;
    }

    public void setPumpLostSensorError(int pumpLostSensorError) {
        PumpLostSensorError = pumpLostSensorError;
    }

    public int getPumpClockError() {
        return PumpClockError;
    }

    public void setPumpClockError(int pumpClockError) {
        PumpClockError = pumpClockError;
    }

    public int getPumpBatteryError() {
        return PumpBatteryError;
    }

    public void setPumpBatteryError(int pumpBatteryError) {
        PumpBatteryError = pumpBatteryError;
    }

    public void clearAllCommsErrors() {
        this.PumpCgmNA = 0;
        this.CommsSuccess = 0;
        this.CommsError = 0;
        this.CommsConnectError = 0;
        this.CommsSignalError = 0;
        this.CommsSgvSuccess = 0;
        this.PumpLostSensorError = 0;
        this.PumpClockError = 0;
        this.PumpBatteryError = 0;
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
}
