package info.nightscout.android.model.medtronicNg;

import java.util.Date;
import java.util.List;

import info.nightscout.android.model.store.DataStore;
import io.realm.RealmModel;

/**
 * Created by Pogman on 19.10.17.
 */

public interface PumpHistoryInterface extends RealmModel {

    Date getEventDate();
    void setEventDate(Date eventDate);

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

    List nightscout(DataStore dataStore);
}