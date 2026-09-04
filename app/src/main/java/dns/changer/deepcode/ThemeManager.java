package dns.changer.deepcode;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.TypedValue;

public final class ThemeManager {

    public static final String PREFS = "vpn_prefs";
    public static final String KEY_THEME_ID = "app_theme_id";
    private static final String LEGACY_KEY_GRAY = "gray_theme";

    public static class ThemeDef {
        public final int id;
        public final String nameEn;
        public final String nameFa;
        public final int styleRes;
        public final int previewBg;
        public final int previewSurface;
        public final int previewAccent;
        public final int previewOnAccent;
        public final boolean light;

        ThemeDef(int id, String nameEn, String nameFa, int styleRes,
                 int previewBg, int previewSurface, int previewAccent, int previewOnAccent, boolean light) {
            this.id = id;
            this.nameEn = nameEn;
            this.nameFa = nameFa;
            this.styleRes = styleRes;
            this.previewBg = previewBg;
            this.previewSurface = previewSurface;
            this.previewAccent = previewAccent;
            this.previewOnAccent = previewOnAccent;
            this.light = light;
        }
    }

    private static final ThemeDef[] THEMES = new ThemeDef[] {
        new ThemeDef(0, "Ember", "اخگر", R.style.AppTheme_Ember,
                0xFF2B100E, 0xFF1B100E, 0xFFFFA88B, 0xFF2B100E, false),
        new ThemeDef(1, "Slate", "سنگی", R.style.AppTheme_Slate,
                0xFF212121, 0xFF303030, 0xFF9E9E9E, 0xFF121212, false),
        new ThemeDef(2, "Twilight", "شفق", R.style.AppTheme_Twilight,
                0xFF0D1B2A, 0xFF13293D, 0xFF00E5C7, 0xFF06222E, false),
        new ThemeDef(3, "Midnight", "نیمه‌شب", R.style.AppTheme_Midnight,
                0xFF05070D, 0xFF10131C, 0xFF7C8CFF, 0xFF0A0C14, false),
        new ThemeDef(4, "Sand", "شنی", R.style.AppTheme_Sand,
                0xFFF4EEE1, 0xFFEFE6D2, 0xFF6B8F71, 0xFFF4EEE1, true),
        new ThemeDef(5, "Sky", "آسمانی", R.style.AppTheme_Sky,
                0xFFE9F3F7, 0xFFDCEBF1, 0xFF3E8FA6, 0xFFFFFFFF, true),
        new ThemeDef(6, "Autumn", "پاییزی", R.style.AppTheme_Autumn,
                0xFFFBF7F0, 0xFFF3E9D8, 0xFFDD6B20, 0xFFFFFFFF, true),
        new ThemeDef(7, "Neon", "نئون", R.style.AppTheme_Neon,
                0xFF0A0A0A, 0xFF161616, 0xFF39FF14, 0xFF0A0A0A, false),
        new ThemeDef(8, "Ocean", "اقیانوس", R.style.AppTheme_Ocean,
                0xFF0A192F, 0xFF112240, 0xFF64FFDA, 0xFF0A192F, false),
        new ThemeDef(9, "Rose", "رز", R.style.AppTheme_Rose,
                0xFF1A0A12, 0xFF2A1520, 0xFFFF8FAB, 0xFF1A0A12, false),
        new ThemeDef(10, "Forest", "جنگل", R.style.AppTheme_Forest,
                0xFF0D1F17, 0xFF163028, 0xFFA8E6CF, 0xFF0D1F17, false),
        new ThemeDef(11, "Amber", "کهربا", R.style.AppTheme_Amber,
                0xFF1A1408, 0xFF2A2210, 0xFFFFD54F, 0xFF1A1408, false),
        new ThemeDef(12, "Violet", "بنفش", R.style.AppTheme_Violet,
                0xFF120A1A, 0xFF1E1230, 0xFFCE93D8, 0xFF120A1A, false),
        new ThemeDef(13, "Crimson", "قرمز", R.style.AppTheme_Crimson,
                0xFF1A0808, 0xFF2A1010, 0xFFFF6B6B, 0xFF1A0808, false),
    };

    private ThemeManager() {}

    public static ThemeDef[] all() {
        return THEMES;
    }

    public static ThemeDef byId(int id) {
        for (ThemeDef t : THEMES) if (t.id == id) return t;
        return THEMES[0];
    }

    public static int getSelectedId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.contains(KEY_THEME_ID)) {
            return prefs.getInt(KEY_THEME_ID, 0);
        }
        boolean wasGray = prefs.getBoolean(LEGACY_KEY_GRAY, false);
        int migrated = wasGray ? 1 : 0;
        prefs.edit().putInt(KEY_THEME_ID, migrated).apply();
        return migrated;
    }

    public static void setSelectedId(Context context, int id) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_THEME_ID, id).apply();
    }

    public static int getSelectedStyleRes(Context context) {
        return byId(getSelectedId(context)).styleRes;
    }

    public static boolean isLight(Context context) {
        return byId(getSelectedId(context)).light;
    }

    public static int getDefaultTextColor(Context context) {
        if (isLight(context)) {
            return 0xFF1C1B1F;
        }
        return 0xFFEEEEEE;
    }

    public static int resolveColor(Context context, int attr) {
        TypedValue tv = new TypedValue();
        context.getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    public static void applyTheme(android.app.Activity activity) {
        activity.setTheme(getSelectedStyleRes(activity));
    }
}
