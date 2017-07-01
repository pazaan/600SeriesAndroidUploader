package info.nightscout.android.medtronic.message;

import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class ChannelNegotiateRequestMessage extends MedtronicRequestMessage<ChannelNegotiateResponseMessage> {
    private static final String TAG = ChannelNegotiateRequestMessage.class.getSimpleName();

    public ChannelNegotiateRequestMessage(MedtronicCnlSession pumpSession) throws ChecksumException {
        super(CommandType.SEND_MESSAGE, CommandAction.CHANNEL_NEGOTIATE, pumpSession, buildPayload(pumpSession));
    }

    @Override
    public ChannelNegotiateResponseMessage send(UsbHidDriver mDevice) throws IOException, TimeoutException, ChecksumException, EncryptionException {
        sendMessage(mDevice);

        // Don't care what the 0x81 response message is at this stage
        Log.d(TAG, "negotiateChannel: Reading 0x81 message");
        readMessage(mDevice);
        // The 0x80 message
        Log.d(TAG, "negotiateChannel: Reading 0x80 message");

        return this.getResponse(readMessage(mDevice));
    }

    @Override
    protected ChannelNegotiateResponseMessage getResponse(byte[] payload) throws ChecksumException, EncryptionException, IOException {
        return new ChannelNegotiateResponseMessage(mPumpSession, payload);
    }

    protected static byte[] buildPayload( MedtronicCnlSession pumpSession ) {
        ByteBuffer payload = ByteBuffer.allocate(26);
        payload.order(ByteOrder.LITTLE_ENDIAN);
        // The MedtronicMessage sequence number is always sent as 1 for this message,
        // even though the sequence should keep incrementing as normal
        payload.put((byte) 1);
        payload.put(pumpSession.getRadioChannel());
        byte[] unknownBytes = {0, 0, 0, 0x07, 0x07, 0, 0, 0x02};
        payload.put(unknownBytes);
        payload.putLong(pumpSession.getLinkMAC());
        payload.putLong(pumpSession.getPumpMAC());

        return payload.array();
    }
}
