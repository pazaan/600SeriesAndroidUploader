package info.nightscout.android.utils;


import info.nightscout.android.medtronic.service.MedtronicCnlIntentService;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;

/**
 * Created by volker on 30.03.2017.
 */

public class ConfigurationStore {
    private static ConfigurationStore instance;

    private boolean reducePollOnPumpAway = false;
    private long pollInterval = MedtronicCnlIntentService.POLL_PERIOD_MS;
    private long lowBatteryPollInterval = MedtronicCnlIntentService.LOW_BATTERY_POLL_PERIOD_MS;

    public static ConfigurationStore getInstance() {
        if (ConfigurationStore.instance == null) {
            instance = new ConfigurationStore();
        }

        return instance;
    }

    public boolean isReducePollOnPumpAway() {
        return reducePollOnPumpAway;
    }

    public void setReducePollOnPumpAway(boolean reducePollOnPumpAway) {
        this.reducePollOnPumpAway = reducePollOnPumpAway;
    }

    public long getPollInterval() {
        return pollInterval;
    }

    public void setPollInterval(long pollInterval) {
        this.pollInterval = pollInterval;
    }

    public long getLowBatteryPollInterval() {
        return lowBatteryPollInterval;
    }

    public void setLowBatteryPollInterval(long lowBatteryPollInterval) {
        this.lowBatteryPollInterval = lowBatteryPollInterval;
    }

}
