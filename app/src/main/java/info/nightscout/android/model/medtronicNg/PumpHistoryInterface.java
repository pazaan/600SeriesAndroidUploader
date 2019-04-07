package info.nightscout.android.model.medtronicNg;

import java.util.Date;
import java.util.List;

import info.nightscout.android.history.MessageItem;
import info.nightscout.android.history.NightscoutItem;
import info.nightscout.android.history.PumpHistorySender;
import io.realm.RealmModel;

/**
 * Created by Pogman on 19.10.17.
 */

public interface PumpHistoryInterface extends RealmModel {

    String getSenderREQ();

    void setSenderREQ(String senderREQ);

    String getSenderACK();

    void setSenderACK(String senderACK);

    String getSenderDEL();

    void setSenderDEL(String senderDEL);

    Date getEventDate();

    void setEventDate(Date eventDate);

    long getPumpMAC();

    void setPumpMAC(long pumpMAC);

    String getKey();

    void setKey(String key);

    List<NightscoutItem> nightscout(PumpHistorySender sender, String senderID);

    List<MessageItem> message(PumpHistorySender sender, String senderID);
}