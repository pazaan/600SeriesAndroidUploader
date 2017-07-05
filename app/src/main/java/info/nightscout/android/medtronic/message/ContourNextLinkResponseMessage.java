package info.nightscout.android.medtronic.message;

import java.util.Locale;

import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public abstract class ContourNextLinkResponseMessage extends ContourNextLinkMessage {

    public ContourNextLinkResponseMessage(byte[] payload) throws ChecksumException {
        super(payload);
    }


    public void checkControlMessage(ASCII controlCharacter) throws UnexpectedMessageException {
        checkControlMessage(mPayload.array(), controlCharacter);
    }

    public void checkControlMessage(byte[] msg, ASCII controlCharacter) throws UnexpectedMessageException {
        if (msg.length != 1 || !controlCharacter.equals(msg[0])) {
            throw new UnexpectedMessageException(String.format(Locale.getDefault(), "Expected to get control character '%d' Got '%d'.",
                    (int) controlCharacter.getValue(), (int) msg[0]));
        }
    }
}
