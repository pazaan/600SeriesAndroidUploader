package info.nightscout.android.medtronic.message;

import android.util.Log;

import org.anarres.lzo.LzoAlgorithm;
import org.anarres.lzo.LzoDecompressor;
import org.anarres.lzo.LzoLibrary;
import org.anarres.lzo.LzoTransformer;
import org.anarres.lzo.lzo_uintp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;
import info.nightscout.android.utils.HexDump;

import static info.nightscout.android.utils.ToolKit.read16BEtoShort;
import static info.nightscout.android.utils.ToolKit.read8toUInt;
import static info.nightscout.android.utils.ToolKit.read32BEtoInt;
import static info.nightscout.android.utils.ToolKit.read16BEtoUInt;

/**
 * Created by Pogman on 6.10.17.
 */

public class ReadHistoryRequestMessage extends MedtronicSendMessageRequestMessage<ReadHistoryResponseMessage> {
    private static final String TAG = ReadHistoryRequestMessage.class.getSimpleName();

    private static final int HEADER_SIZE = 0x000D;
    private static final int BLOCK_SIZE = 0x0800;

    private ByteArrayOutputStream blocks;

    public ReadHistoryRequestMessage(MedtronicCnlSession pumpSession, int startRTC, int endRTC, int dataType) throws EncryptionException, ChecksumException {
        super(MessageType.READ_HISTORY, pumpSession, buildPayload(startRTC, endRTC, dataType));
    }

    protected static byte[] buildPayload(int startRTC, int endRTC, int dataType) {
        ByteBuffer payload = ByteBuffer.allocate(12);
        payload.order(ByteOrder.BIG_ENDIAN);
        payload.put(0x00, (byte) dataType);  // pump data = 0x02, sensor data = 0x03
        payload.put(0x01, (byte) 0x04);  // full history = 0x03, partial history = 0x04
        payload.putInt(0x02, startRTC);
        payload.putInt(0x06, endRTC);
        payload.put(0x0A, (byte) 0x00);
        payload.put(0x0B, (byte) 0x00);
        return payload.array();
    }

    public ReadHistoryResponseMessage send(UsbHidDriver mDevice, int millis) throws IOException, TimeoutException, ChecksumException, EncryptionException, UnexpectedMessageException {
        blocks = new ByteArrayOutputStream();

        sendToPump(mDevice, TAG);

        byte[] payload;

        boolean fetchMoreData = true;
        boolean receivedEndHistoryCommand = false;

        while (fetchMoreData) {
            payload = readFromPump(mDevice, mPumpSession, TAG);

            if (payload.length >= 3) {

                switch (MedtronicSendMessageRequestMessage.MessageType.convert(read16BEtoShort(payload, NGP_RESPONSE_COMMAND))) {

                    case END_HISTORY_TRANSMISSION:
                        receivedEndHistoryCommand = true;
                        break;

                    case EHSM_SESSION:
                        if (receivedEndHistoryCommand) fetchMoreData = false;
                        break;

                    case UNMERGED_HISTORY:
                        try {
                            addHistoryBlock(payload);
                        } catch (UnexpectedMessageException e) {
                            clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
                            throw e;
                        } catch (ChecksumException e) {
                            clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
                            throw e;
                        } catch (Exception e) {
                            clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
                            throw new UnexpectedMessageException("history message block corrupt");
                        }
                }

            }
        }

        // unread messages sitting in the input stream for too long can cause the CNL to E86
        // clearing them out now before any delays due to history parsing etc (very rare error)
        if (clearMessage(mDevice, 100) > 0)
            Log.w(TAG, "END HISTORY TRANSMISSION: cleared unexpected messages");

        return getResponse(blocks.toByteArray());
    }

    @Override
    protected ReadHistoryResponseMessage getResponse(byte[] payload) throws ChecksumException, EncryptionException, IOException, UnexpectedMessageException {
        return new ReadHistoryResponseMessage(mPumpSession, payload);
    }

    private void addHistoryBlock(byte[] payload) throws UnexpectedMessageException, ChecksumException {
        int dataType = read8toUInt(payload, 0x03); // pump data = 0x02, sensor data = 0x03
        int historySizeCompressed = read32BEtoInt(payload, 0x04);
        int historySizeUncompressed = read32BEtoInt(payload, 0x08);
        boolean historyCompressed = (payload[0x0C] & 0x01) == 0x01;

        Log.d(TAG, "dataType=" + dataType + " historySizeCompressed=" + historySizeCompressed + " historySizeUncompressed=" + historySizeUncompressed + " historyCompressed=" + historyCompressed);

        // Check that we have the correct number of bytes in this message
        if (payload.length - HEADER_SIZE != historySizeCompressed) {
            throw new UnexpectedMessageException("Unexpected message size");
        }

        byte[] blockPayload;

        if (historyCompressed) {
            blockPayload = new byte[historySizeUncompressed];

            try {
                LzoAlgorithm algorithm = LzoAlgorithm.LZO1X;
                LzoDecompressor decompressor = LzoLibrary.getInstance().newDecompressor(algorithm, null);
                int lzoReturnCode = decompressor.decompress(payload, HEADER_SIZE, historySizeCompressed, blockPayload, 0, new lzo_uintp(historySizeUncompressed));
                if (lzoReturnCode != LzoTransformer.LZO_E_OK) {
                    throw new UnexpectedMessageException("Error trying to decompress message (" + decompressor.toErrorString(lzoReturnCode) + ")");
                }
            } catch (Exception e) {
                throw new UnexpectedMessageException("Error trying to decompress message");
            }

        } else {
            blockPayload = Arrays.copyOfRange(payload, HEADER_SIZE, payload.length);
        }

        if (blockPayload.length % BLOCK_SIZE > 0) {
            throw new UnexpectedMessageException("Block payload size is not a multiple of 2048");
        }

        for (int i = 0; i < blockPayload.length / BLOCK_SIZE; i++) {
            int blockStart = i * BLOCK_SIZE;
            int blockSize = read16BEtoUInt(blockPayload, blockStart + BLOCK_SIZE - 4);
            int blockChecksum = read16BEtoUInt(blockPayload, blockStart + BLOCK_SIZE - 2);

            int calculatedChecksum = MessageUtils.CRC16CCITT(blockPayload, blockStart, 0xFFFF, 0x1021, blockSize);
            if (blockChecksum != calculatedChecksum) {
                throw new ChecksumException("Bad checksum in block " + i + " (" + HexDump.toHexString(blockChecksum) + "/" + HexDump.toHexString(calculatedChecksum) + ")");
            } else {
                blocks.write(blockPayload, blockStart, blockSize);
            }
        }
    }
}
