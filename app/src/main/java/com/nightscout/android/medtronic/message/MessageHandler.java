package com.nightscout.android.medtronic.message;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public interface MessageHandler {
    void send( ContourNextLinkMessage message );
    ContourNextLinkMessage receive();
}
