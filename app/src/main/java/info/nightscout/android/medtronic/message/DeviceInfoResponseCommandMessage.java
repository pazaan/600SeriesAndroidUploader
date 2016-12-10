package info.nightscout.android.medtronic.message;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import info.nightscout.android.medtronic.MedtronicCnlSession;

/**
 * Created by lgoedhart on 10/05/2016.
 */
public class DeviceInfoResponseCommandMessage extends MedtronicResponseMessage {
    private String serial = "";
    private final Pattern pattern = Pattern.compile(".*?\\^(\\d{4}-\\d{7})\\^.*");

    protected DeviceInfoResponseCommandMessage(MedtronicCnlSession pumpSession, byte[] payload)
            throws ChecksumException, EncryptionException, TimeoutException, UnexpectedMessageException, IOException {
        super(pumpSession, payload);

        extractStickSerial(new String(payload));
    }

    public String getSerial() {
        return serial;
    }

    private void extractStickSerial(String astmMessage) {
        Matcher matcher = pattern.matcher(astmMessage);
        if (matcher.find()) {
            serial = matcher.group(1);
        }
    }

}