package dns.changer.deepcode;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.List;

public class DnsLogActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private LogLineAdapter adapter;
    private LinearLayoutManager layoutManager;
    private TextView countLabel;
    private MaterialButton clearButton;
    private MaterialButton jumpBottomButton;
    private SharedPreferences prefs;
    private boolean stickToBottom = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("vpn_prefs", MODE_PRIVATE);
        ThemeManager.applyTheme(this);

        setContentView(R.layout.activity_dns_log);
        initializeViews();

        int logLimit = LogHelper.getLogLimit(this);
        adapter = new LogLineAdapter();
        adapter.setCap(logLimit);
        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(24);

        List<String> initial = LogHelper.getLogLines(this);
        adapter.setInitialLines(initial);
        updateCountLabel();
        scrollToBottom();

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView rv, int dx, int dy) {
                int last = layoutManager.findLastVisibleItemPosition();
                int total = adapter.getItemCount();
                stickToBottom = total == 0 || last >= total - 2;
                jumpBottomButton.setAlpha(stickToBottom ? 0.4f : 1f);
            }
        });

        jumpBottomButton.setOnClickListener(v -> {
            stickToBottom = true;
            scrollToBottom();
        });

        clearButton.setOnClickListener(v -> {
            LogHelper.clearLogs(this);
            adapter.clearAll();
            updateCountLabel();
        });

        LogHelper.setLogUpdateListener(new LogHelper.LogUpdateListener() {
            @Override
            public void onLogUpdate(String newLog) {
                runOnUiThread(() -> {
                    adapter.appendLine(newLog);
                    updateCountLabel();
                    if (stickToBottom) scrollToBottom();
                });
            }

            @Override
            public void onLogsCleared() {
                runOnUiThread(() -> {
                    adapter.clearAll();
                    updateCountLabel();
                });
            }
        });
    }

    private void initializeViews() {
        recyclerView = findViewById(R.id.log_recycler_view);
        clearButton = findViewById(R.id.clear_log_button);
        jumpBottomButton = findViewById(R.id.jump_bottom_button);
        countLabel = findViewById(R.id.log_count_label);
    }

    private void updateCountLabel() {
        int count = adapter.getLineCount();
        int cap = LogHelper.getLogLimit(this);
        countLabel.setText(count + " / " + cap + (isEn() ? " lines" : " خط"));
    }

    private boolean isEn() {
        return getResources().getConfiguration().locale.getLanguage().equals("en");
    }

    private void scrollToBottom() {
        recyclerView.post(() -> {
            int count = adapter.getItemCount();
            if (count > 0) recyclerView.scrollToPosition(count - 1);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        LogHelper.setLogUpdateListener(new LogHelper.LogUpdateListener() {
            @Override
            public void onLogUpdate(String newLog) {
                runOnUiThread(() -> {
                    adapter.appendLine(newLog);
                    updateCountLabel();
                    if (stickToBottom) scrollToBottom();
                });
            }

            @Override
            public void onLogsCleared() {
                runOnUiThread(() -> {
                    adapter.clearAll();
                    updateCountLabel();
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LogHelper.clearListener();
    }
}
