package info.nightscout.android.model.store;

import java.util.Date;

import io.realm.RealmObject;
import io.realm.annotations.Ignore;
import io.realm.annotations.Index;
import io.realm.annotations.PrimaryKey;

public class StatNightscout extends RealmObject implements StatInterface {
    @Ignore
    private static final String TAG = StatNightscout.class.getSimpleName();

    @PrimaryKey
    private String key;
    @Index
    private Date date;

    private int run;
    private int error;
    private int siteUnavailable;
    private int totalRecords;
    private int totalHttp;
    private int timer;
    private long timerMS;
    private int timer1;
    private long timer1MS;

    public void timer(long timer) {
        if (timer <= 1000) {
            timer1++;
            timer1MS += timer;
        } else {
            this.timer++;
            timerMS += + timer;
        }
    }

    @Override
    public String toString() {
        return String.format("Run: %s Error: %s Unavailable: %s Records: %s Http: %s Timers: %s~%sms %s~%sms",
                run,
                error,
                siteUnavailable,
                totalRecords,
                totalHttp,
                timer,
                timer == 0 ? 0 : timerMS / timer,
                timer1,
                timer1 == 0 ? 0 : timer1MS / timer1
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

    public int getSiteUnavailable() {
        return siteUnavailable;
    }

    public void incSiteUnavailable() {
        siteUnavailable++;
    }

    public int getTotalRecords() {
        return totalRecords;
    }

    public void settotalRecords(int totalRecords) {
        this.totalRecords = totalRecords;
    }

    public int getTotalHttp() {
        return totalHttp;
    }

    public void setTotalHttp(int totalHttp) {
        this.totalHttp = totalHttp;
    }
}
