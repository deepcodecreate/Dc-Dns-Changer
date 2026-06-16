package dns.changer.deepcode;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import java.util.Locale;

public class LocaleHelper {
    private static final String SELECTED_LANGUAGE = "Locale.Helper.Selected.Language";

    public static Context setLocale(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE);
        boolean isEnglish = prefs.getBoolean("english_language", false);
        String lang = isEnglish ? "en" : "fa";
        return updateResources(context, lang);
    }

    private static Context updateResources(Context context, String language) {
        Locale locale = new Locale(language);
        Locale.setDefault(locale);

        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLocale(locale);
            context = context.createConfigurationContext(config);
        } else {
            config.locale = locale;
            res.updateConfiguration(config, res.getDisplayMetrics());
        }
        
        return context;
    }

    public static void applyLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE);
        boolean isEnglish = prefs.getBoolean("english_language", false);
        String lang = isEnglish ? "en" : "fa";
        updateResources(context, lang);
    }
}