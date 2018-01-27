package info.nightscout.android.medtronic.message;

import android.util.Log;

import java.util.Date;

import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;

import static info.nightscout.android.utils.ToolKit.read32BEtoInt;
import static info.nightscout.android.utils.ToolKit.read16BEtoUInt;

/**
 * Created by lgoedhart on 27/03/2016.
 */
public class PumpTimeResponseMessage extends MedtronicSendMessageResponseMessage {
    private static final String TAG = PumpTimeResponseMessage.class.getSimpleName();

    private Date pumpTime;
    private int pumpTimeRTC;
    private int pumpTimeOFFSET;

    protected PumpTimeResponseMessage(MedtronicCnlSession pumpSession, byte[] payload) throws EncryptionException, ChecksumException, UnexpectedMessageException {
        super(pumpSession, payload);

        if (!MedtronicSendMessageRequestMessage.MessageType.READ_PUMP_TIME.response(read16BEtoUInt(payload, 0x01))) {
            Log.e(TAG, "Invalid message received for getPumpTime");
            throw new UnexpectedMessageException("Invalid message received for PumpTime");
        }

        pumpTimeRTC = read32BEtoInt(payload, 0x04);
        pumpTimeOFFSET = read32BEtoInt(payload, 0x08);
        pumpTime = MessageUtils.decodeDateTime(pumpTimeRTC & 0xFFFFFFFFL, pumpTimeOFFSET);
    }

    public Date getPumpTime() {
        return pumpTime;
    }

    public int getPumpTimeRTC() {
        return pumpTimeRTC;
    }

    public int getPumpTimeOFFSET() {
        return pumpTimeOFFSET;
    }
}
