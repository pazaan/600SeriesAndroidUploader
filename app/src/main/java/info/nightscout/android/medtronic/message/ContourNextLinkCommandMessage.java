package info.nightscout.android.medtronic.message;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class ContourNextLinkCommandMessage extends ContourNextLinkMessage {
    public ContourNextLinkCommandMessage(byte command) {
        super(new byte[]{command});
    }

    public ContourNextLinkCommandMessage(String command) {
        super(command.getBytes());
    }
}
