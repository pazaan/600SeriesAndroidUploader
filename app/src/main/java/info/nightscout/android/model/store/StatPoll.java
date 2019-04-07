package info.nightscout.android.model.store;

import java.util.Date;

import io.realm.RealmObject;
import io.realm.annotations.Ignore;
import io.realm.annotations.Index;
import io.realm.annotations.PrimaryKey;

public class StatPoll extends RealmObject implements StatInterface {
    @Ignore
    private static final String TAG = StatPoll.class.getSimpleName();

    @PrimaryKey
    private String key;
    @Index
    private Date date;

    private int pollCount;
    private int pollConnect;
    private int pollNoConnect;
    private int pollRSSIweak;
    private int pollRSSI;
    private int pollPumpStatus;
    private int pollError;
    private int pollUsbError;
    private int pollCgmNA;
    private int pollCgmOld;
    private int pollCgmLost;

    private int historyCgmRequest;
    private int historyCgmSuccess;
    private int historyPumpRequest;
    private int historyPumpSuccess;

    private int historyReqStale;
    private int historyReqRecency;
    private int historyReqAlert;
    private int historyReqAlertRecheck;
    private int historyReqAlertCleared;
    private int historyReqAutoMode;
    private int historyReqTreatment;
    private int historyReqBG;
    private int historyReqCalibration;
    private int historyReqConsumable;
    private int historyReqBackfill;
    private int historyReqEstimate;

    private int timer;
    private long timerMS;
    private int timer1;
    private long timer1MS;

    public void timer(long timer) {
        if (timer <= 10000) {
            timer1++;
            timer1MS += timer;
        } else {
            this.timer++;
            timerMS += + timer;
        }
    }

    @Override
    public String toString() {
        return String.format("Run: %s Connect: %s/%s RSSI: %s%% weak: %s(%s%%) Status: %s Error: %s CgmNA: %s CgmOld: %s CgmLost: %s HistoryCgm: %s/%s HistoryPump: %s/%s Recency: %s Stale: %s Alert: %s~%s~%s Automode: %s Treatment: %s BG: %s~%s Consumable: %s Backfill: %s Estimate: %s  Timers: %s~%sms %s~%sms",
                pollCount,
                pollConnect,
                pollConnect + pollNoConnect,
                pollConnect == 0 ? 0 : pollRSSI / pollConnect,
                pollRSSIweak,
                pollConnect == 0 ? 0 : (pollRSSIweak * 100) / pollConnect,
                pollPumpStatus,
                pollError,
                pollCgmNA,
                pollCgmOld,
                pollCgmLost,
                historyCgmSuccess,
                historyCgmRequest,
                historyPumpSuccess,
                historyPumpRequest,
                historyReqRecency,
                historyReqStale,
                historyReqAlert,
                historyReqAlertRecheck,
                historyReqAlertCleared,
                historyReqAutoMode,
                historyReqTreatment,
                historyReqBG,
                historyReqCalibration,
                historyReqConsumable,
                historyReqBackfill,
                historyReqEstimate,
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

    public void incPollCount() {
        pollCount++;
    }

    public int getPollCount() {
        return pollCount;
    }

    public void incPollConnect() {
        pollConnect++;
    }

    public int getPollConnect() {
        return pollConnect;
    }

    public void incPollNoConnect() {
        pollNoConnect++;
    }

    public int getPollNoConnect() {
        return pollNoConnect;
    }

    public void incPollRSSIweak() {
        pollRSSIweak++;
    }

    public int getPollRSSIweak() {
        return pollRSSIweak;
    }

    public void setPollRSSIweak(int pollRSSIweak) {
        this.pollRSSIweak = pollRSSIweak;
    }

    public int getPollRSSI() {
        return pollRSSI;
    }

    public void setPollRSSI(int pollRSSI) {
        this.pollRSSI = pollRSSI;
    }

    public void incPollPumpStatus() {
        pollPumpStatus++;
    }

    public int getPollPumpStatus() {
        return pollPumpStatus;
    }

    public void incPollError() {
        pollError++;
    }

    public void decPollError() {
        pollError--;
    }

    public int getPollError() {
        return pollError;
    }

    public void incPollUsbError() {
        pollUsbError++;
    }

    public int getPollUsbError() {
        return pollUsbError;
    }

    public void incPollCgmNA() {
        pollCgmNA++;
    }

    public int getPollCgmNA() {
        return pollCgmNA;
    }

    public void incPollCgmOld() {
        pollCgmOld++;
    }

    public int getPollCgmOld() {
        return pollCgmOld;
    }

    public void incPollCgmLost() {
        pollCgmLost++;
    }

    public int getPollCgmLost() {
        return pollCgmLost;
    }

    public int getHistoryCgmRequest() {
        return historyCgmRequest;
    }

    public void incHistoryCgmRequest() {
        historyCgmRequest++;
    }

    public int getHistoryCgmSuccess() {
        return historyCgmSuccess;
    }

    public void incHistoryCgmSuccess() {
        historyCgmSuccess++;
    }

    public int getHistoryPumpRequest() {
        return historyPumpRequest;
    }

    public void incHistoryPumpRequest() {
        historyPumpRequest++;
    }

    public int getHistoryPumpSuccess() {
        return historyPumpSuccess;
    }

    public void incHistoryPumpSuccess() {
        historyPumpSuccess++;
    }

    public int getHistoryReqStale() {
        return historyReqStale;
    }

    public void incHistoryReqStale() {
        historyReqStale++;
    }

    public int getHistoryReqRecency() {
        return historyReqRecency;
    }

    public void incHistoryReqRecency() {
        historyReqRecency++;
    }

    public int getHistoryReqAlert() {
        return historyReqAlert;
    }

    public void incHistoryReqAlert() {
        historyReqAlert++;
    }

    public int getHistoryReqAlertRecheck() {
        return historyReqAlertRecheck;
    }

    public void incHistoryReqAlertRecheck() {
        historyReqAlertRecheck++;
    }

    public int getHistoryReqAlertCleared() {
        return historyReqAlertCleared;
    }

    public void incHistoryReqAlertCleared() {
        historyReqAlertCleared++;
    }

    public int getHistoryReqAutoMode() {
        return historyReqAutoMode;
    }

    public void incHistoryReqAutoMode() {
        historyReqAutoMode++;
    }

    public int getHistoryReqTreatment() {
        return historyReqTreatment;
    }

    public void incHistoryReqTreatment() {
        historyReqTreatment++;
    }

    public int getHistoryReqBG() {
        return historyReqBG;
    }

    public void incHistoryReqBG() {
        historyReqBG++;
    }

    public int getHistoryReqCalibration() {
        return historyReqCalibration;
    }

    public void incHistoryReqCalibration() {
        historyReqCalibration++;
    }

    public int getHistoryReqConsumable() {
        return historyReqConsumable;
    }

    public void incHistoryReqConsumable() {
        historyReqConsumable++;
    }

    public int getHistoryReqBackfill() {
        return historyReqBackfill;
    }

    public void incHistoryReqBackfill() {
        historyReqBackfill++;
    }

    public int getHistoryReqEstimate() {
        return historyReqEstimate;
    }

    public void incHistoryReqEstimate() {
        historyReqEstimate++;
    }
}
