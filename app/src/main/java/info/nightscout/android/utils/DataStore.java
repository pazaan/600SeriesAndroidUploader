package info.nightscout.android.utils;

import java.util.Date;

import io.realm.RealmObject;
import io.realm.annotations.Index;

/**
 * Created by volker on 30.03.2017.
 */

public class DataStore extends RealmObject {
    @Index
    private long timestamp;

    private Date uploaderStartDate;

    private boolean RequestProfileInit = false;
    private boolean RequestProfile = false;
    private boolean RequestPumpHistory = false;
    private boolean RequestCgmHistory = false;

    private int PumpCgmNA = 0;

    private int CommsSuccess = 0;
    private int CommsError = 0;
    private int CommsConnectError = 0;
    private int CommsSignalError = 0;
    private int CommsSgvSuccess = 0;
    private int PumpLostSensorError = 0;
    private int PumpClockError = 0;
    private int PumpBatteryError = 0;

    public DataStore() {
        this.timestamp = new Date().getTime();
        this.uploaderStartDate = new Date();
    }

    public Date getUploaderStartDate() {
        return uploaderStartDate;
    }

    public boolean isRequestProfileInit() {
        return RequestProfileInit;
    }

    public void setRequestProfileInit(boolean requestProfileInit) {
        RequestProfileInit = requestProfileInit;
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
}
