package dns.changer.deepcode;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import androidx.appcompat.app.AppCompatActivity;

public class DnsLogActivity extends AppCompatActivity {

    private ZoomableTextView logTextView;
    private ScrollView scrollView;
    private int logLimit = 1000;
    private SharedPreferences prefs;
    private Button clearButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        prefs = getSharedPreferences("vpn_prefs", MODE_PRIVATE);
        boolean isGrayTheme = prefs.getBoolean("gray_theme", false);
        setTheme(isGrayTheme ? R.style.AppTheme_GrayMaterial : R.style.AppTheme);
        
        setContentView(R.layout.activity_dns_log);
        initializeViews();
        
        logLimit = prefs.getInt("log_limit", 1000);
        displayExistingLogs();
        setupLogUpdateListener();
        configureScrollView();
        setupClearButton();
    }

    private void initializeViews() {
        logTextView = findViewById(R.id.log_text_view);
        scrollView = findViewById(R.id.log_scroll_view);
        clearButton = findViewById(R.id.clear_log_button);
    }

    private void displayExistingLogs() {
        String logs = LogHelper.getLogs(this);
        logTextView.setText(Utils.colorizeWords(logs));
        scrollToBottom();
    }

    private void setupLogUpdateListener() {
        LogHelper.setLogUpdateListener(new LogHelper.LogUpdateListener() {
            @Override
            public void onLogUpdate(String newLog) {
                updateLogText(newLog);
            }

            @Override
            public void onLogsCleared() {
                showLogClearedMessage();
            }
        });
    }

    private void updateLogText(String newLog) {
        runOnUiThread(() -> {
            SpannableStringBuilder currentText = new SpannableStringBuilder(logTextView.getText());
            
            if (currentText.length() > 0) {
                currentText.append("\n");
            }
            currentText.append(Utils.colorizeWords(newLog));
            logTextView.setText(currentText);
            
            scrollToBottom();
        });
    }

    private void showLogClearedMessage() {
        runOnUiThread(() -> {
            logTextView.setText(Utils.colorizeWords("← Logs Deleted " + logLimit + " Lines"));
            scrollToBottom();
        });
    }

    private void configureScrollView() {
        scrollView.setHorizontalScrollBarEnabled(false);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOverScrollMode(ScrollView.OVER_SCROLL_NEVER);
    }

    private void setupClearButton() {
        clearButton.setOnClickListener(v -> {
            LogHelper.clearLogs(this);
            logTextView.setText("");
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupLogUpdateListener();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LogHelper.clearListener();
    }

    private void scrollToBottom() {
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }
}