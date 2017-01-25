package info.nightscout.android.medtronic.message;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;

/**
 * Created by lgoedhart on 10/05/2016.
 */
public class DeviceInfoResponseCommandMessage extends ContourNextLinkResponseMessage {
    private String serial = "";
    private final Pattern pattern = Pattern.compile(".*?\\^(\\d{4}-\\d{7})\\^.*");

    protected DeviceInfoResponseCommandMessage(byte[] payload)
            throws ChecksumException, EncryptionException, TimeoutException, UnexpectedMessageException, IOException {
        super(payload);

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