package info.nightscout.android.utils;


import org.apache.commons.lang3.time.DateUtils;

import java.util.Date;

import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import io.realm.Realm;

/**
 * Created by volker on 30.03.2017.
 */

public class DataStore {
    private static DataStore instance;

    private PumpStatusEvent lastPumpStatus;
    private int uploaderBatteryLevel = 0;
    private int unavailableSGVCount = 0;
    private int lastBolusWizardBGL = 0;
    private long activePumpMac = 0;
    private int commsErrorCount = 0;
    private int commsSuccessCount = 0;
    private int commsConnectThreshold = 0;
    private int commsSignalThreshold = 0;
    private float commsUnavailableThreshold = 0;

    private DataStore() {}

    public static DataStore getInstance() {
        if (DataStore.instance == null) {
            instance = new DataStore();

            // set some initial dummy values
            PumpStatusEvent dummyStatus = new PumpStatusEvent();
            dummyStatus.setSgvDate(DateUtils.addDays(new Date(), -1));
            dummyStatus.setSgv(0);

            // bypass setter to avoid dealing with a real Realm object
            instance.lastPumpStatus = dummyStatus;
        }

        return instance;
    }

    public PumpStatusEvent getLastPumpStatus() {
        return lastPumpStatus;
    }

    public void setLastPumpStatus(PumpStatusEvent lastPumpStatus) {
        Realm realm = Realm.getDefaultInstance();

        this.lastPumpStatus = realm.copyFromRealm(lastPumpStatus);
        if (!realm.isClosed()) realm.close();
    }

    public int getUploaderBatteryLevel() {
        return uploaderBatteryLevel;
    }

    public void setUploaderBatteryLevel(int uploaderBatteryLevel) {
        this.uploaderBatteryLevel = uploaderBatteryLevel;
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

    public int getLastBolusWizardBGL() {
        return lastBolusWizardBGL;
    }

    public void setLastBolusWizardBGL(int lastBolusWizardBGL) {
        this.lastBolusWizardBGL = lastBolusWizardBGL;
    }

    public long getActivePumpMac() {
        return activePumpMac;
    }

    public void setActivePumpMac(long activePumpMac) {
        this.activePumpMac = activePumpMac;
    }

    public int getCommsErrorCount() {
        return commsErrorCount;
    }

    public int incCommsErrorCount() { return commsErrorCount++; }

    public int decCommsErrorThreshold() {
        if (commsErrorCount > 0) commsErrorCount--;
        return commsErrorCount;}

    public void clearCommsErrorCount() {
        this.commsErrorCount = 0;
    }

    public int getCommsSuccessCount() {
        return commsSuccessCount;
    }

    public int incCommsSuccessCount() { return commsSuccessCount++; }

    public int decCommsSuccessCount() {
        if (commsSuccessCount > 0) commsSuccessCount--;
        return commsSuccessCount;}

    public void clearCommsSuccessCount() {
        this.commsSuccessCount = 0;
    }

    public int getCommsConnectThreshold() {
        return commsConnectThreshold;
    }

    public int incCommsConnectThreshold() { return commsConnectThreshold++; }

    public int decCommsConnectThreshold() {
        if (commsConnectThreshold > 0) commsConnectThreshold--;
        return commsConnectThreshold;}

    public void clearCommsConnectThreshold() {
        this.commsConnectThreshold = 0;
    }

    public int getCommsSignalThreshold() {
        return commsSignalThreshold;
    }

    public int incCommsSignalThreshold() { return commsSignalThreshold++; }

    public int decCommsSignalThreshold() {
        if (commsSignalThreshold > 0) commsSignalThreshold--;
        return commsSignalThreshold;}

    public void clearCommsSignalThreshold() {
        this.commsSignalThreshold = 0;
    }

    public float getCommsUnavailableThreshold() {
        return commsUnavailableThreshold;
    }

    public float addCommsUnavailableThreshold(float value) {
        commsUnavailableThreshold+= value;
        if (commsUnavailableThreshold < 0) commsUnavailableThreshold = 0;
        return commsUnavailableThreshold;}

    public void clearCommsUnavailableThreshold() {
        this.commsUnavailableThreshold = 0;
    }

    public void clearAllCommsErrors() {
        this.commsErrorCount = 0;
        this.commsSuccessCount = 0;
        this.commsConnectThreshold = 0;
        this.commsSignalThreshold = 0;
        this.commsUnavailableThreshold = 0;
    }
}
