package info.nightscout.android.medtronic.message;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class ContourNextLinkMessage {
    protected ByteBuffer mPayload;

    public ContourNextLinkMessage(byte[] bytes) {
        if (bytes != null) {
            this.mPayload = ByteBuffer.allocate(bytes.length);
            this.mPayload.put(bytes);
        }
    }

    public byte[] encode() {
        return mPayload.array();
    }

    public void send(ContourNextLinkMessageHandler handler) throws IOException {
        handler.sendMessage(this);
    }

    // FIXME - get rid of this - make a Builder instead
    protected void setPayload(byte[] payload) {
        mPayload = ByteBuffer.allocate(payload.length);
        mPayload.put(payload);
    }
}
