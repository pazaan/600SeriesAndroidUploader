package info.nightscout.android.medtronic.message;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;

/**
 * Created by lgoedhart on 26/03/2016.
 */

public class ReadHistoryInfoRequestMessage extends MedtronicSendMessageRequestMessage<ReadHistoryInfoResponseMessage> {
    private static final String TAG = ReadHistoryInfoRequestMessage.class.getSimpleName();

    public ReadHistoryInfoRequestMessage(MedtronicCnlSession pumpSession, int startRTC, int endRTC, int dataType) throws EncryptionException, ChecksumException {
        super(MessageType.READ_HISTORY_INFO, pumpSession, buildPayload(startRTC, endRTC, dataType));
    }

    protected static byte[] buildPayload(int startRTC, int endRTC, int dataType) {
        ByteBuffer payload = ByteBuffer.allocate(12);
        payload.order(ByteOrder.BIG_ENDIAN);
        payload.put(0x00, (byte) dataType);  // pump data = 0x02, sensor data = 0x03
        payload.put(0x01, (byte) 0x04);  // full history = 0x03, partial history = 0x04
        payload.putInt(0x02, startRTC);
        payload.putInt(0x06, endRTC);
        payload.put(0x0A, (byte) 0x00);
        payload.put(0x0B, (byte) 0x00);
        return payload.array();
    }

    public ReadHistoryInfoResponseMessage send(UsbHidDriver mDevice, int millis) throws IOException, TimeoutException, ChecksumException, EncryptionException, UnexpectedMessageException {
        sendToPump(mDevice, TAG);
        return getResponse(readFromPump(mDevice, mPumpSession, TAG));
    }

    @Override
    protected ReadHistoryInfoResponseMessage getResponse(byte[] payload) throws ChecksumException, EncryptionException, IOException, UnexpectedMessageException {
        return new ReadHistoryInfoResponseMessage(mPumpSession, payload);
    }

}
