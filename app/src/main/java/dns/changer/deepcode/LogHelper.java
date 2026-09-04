package dns.changer.deepcode;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class LogHelper {

    public interface LogUpdateListener {
        void onLogUpdate(String newLog);
        void onLogsCleared();
    }

    private static final String PREFS = "vpn_prefs";
    private static final String LIMIT_KEY = "log_limit";
    private static final String LOG_FILE_NAME = "dns_log.txt";
    private static final int DEFAULT_LIMIT = 1000;
    private static final int HARD_CAP = 10000;

    private static final int TRIM_EVERY = 200;

    private static final ArrayDeque<String> buffer = new ArrayDeque<>();
    private static final Object lock = new Object();
    private static volatile LogUpdateListener listener;
    private static volatile int limit = DEFAULT_LIMIT;
    private static volatile boolean loaded = false;
    private static final AtomicInteger appendsSinceTrim = new AtomicInteger(0);
    private static final ExecutorService io = Executors.newSingleThreadExecutor();

    public static void init(Context context) {
        ensureLoaded(context);
    }

    public static void log(Context context, String message) {
        ensureLoaded(context);
        final String line = "[" + getCurrentTime() + "] " + message;

        synchronized (lock) {
            buffer.addLast(line);
            if (buffer.size() > limit) {
                buffer.pollFirst();
            }
        }

        final Context appContext = context.getApplicationContext();
        io.execute(() -> appendToFile(appContext, line));

        if (listener != null) listener.onLogUpdate(line);
    }

    
    public static List<String> getLogLines(Context context) {
        ensureLoaded(context);
        synchronized (lock) {
            return new ArrayList<>(buffer);
        }
    }

    
    public static String getLogs(Context context) {
        List<String> lines = getLogLines(context);
        return String.join("\n", lines);
    }

    public static void clearLogs(Context context) {
        synchronized (lock) {
            buffer.clear();
        }
        final Context appContext = context.getApplicationContext();
        io.execute(() -> {
            File f = logFile(appContext);
            if (f.exists()) f.delete();
        });
        if (listener != null) listener.onLogsCleared();
    }

    public static void setLogLimit(Context context, int newLimit) {
        int clamped = Math.max(1, Math.min(HARD_CAP, newLimit));
        limit = clamped;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(LIMIT_KEY, clamped).apply();
        synchronized (lock) {
            while (buffer.size() > clamped) {
                buffer.pollFirst();
            }
        }
    }

    public static int getLogLimit(Context context) {
        ensureLoaded(context);
        return limit;
    }

    public static void setLogUpdateListener(LogUpdateListener l) {
        listener = l;
    }

    public static void clearListener() {
        listener = null;
    }

    private static void ensureLoaded(Context context) {
        if (loaded) return;
        synchronized (lock) {
            if (loaded) return;
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            limit = Math.max(1, Math.min(HARD_CAP, prefs.getInt(LIMIT_KEY, DEFAULT_LIMIT)));
            loadFromFile(context.getApplicationContext());
            loaded = true;
        }
    }

    private static File logFile(Context appContext) {
        return new File(appContext.getFilesDir(), LOG_FILE_NAME);
    }

    private static void loadFromFile(Context appContext) {
        File f = logFile(appContext);
        if (!f.exists()) return;
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            String line;
            ArrayDeque<String> loadedLines = new ArrayDeque<>();
            while ((line = raf.readLine()) != null) {

                byte[] bytes = line.getBytes(StandardCharsets.ISO_8859_1);
                String decoded = new String(bytes, StandardCharsets.UTF_8);
                loadedLines.addLast(decoded);
                if (loadedLines.size() > limit) {
                    loadedLines.pollFirst();
                }
            }
            buffer.clear();
            buffer.addAll(loadedLines);
        } catch (IOException ignored) {
        }
    }

    private static void appendToFile(Context appContext, String line) {
        try {
            File f = logFile(appContext);
            try (BufferedWriter w = new BufferedWriter(new FileWriter(f, true))) {
                w.write(line);
                w.newLine();
            }
            if (appendsSinceTrim.incrementAndGet() >= TRIM_EVERY) {
                appendsSinceTrim.set(0);
                trimFile(appContext);
            }
        } catch (IOException ignored) {
        }
    }

    
    private static void trimFile(Context appContext) {
        List<String> snapshot;
        synchronized (lock) {
            snapshot = new ArrayList<>(buffer);
        }
        File f = logFile(appContext);
        File tmp = new File(appContext.getFilesDir(), LOG_FILE_NAME + ".tmp");
        try (BufferedWriter w = new BufferedWriter(new FileWriter(tmp, false))) {
            for (String line : snapshot) {
                w.write(line);
                w.newLine();
            }
        } catch (IOException e) {
            return;
        }
        tmp.renameTo(f);
    }

    private static String getCurrentTime() {
        return new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
    }
}
