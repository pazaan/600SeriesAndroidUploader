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

    private int PumpCgmNA = 0;
    private int PumpOffsetCheck = 0;
    private long PumpOffsetCheckTime = 0;
    private long PumpOffset = 0;

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

    public int getPumpCgmNA() {
        return PumpCgmNA;
    }

    public void setPumpCgmNA(int pumpCgmNA) {
        this.PumpCgmNA = pumpCgmNA;
    }

    public int getPumpOffsetCheck() {
        return PumpOffsetCheck;
    }

    public void setPumpOffsetCheck(int PumpOffsetCheck) {
        this.PumpOffsetCheck = PumpOffsetCheck;
    }

    public long getPumpOffset() {
        return PumpOffset;
    }

    public void setPumpOffset(long PumpOffset) {
        this.PumpOffset = PumpOffset;
    }

    public int getCommsSuccess() {
        return CommsSuccess;
    }

    public void setCommsSuccess(int CommsSuccess) {
        this.CommsSuccess = CommsSuccess;
    }

    public int getCommsError() {
        return CommsError;
    }

    public void setCommsError(int CommsError) {
        this.CommsError = CommsError;
    }

    public int getCommsConnectError() {
        return CommsConnectError;
    }

    public void setCommsConnectError(int CommsConnectError) {
        this.CommsConnectError = CommsConnectError;
    }

    public int getCommsSignalError() {
        return CommsSignalError;
    }

    public void setCommsSignalError(int CommsSignalError) {
        this.CommsSignalError = CommsSignalError;
    }

    public int getCommsSgvSuccess() {
        return CommsSgvSuccess;
    }

    public void setCommsSgvSuccess(int CommsSgvSuccess) {
        this.CommsSgvSuccess = CommsSgvSuccess;
    }

    public int getPumpLostSensorError() {
        return PumpLostSensorError;
    }

    public void setPumpLostSensorError(int PumpLostSensorError) {
        this.PumpLostSensorError = PumpLostSensorError;
    }

    public int getPumpClockError() {
        return PumpClockError;
    }

    public void setPumpClockError(int PumpClockError) {
        this.PumpClockError = PumpClockError;
    }

    public int getPumpBatteryError() {
        return PumpBatteryError;
    }

    public void setPumpBatteryError(int PumpBatteryError) {
        this.PumpBatteryError = PumpBatteryError;
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
