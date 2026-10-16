package dns.changer.deepcode;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * ExpressVPN-style connect control background with theme-aware dual curved arcs
 * and animated state rings framing the central power button.
 */
public class ExpressPowerArcView extends View {

    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;

    private int currentState = STATE_DISCONNECTED;

    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outerRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pulsePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF arcRect = new RectF();
    private final RectF outerRect = new RectF();

    private int accentColor = 0xFF7C8CFF;
    private int inactiveColor = 0x33BDBDBD;

    private float rotationAngle = 0f;
    private float pulseRadiusRatio = 0f;
    private float idleAlphaRatio = 1.0f;

    private ValueAnimator rotateAnimator;
    private ValueAnimator pulseAnimator;
    private ValueAnimator idleAnimator;

    public ExpressPowerArcView(Context context) {
        super(context);
        init();
    }

    public ExpressPowerArcView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ExpressPowerArcView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);

        outerRingPaint.setStyle(Paint.Style.STROKE);
        outerRingPaint.setStrokeWidth(dpToPx(1.5f));

        pulsePaint.setStyle(Paint.Style.STROKE);
        pulsePaint.setStrokeWidth(dpToPx(2.5f));

        updatePaintColors();
        startIdleAnimators();
    }

    public void setThemeColors(int accent, int inactive) {
        this.accentColor = accent;
        this.inactiveColor = inactive;
        updatePaintColors();
        invalidate();
    }

    private void updatePaintColors() {
        int alphaAccent = Color.argb(160, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor));
        if (currentState == STATE_CONNECTED) {
            arcPaint.setColor(accentColor);
            outerRingPaint.setColor(alphaAccent);
            pulsePaint.setColor(Color.argb(110, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)));
        } else if (currentState == STATE_CONNECTING) {
            arcPaint.setColor(accentColor);
            outerRingPaint.setColor(alphaAccent);
            pulsePaint.setColor(Color.TRANSPARENT);
        } else { // DISCONNECTED
            arcPaint.setColor(inactiveColor);
            outerRingPaint.setColor(Color.argb(25, 255, 255, 255));
            pulsePaint.setColor(Color.TRANSPARENT);
        }
    }

    public void setState(int state) {
        if (this.currentState == state) return;
        this.currentState = state;
        updatePaintColors();
        stopAllAnimators();

        if (state == STATE_CONNECTING) {
            startConnectingAnimators();
        } else if (state == STATE_CONNECTED) {
            startConnectedAnimators();
        } else {
            startIdleAnimators();
        }
        invalidate();
    }

    private void startIdleAnimators() {
        idleAnimator = ValueAnimator.ofFloat(0.4f, 1.0f, 0.4f);
        idleAnimator.setDuration(2800);
        idleAnimator.setRepeatCount(ValueAnimator.INFINITE);
        idleAnimator.addUpdateListener(animation -> {
            idleAlphaRatio = (float) animation.getAnimatedValue();
            invalidate();
        });
        idleAnimator.start();
    }

    private void startConnectingAnimators() {
        rotateAnimator = ValueAnimator.ofFloat(0f, 360f);
        rotateAnimator.setDuration(1100);
        rotateAnimator.setInterpolator(new LinearInterpolator());
        rotateAnimator.setRepeatCount(ValueAnimator.INFINITE);
        rotateAnimator.addUpdateListener(animation -> {
            rotationAngle = (float) animation.getAnimatedValue();
            invalidate();
        });
        rotateAnimator.start();
    }

    private void startConnectedAnimators() {
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f);
        pulseAnimator.setDuration(1600);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.addUpdateListener(animation -> {
            pulseRadiusRatio = (float) animation.getAnimatedValue();
            invalidate();
        });
        pulseAnimator.start();
    }

    private void stopAllAnimators() {
        if (rotateAnimator != null) {
            rotateAnimator.cancel();
            rotateAnimator = null;
        }
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
        }
        if (idleAnimator != null) {
            idleAnimator.cancel();
            idleAnimator = null;
        }
        rotationAngle = 0f;
        pulseRadiusRatio = 0f;
        idleAlphaRatio = 1.0f;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) return;

        float centerX = width / 2f;
        float centerY = height / 2f;

        float arcStroke = dpToPx(6.5f);
        arcPaint.setStrokeWidth(arcStroke);

        // Keep the original 72dp button/arc proportions while the larger view
        // provides enough room for the expanding pulse ring at its widest point.
        float buttonRadius = dpToPx(36f);
        float arcRadius = buttonRadius + dpToPx(8f); // Distance for the ExpressVPN curved arcs
        float outerRingRadius = arcRadius + dpToPx(9.5f);

        arcRect.set(centerX - arcRadius, centerY - arcRadius, centerX + arcRadius, centerY + arcRadius);
        outerRect.set(centerX - outerRingRadius, centerY - outerRingRadius, centerX + outerRingRadius, centerY + outerRingRadius);

        // 1. Draw outer subtle ring
        canvas.drawCircle(centerX, centerY, outerRingRadius, outerRingPaint);

        // 2. Connected state: radiating pulse wave outwards
        if (currentState == STATE_CONNECTED && pulseRadiusRatio > 0f) {
            float pulseRadius = arcRadius + (outerRingRadius - arcRadius + dpToPx(14f)) * pulseRadiusRatio;
            int pulseAlpha = (int) (130 * (1.0f - pulseRadiusRatio));
            pulsePaint.setColor(Color.argb(pulseAlpha, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)));
            canvas.drawCircle(centerX, centerY, pulseRadius, pulsePaint);
        }

        // 3. Draw ExpressVPN-style Dual Sweeping Curved Arcs (Opposing sweeping arcs)
        canvas.save();
        canvas.rotate(rotationAngle, centerX, centerY);

        float sweepAngle = 105f; // Wide thick arcs
        float gapAngle = 75f;

        if (currentState == STATE_DISCONNECTED) {
            arcPaint.setAlpha((int) (255 * idleAlphaRatio * 0.55f));
        } else {
            arcPaint.setAlpha(255);
        }

        // First Arc
        canvas.drawArc(arcRect, 0f + gapAngle / 2f, sweepAngle, false, arcPaint);
        // Second Arc
        canvas.drawArc(arcRect, 180f + gapAngle / 2f, sweepAngle, false, arcPaint);

        canvas.restore();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAllAnimators();
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}
