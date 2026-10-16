package dns.changer.deepcode;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.core.widget.ImageViewCompat;
import android.content.res.ColorStateList;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.List;

public class LogFragment extends Fragment {

    private RecyclerView recyclerView;
    private LogLineAdapter adapter;
    private LinearLayoutManager layoutManager;
    private TextView countLabel;
    private MaterialButton copyButton;
    private MaterialButton clearButton;
    private View jumpBottomButton;
    private TextView jumpLabel;
    private boolean stickToBottom = true;
    private boolean jumpShown = false;
    private int pendingNew = 0;

    private final LogHelper.LogUpdateListener logListener = new LogHelper.LogUpdateListener() {
        @Override
        public void onLogUpdate(String newLog) {
            if (getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
                if (adapter == null) return;
                adapter.appendLine(newLog);
                updateCountLabel();
                if (stickToBottom) {
                    scrollToBottom();
                } else {
                    pendingNew++;
                    updateJumpLabel();
                }
            });
        }

        @Override
        public void onLogsCleared() {
            if (getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
                if (adapter == null) return;
                adapter.clearAll();
                pendingNew = 0;
                updateCountLabel();
                setJumpVisible(false);
            });
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_log, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializeViews(view);

        int logLimit = LogHelper.getLogLimit(requireContext());
        adapter = new LogLineAdapter();
        adapter.setCap(logLimit);
        layoutManager = new LinearLayoutManager(requireContext());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutAnimation(AnimationUtils
                .loadLayoutAnimation(requireContext(), R.anim.layout_animation_fall_in));
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(24);

        List<String> initial = LogHelper.getLogLines(requireContext());
        adapter.setInitialLines(initial);
        recyclerView.scheduleLayoutAnimation();
        updateCountLabel();
        updateButtonLabels();
        scrollToBottom();

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                int last = layoutManager.findLastVisibleItemPosition();
                int total = adapter.getItemCount();
                stickToBottom = total == 0 || last >= total - 2;
                if (stickToBottom) pendingNew = 0;
                updateJumpLabel();
                setJumpVisible(!stickToBottom);
            }
        });

        styleJumpButton(view);
        jumpBottomButton.setOnClickListener(v -> {
            stickToBottom = true;
            pendingNew = 0;
            updateJumpLabel();
            setJumpVisible(false);
            jumpToLatest();
        });

        if (copyButton != null) {
            copyButton.setOnClickListener(v -> {
                if (adapter == null) return;
                String logs = adapter.getAllLogsText();
                if (logs.isEmpty()) {
                    Toast.makeText(requireContext(), isEn() ? "Log is empty" : "لاگ خالی است", Toast.LENGTH_SHORT).show();
                    return;
                }
                ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("DNS Logs", logs));
                    Toast.makeText(requireContext(), isEn() ? "All logs copied to clipboard" : "تمام لاگ‌ها کپی شدند", Toast.LENGTH_SHORT).show();
                }
            });
        }

        clearButton.setOnClickListener(v -> {
            LogHelper.clearLogs(requireContext());
            adapter.clearAll();
            updateCountLabel();
        });
    }

    private void initializeViews(View view) {
        recyclerView = view.findViewById(R.id.log_recycler_view);
        copyButton = view.findViewById(R.id.copy_log_button);
        clearButton = view.findViewById(R.id.clear_log_button);
        jumpBottomButton = view.findViewById(R.id.jump_bottom_button);
        jumpLabel = view.findViewById(R.id.jump_bottom_label);
        countLabel = view.findViewById(R.id.log_count_label);
    }

    /** Paints the pill with the active theme: accent capsule, logo in a contrasting badge. */
    private void styleJumpButton(View root) {
        ThemeManager.ThemeDef def = ThemeManager.byId(ThemeManager.getSelectedId(requireContext()));
        float d = getResources().getDisplayMetrics().density;

        GradientDrawable pill = new GradientDrawable();
        pill.setColor(def.previewAccent);
        pill.setCornerRadius(40 * d);
        jumpBottomButton.setBackground(pill);

        View badge = root.findViewById(R.id.jump_logo_badge);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(def.previewOnAccent);
        badge.setBackground(circle);

        ImageView logo = root.findViewById(R.id.jump_logo);
        ImageViewCompat.setImageTintList(logo, ColorStateList.valueOf(def.previewAccent));

        ImageView arrow = root.findViewById(R.id.jump_arrow);
        ImageViewCompat.setImageTintList(arrow, ColorStateList.valueOf(def.previewOnAccent));
        jumpLabel.setTextColor(def.previewOnAccent);

        jumpBottomButton.setContentDescription(isEn() ? "Jump to latest log" : "رفتن به آخرین لاگ");
        updateJumpLabel();
    }

    private void updateJumpLabel() {
        if (jumpLabel == null) return;
        String base = isEn() ? "Latest" : "آخرین لاگ";
        jumpLabel.setText(pendingNew > 0 ? base + " · " + (pendingNew > 99 ? "99+" : pendingNew) : base);
    }

    private void setJumpVisible(boolean show) {
        if (jumpBottomButton == null || show == jumpShown) return;
        jumpShown = show;
        float d = getResources().getDisplayMetrics().density;
        jumpBottomButton.animate().cancel();
        if (show) {
            jumpBottomButton.setVisibility(View.VISIBLE);
            jumpBottomButton.setAlpha(0f);
            jumpBottomButton.setTranslationY(16 * d);
            jumpBottomButton.setScaleX(0.9f);
            jumpBottomButton.setScaleY(0.9f);
            jumpBottomButton.animate()
                    .alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                    .setDuration(260)
                    .setInterpolator(new OvershootInterpolator(1.4f))
                    .start();
        } else {
            jumpBottomButton.animate()
                    .alpha(0f).translationY(16 * d).scaleX(0.9f).scaleY(0.9f)
                    .setDuration(160)
                    .withEndAction(() -> {
                        if (!jumpShown && jumpBottomButton != null) {
                            jumpBottomButton.setVisibility(View.GONE);
                        }
                    })
                    .start();
        }
    }

    private void jumpToLatest() {
        if (adapter == null) return;
        int count = adapter.getItemCount();
        if (count == 0) return;
        int last = layoutManager.findLastVisibleItemPosition();
        if (count - last > 60) {
            recyclerView.scrollToPosition(count - 1);
        } else {
            recyclerView.smoothScrollToPosition(count - 1);
        }
    }

    private void updateButtonLabels() {
        boolean isEn = isEn();
        if (copyButton != null) {
            copyButton.setText(isEn ? "Copy Logs" : "کپی لاگ‌ها");
        }
        if (clearButton != null) {
            clearButton.setText(isEn ? "Clear Log" : "پاک‌سازی");
        }
    }

    private void updateCountLabel() {
        if (adapter == null || countLabel == null) return;
        int count = adapter.getLineCount();
        int cap = LogHelper.getLogLimit(requireContext());
        countLabel.setText(count + " / " + cap + (isEn() ? " lines" : " خط"));
    }

    private boolean isEn() {
        return getResources().getConfiguration().locale.getLanguage().equals("en");
    }

    private void scrollToBottom() {
        recyclerView.post(() -> {
            if (adapter == null) return;
            int count = adapter.getItemCount();
            if (count > 0) recyclerView.scrollToPosition(count - 1);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isHidden()) LogHelper.setLogUpdateListener(logListener);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) {
            LogHelper.clearListener();
        } else {
            LogHelper.setLogUpdateListener(logListener);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        LogHelper.clearListener();
    }
}
