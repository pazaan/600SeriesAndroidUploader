package com.nightscout.android.upload.MedtronicNG;

import com.nightscout.android.upload.DeviceRecord;

import java.io.Serializable;
import java.util.Date;

/**
 * Created by lgoedhart on 27/03/2016.
 */
public class CGMRecord extends DeviceRecord implements Serializable {
    public enum TREND {
        NONE(0),
        DOUBLE_UP(1),
        SINGLE_UP(2),
        FOURTY_FIVE_UP(3),
        FLAT(4),
        FOURTY_FIVE_DOWN(5),
        SINGLE_DOWN(6),
        DOUBLE_DOWN(7),
        NOT_COMPUTABLE(8),
        RATE_OUT_OF_RANGE(9),
        NOT_SET(10);

        private byte value;
        TREND(int trend) {
           this.value = (byte)trend;
        }
    }

    private TREND trend = TREND.NOT_SET;

    //public Date pumpDate = new Date(); // Store as a date, so we can parse to string later.
    public int sensorBGL = 0; // in mg/dL. 0 means no sensor reading
    public Date sensorBGLDate = new Date();
    public String direction;

    public void setTrend( TREND trend ) {
        this.trend = trend;

        switch( trend ) {
            case NONE:
                this.direction = "NONE";
                break;
            case DOUBLE_UP:
                this.direction = "DoubleUp";
                break;
            case SINGLE_UP:
                this.direction = "SingleUp";
                break;
            case FOURTY_FIVE_UP:
                this.direction = "FortyFiveUp";
                break;
            case FLAT:
                this.direction = "Flat";
                break;
            case FOURTY_FIVE_DOWN:
                this.direction = "FortyFiveDown";
                break;
            case SINGLE_DOWN:
                this.direction = "SingleDown";
                break;
            case DOUBLE_DOWN:
                this.direction = "DoubleDown";
                break;
            case NOT_COMPUTABLE:
                this.direction = "NOT COMPUTABLE";
                break;
            case RATE_OUT_OF_RANGE:
                this.direction = "RATE OUT OF RANGE";
                break;
            case NOT_SET:
                this.direction = "NONE";
                break;
        }
    }

    public TREND getTrend() {
        return trend;
    }

    public static TREND fromMessageByte(byte messageByte) {
        switch( messageByte ) {
            case (byte) 0x60:
                return TREND.FLAT;
            case (byte) 0xc0:
                return TREND.DOUBLE_UP;
            case (byte) 0xa0:
                return TREND.SINGLE_UP;
            case (byte) 0x80:
                return TREND.FOURTY_FIVE_UP;
            case (byte) 0x40:
                return TREND.FOURTY_FIVE_DOWN;
            case (byte) 0x20:
                return TREND.SINGLE_DOWN;
            case (byte) 0x00:
                return TREND.DOUBLE_DOWN;
            default:
                return TREND.NOT_COMPUTABLE;
        }
    }
}
