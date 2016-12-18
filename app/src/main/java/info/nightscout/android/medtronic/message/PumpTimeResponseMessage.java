package info.nightscout.android.medtronic.message;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Date;

import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;

/**
 * Created by lgoedhart on 27/03/2016.
 */
public class PumpTimeResponseMessage extends MedtronicSendMessageResponseMessage {
    private static final String TAG = PumpTimeResponseMessage.class.getSimpleName();

    private Date pumpTime;

    protected PumpTimeResponseMessage(MedtronicCnlSession pumpSession, byte[] payload) throws EncryptionException, ChecksumException {
        super(pumpSession, payload);

        if (this.encode().length < (61 + 8)) {
            // Invalid message. Return an invalid date.
            // TODO - deal with this more elegantly
            Log.e(TAG, "Invalid message received for getPumpTime");
            pumpTime = new Date();
        }

        // FIXME - this needs to go into PumpTimeResponseMessage
        ByteBuffer dateBuffer = ByteBuffer.allocate(8);
        dateBuffer.order(ByteOrder.BIG_ENDIAN);
        dateBuffer.put(this.encode(), 0x3d, 8);
        long rtc = dateBuffer.getInt(0) & 0x00000000ffffffffL;
        long offset = dateBuffer.getInt(4);
        pumpTime = MessageUtils.decodeDateTime(rtc, offset);
    }

    public Date getPumpTime() {
        return pumpTime;
    }
}
