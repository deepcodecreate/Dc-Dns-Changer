package dns.changer.deepcode;

import android.content.Context;
import android.content.SharedPreferences;

public class LogHelper {

    public interface LogUpdateListener {
        void onLogUpdate(String newLog);
        void onLogsCleared();
    }

    private static LogUpdateListener listener;
    private static final String LOG_KEY = "dns_log";
    private static final String LIMIT_KEY = "log_limit";

    public static void log(Context context, String message) {
        SharedPreferences prefs = context.getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE);
        int logLimit = prefs.getInt(LIMIT_KEY, 1000);

        String oldLogs = prefs.getString(LOG_KEY, "").trim();
        String[] lines = oldLogs.isEmpty() ? new String[0] : oldLogs.split("\n");

        if (lines.length >= logLimit) {
            prefs.edit().remove(LOG_KEY).apply();
            oldLogs = "";
            if (listener != null) listener.onLogsCleared();
        }

        String newLog = "[" + getCurrentTime() + "] " + message;
        String updatedLogs = (oldLogs.isEmpty() ? newLog : oldLogs + "\n" + newLog);

        prefs.edit().putString(LOG_KEY, updatedLogs).apply();

        if (listener != null) listener.onLogUpdate(newLog);
    }

    public static String getLogs(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE);
        return prefs.getString(LOG_KEY, "");
    }

    public static void clearLogs(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE);
        prefs.edit().remove(LOG_KEY).apply();
        if (listener != null) listener.onLogsCleared();
    }

    public static void setLogUpdateListener(LogUpdateListener l) {
        listener = l;
    }

    public static void clearListener() {
        listener = null;
    }

    private static String getCurrentTime() {
        return new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
    }
}