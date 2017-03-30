package info.nightscout.android.utils;


import java.util.Date;

import info.nightscout.android.model.medtronicNg.PumpStatusEvent;

/**
 * Created by volker on 30.03.2017.
 */

public class DataStore {
    private static DataStore instance;

    private PumpStatusEvent lastPumpStatus;
    private int uplooaderBatteryLevel = 0;
    private int unavailableSGVCount = 0;
    private long activePumpMac = 0;

    private DataStore() {}

    public static DataStore getInstance() {
        if (DataStore.instance == null) {
            instance = new DataStore();

            // set some initial dummy values
            PumpStatusEvent dummyStatus = new PumpStatusEvent();
            dummyStatus.setEventDate(new Date(0));

            instance.setLastPumpStatus(dummyStatus);
        }

        return instance;
    }

    public PumpStatusEvent getLastPumpStatus() {
        return lastPumpStatus;
    }

    public void setLastPumpStatus(PumpStatusEvent lastPumpStatus) {
        this.lastPumpStatus = lastPumpStatus;
    }

    public int getUplooaderBatteryLevel() {
        return uplooaderBatteryLevel;
    }

    public void setUplooaderBatteryLevel(int uplooaderBatteryLevel) {
        this.uplooaderBatteryLevel = uplooaderBatteryLevel;
    }

    public int getUnavailableSGVCount() {
        return unavailableSGVCount;
    }

    public int incUnavailableSGVCount() {
        return unavailableSGVCount++;
    }

    public void clearUnavailableSGVCount() {
        this.unavailableSGVCount = 0;
    }
    public void setUnavailableSGVCount(int unavailableSGVCount) {
        this.unavailableSGVCount = unavailableSGVCount;
    }

    public long getActivePumpMac() {
        return activePumpMac;
    }

    public void setActivePumpMac(long activePumpMac) {
        this.activePumpMac = activePumpMac;
    }
}
