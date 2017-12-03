package info.nightscout.android.model.store;

import java.util.Date;

import io.realm.RealmObject;
import io.realm.annotations.Index;

public class DataStore extends RealmObject {
    @Index
    private long timestamp;

    //private int xxx;

    private Date uploaderStartDate;

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
    private boolean sysEnableWait500ms;

    private boolean nsEnableTreatments;
    private boolean nsEnableHistorySync;
    private boolean nsEnableFingerBG;
    private boolean nsEnableCalibrationInfo;
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

    public DataStore() {
        this.timestamp = new Date().getTime();
        this.uploaderStartDate = new Date();
    }

    public Date getUploaderStartDate() {
        return uploaderStartDate;
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
        this.CommsSuccess = 0;
        this.CommsError = 0;
        this.CommsConnectError = 0;
        this.CommsSignalError = 0;
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
}
