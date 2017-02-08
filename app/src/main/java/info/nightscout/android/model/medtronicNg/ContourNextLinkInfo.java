package info.nightscout.android.model.medtronicNg;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

/**
 * Created by lgoedhart on 4/06/2016.
 */
public class ContourNextLinkInfo extends RealmObject {
    @PrimaryKey
    private String serialNumber;
    private String key;

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }
}
