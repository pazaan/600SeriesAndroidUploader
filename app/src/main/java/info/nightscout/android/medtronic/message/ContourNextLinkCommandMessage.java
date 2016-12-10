package info.nightscout.android.medtronic.message;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.USB.UsbHidDriver;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class ContourNextLinkCommandMessage extends ContourNextLinkMessage {
    public ContourNextLinkCommandMessage(byte command) {
        super(new byte[]{command});
    }

    public ContourNextLinkCommandMessage(String command) {
        super(command.getBytes());
    }

    public ContourNextLinkCommandResponseMessage send(UsbHidDriver mDevice) throws IOException, TimeoutException, EncryptionException, ChecksumException, UnexpectedMessageException {
        sendMessage(mDevice);

        ContourNextLinkCommandResponseMessage response = new ContourNextLinkCommandResponseMessage(mPumpSession, readMessage(mDevice));;

        return response;
    }
}
