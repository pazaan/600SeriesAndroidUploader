package info.nightscout.android.history;

import java.util.Date;

/**
 * Created by Pogman on 10.5.18.
 */

public class MessageItem {

    private Date date = new Date(System.currentTimeMillis());

    private PRIORITY priority = PRIORITY.NORMAL;
    private TYPE type = TYPE.INFO;

    private String clock = "";
    private String title = "";
    private String message = "";
    private String extended = "";

    private boolean cleared = false;
    private boolean silenced = false;

    public enum TYPE {
        INFO,
        ERROR,
        ALERT,
        ALERT_EMERGENCY,
        ALERT_ACTIONABLE,
        ALERT_INFORMATIONAL,
        ALERT_ON_HIGH,
        ALERT_ON_LOW,
        ALERT_BEFORE_HIGH,
        ALERT_BEFORE_LOW,
        ALERT_UPLOADER_ERROR,
        ALERT_UPLOADER_STATUS,
        ALERT_UPLOADER_BATTERY,
        AUTOMODE_ACTIVE,
        AUTOMODE_STOP,
        AUTOMODE_EXIT,
        AUTOMODE_MINMAX,
        REMINDER,
        BOLUS,
        BASAL,
        BG,
        SUSPEND,
        RESUME,
        CONSUMABLE,
        CALIBRATION,
        DAILY_TOTALS,
        NA
    }

    public enum PRIORITY {
        LOWEST(-2),
        LOW(-1),
        NORMAL(0),
        HIGH(1),
        EMERGENCY(2);

        private int value;

        PRIORITY(int value) {
            this.value = value;
        }

        public int value() {
            return value;
        }

        public boolean equals(int value) {
            return this.value == value;
        }

        public static PRIORITY convert(int value) {
            for (PRIORITY priority : PRIORITY.values())
                if (priority.value == value) return priority;
            return PRIORITY.NORMAL;
        }
    }

    public Date getDate() {
        return date;
    }

    public MessageItem date(Date date) {
        this.date = date;
        return this;
    }

    public MessageItem priority(PRIORITY priority) {
        this.priority = priority;
        return this;
    }

    public MessageItem type(TYPE type) {
        this.type = type;
        return this;
    }

    public MessageItem clock(String clock) {
        this.clock = clock;
        return this;
    }

    public MessageItem title(String title) {
        this.title = title;
        return this;
    }

    public MessageItem message(String message) {
        this.message = message;
        return this;
    }

    public MessageItem extended(String extended) {
        this.extended = extended;
        return this;
    }

    public MessageItem cleared(boolean cleared) {
        this.cleared = cleared;
        return this;
    }

    public MessageItem silenced(boolean silenced) {
        this.silenced = silenced;
        return this;
    }

    public PRIORITY getPriority() {
        return priority;
    }

    public int getPriorityValue() {
        return priority.value;
    }

    public TYPE getType() {
        return type;
    }

    public String getClock() {
        return clock;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getExtended() {
        return extended;
    }

    public boolean isCleared() {
        return cleared;
    }

    public boolean isSilenced() {
        return silenced;
    }

}