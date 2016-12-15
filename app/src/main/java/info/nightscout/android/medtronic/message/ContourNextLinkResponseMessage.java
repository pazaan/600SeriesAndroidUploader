package info.nightscout.android.medtronic.message;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.message.ChecksumException;
import info.nightscout.android.medtronic.message.ContourNextLinkMessage;
import info.nightscout.android.medtronic.message.UnexpectedMessageException;

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
