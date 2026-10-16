package dns.changer.deepcode;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Floating "capsule" tab bar in the style of the new Telegram navigation:
 * a rounded pill that hovers above the screen bottom, with a soft capsule
 * that slides behind the selected tab.
 *
 * Colors are pushed in from code (setColors) instead of theme-attribute
 * selectors, which keeps it safe on MIUI builds.
 */
public class FloatingNavBar extends FrameLayout {

    public interface OnItemSelectedListener {
        boolean onItemSelected(int itemId);
    }

    private static final class Item {
        int id;
        LinearLayout view;
        ImageView icon;
        TextView label;
        int color;
        ValueAnimator anim;
    }

    private static final int ITEM_HEIGHT_DP = 54;
    private static final int INDICATOR_INSET_DP = 4;

    private final List<Item> items = new ArrayList<>();
    private LinearLayout row;
    private View indicator;
    private Typeface font;

    private int selectedId = View.NO_ID;
    private int barColor = 0xFF202020;
    private int strokeColor = 0x33FFFFFF;
    private int indicatorColor = 0x33FFFFFF;
    private int accent = 0xFFFFFFFF;
    private int normal = 0xFF9E9E9E;

    private OnItemSelectedListener listener;

    public FloatingNavBar(Context context) {
        super(context);
        init(context);
    }

    public FloatingNavBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public FloatingNavBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        try {
            font = ResourcesCompat.getFont(context, R.font.appfont);
        } catch (Exception ignored) {
            font = null;
        }

        setClipToPadding(false);
        setClipChildren(false);
        int pad = dp(5);
        setPadding(pad, pad, pad, pad);
        setElevation(dp(10));

        indicator = new View(context);
        indicator.setVisibility(INVISIBLE);
        addView(indicator, new LayoutParams(0, dp(ITEM_HEIGHT_DP), Gravity.LEFT | Gravity.CENTER_VERTICAL));

        row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        addView(row, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ITEM_HEIGHT_DP)));
        row.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orr, ob) -> updateIndicator(false));

        applyColors();
    }

    public void setOnItemSelectedListener(OnItemSelectedListener l) {
        this.listener = l;
    }

    /**
     * @param bg      the theme's header/surface color
     * @param accentC the theme accent color
     * @param light   true for light themes
     */
    public void setColors(int bg, int accentC, boolean light) {
        accent = accentC;
        barColor = light ? bg : blend(bg, accentC, 0.07f);
        normal = blend(accentC, barColor, light ? 0.35f : 0.5f);
        strokeColor = withAlpha(accentC, light ? 0x2E : 0x33);
        indicatorColor = withAlpha(accentC, light ? 0x30 : 0x2B);
        applyColors();
    }

    public void addItem(int id, int iconRes, CharSequence title) {
        Context ctx = getContext();
        Item it = new Item();
        it.id = id;

        LinearLayout v = new LinearLayout(ctx);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.CENTER);
        v.setClickable(true);
        v.setFocusable(true);
        v.setContentDescription(title);
        v.setPadding(dp(2), 0, dp(2), 0);

        ImageView icon = new ImageView(ctx);
        icon.setImageResource(iconRes);
        icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        v.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));

        TextView label = new TextView(ctx);
        label.setText(title);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        label.setSingleLine(true);
        label.setGravity(Gravity.CENTER);
        label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (font != null) label.setTypeface(font);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(3);
        v.addView(label, lp);

        it.view = v;
        it.icon = icon;
        it.label = label;
        it.color = normal;
        row.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        items.add(it);

        v.setOnClickListener(view -> {
            bounce(it);
            if (it.id != selectedId) setSelectedItemId(it.id);
        });

        if (selectedId == View.NO_ID) {
            selectedId = id;
        }
        tintItem(it, it.id == selectedId, false);
    }

    /** Like BottomNavigationView: notifies the listener, then selects when it accepts. */
    public void setSelectedItemId(int id) {
        if (id == selectedId) return;
        if (find(id) == null) return;
        if (listener != null && !listener.onItemSelected(id)) return;
        select(id, true);
    }

    public int getSelectedItemId() {
        return selectedId;
    }

    private void select(int id, boolean animate) {
        Item old = find(selectedId);
        Item now = find(id);
        selectedId = id;
        if (old != null) tintItem(old, false, animate);
        if (now != null) tintItem(now, true, animate);
        updateIndicator(animate);
    }

    private void updateIndicator(boolean animate) {
        Item it = find(selectedId);
        if (it == null || it.view.getWidth() == 0) return;
        int inset = dp(INDICATOR_INSET_DP);
        int w = it.view.getWidth() - inset * 2;
        int h = it.view.getHeight();
        LayoutParams lp = (LayoutParams) indicator.getLayoutParams();
        if (lp.width != w || lp.height != h) {
            lp.width = w;
            lp.height = h;
            indicator.setLayoutParams(lp);
        }
        float tx = it.view.getLeft() + inset;
        indicator.animate().cancel();
        if (animate && indicator.getVisibility() == VISIBLE) {
            indicator.animate()
                    .translationX(tx)
                    .setDuration(320)
                    .setInterpolator(new OvershootInterpolator(0.85f))
                    .start();
        } else {
            indicator.setTranslationX(tx);
            indicator.setVisibility(VISIBLE);
        }
    }

    private void bounce(Item it) {
        it.icon.animate().cancel();
        it.icon.setScaleX(0.8f);
        it.icon.setScaleY(0.8f);
        it.icon.animate()
                .scaleX(1f).scaleY(1f)
                .setDuration(280)
                .setInterpolator(new OvershootInterpolator(2.4f))
                .start();
    }

    private void tintItem(Item it, boolean selected, boolean animate) {
        int to = selected ? accent : normal;
        if (it.anim != null) it.anim.cancel();
        if (!animate || it.color == to) {
            setItemColor(it, to);
            return;
        }
        ValueAnimator va = ValueAnimator.ofObject(new ArgbEvaluator(), it.color, to);
        va.setDuration(220);
        va.addUpdateListener(a -> setItemColor(it, (Integer) a.getAnimatedValue()));
        it.anim = va;
        va.start();
    }

    private void setItemColor(Item it, int c) {
        it.color = c;
        it.icon.setImageTintList(ColorStateList.valueOf(c));
        it.label.setTextColor(c);
    }

    private void applyColors() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(barColor);
        bg.setCornerRadius(dp(40));
        bg.setStroke(dp(1), strokeColor);
        setBackground(bg);

        GradientDrawable ind = new GradientDrawable();
        ind.setColor(indicatorColor);
        ind.setCornerRadius(dp(40));
        indicator.setBackground(ind);

        for (Item it : items) {
            tintItem(it, it.id == selectedId, false);
        }
    }

    private Item find(int id) {
        for (Item it : items) if (it.id == id) return it;
        return null;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    /** t = 0 -> a, t = 1 -> b (opaque result). */
    private static int blend(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        for (Item it : items) {
            if (it.anim != null) it.anim.cancel();
        }
        indicator.animate().cancel();
    }
}
