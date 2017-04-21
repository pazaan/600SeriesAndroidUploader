package info.nightscout.android.medtronic.message;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class ContourNextLinkCommandMessage extends ContourNextLinkRequestMessage {
    public ContourNextLinkCommandMessage(ASCII command) {
        super(new byte[]{command.getValue()});
    }

    public ContourNextLinkCommandMessage(byte command) {
        super(new byte[]{command});
    }

    public ContourNextLinkCommandMessage(String command) {
        super(command.getBytes());
    }


    public ContourNextLinkCommandResponse send(UsbHidDriver mDevice) throws IOException, TimeoutException, EncryptionException, ChecksumException {
        sendMessage(mDevice);

        ContourNextLinkCommandResponse response = new ContourNextLinkCommandResponse(readMessage(mDevice));

        // FIXME - We need to care what the response message is - wrong MAC and all that
        return response;
    }
}
