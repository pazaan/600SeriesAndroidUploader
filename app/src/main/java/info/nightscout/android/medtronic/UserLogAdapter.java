package info.nightscout.android.medtronic;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.support.annotation.NonNull;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.Log;
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
    private static final String TAG = UserLogAdapter.class.getSimpleName();

    private final static int FADE_DURATION_MS = 400;

    // HSV Hue 0-360 Saturation 0-1 Value 0-1
    private final static float[] WARN_HSV = new float[] {60f, .85f, 1f};
    private final static float[] CGM_HSV =new float[] {0f, .22f, 1f};
    private final static float[] HISTORY_HSV = new float[] {270f, .22f, 1f};
    private final static float[] HEART_HSV = new float[] {0f, 1f, 1f};
    private final static float[] SHARE_HSV = new float[] {125f, .22f, 1f};
    private final static float[] PUSHOVER_HSV = new float[] {40f, .22f, 1f};
    private final static float[] NOTE_HSV = new float[] {0f, 0f, .9f};
    private final static float[] ESTIMATE_HSV = new float[] {185f, .22f, 1f};
    private final static float[] ISIG_HSV = new float[] {185f, .22f, 1f};

    private final static float HIGHLIGHT = 1.35f;
    private final static float LOWLIGHT = 0.65f;

    private int cNormal;
    private int cHigh;
    private int cLow;
    private int cEestimate;
    private int cIsig;
    private int cHistory;
    private int cPushover;

    private boolean largeText;

    private IconicsDrawable iWARN;
    private IconicsDrawable iINFO;
    private IconicsDrawable iNOTE;
    private IconicsDrawable iHELP;
    private IconicsDrawable iREFRESH;
    private IconicsDrawable iCGM;
    private IconicsDrawable iSETTING;
    private IconicsDrawable iHEART;
    private IconicsDrawable iSHARE;

    private int iBounds;
    private int iOffsetXDp;
    private int iOffsetYDp;

    private boolean init = false;

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

        if (!init) initDrawables(tv);

        SpannableStringBuilder ssb = new SpannableStringBuilder();

        UserLogMessage.TYPE type = UserLogMessage.TYPE.convert(ul.getType());
        String clock = FormatKit.getInstance().formatAsDayClockSeconds(ul.getTimestamp());
        String text = ul.getMessageParsed();

        if (largeText) clock += "\n";

        switch (type) {
            case WARN:
                ssb.append(" * ").append(text);
                ssb.setSpan(new ImageSpan(iWARN, DynamicDrawableSpan.ALIGN_BASELINE), 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new ForegroundColorSpan(iWARN.getColor()), 3, text.length() + 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
            case HELP:
                ssb.append(" * ").append(text);
                ssb.setSpan(new ImageSpan(iHELP, DynamicDrawableSpan.ALIGN_BASELINE), 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new ForegroundColorSpan(iHELP.getColor()), 3, text.length() + 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
            case INFO:
                ssb.append(" * ").append(text);
                ssb.setSpan(new ImageSpan(iINFO, DynamicDrawableSpan.ALIGN_BASELINE), 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new ForegroundColorSpan(iINFO.getColor()), 3, text.length() + 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
            case NOTE:
                ssb.append(" * ").append(text);
                ssb.setSpan(new ImageSpan(iNOTE, DynamicDrawableSpan.ALIGN_BASELINE), 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new ForegroundColorSpan(iNOTE.getColor()), 3, text.length() + 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
            case HISTORY:
                ssb.append(" * ").append(text);
                ssb.setSpan(new ImageSpan(iREFRESH, DynamicDrawableSpan.ALIGN_BASELINE), 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new ForegroundColorSpan(iREFRESH.getColor()), 3, text.length() + 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
            case CGM:
                ssb.append(" * ").append(text);
                ssb.setSpan(new ImageSpan(iCGM, DynamicDrawableSpan.ALIGN_BASELINE), 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new ForegroundColorSpan(iCGM.getColor()), 3, text.length() + 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
            case OPTION:
                ssb.append(" * ").append(text);
                ssb.setSpan(new ImageSpan(iSETTING, DynamicDrawableSpan.ALIGN_BASELINE), 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new ForegroundColorSpan(iSETTING.getColor()), 3, text.length() + 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
            case STARTUP:
            case SHUTDOWN:
            case HEART:
                ssb.append(" * ").append(text);
                ssb.setSpan(new ImageSpan(iHEART, DynamicDrawableSpan.ALIGN_BASELINE), 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new ForegroundColorSpan(cHigh), 3, text.length() + 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), 3, text.length() + 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
            case SGV:
                ssb.append(" ").append(text);
                ssb.setSpan(new ForegroundColorSpan(cHigh), 1, text.length() + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
            case ESTIMATE:
                ssb.append(" ").append(text);
                ssb.setSpan(new ForegroundColorSpan(cEestimate), 1, text.length() + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
            case ISIG:
                ssb.append(" ").append(text);
                ssb.setSpan(new ForegroundColorSpan(cIsig), 1, text.length() + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
            case REQUESTED:
            case RECEIVED:
                ssb.append(" ").append(text);
                ssb.setSpan(new ForegroundColorSpan(cHistory), 1, text.length() + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
            case SHARE:
                ssb.append(" * ").append(text);
                ssb.setSpan(new ImageSpan(iSHARE, DynamicDrawableSpan.ALIGN_BASELINE), 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new ForegroundColorSpan(iSHARE.getColor()), 3, text.length() + 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
            case PUSHOVER:
                ssb.append(" ").append(text);
                ssb.setSpan(new ForegroundColorSpan(cPushover), 1, text.length() + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
            default:
                ssb.append(" ").append(text);
                ssb.setSpan(new ForegroundColorSpan(cNormal), 1, text.length() + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        ssb.insert(0, clock);
        ssb.setSpan(new ForegroundColorSpan(cLow), 0, clock.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new RelativeSizeSpan(0.78f), 0, clock.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        tv.setText(ssb);
    }

    private void initDrawables(TextView tv) {

        largeText = mContext.getResources().getConfiguration().fontScale > 1;

        int c = tv.getCurrentTextColor();
        float s = tv.getTextSize();

        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);

        int normal = c >> 24 & 0xFF;
        if (normal == 0xFF) normal = Color.HSVToColor(new float[] {0.f, 0.f, hsv[2]}) & 0xFF;
        int high = (int) (HIGHLIGHT *normal);
        int low = (int) (LOWLIGHT * normal);
        if (high > 0xFF) high = 0xFF;
        if (low > 0xFF) low = 0xFF;

        cNormal = Color.HSVToColor(normal, new float[] {hsv[0], hsv[1], 1.f});
        cHigh = Color.HSVToColor(high, new float[] {hsv[0], hsv[1], 1.f});
        cLow = Color.HSVToColor(low, new float[] {hsv[0], hsv[1], 1.f});

        Log.d(TAG, String.format("textColor: %08x textSize: %s normal: %08x high: %08x low: %08x", c, s, cNormal, cHigh, cLow));

        cEestimate = Color.HSVToColor(high, ESTIMATE_HSV);
        cIsig = Color.HSVToColor(normal, ISIG_HSV);
        cHistory = Color.HSVToColor(normal, HISTORY_HSV);
        cPushover = Color.HSVToColor(normal, PUSHOVER_HSV);

        iBounds = (int) (s * 1.2);
        iOffsetXDp = 0;
        iOffsetYDp = 3;

        iWARN = icon("ion_alert_circled", Color.HSVToColor(high, WARN_HSV));
        iINFO = icon("ion_information_circled", cHigh);
        iNOTE = icon("ion_document", Color.HSVToColor(high, NOTE_HSV));
        iHELP = icon("ion_ios_lightbulb", cHigh);
        iCGM = icon("ion_ios_pulse_strong", Color.HSVToColor(high, CGM_HSV));
        iHEART = icon("ion_heart", Color.HSVToColor(high, HEART_HSV));
        iSHARE = icon("ion_android_share_alt", Color.HSVToColor(normal, SHARE_HSV));
        iREFRESH = icon("ion_refresh", cNormal);
        iSETTING = icon("ion_android_settings", cHigh);

        init = true;
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
