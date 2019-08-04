package info.nightscout.android.model.store;

import java.util.Date;

import io.realm.RealmObject;
import io.realm.annotations.Ignore;
import io.realm.annotations.Index;
import io.realm.annotations.PrimaryKey;

public class StatCnl extends RealmObject implements StatInterface {
    @Ignore
    private static final String TAG = StatCnl.class.getSimpleName();

    @PrimaryKey
    private String key;
    @Index
    private Date date;

    private Date cnlConnectDate;
    private Date cnlDisconnectDate;
    private int cnlConnect;
    private int cnlDisconnect;
    private int cnlError;
    private int cnlJitter;

    public void connected() {
        long now = System.currentTimeMillis();
        cnlConnect++;
        cnlConnectDate = new Date(now);
        if (cnlDisconnectDate != null) {
            long timespan = now - cnlDisconnectDate.getTime();
            if (timespan < 4000L) cnlJitter++;
        }
    }

    public void disconnected() {
        long now = System.currentTimeMillis();
        cnlDisconnect++;
        cnlDisconnectDate = new Date(now);
    }

    @Override
    public String toString() {
        return String.format("Connect: %s Disconnect: %s Error: %s Jitter: %s",
                cnlConnect,
                cnlDisconnect,
                cnlError,
                cnlJitter
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

    public Date getCnlConnectDate() {
        return cnlConnectDate;
    }

    public void setCnlConnectDate(Date cnlConnectDate) {
        this.cnlConnectDate = cnlConnectDate;
    }

    public Date getCnlDisconnectDate() {
        return cnlDisconnectDate;
    }

    public void setCnlDisconnectDate(Date cnlDisconnectDate) {
        this.cnlDisconnectDate = cnlDisconnectDate;
    }

    public int getCnlConnect() {
        return cnlConnect;
    }

    public void setCnlConnect(int cnlConnect) {
        this.cnlConnect = cnlConnect;
    }

    public int getCnlDisconnect() {
        return cnlDisconnect;
    }

    public void setCnlDisconnect(int cnlDisconnect) {
        this.cnlDisconnect = cnlDisconnect;
    }

    public int getCnlError() {
        return cnlError;
    }

    public void setCnlError(int cnlError) {
        this.cnlError = cnlError;
    }

    public int getCnlJitter() {
        return cnlJitter;
    }

    public void setCnlJitter(int cnlJitter) {
        this.cnlJitter = cnlJitter;
    }
}
