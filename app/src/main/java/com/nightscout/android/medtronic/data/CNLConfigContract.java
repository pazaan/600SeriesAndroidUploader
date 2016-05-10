package com.nightscout.android.medtronic.data;

import android.provider.BaseColumns;

/**
 * Created by lgoedhart on 9/05/2016.
 */
public class CNLConfigContract {
    // To prevent someone from accidentally instantiating the contract class,
    // give it an empty constructor.
    public CNLConfigContract() {}

    /* Inner class that defines the table contents */
    public static abstract class ConfigEntry implements BaseColumns {
        public static final String TABLE_NAME = "config";
        public static final String COLUMN_NAME_STICK_SERIAL = "stick_serial";
        public static final String COLUMN_NAME_HMAC = "hmac";
        public static final String COLUMN_NAME_KEY = "key";
        public static final String COLUMN_NAME_LAST_RADIO_CHANNEL = "last_radio_channel";
    }
}
