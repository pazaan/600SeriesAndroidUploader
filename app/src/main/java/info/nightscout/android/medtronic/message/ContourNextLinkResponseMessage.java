package info.nightscout.android.medtronic.message;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class ContourNextLinkResponseMessage extends ContourNextLinkMessage {

    public ContourNextLinkResponseMessage(byte[] payload) throws ChecksumException {
        super(payload);
    }


    public void checkControlMessage(ASCII controlCharacter) throws IOException, TimeoutException, UnexpectedMessageException {
        checkControlMessage(mPayload.array(), controlCharacter);
    }

    public void checkControlMessage(byte[] msg, ASCII controlCharacter) throws IOException, TimeoutException, UnexpectedMessageException {
        if (msg.length != 1 || !controlCharacter.equals(msg[0])) {
            throw new UnexpectedMessageException(String.format(Locale.getDefault(), "Expected to get control character '%d' Got '%d'.",
                    (int) controlCharacter.getValue(), (int) msg[0]));
        }
    }
}
