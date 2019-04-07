package info.nightscout.android.model.store;

import info.nightscout.android.utils.FormatKit;
import io.realm.RealmObject;
import io.realm.annotations.Index;
import io.realm.annotations.PrimaryKey;

public class UserLog extends RealmObject {
    @PrimaryKey
    private long index;
    @Index
    private long timestamp;
    @Index
    private int type;
    @Index
    private int flag;

    private String message;

    public UserLog message(long index, long timestamp, int type, int flag, String message) {
        this.index = index;
        this.timestamp = timestamp;
        this.type = type;
        this.flag = flag;
        this.message = message;
        return this;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getType() {
        return type;
    }

    public int getFlag() {
        return flag;
    }

    public String getMessage() {
        return message;
    }

    public String getMessageParsed() {

        StringBuilder sb = new StringBuilder();

        try {
            String parts[] = message.split("[{}]");
            for (String part : parts) {
                String data[] = part.split(";");
                switch (data[0]) {
                    case "str":
                        sb.append(FormatKit.getInstance().getString(data[1]));
                        break;
                    case "qstr":
                        sb.append(FormatKit.getInstance().getQuantityString(data[1], Integer.parseInt(data[2])));
                        break;
                    case "id":
                        sb.append(FormatKit.getInstance().getString(Integer.parseInt(data[1])));
                        break;
                    case "qid":
                        sb.append(FormatKit.getInstance().getQuantityString(Integer.parseInt(data[1]), Integer.parseInt(data[2])));
                        break;
                    case "sgv":
                        sb.append(FormatKit.getInstance().formatAsGlucose(Integer.parseInt(data[1]), false, true));
                        break;
                    case "diff":
                        sb.append(FormatKit.getInstance().formatSecondsAsDiff(Integer.parseInt(data[1])));
                        break;
                    case "weekday":
                        sb.append(FormatKit.getInstance().formatAsDayName(Long.parseLong(data[1])));
                        break;
                    case "date":
                        sb.append(FormatKit.getInstance().formatAsYMD(Long.parseLong(data[1])));
                        break;
                    case "date.time":
                        sb.append(FormatKit.getInstance().formatAsYMD(Long.parseLong(data[1])));
                        sb.append(" ");
                        sb.append(FormatKit.getInstance().formatAsClockSeconds(Long.parseLong(data[1])));
                        break;
                    case "time.sgv":
                        sb.append(FormatKit.getInstance().formatAsClock(Long.parseLong(data[1])));
                        break;
                    case "time.poll":
                        sb.append(FormatKit.getInstance().formatAsClock(Long.parseLong(data[1])));
                        break;
                    case "time.hist":
                        sb.append(FormatKit.getInstance().formatAsDayClock(Long.parseLong(data[1])));
                        break;
                    case "time.sgv.e":
                        sb.append(FormatKit.getInstance().formatAsClockSecondsNoAmPm(Long.parseLong(data[1])));
                        break;
                    case "time.poll.e":
                        sb.append(FormatKit.getInstance().formatAsClockSecondsNoAmPm(Long.parseLong(data[1])));
                        break;
                    case "time.hist.e":
                        sb.append(FormatKit.getInstance().formatAsYMD(Long.parseLong(data[1])));
                        sb.append(" ");
                        sb.append(FormatKit.getInstance().formatAsClockSeconds(Long.parseLong(data[1])));
                        break;
                    default:
                        if (data[0].length() > 0) sb.append(data[0]);
                }
            }
        } catch (Exception e) {
            sb.append("???");
        }

        return sb.toString();
    }

}
