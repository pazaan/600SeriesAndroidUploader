package info.nightscout.android.model.medtronicNg;

import android.util.Log;

import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

/**
 * Created by lgoedhart on 4/06/2016.
 */
public class PumpInfo extends RealmObject {
    @PrimaryKey
    private long pumpMac;
    private String deviceName;
    private byte lastRadioChannel;
    private long lastQueryTS = 0;
    private RealmList<ContourNextLinkInfo> associatedCnls;
    private RealmList<PumpStatusEvent> pumpHistory = new RealmList<>();
    private RealmList<BasalSchedule> basalSchedules;

    public long getPumpMac() {
        return pumpMac;
    }

    private void setPumpMac(long pumpMac) {
        this.pumpMac = pumpMac;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public byte getLastRadioChannel() {
        return lastRadioChannel;
    }

    public void setLastRadioChannel(byte lastRadioChannel) {
        this.lastRadioChannel = lastRadioChannel;
    }

    public RealmList<ContourNextLinkInfo> getAssociatedCnls() {
        return associatedCnls;
    }

    public void setAssociatedCnls(RealmList<ContourNextLinkInfo> associatedCnls) {
        this.associatedCnls = associatedCnls;
    }

    public RealmList<PumpStatusEvent> getPumpHistory() {
        return pumpHistory;
    }

    public void setPumpHistory(RealmList<PumpStatusEvent> pumpHistory) {
        this.pumpHistory = pumpHistory;
    }

    public long getPumpSerial() {
        return pumpMac & 0xffffff;
    }

    public long getLastQueryTS() {
        return lastQueryTS;
    }

    public void updateLastQueryTS() {
        lastQueryTS = System.currentTimeMillis();
    }

    public RealmList<BasalSchedule> getBasalSchedules() {
        return basalSchedules;
    }

    public void setBasalSchedules(RealmList<BasalSchedule> basalSchedules) {
        this.basalSchedules = basalSchedules;
    }

    public boolean checkBasalRatesMatch(PumpStatusEvent pumpRecord) {
        byte activeBasal = pumpRecord.getActiveBasalPattern();

        BasalSchedule schedule = basalSchedules
                .where()
                .equalTo("scheduleNumber", activeBasal)
                .findFirst();

        if(schedule == null) {
            Log.d("Schedule Check", "Didn't find a matching schedule for " + activeBasal);
            return false;
        } else {
            Log.d("Schedule Check", "Found a schedule for " + activeBasal + " with name " + schedule.getName());
            return true;
        }
    }

}
