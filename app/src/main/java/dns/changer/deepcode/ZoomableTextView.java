package dns.changer.deepcode;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import androidx.appcompat.widget.AppCompatTextView;

public class ZoomableTextView extends AppCompatTextView {

    private float scaleFactor = 1.0f;
    private ScaleGestureDetector scaleDetector;
    private static final String PREF_NAME = "zoom_prefs";
    private static final String ZOOM_KEY = "log_text_zoom";

    public ZoomableTextView(Context context) {
        super(context);
        init(context);
    }

    public ZoomableTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ZoomableTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        scaleFactor = prefs.getFloat(ZOOM_KEY, 0.5f);
        setTextSize(14 * scaleFactor);

        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());

        setTextIsSelectable(true);
        setLineSpacing(0, 1.1f);
        setHorizontallyScrolling(false);
        setMaxLines(Integer.MAX_VALUE);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);

        if (scaleDetector.isInProgress()) {
            return true;
        }

        return super.onTouchEvent(event);
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            scaleFactor *= detector.getScaleFactor();
            scaleFactor = Math.max(0.5f, Math.min(scaleFactor, 3.0f));
            setTextSize(14 * scaleFactor);

            SharedPreferences prefs = getContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit().putFloat(ZOOM_KEY, scaleFactor).apply();

            return true;
        }
    }
}