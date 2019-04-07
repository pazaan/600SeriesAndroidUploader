package info.nightscout.android.medtronic;

import android.content.Context;
import android.util.AttributeSet;

import com.jjoe64.graphview.GraphView;

public class ChartSgv extends GraphView {

    public ChartSgv(Context context) {
        super(context);
        init();
    }

    public ChartSgv(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ChartSgv(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    @Override
    public boolean performClick() {
        // Calls the super implementation, which generates an AccessibilityEvent
        // and calls the onClick() listener on the view, if any
        super.performClick();

        // Handle the action for the custom click here

        return true;
    }

}
