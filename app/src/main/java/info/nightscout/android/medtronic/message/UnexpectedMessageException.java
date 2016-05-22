package info.nightscout.android.medtronic.message;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class UnexpectedMessageException extends Throwable {
    public UnexpectedMessageException(String message) {
        super(message);
    }
}
