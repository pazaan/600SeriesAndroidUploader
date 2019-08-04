package info.nightscout.android.medtronic.message;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;

/**
 * Created by volker on 10.12.2016.
 */

public class DeviceInfoRequestCommandMessage extends ContourNextLinkRequestMessage<DeviceInfoResponseCommandMessage> {
    public DeviceInfoRequestCommandMessage() {
        super("X".getBytes());
    }

    @Override
    public DeviceInfoResponseCommandMessage send(UsbHidDriver mDevice, int millis) throws IOException, TimeoutException, EncryptionException, ChecksumException, UnexpectedMessageException {

        clearMessage(mDevice, PRESEND_CLEAR_TIMEOUT_MS);

        sendMessage(mDevice);

        if (millis > 0) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException ignored) {
            }
        }
        byte[] response1 = readMessage(mDevice, CNL_READ_TIMEOUT_MS);
        if (millis > 0) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException ignored) {
            }
        }
        byte[] response2 = readMessage(mDevice, CNL_READ_TIMEOUT_MS);

        boolean doRetry = false;
        DeviceInfoResponseCommandMessage response = null;

        do {
            try {
                if (ASCII.EOT.equals(response1[0])) {
                    // response 1 is the ASTM message
                    response = this.getResponse(response1);
                    // ugly....
                    response.checkControlMessage(response2, ASCII.ENQ);
                } else {
                    // response 2 is the ASTM message
                    response = this.getResponse(response2);
                    // ugly, too....
                    response.checkControlMessage(response1, ASCII.ENQ);
                }
            } catch (TimeoutException e) {
                doRetry = true;
            }
        } while (doRetry);

        return response;
    }

    @Override
    protected DeviceInfoResponseCommandMessage getResponse(byte[] payload) throws ChecksumException, EncryptionException, IOException, UnexpectedMessageException, TimeoutException {
        return new DeviceInfoResponseCommandMessage(payload);
    }
}
