package info.nightscout.android.model.store;

import java.util.Date;

import info.nightscout.android.utils.FormatKit;
import io.realm.RealmObject;
import io.realm.annotations.Ignore;
import io.realm.annotations.Index;
import io.realm.annotations.PrimaryKey;

public class StatPushover extends RealmObject implements StatInterface {
    @Ignore
    private static final String TAG = StatNightscout.class.getSimpleName();

    @PrimaryKey
    private String key;
    @Index
    private Date date;

    private int run;
    private int error;
    private int validError;

    private int messagesSent;

    private int limit;
    private int remaining;
    private long resetTime;

    @Override
    public String toString() {
        return String.format("Run: %s Error: %s ValidError: %s Sent: %s Limit: %s/%s Reset: %s",
                run,
                error,
                validError,
                messagesSent,
                limit - remaining,
                limit,
                resetTime == 0 ? "-" : FormatKit.getInstance().formatAsYMD(resetTime)
        );
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public void setKey(String key) {
        this.key = key;
    }

    @Override
    public Date getDate() {
        return date;
    }

    @Override
    public void setDate(Date date) {
        this.date = date;
    }

    public int getRun() {
        return run;
    }

    public void incRun() {
        run++;
    }

    public int getError() {
        return error;
    }

    public void incError() {
        error++;
    }

    public int getValidError() {
        return validError;
    }

    public void incValidError() {
        validError++;
    }

    public int getMessagesSent() {
        return messagesSent;
    }

    public void setMessagesSent(int messagesSent) {
        this.messagesSent = messagesSent;
    }

    public void incMessagesSent() {
        messagesSent++;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getRemaining() {
        return remaining;
    }

    public void setRemaining(int remaining) {
        this.remaining = remaining;
    }

    public long getResetTime() {
        return resetTime;
    }

    public void setResetTime(long resetTime) {
        this.resetTime = resetTime;
    }
}
