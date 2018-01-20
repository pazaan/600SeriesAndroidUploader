package info.nightscout.android.model.medtronicNg;

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
    private RealmList<ContourNextLinkInfo> associatedCnls;
    private RealmList<PumpStatusEvent> pumpHistory = new RealmList<>();

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
}
