package com.nightscout.android.medtronic.message;

import java.nio.ByteBuffer;

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
