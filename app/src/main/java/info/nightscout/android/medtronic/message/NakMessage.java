package info.nightscout.android.medtronic.message;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;

/**
 * Created by Pogman on 24.10.17.
 */

public class NakMessage extends MedtronicSendMessageRequestMessage {
    private static final String TAG = NakMessage.class.getSimpleName();

    public NakMessage(MedtronicCnlSession pumpSession, byte[] payload) throws EncryptionException, ChecksumException {
        super(MessageType.NAK_COMMAND, pumpSession, payload);
    }

    public NakMessage send(UsbHidDriver mDevice) throws IOException, TimeoutException, ChecksumException, EncryptionException, UnexpectedMessageException {
        sendToPump(mDevice, TAG);

        return null;
    }
}

/*

    NAK code

      NO_ERROR: 0x00,
      PAUSE_IS_REQUESTED: 0x02,
      SELF_TEST_HAS_FAILED: 0x03,
      MESSAGE_WAS_REFUSED: 0x04,
      TIMEOUT_ERROR: 0x05,
      ELEMENT_VERSION_IS_NOT_CORRECT: 0x06,
      DEVICE_HAS_ERROR: 0x07,
      MESSAGE_IS_NOT_SUPPORTED: 0x08, // CLP says 0x0B :\
      DATA_IS_OUT_OF_RANGE: 0x09,
      DATA_IS_NOT_CONSISTENT: 0x0A,
      FEATURE_IS_DISABLED: 0x0B, // CLP says 0x0B here, too
      DEVICE_IS_BUSY: 0x0C,
      DATA_DOES_NOT_EXIST: 0x0D,
      HARDWARE_FAILURE: 0x0E,
      DEVICE_IS_IN_WRONG_STATE: 0x0F,
      DATA_IS_LOCKED_BY_ANOTHER: 0x10,
      DATA_IS_NOT_LOCKED: 0x11,
      CANNULA_FILL_CANNOT_BE_PERFORMED: 0x12,
      DEVICE_IS_DISCONNECTED: 0x13,
      EASY_BOLUS_IS_ACTIVE: 0x14,
      PARAMETERS_ARE_NOT_AVAILABLE: 0x15,
      MESSAGE_IS_OUT_OF_SEQUENCE: 0x16,
      TEMP_BASAL_RATE_OUT_OF_RANGE: 0x17,

 */