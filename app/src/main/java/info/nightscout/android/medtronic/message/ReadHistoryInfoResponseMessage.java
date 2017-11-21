package info.nightscout.android.medtronic.message;

import android.util.Log;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;

import static info.nightscout.android.utils.ToolKit.read32BEtoInt;
import static info.nightscout.android.utils.ToolKit.read32BEtoLong;
import static info.nightscout.android.utils.ToolKit.read32BEtoULong;
import static info.nightscout.android.utils.ToolKit.read16BEtoUInt;

/**
 * Created by lgoedhart on 27/03/2016.
 */
public class ReadHistoryInfoResponseMessage extends MedtronicSendMessageResponseMessage {
    private static final String TAG = ReadHistoryInfoResponseMessage.class.getSimpleName();

    private Date fromDate;
    private Date toDate;
    private int length;
    private int blocks;

    private DateFormat dateFormatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US);

    protected ReadHistoryInfoResponseMessage(MedtronicCnlSession pumpSession, byte[] payload) throws EncryptionException, ChecksumException, UnexpectedMessageException {
        super(pumpSession, payload);

        if (!MedtronicSendMessageRequestMessage.MessageType.READ_HISTORY_INFO.response(read16BEtoUInt(payload, 0x01))) {
            Log.e(TAG, "Invalid message received for ReadHistoryInfo");
            throw new UnexpectedMessageException("Invalid message received for ReadHistoryInfo");
        }

        length = read32BEtoInt(payload, 0x04);
        blocks = length / 2048;
        fromDate = MessageUtils.decodeDateTime(read32BEtoULong(payload, 0x08), read32BEtoLong(payload, 0x0C));
        toDate = MessageUtils.decodeDateTime(read32BEtoULong(payload, 0x10), read32BEtoLong(payload, 0x14));

        Log.d(TAG, "ReadHistoryInfo: length = " + length + " blocks = " + (length / 2048));
        Log.d(TAG, "ReadHistoryInfo: start = " + dateFormatter.format(fromDate));
        Log.d(TAG, "ReadHistoryInfo: end = " + dateFormatter.format(toDate));
    }

    public Date getFromDate() {
        return fromDate;
    }

    public Date getToDate() {
        return toDate;
    }

    public int getLength() {
        return length;
    }

    public int getBlocks() {
        return blocks;
    }
}
