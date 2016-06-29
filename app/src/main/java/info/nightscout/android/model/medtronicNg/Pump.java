package info.nightscout.android.model.medtronicNg;

import info.nightscout.android.model.CgmStatusEvent;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

/**
 * Created by lgoedhart on 4/06/2016.
 */
public class Pump extends RealmObject {
    @PrimaryKey
    private String serialNumber;
    private int lastRadioChannel;
    private RealmList<ContourNextLinkInfo> associatedCnls;
    private RealmList<CgmStatusEvent> cgmHistory;
    private RealmList<PumpStatusEvent> pumpHistory;

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public int getLastRadioChannel() {
        return lastRadioChannel;
    }

    public void setLastRadioChannel(int lastRadioChannel) {
        this.lastRadioChannel = lastRadioChannel;
    }

    public RealmList<ContourNextLinkInfo> getAssociatedCnls() {
        return associatedCnls;
    }

    public void setAssociatedCnls(RealmList<ContourNextLinkInfo> associatedCnls) {
        this.associatedCnls = associatedCnls;
    }

    public RealmList<CgmStatusEvent> getCgmHistory() {
        return cgmHistory;
    }

    public void setCgmHistory(RealmList<CgmStatusEvent> cgmHistory) {
        this.cgmHistory = cgmHistory;
    }

    public RealmList<PumpStatusEvent> getPumpHistory() {
        return pumpHistory;
    }

    public void setPumpHistory(RealmList<PumpStatusEvent> pumpHistory) {
        this.pumpHistory = pumpHistory;
    }
}
