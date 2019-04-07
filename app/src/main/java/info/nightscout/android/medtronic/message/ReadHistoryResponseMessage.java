package info.nightscout.android.medtronic.message;

import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;

/**
 * Created by Pogman on 6.10.17.
 */

public class ReadHistoryResponseMessage extends MedtronicSendMessageResponseMessage {
    private static final String TAG = ReadHistoryResponseMessage.class.getSimpleName();

    private byte[] eventData;
    private int reqStartRTC;
    private int reqEndRTC;
    private int reqType;
    private long reqStartTime;
    private long reqEndTime;

    protected ReadHistoryResponseMessage(MedtronicCnlSession pumpSession, byte[] payload) throws EncryptionException, ChecksumException, UnexpectedMessageException {
        super(pumpSession, payload);

        eventData = payload;
    }

    public byte[] getEventData() {
        return eventData;
    }

    public int getReqStartRTC() {
        return reqStartRTC;
    }

    public void setReqStartRTC(int reqStartRTC) {
        this.reqStartRTC = reqStartRTC;
    }

    public int getReqEndRTC() {
        return reqEndRTC;
    }

    public void setReqEndRTC(int reqEndRTC) {
        this.reqEndRTC = reqEndRTC;
    }

    public int getReqType() {
        return reqType;
    }

    public void setReqType(int reqType) {
        this.reqType = reqType;
    }

    public long getReqStartTime() {
        return reqStartTime;
    }

    public void setReqStartTime(long reqStartTime) {
        this.reqStartTime = reqStartTime;
    }

    public long getReqEndTime() {
        return reqEndTime;
    }

    public void setReqEndTime(long reqEndTime) {
        this.reqEndTime = reqEndTime;
    }
}

