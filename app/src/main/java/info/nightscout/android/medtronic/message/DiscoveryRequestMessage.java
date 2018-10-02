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
 * Created by John on 18.3.18.
 */

public class DiscoveryRequestMessage extends MedtronicRequestMessage<DiscoveryResponseMessage> {
    private static final String TAG = DiscoveryRequestMessage.class.getSimpleName();

    public DiscoveryRequestMessage(MedtronicCnlSession pumpSession) throws ChecksumException {
        //super(CommandType.SEND_MESSAGE, CommandAction.SCAN_NETWORK, pumpSession, buildPayload(pumpSession));
        //super(CommandType.SEND_MESSAGE, CommandAction.SCAN_NETWORK, pumpSession, null);
        //super(CommandType.SEND_MESSAGE, CommandAction.READ_NETWORK_STATUS, pumpSession, null);
        //super(CommandType.SEND_MESSAGE, CommandAction.READ_STATUS, pumpSession, null);
        //super(CommandType.SEND_MESSAGE, CommandAction.READ_STATISTICS, pumpSession, null);
        //super(CommandType.SEND_MESSAGE, CommandAction.READ_DATA, pumpSession, null);
        super(CommandType.SEND_MESSAGE, CommandAction.CLEAR_STATUS, pumpSession, null);
    }

    @Override
    public DiscoveryResponseMessage send(UsbHidDriver mDevice) throws IOException, TimeoutException, ChecksumException, EncryptionException, UnexpectedMessageException {
        byte[] payload;

        clearMessage(mDevice, PRESEND_CLEAR_TIMEOUT_MS);
        sendMessage(mDevice);

        Log.d(TAG, "Reading 0x81 message");
        payload = readResponse0x81(mDevice, READ_TIMEOUT_MS, TAG);

        Log.d(TAG, "Reading 0x80 message");
        payload = readResponse0x80(mDevice, READ_TIMEOUT_MS, TAG);

        return this.getResponse(payload);
    }

    @Override
    protected DiscoveryResponseMessage getResponse(byte[] payload) throws ChecksumException, EncryptionException, IOException {
        return new DiscoveryResponseMessage(mPumpSession, payload);
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

/*

READ_NETWORK_STATUS

contains the response as returned when negotiating channel

negotiate channel 0x80 response:

           ?0 ?1 ?2 ?3 ?4 ?5 ?6 ?7 ?8 ?9 ?A ?B ?C ?D ?E ?F
0x00000000 51 03 30 30 30 30 30 30 00 00 00 00 00 00 00 00
0x00000010 00 00 80 A7 01 00 80 00 00 00 00 00 2E 00 00 00
0x00000020 B1*55 2C 00 04 2C 37 10 EE 45 02 2C 37 10 EE 45
0x00000030 F7 23 00 82 00 00 00 00 00 07 00 55 BE 22 11 82
0x00000040 06 F7 23 00 42 00 00 00 00 00 00 00 11 8C 2A


network status 0x81 response:

           ?0 ?1 ?2 ?3 ?4 ?5 ?6 ?7 ?8 ?9 ?A ?B ?C ?D ?E ?F
0x00000000 51 03 30 30 30 30 30 30 00 00 00 00 00 00 00 00
0x00000010 00 00 81 05 00 00 00 00 00 00 00 00 2E 00 00 00
0x00000020 8F*55 2C 00 04 2C 37 10 EE 45 02 2C 37 10 EE 45
0x00000030 F7 23 00 82 00 00 00 00 00 07 00 55 BE 22 11 82
0x00000040 06 F7 23 00 42 00 00 00 00 00 00 00 11 8C 2A


0x80
0x81

 */