package info.nightscout.android.medtronic.message;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;
import info.nightscout.android.utils.HexDump;

/**
 * Created by lgoedhart on 27/03/2016.
 */
public class ReadHistoryInfoResponseMessage extends MedtronicSendMessageResponseMessage {
    private static final String TAG = ReadHistoryInfoResponseMessage.class.getSimpleName();

    protected ReadHistoryInfoResponseMessage(MedtronicCnlSession pumpSession, byte[] payload) throws EncryptionException, ChecksumException, UnexpectedMessageException {
        super(pumpSession, payload);


        if (this.encode().length < 32) {
            // Invalid message.
            // TODO - deal with this more elegantly
            Log.e(TAG, "Invalid message received for ReadHistoryInfo");
            throw new UnexpectedMessageException("Invalid message received for ReadHistoryInfo");
        } else {

            ByteBuffer basalRatesBuffer = ByteBuffer.allocate(payload.length);
            basalRatesBuffer.order(ByteOrder.BIG_ENDIAN);
            basalRatesBuffer.put(this.encode());

            String responseString = HexDump.dumpHexString(basalRatesBuffer.array());
            Log.d(TAG, "ReadHistoryInfo: " + responseString);
            Log.d(TAG, "ReadHistoryInfo-length: " + basalRatesBuffer.getLong(28));
        }


    }

}
