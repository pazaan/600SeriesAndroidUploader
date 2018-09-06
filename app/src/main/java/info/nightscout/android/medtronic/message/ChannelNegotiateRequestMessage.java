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
import info.nightscout.android.utils.HexDump;

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
        byte[] payload;

        //clearMessage(mDevice, PRESEND_CLEAR_TIMEOUT_MS);
        sendMessage(mDevice);

        Log.d(TAG, "negotiateChannel: Reading 0x81 message");
        payload = readMessage_0x81(mDevice);

        // minimum size?
        if (payload.length <= 0x21) {
            Log.e(TAG, "*** 0x81 response message size less then expected" + HexDump.dumpHexString(payload));
            clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
            throw new UnexpectedMessageException("0x81 response message size less then expected (negotiateChannel)");
        }
        // 0x81 message?
        if ((payload[0x12] & 0xFF) != 0x81) {
            Log.e(TAG, "*** 0x81 response message not a 0x81" + HexDump.dumpHexString(payload));
            clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
            throw new UnexpectedMessageException("0x81 response message not a 0x81 (negotiateChannel)");
        }
        int internal = payload[0x1C] & 0x000000FF | payload[0x1D] << 8 & 0x0000FF00; // 16bit LE internal payload size
        // correct size including internal payload?
        if (payload.length != (0x21 + internal)) {
            Log.e(TAG, "*** 0x81 response message size mismatch" + HexDump.dumpHexString(payload));
            clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
            throw new UnexpectedMessageException("0x81 response message size mismatch (negotiateChannel)");
        }
        // 0x55 response?
        if (internal == 0 || (internal > 0 && payload[0x0021] != 0x55)) {
            Log.e(TAG, "*** 0x81 response message internal payload not a 0x55" + HexDump.dumpHexString(payload));
            clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
            throw new UnexpectedMessageException("0x81 response message internal payload not a 0x55 (negotiateChannel)");
        }

        Log.d(TAG, "negotiateChannel: Reading 0x80 message");
        payload = readMessage(mDevice);

        // minimum size?
        if (payload.length < 0x21) {
            Log.e(TAG, "*** 0x80 response message size less then expected" + HexDump.dumpHexString(payload));
            clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
            throw new UnexpectedMessageException("0x80 response message size less then expected (negotiateChannel)");
        }
        // 0x80 message?
        if ((payload[0x12] & 0xFF) != 0x80) {
            Log.e(TAG, "*** 0x80 response message not a 0x80" + HexDump.dumpHexString(payload));
            clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
            throw new UnexpectedMessageException("0x80 response message not a 0x80 (negotiateChannel)");
        }
        internal = payload[0x1C] & 0x000000FF | payload[0x1D] << 8 & 0x0000FF00; // 16bit LE internal payload size
        // correct size including internal payload?
        if (payload.length != (0x21 + internal)) {
            Log.e(TAG, "*** 0x80 response message size mismatch" + HexDump.dumpHexString(payload));
            clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
            throw new UnexpectedMessageException("0x80 response message size mismatch (negotiateChannel)");
        }
        // 0x55 response?
        if (internal == 0 || (internal > 0 && payload[0x0021] != 0x55)) {
            Log.w(TAG, "*** 0x80 response message internal payload not a 0x55" + HexDump.dumpHexString(payload));
            clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
            throw new UnexpectedMessageException("0x80 response message internal payload not a 0x55 (negotiateChannel)");
        }

        return this.getResponse(payload);
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
