package info.nightscout.android.utils;

import android.content.Context;
import android.util.AttributeSet;
import java.text.DateFormatSymbols;

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

            try {
                for (String part : parts) {
                    String data[] = part.split(";");

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

                        case "hour":
                            sb.append(FormatKit.getInstance().getString(R.string.hour));
                            break;
                        case "hour_h":
                            sb.append(FormatKit.getInstance().getString(R.string.hour_h));
                            break;
                        case "minute":
                            sb.append(FormatKit.getInstance().getString(R.string.minute));
                            break;
                        case "minute_m":
                            sb.append(FormatKit.getInstance().getString(R.string.minute_m));
                            break;

                        case "am_pm":
                            // localised minimal representation of am/pm string options used by Urchin watchface
                            String[] ampm = DateFormatSymbols.getInstance().getAmPmStrings();
                            String am = ampm[0].replace(".", "").replace(",", "").toLowerCase();
                            String pm = ampm[1].replace(".", "").replace(",", "").toLowerCase();
                            am = am.length() > 1 ? am.substring(0, 2) : (am.length() > 0 ? am.substring(0, 1) : "");
                            pm = pm.length() > 1 ? pm.substring(0, 2) : (pm.length() > 0 ? pm.substring(0, 1) : "");
                            switch (data[1]) {
                                case "am":
                                    sb.append(am);
                                    break;
                                case "AM":
                                    sb.append(am.toUpperCase());
                                    break;
                                case "a":
                                    sb.append(am.length() > 1 ? am.substring(0,1) : am);
                                    break;
                                case "A":
                                    sb.append(am.length() > 1 ? am.substring(0,1).toUpperCase() : am.toUpperCase());
                                    break;
                                case "pm":
                                    sb.append(pm);
                                    break;
                                case "PM":
                                    sb.append(pm.toUpperCase());
                                    break;
                                case "p":
                                    sb.append(pm.length() > 1 ? pm.substring(0,1) : pm);
                                    break;
                                case "P":
                                    sb.append(pm.length() > 1 ? pm.substring(0,1).toUpperCase() : pm.toUpperCase());
                                    break;
                                default:
                                    sb.append("[am_pm error]");
                            }
                            break;

                        case "prime_cannula":
                            sb.append(FormatKit.getInstance().getString(R.string.pref_ns_cannula_change_Prime_Cannula));
                            break;
                        case "urchin_u":
                            sb.append(FormatKit.getInstance().getString(R.string.urchin_text_unit_u));
                            break;
                        case "urchin_U":
                            sb.append(FormatKit.getInstance().getString(R.string.urchin_text_unit_U));
                            break;
                        case "urchin_none":
                            sb.append(FormatKit.getInstance().getString(R.string.urchin_text_none));
                            break;
                        case "urchin_space":
                            sb.append(FormatKit.getInstance().getString(R.string.urchin_text_space));
                            break;
                        case "urchin_dot":
                            sb.append(FormatKit.getInstance().getString(R.string.urchin_text_dot));
                            break;
                        case "urchin_bullet":
                            sb.append(FormatKit.getInstance().getString(R.string.urchin_text_bullet));
                            break;
                        case "urchin_dash":
                            sb.append(FormatKit.getInstance().getString(R.string.urchin_text_dash));
                            break;
                        case "urchin_forward_slash":
                            sb.append(FormatKit.getInstance().getString(R.string.urchin_text_forward_slash));
                            break;
                        case "urchin_backslash":
                            sb.append(FormatKit.getInstance().getString(R.string.urchin_text_backslash));
                            break;
                        case "urchin_line":
                            sb.append(FormatKit.getInstance().getString(R.string.urchin_text_line));
                            break;
                        case "urchin_hi":
                            sb.append(FormatKit.getInstance().getString(R.string.urchin_watchface_Battery_Hi));
                            break;
                        case "urchin_lo":
                            sb.append(FormatKit.getInstance().getString(R.string.urchin_watchface_Battery_Lo));
                            break;
                        case "urchin_high":
                            sb.append(FormatKit.getInstance().getString(R.string.urchin_watchface_Battery_High));
                            break;
                        case "urchin_medium":
                            sb.append(FormatKit.getInstance().getString(R.string.urchin_watchface_Battery_Medium));
                            break;
                        case "urchin_low":
                            sb.append(FormatKit.getInstance().getString(R.string.urchin_watchface_Battery_Low));
                            break;
                        case "urchin_full":
                            sb.append(FormatKit.getInstance().getString(R.string.urchin_watchface_Battery_Full));
                            break;
                        case "urchin_empty":
                            sb.append(FormatKit.getInstance().getString(R.string.urchin_watchface_Battery_Empty));
                            break;

                        default:
                            sb.append(data[0]);
                    }
                }
            } catch (Exception e) {
                sb.append("[error]");
            }

            out[i++] = sb.toString();
        }

        setEntries(out);
    }
}
