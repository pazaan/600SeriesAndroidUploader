package info.nightscout.android.medtronic.message;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class ReadHistoryInfoRequestMessage extends MedtronicSendMessageRequestMessage {
    public ReadHistoryInfoRequestMessage(MedtronicCnlSession pumpSession) throws EncryptionException, ChecksumException {
        super(SendMessageType.READ_BASAL_PATTERN_REQUEST, pumpSession, new byte[] {
                2,
                3,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0
        });
    }

    public ReadHistoryInfoResponseMessage send(UsbHidDriver mDevice) throws IOException, TimeoutException, ChecksumException, EncryptionException, UnexpectedMessageException {
        sendMessage(mDevice);

        // Read the 0x81
        ReadHistoryInfoResponseMessage response = new ReadHistoryInfoResponseMessage(mPumpSession, readMessage(mDevice));

        return response;
    }
}
