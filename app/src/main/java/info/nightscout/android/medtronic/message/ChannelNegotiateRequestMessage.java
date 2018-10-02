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
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class ChannelNegotiateRequestMessage extends MedtronicRequestMessage<ChannelNegotiateResponseMessage> {
    private static final String TAG = ChannelNegotiateRequestMessage.class.getSimpleName();

    public ChannelNegotiateRequestMessage(MedtronicCnlSession pumpSession) throws ChecksumException {
        super(CommandType.SEND_MESSAGE, CommandAction.JOIN_NETWORK, pumpSession, buildPayload(pumpSession));
    }

    @Override
    public ChannelNegotiateResponseMessage send(UsbHidDriver mDevice) throws IOException, TimeoutException, ChecksumException, EncryptionException, UnexpectedMessageException {
        sendMessage(mDevice);

        Log.d(TAG, "negotiateChannel: Reading 0x81 message");
        readResponse0x81(mDevice, READ_TIMEOUT_MS, TAG);

        Log.d(TAG, "negotiateChannel: Reading 0x80 message");
        return getResponse(readResponse0x80(mDevice, READ_TIMEOUT_MS, TAG));
    }

    @Override
    protected ChannelNegotiateResponseMessage getResponse(byte[] payload) throws ChecksumException, EncryptionException, IOException {
        return new ChannelNegotiateResponseMessage(mPumpSession, payload);
    }

    protected static byte[] buildPayload( MedtronicCnlSession pumpSession ) {
        ByteBuffer payload = ByteBuffer.allocate(26);
        payload.order(ByteOrder.LITTLE_ENDIAN);
        // The MedtronicMessage sequence number is always sent as 1 for this message
        // addendum: when network is joined the send sequence number is 1 (first pump request-response)
        // sequence should then be 2 and increment for ongoing messages
        pumpSession.setMedtronicSequenceNumber((byte) 1);
        payload.put((byte) 1);
        payload.put(pumpSession.getRadioChannel());
        byte[] unknownBytes = {0, 0, 0, 0x07, 0x07, 0, 0, 0x02};
        payload.put(unknownBytes);
        payload.putLong(pumpSession.getLinkMAC());
        payload.putLong(pumpSession.getPumpMAC());

        return payload.array();
    }
}
