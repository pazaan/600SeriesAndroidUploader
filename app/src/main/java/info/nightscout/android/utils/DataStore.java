package info.nightscout.android.utils;


/**
 * Created by volker on 30.03.2017.
 */

public class DataStore {
    private static DataStore instance;

    private long activePumpMac = 0;
    private int uploaderBatteryLevel = 0;
    private int pumpCgmNA = 0;

    private int CommsSuccess = 0;
    private int CommsError = 0;
    private int CommsConnectError = 0;
    private int CommsSignalError = 0;
    private int CommsSgvSuccess = 0;
    private int PumpLostSensorError = 0;
    private int PumpClockError = 0;

    private DataStore() {}

    public static DataStore getInstance() {
        if (DataStore.instance == null) {
            instance = new DataStore();

        }
        return instance;
    }

    public long getActivePumpMac() {
        return activePumpMac;
    }

    public void setActivePumpMac(long activePumpMac) {
        this.activePumpMac = activePumpMac;
    }

    public int getUploaderBatteryLevel() {
        return uploaderBatteryLevel;
    }

    public void setUploaderBatteryLevel(int uploaderBatteryLevel) {
        this.uploaderBatteryLevel = uploaderBatteryLevel;
    }

    public int getPumpCgmNA() {
        return pumpCgmNA;
    }

    public int incPumpCgmNA() {
        return pumpCgmNA++;
    }

    public void clearPumpCgmNA() {
        this.pumpCgmNA = 0;
    }

    public int getCommsSuccess() {
        return CommsSuccess;
    }

    public int incCommsSuccess() { return CommsSuccess++; }

    public void clearCommsSuccess() {
        this.CommsSuccess = 0;
    }

    public int getCommsError() {
        return CommsError;
    }

    public int incCommsError() { return CommsError++; }

    public void clearCommsError() {
        this.CommsError = 0;
    }

    public int getCommsConnectError() {
        return CommsConnectError;
    }

    public int incCommsConnectError() { return CommsConnectError++; }

    public int decCommsConnectError() {
        if (CommsConnectError > 0) CommsConnectError--;
        return CommsConnectError;}

    public void clearCommsConnectError() {
        this.CommsConnectError = 0;
    }

    public int getCommsSignalError() {
        return CommsSignalError;
    }

    public int incCommsSignalError() { return CommsSignalError++; }

    public int decCommsSignalError() {
        if (CommsSignalError > 0) CommsSignalError--;
        return CommsSignalError;}

    public void clearCommsSignalError() {
        this.CommsSignalError = 0;
    }

    public int getCommsSgvSuccess() {
        return CommsSgvSuccess;
    }

    public int incCommsSgvSuccess() { return CommsSgvSuccess++; }

    public int getPumpLostSensorError() {
        return PumpLostSensorError;
    }

    public int incPumpLostSensorError() { return PumpLostSensorError++; }

    public void clearPumpLostSensorError() {
        this.PumpLostSensorError = 0;
    }

    public int getPumpClockError() {
        return PumpClockError;
    }

    public int incPumpClockError() { return PumpClockError++; }

    public void clearPumpClockError() {
        this.PumpClockError = 0;
    }

    public void clearAllCommsErrors() {
        this.CommsSuccess = 0;
        this.CommsError = 0;
        this.CommsConnectError = 0;
        this.CommsSignalError = 0;
        this.PumpLostSensorError = 0;
        this.PumpClockError = 0;
    }
}
