package info.nightscout.android.utils;

import android.content.Context;
import android.util.AttributeSet;

import info.nightscout.android.R;

public class ListPreference extends android.preference.ListPreference{

    public ListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        parseEntries();
    }

    public ListPreference(Context context) {
        super(context);
        parseEntries();
    }

    private void parseEntries() {

        CharSequence[] entries = getEntries();

        CharSequence[] out = new String[entries.length];

        int i=0;
        for(CharSequence ch: entries){

            StringBuilder sb = new StringBuilder();

            String parts[] = ch.toString().split("[{}]");

            for (String part : parts) {
                String data[] = part.split(";");

                if (data.length < 2) {
                    sb.append(part);
                } else {
                    switch (data[0]) {
                        case "days":
                            sb.append(FormatKit.getInstance().getQuantityString(R.plurals.days, Integer.parseInt(data[1])));
                            break;
                        case "hours":
                            sb.append(FormatKit.getInstance().getQuantityString(R.plurals.hours, Integer.parseInt(data[1])));
                            break;
                        case "minutes":
                            sb.append(FormatKit.getInstance().getQuantityString(R.plurals.minutes, Integer.parseInt(data[1])));
                            break;
                        case "seconds":
                            sb.append(FormatKit.getInstance().getQuantityString(R.plurals.seconds, Integer.parseInt(data[1])));
                            break;
                        case "grams":
                            sb.append(FormatKit.getInstance().getQuantityString(R.plurals.grams, Integer.parseInt(data[1])));
                            break;
                        case "glucose":
                            sb.append(FormatKit.getInstance().formatAsGlucose(Integer.parseInt(data[1])));
                            break;
                        case "insulin":
                            sb.append(FormatKit.getInstance().formatAsInsulin(Double.parseDouble(data[1])));
                            break;
                        case "clock":
                            sb.append(FormatKit.getInstance().formatAsClock(Integer.parseInt(data[1]), Integer.parseInt(data[2])));
                            break;
                        case "duration":
                            sb.append(FormatKit.getInstance().formatMinutesAsDHM(Integer.parseInt(data[1])));
                            break;
                        case "pattern":
                            sb.append(FormatKit.getInstance().getNameBasalPattern(Integer.parseInt(data[1])));
                            break;
                        case "string":
                            sb.append(FormatKit.getInstance().getString(data[1]));
                            break;
                    }
                }
            }

            out[i++] = sb.toString();
        }

        setEntries(out);
    }
}
