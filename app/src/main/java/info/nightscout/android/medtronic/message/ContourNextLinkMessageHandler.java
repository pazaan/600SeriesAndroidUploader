package info.nightscout.android.medtronic.message;

import java.io.IOException;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public interface ContourNextLinkMessageHandler {
    void sendMessage( ContourNextLinkMessage message ) throws IOException;
    ContourNextLinkMessage receiveMessage();
}
