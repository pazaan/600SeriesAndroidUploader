package info.nightscout.android.medtronic.message;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import info.nightscout.android.BuildConfig;
import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;
import info.nightscout.android.utils.HexDump;

import static info.nightscout.android.utils.ToolKit.getInt;
import static info.nightscout.android.utils.ToolKit.getIntL;
import static info.nightscout.android.utils.ToolKit.getIntLU;
import static info.nightscout.android.utils.ToolKit.getShortIU;

/**
 * Created by lgoedhart on 27/03/2016.
 */
public class ReadHistoryInfoResponseMessage extends MedtronicSendMessageResponseMessage {
    private static final String TAG = ReadHistoryInfoResponseMessage.class.getSimpleName();

    protected ReadHistoryInfoResponseMessage(MedtronicCnlSession pumpSession, byte[] payload) throws EncryptionException, ChecksumException, UnexpectedMessageException {
        super(pumpSession, payload);

        if (!MedtronicSendMessageRequestMessage.MessageType.READ_HISTORY_INFO.response(getShortIU(payload, 0x01))) {
            Log.e(TAG, "Invalid message received for ReadHistoryInfo");
            throw new UnexpectedMessageException("Invalid message received for ReadHistoryInfo");
        }

        int length = getInt(payload, 0x04);
        Date start = MessageUtils.decodeDateTime(getIntLU(payload, 0x08), getIntL(payload, 0x0C));
        Date end = MessageUtils.decodeDateTime(getIntLU(payload, 0x10), getIntL(payload, 0x14));

        Log.d(TAG, "ReadHistoryInfo: length = " + length + " blocks = " + (length / 2048));
        Log.d(TAG, "ReadHistoryInfo: start = " + dateFormatter.format(start));
        Log.d(TAG, "ReadHistoryInfo: end = " + dateFormatter.format(end));
    }

    private DateFormat dateFormatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US);
}
