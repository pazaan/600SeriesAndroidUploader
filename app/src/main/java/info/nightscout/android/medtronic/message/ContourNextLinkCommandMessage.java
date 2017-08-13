package info.nightscout.android.medtronic.message;

import info.nightscout.android.medtronic.exception.ChecksumException;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class ContourNextLinkCommandMessage extends ContourNextLinkRequestMessage<ContourNextLinkCommandResponse> {
    public ContourNextLinkCommandMessage(ASCII command) {
        super(new byte[]{command.getValue()});
    }

    public ContourNextLinkCommandMessage(byte command) {
        super(new byte[]{command});
    }

    public ContourNextLinkCommandMessage(String command) {
        super(command.getBytes());
    }

    @Override
    protected ContourNextLinkCommandResponse getResponse(byte[] payload) throws ChecksumException {
        return new ContourNextLinkCommandResponse(payload);
    }

}
