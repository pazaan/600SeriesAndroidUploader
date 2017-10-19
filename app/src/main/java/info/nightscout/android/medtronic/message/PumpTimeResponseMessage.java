package info.nightscout.android.medtronic.message;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Date;

import info.nightscout.android.BuildConfig;
import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;
import info.nightscout.android.medtronic.service.MedtronicCnlService;
import info.nightscout.android.utils.HexDump;

import static info.nightscout.android.utils.ToolKit.getIntL;
import static info.nightscout.android.utils.ToolKit.getIntLU;
import static info.nightscout.android.utils.ToolKit.getShortIU;

/**
 * Created by lgoedhart on 27/03/2016.
 */
public class PumpTimeResponseMessage extends MedtronicSendMessageResponseMessage {
    private static final String TAG = PumpTimeResponseMessage.class.getSimpleName();

    private Date pumpTime;

    protected PumpTimeResponseMessage(MedtronicCnlSession pumpSession, byte[] payload) throws EncryptionException, ChecksumException, UnexpectedMessageException {
        super(pumpSession, payload);

        if (!MedtronicSendMessageRequestMessage.MessageType.READ_PUMP_TIME.response(getShortIU(payload, 0x01))) {
            Log.e(TAG, "Invalid message received for getPumpTime");
            throw new UnexpectedMessageException("Invalid message received for PumpTime");
        }

        long rtc = getIntLU(payload, 0x04);
        long offset = getIntL(payload, 0x08);
        pumpTime = MessageUtils.decodeDateTime(rtc, offset);

        MedtronicCnlService.pumpRTC = (int) rtc;
        MedtronicCnlService.pumpOFFSET = (int) offset;
    }

    public Date getPumpTime() {
        return pumpTime;
    }
}
