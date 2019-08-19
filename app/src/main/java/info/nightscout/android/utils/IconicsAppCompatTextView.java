package info.nightscout.android.utils;

import android.content.Context;
import android.util.AttributeSet;

import com.mikepenz.iconics.Iconics;

public class IconicsAppCompatTextView extends android.support.v7.widget.AppCompatTextView {

    public IconicsAppCompatTextView(Context context) {
        super(context);
    }

    public IconicsAppCompatTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public IconicsAppCompatTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        if (!isInEditMode()) {
            super.setText(new Iconics.IconicsBuilder().ctx(getContext()).on(text).build(), type);
        } else {
            super.setText(text, type);
        }
    }

}
