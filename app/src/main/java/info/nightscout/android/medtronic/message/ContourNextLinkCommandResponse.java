package info.nightscout.android.medtronic.message;

/**
 * Created by volker on 10.12.2016.
 */
public class ContourNextLinkCommandResponse extends ContourNextLinkBinaryResponseMessage {

    public ContourNextLinkCommandResponse(byte[] payload) throws ChecksumException {
        super(payload);
    }
}
