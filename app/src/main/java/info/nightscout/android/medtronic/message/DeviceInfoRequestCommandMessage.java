package info.nightscout.android.medtronic.message;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.USB.UsbHidDriver;

/**
 * Created by volker on 10.12.2016.
 */

public class DeviceInfoRequestCommandMessage extends ContourNextLinkMessage {
    public DeviceInfoRequestCommandMessage() {
        super("X".getBytes());
    }

    public DeviceInfoResponseCommandMessage send(UsbHidDriver mDevice) throws IOException, TimeoutException, EncryptionException, ChecksumException, UnexpectedMessageException {
        sendMessage(mDevice);

        byte[] response1 = readMessage(mDevice);
        byte[] response2 = readMessage(mDevice);

        boolean doRetry = false;
        DeviceInfoResponseCommandMessage response = null;

        do {
            try {
                if (response1[0] == ASCII.EOT.value) {
                    // response 1 is the ASTM message
                    response = new DeviceInfoResponseCommandMessage(mPumpSession, response1);
                    response.checkControlMessage(response2, ASCII.ENQ.value);
                } else {
                    // response 2 is the ASTM message
                    response = new DeviceInfoResponseCommandMessage(mPumpSession, response1);
                    response.checkControlMessage(response1, ASCII.ENQ.value);
                }
            } catch (TimeoutException e) {
                doRetry = true;
            }
        } while (doRetry);

        return response;
    }
}
