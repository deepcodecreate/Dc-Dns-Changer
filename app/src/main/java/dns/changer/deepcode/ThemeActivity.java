package dns.changer.deepcode;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class ThemeActivity extends BaseActivity {

    private boolean changed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.applyTheme(this);
        setContentView(R.layout.activity_theme);

        SharedPreferences prefs = getSharedPreferences("vpn_prefs", MODE_PRIVATE);
        boolean english = prefs.getBoolean("english_language", false);

        TextView title = findViewById(R.id.theme_title_text);
        title.setText(english ? "App Theme" : "تم برنامه");

        ImageView back = findViewById(R.id.theme_back_button);
        back.setOnClickListener(v -> finishWithResult());

        RecyclerView recyclerView = findViewById(R.id.theme_recycler_view);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));

        int selectedId = ThemeManager.getSelectedId(this);
        ThemePreviewAdapter adapter = new ThemePreviewAdapter(
                ThemeManager.all(), selectedId, english,
                theme -> {
                    if (theme.id != selectedId) {
                        ThemeManager.setSelectedId(ThemeActivity.this, theme.id);
                        changed = true;
                    }
                });
        recyclerView.setAdapter(adapter);
    }

    private void finishWithResult() {
        setResult(changed ? Activity.RESULT_OK : Activity.RESULT_CANCELED);
        finish();
    }

    @Override
    public void onBackPressed() {
        finishWithResult();
    }
}
