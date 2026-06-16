package dns.changer.deepcode;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.provider.Settings;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;
import com.google.android.material.button.MaterialButton;
import android.content.res.ColorStateList;
import android.widget.ListView;
import android.widget.ArrayAdapter;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.Spinner;
import android.widget.AdapterView;
import android.widget.PopupMenu;
import java.io.File;
import java.io.DataOutputStream;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.materialswitch.MaterialSwitch;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private MaterialSwitch dhcpSwitch, rootSwitch, tcpSwitch, langSwitch, themeSwitch;
    private TextInputEditText ipv4Input, logLimitInput;
    private TextInputLayout ipv4Layout, logLimitLayout;
    private MaterialButton saveButton;
    private ImageView backButton;
    private TextView settingsTitleText;
    private SharedPreferences prefs;
    private boolean isEnglish;
    private boolean isBindingSetup = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE);
        boolean isGrayTheme = prefs.getBoolean("gray_theme", false);

        if (isGrayTheme) {
            setTheme(R.style.AppTheme_GrayMaterial);
        } else {
            setTheme(R.style.AppTheme);
        }

        setContentView(R.layout.settings_activity);

        initializeViews();
        loadSettings();
        setupListeners();
        setupSwitchColors(isGrayTheme);
        updateUIForLanguage();
    }

    private void initializeViews() {
        dhcpSwitch = findViewById(R.id.dhcp_switch);
        rootSwitch = findViewById(R.id.root_switch);
        tcpSwitch = findViewById(R.id.tcp_switch);
        langSwitch = findViewById(R.id.lang_switch);
        themeSwitch = findViewById(R.id.theme_switch);
        ipv4Input = findViewById(R.id.ipv4_input);
        logLimitInput = findViewById(R.id.log_limit_input);
        ipv4Layout = findViewById(R.id.ipv4_layout);
        logLimitLayout = findViewById(R.id.log_limit_layout);
        saveButton = findViewById(R.id.save_button);
        backButton = findViewById(R.id.back_button);
        settingsTitleText = findViewById(R.id.settings_title_text);
        isEnglish = prefs.getBoolean("english_language", false);
    }

    private void loadSettings() {
        isBindingSetup = true;
        dhcpSwitch.setChecked(prefs.getBoolean("use_dhcp", false));
        rootSwitch.setChecked(prefs.getBoolean("root_mode", false));
        tcpSwitch.setChecked(prefs.getBoolean("dns_over_tcp", false));
        langSwitch.setChecked(prefs.getBoolean("english_language", false));
        themeSwitch.setChecked(prefs.getBoolean("gray_theme", false));
        ipv4Input.setText(prefs.getString("ipv4_address", "10.0.0.2"));
        logLimitInput.setText(String.valueOf(prefs.getInt("log_limit", 1000)));
        isBindingSetup = false;
    }

    private void setupSwitchColors(boolean isGrayTheme) {
        int thumbActiveColor = isGrayTheme ? Color.parseColor("#90CAF9") : Color.parseColor("#FFA88B");
        int thumbInactiveColor = Color.parseColor("#BDBDBD");
        int trackActiveColor = isGrayTheme ? Color.parseColor("#4D90CAF9") : Color.parseColor("#4DFFA88B");
        int trackInactiveColor = Color.parseColor("#4DFFFFFF");

        ColorStateList thumbStateList = new ColorStateList(
            new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked}
            },
            new int[]{
                thumbActiveColor,
                thumbInactiveColor
            }
        );

        ColorStateList trackStateList = new ColorStateList(
            new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked}
            },
            new int[]{
                trackActiveColor,
                trackInactiveColor
            }
        );

        MaterialSwitch[] switches = {dhcpSwitch, rootSwitch, tcpSwitch, langSwitch, themeSwitch};
        for (MaterialSwitch sw : switches) {
            if (sw != null) {
                sw.setThumbTintList(thumbStateList);
                sw.setTrackTintList(trackStateList);
            }
        }
    }

    private void setupListeners() {
        dhcpSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isBindingSetup) return;
            prefs.edit().putBoolean("use_dhcp", isChecked).apply();
        });

        rootSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isBindingSetup) return;
            if (isChecked) {
                isBindingSetup = true;
                rootSwitch.setChecked(false);
                isBindingSetup = false;
                showRootWarningDialog();
            } else {
                prefs.edit().putBoolean("root_mode", false).apply();
            }
        });

        tcpSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
        prefs.edit().putBoolean("dns_over_tcp", isChecked).apply();
        if (isChecked) {
                showTcpConfigurationOptions();
            } else {
                
            }
    if (MyVpnService.isRunning(SettingsActivity.this)) {
        Intent stopVpn = new Intent(SettingsActivity.this, MyVpnService.class);
        stopVpn.setAction("DISCONNECT_VPN");
        startService(stopVpn);
        
        new android.os.Handler().postDelayed(() -> {
            Intent startVpn = new Intent(SettingsActivity.this, MyVpnService.class);
            startVpn.putExtra("dns1", prefs.getString("dns1", "78.157.42.101"));
            startVpn.putExtra("dns2", prefs.getString("dns2", "78.157.42.100"));
            startService(startVpn);
        }, 500);
    }
});


        langSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isBindingSetup) return;
            prefs.edit().putBoolean("english_language", isChecked).apply();
            updateLanguage(isChecked);
            restartApp();
        });

        themeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isBindingSetup) return;
            prefs.edit().putBoolean("gray_theme", isChecked).apply();
            restartApp();
        });

        saveButton.setOnClickListener(v -> {
            saveLogLimit();
        });

        backButton.setOnClickListener(v -> {
            onBackPressed();
        });
    }

    private void showRootWarningDialog() {
        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.CustomDialogTheme)
                .setTitle(isEnglish ? "Root Mode Activation" : "فعالسازی حالت روت")
                .setMessage(isEnglish ? 
                    "Root mode requires superuser privileges. It changes system DNS directly without using Android VpnService. Proceed?" : 
                    "حالت روت نیازمند دسترسی سوپریوزر است. این ویژگی دی‌ان‌اس سیستم را مستقیما تغییر می‌دهد و از سرویس وی‌پی‌ان استفاده نمی‌کند. ادامه می‌دهید؟")
                .setPositiveButton(isEnglish ? "Grant & Activate" : "اعطای دسترسی و فعالسازی", (d, w) -> {
                    checkAndRequestRootAccess();
                })
                .setNegativeButton(isEnglish ? "Cancel" : "لغو", (d, w) -> {
                    isBindingSetup = true;
                    rootSwitch.setChecked(false);
                    isBindingSetup = false;
                    prefs.edit().putBoolean("root_mode", false).apply();
                })
                .setCancelable(false)
                .create();
        dialog.show();
        styleDialogButtons(dialog);
    }

    private void checkAndRequestRootAccess() {
        
        new Thread(() -> {
            boolean hasRoot = false;
            Process process = null;
            DataOutputStream os = null;
            try {
                process = Runtime.getRuntime().exec("su");
                os = new DataOutputStream(process.getOutputStream());
                os.writeBytes("exit\n");
                os.flush();
                int exitValue = process.waitFor();
                if (exitValue == 0) {
                    hasRoot = true;
                }
            } catch (Exception e) {
                hasRoot = false;
            } finally {
                try {
                    if (os != null) os.close();
                    if (process != null) process.destroy();
                } catch (Exception ignored) {}
            }

            final boolean rootVerified = hasRoot;
            runOnUiThread(() -> {
                if (rootVerified) {
                    isBindingSetup = true;
                    rootSwitch.setChecked(true);
                    isBindingSetup = false;
                    prefs.edit().putBoolean("root_mode", true).apply();
                    showToast(isEnglish ? "Root access granted successfully" : "دسترسی روت با موفقیت تایید شد");
                    checkBatteryOptimization();
                } else {
                    isBindingSetup = true;
                    rootSwitch.setChecked(false);
                    isBindingSetup = false;
                    prefs.edit().putBoolean("root_mode", false).apply();
                    showToast(isEnglish ? "Root access denied or device is not rooted!" : "دسترسی روت رد شد یا دستگاه شما روت نیست!");
                }
            });
        }).start();
    }

    private void showTcpConfigurationOptions() {
        String[] options = isEnglish ? 
            new String[]{"Standard Stream TCP", "Custom Packet Pipeline", "Force Aggressive Multipath"} : 
            new String[]{"پروتکل استاندارد TCP", "خط لوله پکت سفارشی", "اتصال چندگانه تهاجمی"};

        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.CustomDialogTheme)
                .setTitle(isEnglish ? "Configure DNS over TCP" : "تنظیمات کانال دی‌ان‌اس بر روی TCP")
                .setItems(options, (d, which) -> {
                    showToast((isEnglish ? "Selected: " : "انتخاب شد: ") + options[which]);
                })
                .create();
        dialog.show();
    }

    private void checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.CustomDialogTheme)
                        .setTitle(isEnglish ? "Battery Optimization & Background Alive" : "بهینه‌سازی باتری و فعالیت پس‌زمینه")
                        .setMessage(isEnglish ? 
                            "Please disable battery optimization and allow background persistence (Always Active) to prevent Android from killing network routing modules." : 
                            "لطفا بهینه‌سازی باتری را غیرفعال کرده و اجازه فعالیت در پس‌زمینه (همیشه فعال) را صادر کنید تا از بسته شدن ماژول‌های مسیریابی توسط سیستم جلوگیری شود.")
                        .setPositiveButton(isEnglish ? "Configure Alive & Auto-Start" : "تنظیم پایداری و شروع خودکار", (d, w) -> {
                            openBatterySettings();
                            showStartupAndAutostartDialog();
                        })
                        .setNegativeButton(isEnglish ? "Dismiss" : "بستن", null)
                        .create();
                dialog.show();
                styleDialogButtons(dialog);
            } else {
                showStartupAndAutostartDialog();
            }
        } else {
            showStartupAndAutostartDialog();
        }
    }

    private void openBatterySettings() {
        try {
            Intent intent = new Intent();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            } else {
                intent.setAction(Settings.ACTION_SETTINGS);
            }
            startActivity(intent);
        } catch (Exception e) {
            showToast(e.getMessage());
        }
    }

    private void showStartupAndAutostartDialog() {
        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.CustomDialogTheme)
                .setTitle(isEnglish ? "Startup & Auto-Start Config" : "تنظیمات شروع با استارتاپ")
                .setMessage(isEnglish ? 
                    "To guarantee core operations launch automatically upon system boot up, check auto-start permissions." : 
                    "برای تضمین اجرای خودکار سرویس دی‌ان‌اس بلافاصله پس از بوت شدن و بالا آمدن دستگاه، دسترسی شروع خودکار را بررسی کنید.")
                .setPositiveButton(isEnglish ? "Open Boot Manager" : "مدیریت استارتاپ دستگاه", (d, w) -> {
                    triggerAutostartSettings();
                })
                .setNegativeButton(isEnglish ? "Skip" : "رد کردن", null)
                .create();
        dialog.show();
        styleDialogButtons(dialog);
    }

    private void triggerAutostartSettings() {
        String[][] intentData = {
            {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
            {"com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"},
            {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
            {"com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
            {"com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"},
            {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"},
            {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"},
            {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
            {"com.samsung.android.lovelydarkness", "com.samsung.android.sm.ui.battery.BatteryActivity"},
            {"com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"}
        };

        boolean success = false;
        for (String[] target : intentData) {
            try {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName(target[0], target[1]));
                startActivity(intent);
                success = true;
                break;
            } catch (Exception ignored) {}
        }

        if (!success) {
            try {
                Intent intent = new Intent(Settings.ACTION_SETTINGS);
                startActivity(intent);
            } catch (Exception e) {
                showToast(e.getMessage());
            }
        }
    }

    private void styleDialogButtons(AlertDialog dialog) {
        int accentColor = Color.parseColor("#FFA88B");
        boolean isGrayTheme = prefs.getBoolean("gray_theme", false);
        if (isGrayTheme) {
            accentColor = Color.parseColor("#90CAF9");
        }
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(accentColor);
        }
        if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(accentColor);
        }
    }

    private void updateUIForLanguage() {
        if (isEnglish) {
            settingsTitleText.setText("Settings");
            dhcpSwitch.setText("DHCP Connection");
            rootSwitch.setText("Root Mode");
            tcpSwitch.setText("DNS Over TCP");
            langSwitch.setText("English Language");
            themeSwitch.setText("Gray Theme");
            ipv4Layout.setHint("IPv4 Address");
            logLimitLayout.setHint("Log Limit");
            saveButton.setText("Save Settings");
        } else {
            settingsTitleText.setText("تنظیمات");
            dhcpSwitch.setText("اتصال DHCP (غیر ضروری)");
            rootSwitch.setText("حالت روت (سوپر یوزر)");
            tcpSwitch.setText("پروتکل DNS Over TCP");
            langSwitch.setText("استفاده از زبان انگلیسی");
            themeSwitch.setText("تم خاکستری برنامه");
            ipv4Layout.setHint("آدرس داخلی IPv4");
            logLimitLayout.setHint("محدودیت تعداد لاگ");
            saveButton.setText("ذخیره تنظیمات");
        }
    }

    private void updateLanguage(boolean english) {
        Locale locale = new Locale(english ? "en" : "fa");
        Locale.setDefault(locale);
        Resources resources = getResources();
        Configuration config = resources.getConfiguration();
        config.setLocale(locale);
        resources.updateConfiguration(config, resources.getDisplayMetrics());
    }

    private void restartApp() {
        
        Intent intent = new Intent(this, DnschangerActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        
        Intent thisIntent = new Intent(this, SettingsActivity.class);
        startActivity(thisIntent);
        
        finish();
        overridePendingTransition(0, 0);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveLogLimit();
    }

    private void saveLogLimit() {
        try {
            int limit = Integer.parseInt(logLimitInput.getText().toString().trim());
            if (limit >= 10 && limit <= 1000) {
                prefs.edit().putInt("log_limit", limit).apply();
            } else {
                showToast(isEnglish ? "Log limit must be between 10 and 1000" : "محدودیت لاگ باید بین ۱۰ تا ۱۰۰۰ باشد");
            }
        } catch (NumberFormatException e) {
            showToast(isEnglish ? "Invalid log limit" : "مقدار لاگ نامعتبر است");
        }
        
        String ip = ipv4Input.getText().toString().trim();
        if (!ip.isEmpty()) {
            prefs.edit().putString("ipv4_address", ip).apply();
        }
    }
}
