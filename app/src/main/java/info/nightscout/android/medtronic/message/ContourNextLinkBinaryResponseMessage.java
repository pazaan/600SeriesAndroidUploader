package info.nightscout.android.medtronic.message;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class ContourNextLinkBinaryResponseMessage extends ContourNextLinkResponseMessage {

    public ContourNextLinkBinaryResponseMessage(byte[] payload) throws ChecksumException {
        super(payload);
    }
}
