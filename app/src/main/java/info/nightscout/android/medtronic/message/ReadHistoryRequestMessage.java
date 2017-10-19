package info.nightscout.android.medtronic.message;

import android.util.Log;

import org.anarres.lzo.LzoAlgorithm;
import org.anarres.lzo.LzoDecompressor;
import org.anarres.lzo.LzoLibrary;
import org.anarres.lzo.LzoTransformer;
import org.anarres.lzo.lzo_uintp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;
import info.nightscout.android.utils.HexDump;

import static info.nightscout.android.utils.ToolKit.getByteIU;
import static info.nightscout.android.utils.ToolKit.getInt;
import static info.nightscout.android.utils.ToolKit.getShortIU;

/**
 * Created by John on 6.10.17.
 */

public class ReadHistoryRequestMessage extends MedtronicSendMessageRequestMessage<ReadHistoryResponseMessage> {
    private static final String TAG = ReadHistoryRequestMessage.class.getSimpleName();

    private static final int HEADER_SIZE = 0x000D;
    private static final int BLOCK_SIZE = 0x0800;

    private ByteArrayOutputStream blocks;

    public ReadHistoryRequestMessage(MedtronicCnlSession pumpSession, byte[] payload) throws EncryptionException, ChecksumException {
        super(MessageType.READ_HISTORY, pumpSession, payload);
    }

    public ReadHistoryResponseMessage send(UsbHidDriver mDevice, int millis) throws IOException, TimeoutException, ChecksumException, EncryptionException, UnexpectedMessageException {
        byte[] payload;
        blocks = new ByteArrayOutputStream();

        sendToPump(mDevice, mPumpSession, TAG);

        boolean receivedEndHistoryCommand = false;
        while (!receivedEndHistoryCommand) {
            payload = readFromPump(mDevice, mPumpSession, TAG);

            int cmd = getShortIU(payload, 1);
            if (MessageType.END_HISTORY_TRANSMISSION.response(cmd)) {
                // read 0412 = EHSM_SESSION (1)
                readMessage(mDevice);
                receivedEndHistoryCommand = true;

            } else if (MessageType.UNMERGED_HISTORY.response(cmd)) {
                addHistoryBlock(payload);
            }
        }

        return getResponse(blocks.toByteArray());
    }

    @Override
    protected ReadHistoryResponseMessage getResponse(byte[] payload) throws ChecksumException, EncryptionException, IOException, UnexpectedMessageException {
        return new ReadHistoryResponseMessage(mPumpSession, payload);
    }

    private void addHistoryBlock(byte[] payload) throws UnexpectedMessageException, ChecksumException {
        int dataType = getByteIU(payload, 0x03); // pump data = 0x02, sensor data = 0x03
        int historySizeCompressed = getInt(payload, 0x04);
        int historySizeUncompressed = getInt(payload, 0x08);
        boolean historyCompressed = (payload[0x0C] & 0x01) == 0x01;

        Log.d(TAG, "dataType=" + dataType + " historySizeCompressed=" + historySizeCompressed + " historySizeUncompressed=" + historySizeUncompressed + " historyCompressed=" + historyCompressed);

        // Check that we have the correct number of bytes in this message
        if (payload.length - HEADER_SIZE != historySizeCompressed) {
            throw new UnexpectedMessageException("Unexpected history message size");
        }

        byte[] blockPayload;

        if (historyCompressed) {
            blockPayload = new byte[historySizeUncompressed];

            LzoAlgorithm algorithm = LzoAlgorithm.LZO1X;
            LzoDecompressor decompressor = LzoLibrary.getInstance().newDecompressor(algorithm, null);
            int lzoReturnCode = decompressor.decompress(payload, HEADER_SIZE, historySizeCompressed, blockPayload, 0, new lzo_uintp(historySizeUncompressed));

            if (lzoReturnCode != LzoTransformer.LZO_E_OK)
                throw new UnexpectedMessageException("Error trying to decompress history message (" + decompressor.toErrorString(lzoReturnCode) + ")");
        } else {
            blockPayload = Arrays.copyOfRange(payload, HEADER_SIZE, payload.length);
        }

        if (blockPayload.length % BLOCK_SIZE > 0) {
            throw new UnexpectedMessageException("Block payload size is not a multiple of 2048");
        }

        for (int i = 0; i < blockPayload.length / BLOCK_SIZE; i++) {
            int blockStart = i * BLOCK_SIZE;
            int blockSize = getShortIU(blockPayload, blockStart + BLOCK_SIZE - 4);
            int blockChecksum = getShortIU(blockPayload, blockStart + BLOCK_SIZE - 2);

            int calculatedChecksum = MessageUtils.CRC16CCITT(blockPayload, blockStart, 0xFFFF, 0x1021, blockSize);
            if (blockChecksum != calculatedChecksum) {
                throw new ChecksumException("Unexpected checksum in block " + i + " (" + HexDump.toHexString(blockChecksum) + "/" + HexDump.toHexString(calculatedChecksum) + ")");
            } else {
                blocks.write(blockPayload, blockStart, blockSize);
            }
        }
    }
}
