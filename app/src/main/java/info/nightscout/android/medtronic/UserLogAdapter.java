package info.nightscout.android.medtronic;

import android.content.Context;
import android.graphics.Typeface;
import android.support.annotation.NonNull;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.mikepenz.iconics.IconicsDrawable;

import info.nightscout.android.R;
import info.nightscout.android.model.store.UserLog;
import info.nightscout.android.utils.FormatKit;
import io.realm.RealmBasedRecyclerViewAdapter;
import io.realm.RealmResults;
import io.realm.RealmViewHolder;

public class UserLogAdapter
        extends RealmBasedRecyclerViewAdapter<UserLog, UserLogAdapter.ViewHolder> {

    private final static int FADE_DURATION_MS = 400;

    private final static int cDEFAULT = 0xFFC0C0C0;
    private final static int cHIGHLIGHT = 0xFFE0E0E0;
    private final static int cCLOCK = 0xFFA0A0A0;
    private final static int cWARN = 0xFFE0E000;
    private final static int cCGM = 0xFFCCA3A3;
    private final static int cESTIMATE = 0xFFB7DAE5;
    private final static int cHISTORY = 0xFFABAFCC;
    private final static int cHEART = 0xFFFF0000;

    private IconicsDrawable iWARN;
    private IconicsDrawable iINFO;
    private IconicsDrawable iNOTE;
    private IconicsDrawable iHELP;
    private IconicsDrawable iREFRESH;
    private IconicsDrawable iCGM;
    private IconicsDrawable iSETTING;
    private IconicsDrawable iHEART;
    private IconicsDrawable iSHARE;

    private int cDefault;
    private int iBounds;
    private int iOffsetXDp;
    private int iOffsetYDp;

    private int lastAnimPosition = -1;

    private Context mContext;

    public UserLogAdapter(
            Context context,
            RealmResults<UserLog> realmResults,
            boolean automaticUpdate) {
        super(context, realmResults, automaticUpdate, false);

        mContext = context;
    }

    @Override
    public ViewHolder onCreateRealmViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
        View v = inflater.inflate(R.layout.log_item, viewGroup, false);
        ViewHolder vh = new ViewHolder((FrameLayout) v);
        return vh;
    }

    @Override
    public void onBindRealmViewHolder(@NonNull ViewHolder viewHolder, int position) {
        final UserLog userLog = realmResults.get(position);

        setContent(viewHolder.textView, userLog);

        if (position > lastAnimPosition) {
            setFadeAnimation(viewHolder.itemView);
            lastAnimPosition = position;
        }
    }

    public class ViewHolder extends RealmViewHolder {

        public TextView textView;

        public ViewHolder(FrameLayout container) {
            super(container);
            this.textView = container.findViewById(R.id.log_textview);
        }
    }

    public int getLastAnimPosition() {
        return lastAnimPosition;
    }

    public void setLastAnimPosition(int lastAnimPosition) {
        this.lastAnimPosition = lastAnimPosition;
    }

    private void setFadeAnimation(View view) {
        AlphaAnimation anim = new AlphaAnimation(0.0f, 1.0f);
        anim.setDuration(FADE_DURATION_MS);
        view.startAnimation(anim);
    }

    private void setContent(TextView tv, UserLog ul) {

        if (iWARN == null) initIcons(tv);

        SpannableStringBuilder ssb = new SpannableStringBuilder();

        UserLogMessage.TYPE type = UserLogMessage.TYPE.convert(ul.getType());
        String clock = FormatKit.getInstance().formatAsDayClockSeconds(ul.getTimestamp());
        String text = ul.getMessageParsed();

        switch (type) {
            case WARN:
                ssb.append(" * ").append(text);
                ssb.setSpan(new ImageSpan(iWARN, DynamicDrawableSpan.ALIGN_BASELINE), 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new ForegroundColorSpan(cWARN), 3, text.length() + 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
            case HELP:
                ssb.append(" * ").append(text);
                ssb.setSpan(new ImageSpan(iHELP, DynamicDrawableSpan.ALIGN_BASELINE), 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new ForegroundColorSpan(cHIGHLIGHT), 3, text.length() + 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
            case INFO:
                ssb.append(" * ").append(text);
                ssb.setSpan(new ImageSpan(iINFO, DynamicDrawableSpan.ALIGN_BASELINE), 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new ForegroundColorSpan(cHIGHLIGHT), 3, text.length() + 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
            case NOTE:
                ssb.append(" * ").append(text);
                ssb.setSpan(new ImageSpan(iNOTE, DynamicDrawableSpan.ALIGN_BASELINE), 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new ForegroundColorSpan(cHIGHLIGHT), 3, text.length() + 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
            case HISTORY:
                ssb.append(" * ").append(text);
                ssb.setSpan(new ImageSpan(iREFRESH, DynamicDrawableSpan.ALIGN_BASELINE), 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
            case CGM:
                ssb.append(" * ").append(text);
                ssb.setSpan(new ImageSpan(iCGM, DynamicDrawableSpan.ALIGN_BASELINE), 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new ForegroundColorSpan(cCGM), 3, text.length() + 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
            case OPTION:
                ssb.append(" * ").append(text);
                ssb.setSpan(new ImageSpan(iSETTING, DynamicDrawableSpan.ALIGN_BASELINE), 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new ForegroundColorSpan(cHIGHLIGHT), 3, text.length() + 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
            case STARTUP:
            case SHUTDOWN:
            case HEART:
                ssb.append(" * ").append(text);
                ssb.setSpan(new ImageSpan(iHEART, DynamicDrawableSpan.ALIGN_BASELINE), 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new ForegroundColorSpan(cHIGHLIGHT), 3, text.length() + 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), 3, text.length() + 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
            case SGV:
                ssb.append(" ").append(text);
                ssb.setSpan(new ForegroundColorSpan(cHIGHLIGHT), 1, text.length() + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
            case ESTIMATE:
                ssb.append(" ").append(text);
                ssb.setSpan(new ForegroundColorSpan(cESTIMATE), 1, text.length() + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
            case REQUESTED:
            case RECEIVED:
                ssb.append(" ").append(text);
                ssb.setSpan(new ForegroundColorSpan(cHISTORY), 1, text.length() + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
            case SHARE:
                ssb.append(" * ").append(text);
                ssb.setSpan(new ImageSpan(iSHARE, DynamicDrawableSpan.ALIGN_BASELINE), 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new ForegroundColorSpan(cHIGHLIGHT), 3, text.length() + 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
            default:
                ssb.append(" ").append(text);
                ssb.setSpan(new ForegroundColorSpan(cDEFAULT), 1, text.length() + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        ssb.insert(0, clock);
        ssb.setSpan(new ForegroundColorSpan(cCLOCK), 0, clock.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new AbsoluteSizeSpan(11, true), 0, clock.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        tv.setText(ssb);
    }

    private void initIcons(TextView tv) {

        iBounds = (int) (tv.getTextSize() * 1.2);
        iOffsetXDp = 0;
        iOffsetYDp = 3;

        iWARN = icon("ion_alert_circled", cWARN);
        iINFO = icon("ion_information_circled", cHIGHLIGHT);
        iNOTE = icon("ion_document", cHIGHLIGHT);
        //iNOTE = icon("ion_stats_bars", cHIGHLIGHT);
        iHELP = icon("ion_ios_lightbulb", cHIGHLIGHT);
        iCGM = icon("ion_ios_pulse_strong", cCGM);
        iHEART = icon("ion_heart", cHEART);
        iSHARE = icon("ion_android_share_alt", cHIGHLIGHT);
        //iREFRESH = icon("ion_loop", cDEFAULT);
        iREFRESH = icon("ion_refresh", cDEFAULT);
        iSETTING = icon("ion_android_settings", cHIGHLIGHT);
    }

    private IconicsDrawable icon(String icon, int color) {

        IconicsDrawable iconicsDrawable = new IconicsDrawable(mContext)
                .icon(icon)
                .iconOffsetXDp(iOffsetXDp)
                .iconOffsetYDp(iOffsetYDp)
                .color(color);

        iconicsDrawable.setBounds(0,0, iBounds, iBounds);

        return iconicsDrawable;
    }

}
