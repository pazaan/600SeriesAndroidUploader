package info.nightscout.android.model.medtronicNg;

import java.util.Date;
import java.util.List;

import io.realm.RealmModel;

/**
 * Created by John on 19.10.17.
 */

public interface PumpHistory extends RealmModel {

    Date getEventDate();
    void setEventDate(Date eventDate);

    Date getEventEndDate();
    void setEventEndDate(Date eventEndDate);

    boolean isUploadREQ();
    void setUploadREQ(boolean value);

    boolean isUploadACK();
    void setUploadACK(boolean value);

    boolean isXdripREQ();
    void setXdripREQ(boolean value);

    boolean isXdripACK();
    void setXdripACK(boolean value);

    String getKey();
    void setKey(String key);

    List Nightscout();
}