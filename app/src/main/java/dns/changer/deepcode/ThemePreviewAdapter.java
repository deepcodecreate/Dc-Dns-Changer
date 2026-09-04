package dns.changer.deepcode;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class ThemePreviewAdapter extends RecyclerView.Adapter<ThemePreviewAdapter.Holder> {

    public interface OnThemeChosen {
        void onChosen(ThemeManager.ThemeDef theme);
    }

    private final ThemeManager.ThemeDef[] themes;
    private final boolean english;
    private int selectedId;
    private final OnThemeChosen callback;

    public ThemePreviewAdapter(ThemeManager.ThemeDef[] themes, int selectedId, boolean english, OnThemeChosen callback) {
        this.themes = themes;
        this.selectedId = selectedId;
        this.english = english;
        this.callback = callback;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_theme_preview, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ThemeManager.ThemeDef def = themes[position];
        boolean selected = def.id == selectedId;

        holder.name.setText(english ? def.nameEn : def.nameFa);

        tintDrawable(holder.hero, def.previewAccent);
        tintDrawable(holder.powerIcon, def.previewSurface);
        holder.powerIcon.setColorFilter(def.previewAccent);
        int rowColor = lighten(def.previewSurface);
        tintDrawable(holder.row1, rowColor);
        tintDrawable(holder.row2a, rowColor);
        tintDrawable(holder.row2b, rowColor);

        View card = (View) holder.hero.getParent();
        tintDrawable(card, def.previewBg);

        holder.ring.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
        tintRingDrawable(holder.ring, def.previewAccent);
        holder.checkBadge.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
        holder.checkBadge.setBackgroundResource(R.drawable.theme_check_badge_bg);
        tintDrawable(holder.checkBadge, def.previewAccent);
        holder.checkBadge.setColorFilter(def.previewOnAccent);

        holder.itemView.setOnClickListener(v -> {
            int old = selectedId;
            selectedId = def.id;
            notifyItemChanged(indexOf(old));
            notifyItemChanged(position);
            if (callback != null) callback.onChosen(def);
        });
    }

    private int indexOf(int themeId) {
        for (int i = 0; i < themes.length; i++) if (themes[i].id == themeId) return i;
        return 0;
    }

    private int lighten(int color) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + 25);
        int g = Math.min(255, ((color >> 8) & 0xFF) + 25);
        int b = Math.min(255, (color & 0xFF) + 25);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private void tintDrawable(View view, int color) {
        if (view.getBackground() instanceof GradientDrawable) {
            ((GradientDrawable) view.getBackground().mutate()).setColor(color);
        }
    }

    private void tintRingDrawable(View view, int color) {
        if (view.getBackground() instanceof GradientDrawable) {
            ((GradientDrawable) view.getBackground().mutate()).setStroke(dp(view, 2.5f), color);
        }
    }

    private int dp(View v, float value) {
        return (int) (value * v.getResources().getDisplayMetrics().density);
    }

    @Override
    public int getItemCount() {
        return themes.length;
    }

    static class Holder extends RecyclerView.ViewHolder {
        final FrameLayout hero;
        final ImageView powerIcon;
        final View row1, row2a, row2b;
        final View ring;
        final ImageView checkBadge;
        final TextView name;

        Holder(@NonNull View itemView) {
            super(itemView);
            hero = itemView.findViewById(R.id.preview_hero);
            powerIcon = itemView.findViewById(R.id.preview_power_icon);
            row1 = itemView.findViewById(R.id.preview_row1);
            row2a = itemView.findViewById(R.id.preview_row2a);
            row2b = itemView.findViewById(R.id.preview_row2b);
            ring = itemView.findViewById(R.id.preview_selected_ring);
            checkBadge = itemView.findViewById(R.id.preview_check_badge);
            name = itemView.findViewById(R.id.preview_name);
        }
    }
}
