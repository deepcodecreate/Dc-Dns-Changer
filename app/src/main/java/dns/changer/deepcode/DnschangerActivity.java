package dns.changer.deepcode;

import android.app.ActivityManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import android.graphics.Color;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.content.pm.PackageManager;
import android.Manifest;
import android.provider.Settings;
import android.widget.TextSwitcher;
import android.widget.ViewSwitcher;
import android.view.Gravity;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.util.Random;

public class DnschangerActivity extends AppCompatActivity {

    private static final int VPN_REQUEST_CODE = 100;
    private static final int NOTIFICATION_PERMISSION_CODE = 101;
    private static final int VPN_PERMISSION_REQUEST_CODE = 102;
    private static final int PING_INTERVAL = 500;
    private static final int REQUEST_CODE_SPECIAL_USE_PERMISSION = 1001;
    private static final int HINT_SLIDE_INTERVAL = 8000;

    private TextInputEditText dns1EditText, dns2EditText, ipv6Dns1EditText, ipv6Dns2EditText;
    private TextInputLayout dns1Layout, dns2Layout, ipv6Dns1Layout, ipv6Dns2Layout;
    private MaterialButton vpnButton, selectServerButton;
    private TextView pingTextView;
    private ImageView logoImage, settingsImage, nettest;
    private boolean vpnActive = false;
    private boolean isRootMode = false;
    private SharedPreferences prefs;
    private Handler pingHandler = new Handler();
    private Runnable pingRunnable;
    private boolean isReceiverRegistered = false;

    private TextSwitcher textHintSwitcher;
    private Handler hintHandler = new Handler();
    private Runnable hintRunnable;
    private int currentHintIndex = 0;

    private final String[] hintsFa = {
        "آیا می‌دانستید با دی‌سی می‌توانید به راحتی سرورهایی که خودتان وارد کرده‌اید را به اشتراک بگذارید؟ این قابلیت فقط مال دی‌سیه!",
        "برای تجربه سرعت بالاتر در بازی‌ها، توصیه می‌شود از سرورهای پیشنهادی نزدیک به موقعیت خود استفاده کنید.",
        "دی‌سی چنجر به هیچ عنوان داده‌های شخصی شما را ذخیره نکرده و حریم خصوصی شما کاملاً امن است.",
        "سرویس دی ان اس shekan یک اشغاله بیخیالش بشید (:",
        "دی سی قرار بود فقط برای پابجی باشه اما الان بهترین سرویس برای همه بازی هاست (:",
        "ایا میدونستید با سرویس TCP میتونید با برخی اوپراتور ها در برخی مناطق تحریم یوتیوب رو بشکنید؟"
    };

    private final String[] hintsEn = {
        "Did you know with DC you can easily share the servers you entered? This feature is exclusive to DC!",
        "For a faster gaming experience, it is recommended to use recommended servers close to your location.",
        "DC Changer never stores your personal data and your privacy is completely secure.",
        "Shekan DNS service is a mess, don't worry about it (:",
        "DC was supposed to be just for PUBG, but now it's the best service for all games (:",
        "Did you know that you can use TCP service to break the YouTube embargo with some operators in some regions?"
        
    };

    private BroadcastReceiver vpnStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("VPN_STATE_CHANGED".equals(intent.getAction())) {
                boolean newState = intent.getBooleanExtra("isActive", false);
                vpnActive = newState;
                prefs.edit().putBoolean("vpn_active", vpnActive).apply();
                
                runOnUiThread(() -> {
                    updateButton();
                    if (vpnActive) {
                        startLivePingUpdates();
                    } else {
                        stopLivePingUpdates();
                    }
                });
                
                if (intent.hasExtra("error")) {
                    String errorMessage = intent.getStringExtra("error");
                    showErrorAndDisconnect(errorMessage);
                }
            }
        }
    };

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
       
        setContentView(R.layout.change);
        LogHelper.log(getApplicationContext(), "Activity created");
        prefs = getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE);
        isRootMode = prefs.getBoolean("root_mode", false);
        LogHelper.log(getApplicationContext(), "Root mode: " + isRootMode);
        
        if (isRootMode) {
            String savedDns1 = prefs.getString("dns1", "");
            vpnActive = prefs.getBoolean("vpn_active", false) && 
                       !savedDns1.isEmpty() && 
                       RootCommands.isDnsChanged(savedDns1);
            LogHelper.log(getApplicationContext(), "Root mode active state: " + vpnActive);
        } else {
            vpnActive = prefs.getBoolean("vpn_active", false) && 
                       MyVpnService.isRunning(this);
            LogHelper.log(getApplicationContext(), "VPN mode active state: " + vpnActive);
        }
        
        initializeViews();
        setupTextSwitcher();
        loadSavedPreferences();
        setupButtonListeners();
        updateUIForLanguage();
        updateButton();
        
        pingRunnable = new Runnable() {
            @Override
            public void run() {
                if (vpnActive) {
                    performPingCheck();
                }
                pingHandler.postDelayed(this, PING_INTERVAL);
            }
        };
    }

    private void startLivePingUpdates() {
        LogHelper.log(getApplicationContext(), "Starting live ping updates");
    
        runOnUiThread(() -> {
            boolean isEnglish = prefs.getBoolean("english_language", false);
            pingTextView.setText(isEnglish ? "Ping: Checking..." : "پینگ: در حال بررسی...");
            pingTextView.setTextColor(Color.parseColor("#EEEEEE"));
        });
    
        pingHandler.postDelayed(pingRunnable, PING_INTERVAL);
    }

    private void stopLivePingUpdates() {
        LogHelper.log(getApplicationContext(), "Stopping live ping updates");
        pingHandler.removeCallbacks(pingRunnable);
        boolean isEnglish = prefs.getBoolean("english_language", false);
        pingTextView.setText(isEnglish ? "Status: Not connected" : "وضعیت: متصل نیست");
        pingTextView.setTextColor(Color.parseColor("#EEEEEE"));
    }

    private void updateUIForLanguage() {
        boolean isEnglish = prefs.getBoolean("english_language", false);
        LogHelper.log(getApplicationContext(), "Updating UI for language: " + (isEnglish ? "English" : "Persian"));
        
        pingTextView.setText(vpnActive ? 
            (isEnglish ? "Status: Connected" : "وضعیت: متصل") : 
            (isEnglish ? "Status: Not connected" : "وضعیت: متصل نیست"));
            
        vpnButton.setText(isEnglish ? 
            (isRootMode ? (vpnActive ? "Restore DNS" : "Change DNS") : (vpnActive ? "Disable DNS" : "Enable DNS")) : 
            (isRootMode ? (vpnActive ? "بازگردانی DNS" : "تغییر DNS") : (vpnActive ? "قطع DNS" : "فعال کردن DNS")));
            
        selectServerButton.setText(isEnglish ? "Select Recommended Server" : "انتخاب سرور پیشنهادی");
        
        dns1Layout.setHint(isEnglish ? "Primary DNS" : "DNS اول");
        dns2Layout.setHint(isEnglish ? "Secondary DNS (Optional)" : "DNS دوم (اختیاری)");
        ipv6Dns1Layout.setHint(isEnglish ? "IPv6 Primary DNS" : "DNS اول IPv6");
        ipv6Dns2Layout.setHint(isEnglish ? "IPv6 Secondary DNS" : "DNS دوم IPv6");

        showCurrentHint();
    }

    private void initializeViews() {
        LogHelper.log(getApplicationContext(), "Initializing views");
        dns1EditText = findViewById(R.id.dns1_edittext);
        dns2EditText = findViewById(R.id.dns2_edittext);
        ipv6Dns1EditText = findViewById(R.id.ipv6_dns1_edittext);
        ipv6Dns2EditText = findViewById(R.id.ipv6_dns2_edittext);
        
        dns1Layout = findViewById(R.id.dns1_layout);
        dns2Layout = findViewById(R.id.dns2_layout);
        ipv6Dns1Layout = findViewById(R.id.ipv6_dns1_layout);
        ipv6Dns2Layout = findViewById(R.id.ipv6_dns2_layout);
        
        vpnButton = findViewById(R.id.vpn_button);
        selectServerButton = findViewById(R.id.select_server_button);
        settingsImage = findViewById(R.id.settingsimage);
        nettest = findViewById(R.id.nettest);
        pingTextView = findViewById(R.id.ping_textview);
        logoImage = findViewById(R.id.logo_image);
        
        textHintSwitcher = findViewById(R.id.text_hint_switcher);
    }

    private void setupTextSwitcher() {
        if (textHintSwitcher == null) return;

        textHintSwitcher.setFactory(new ViewSwitcher.ViewFactory() {
            @Override
            public View makeView() {
                TextView textView = new TextView(getApplicationContext());
                textView.setTextSize(14);
                textView.setTextColor(Color.parseColor("#EEEEEE"));
                textView.setGravity(Gravity.CENTER);
                textView.setLineSpacing(4f, 1.1f);
                return textView;
            }
        });

        hintRunnable = new Runnable() {
            @Override
            public void run() {
                boolean isEnglish = prefs.getBoolean("english_language", false);
                String[] currentArray = isEnglish ? hintsEn : hintsFa;
                
                currentHintIndex++;
                if (currentHintIndex >= currentArray.length) {
                    currentHintIndex = 0;
                }
                
                textHintSwitcher.setText(currentArray[currentHintIndex]);
                hintHandler.postDelayed(this, HINT_SLIDE_INTERVAL);
            }
        };
    }

    private void showCurrentHint() {
        if (textHintSwitcher == null) return;
        boolean isEnglish = prefs.getBoolean("english_language", false);
        String[] currentArray = isEnglish ? hintsEn : hintsFa;
        if (currentHintIndex >= currentArray.length) {
            currentHintIndex = 0;
        }
        textHintSwitcher.setCurrentText(currentArray[currentHintIndex]);
    }

    private void startHintTimer() {
        hintHandler.removeCallbacks(hintRunnable);
        hintHandler.postDelayed(hintRunnable, HINT_SLIDE_INTERVAL);
    }

    private void stopHintTimer() {
        hintHandler.removeCallbacks(hintRunnable);
    }

    private void loadSavedPreferences() {
        LogHelper.log(getApplicationContext(), "Loading saved preferences");
        dns1EditText.setText(prefs.getString("dns1", "78.157.42.101"));
        dns2EditText.setText(prefs.getString("dns2", "78.157.42.100"));
        ipv6Dns1EditText.setText(prefs.getString("ipv6_dns1", ""));
        ipv6Dns2EditText.setText(prefs.getString("ipv6_dns2", ""));
    }
    
    private void setupButtonListeners() {
        LogHelper.log(getApplicationContext(), "Setting up button listeners");
        vpnButton.setOnClickListener(v -> {
            try {
                if (!vpnActive) {
                    LogHelper.log(getApplicationContext(), "VPN button clicked - connect");
                    if (isRootMode) {
                        LogHelper.log(getApplicationContext(), "Attempting to change DNS with root");
                        changeDnsWithRoot();
                    } else {
                        LogHelper.log(getApplicationContext(), "Checking permissions for VPN");
                        checkAndRequestVpnPermissions();
                    }
                } else {
                    LogHelper.log(getApplicationContext(), "VPN button clicked - disconnect");
                    stopDnsConnection();
                }
                
                runOnUiThread(() -> {
                    vpnButton.setEnabled(false);
                    vpnButton.setAlpha(0.7f);
                });
                
                new Handler().postDelayed(() -> {
                    runOnUiThread(() -> {
                        vpnButton.setEnabled(true);
                        vpnButton.setAlpha(1.0f);
                    });
                }, 1000);
            } catch (Exception e) {
                LogHelper.log(getApplicationContext(), "Error in VPN button click: " + e.getMessage());
                showErrorAndDisconnect(e.getMessage());
            }
        });
        
        selectServerButton.setOnClickListener(v -> {
            LogHelper.log(getApplicationContext(), "Select server button clicked");
            showServerSelectionDialog();
        });

        settingsImage.setOnClickListener(v -> {
            boolean isEnglish = prefs.getBoolean("english_language", false);
            if (vpnActive) {
                LogHelper.log(getApplicationContext(), "Settings clicked but VPN is active");
                showToast(isEnglish ? "Please disconnect first to change settings" : "لطفاً ابتدا اتصال را قطع کنید");
            } else {
                LogHelper.log(getApplicationContext(), "Opening settings activity");
                startActivity(new Intent(this, SettingsActivity.class));
            }
        });
        
        logoImage.setOnClickListener(v -> {
            Intent intent = new Intent(DnschangerActivity.this, DnsLogActivity.class);
            startActivity(intent);
        });
        
        nettest.setOnClickListener(v -> {
            Intent intent = new Intent(DnschangerActivity.this, SpeedTestActivity.class);
            startActivity(intent);
        });
    }
    
    private void checkAndRequestVpnPermissions() {
        LogHelper.log(getApplicationContext(), "Checking and requesting permissions");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                LogHelper.log(getApplicationContext(), "Requesting notification permission");
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_CODE);
                return;
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!NotificationHelper.areNotificationsEnabled(this)) {
                LogHelper.log(getApplicationContext(), "Notifications not enabled - opening settings");
                openNotificationSettings();
                return;
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE) != PackageManager.PERMISSION_GRANTED) {
                LogHelper.log(getApplicationContext(), "Requesting foreground service permission");
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE},
                    REQUEST_CODE_SPECIAL_USE_PERMISSION);
                return;
            }
        }
        
        checkVpnPermission();
    }

    private void checkVpnPermission() {
        LogHelper.log(getApplicationContext(), "Checking VPN permission");
        Intent vpnIntent = VpnService.prepare(this);
        if (vpnIntent != null) {
            LogHelper.log(getApplicationContext(), "VPN permission not granted - requesting");
            startActivityForResult(vpnIntent, VPN_PERMISSION_REQUEST_CODE);
        } else {
            LogHelper.log(getApplicationContext(), "VPN permission already granted");
            startVpn();
        }
    }

    private void openNotificationSettings() {
        boolean isEnglish = prefs.getBoolean("english_language", false);
        LogHelper.log(getApplicationContext(), "Opening notification settings dialog");
        new MaterialAlertDialogBuilder(this, R.style.CustomDialogTheme)
                .setTitle(isEnglish ? "Notification Permission Required" : "مجوز اعلانات مورد نیاز است")
                .setMessage(isEnglish ?
                        "Please enable notifications for this app to show VPN status." :
                        "لطفاً نوتیفیکیشن‌های این برنامه را برای نمایش وضعیت VPN فعال کنید.")
                .setPositiveButton(isEnglish ? "Open Settings" : "باز کردن تنظیمات", (dialog, which) -> {
                    LogHelper.log(getApplicationContext(), "User clicked to open notification settings");
                    Intent intent = new Intent();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                    } else {
                        intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
                        intent.putExtra("app_package", getPackageName());
                        intent.putExtra("app_uid", getApplicationInfo().uid);
                    }
                    startActivity(intent);
                })
                .setNegativeButton(isEnglish ? "Cancel" : "لغو", null)
                .setBackground(getResources().getDrawable(R.drawable.dialog_background))
                .show();
    }
    
    private void changeDnsWithRoot() {
        String dns1 = dns1EditText.getText().toString().trim();
        String dns2 = dns2EditText.getText().toString().trim();
        String ipv6Dns1 = ipv6Dns1EditText.getText().toString().trim();
        String ipv6Dns2 = ipv6Dns2EditText.getText().toString().trim();
        LogHelper.log(getApplicationContext(), "Attempting to change DNS with root: " + dns1 + ", " + dns2);
        
        if (TextUtils.isEmpty(dns1)) {
            boolean isEnglish = prefs.getBoolean("english_language", false);
            LogHelper.log(getApplicationContext(), "Primary DNS is empty");
            showToast(isEnglish ? "Please enter primary DNS" : "لطفاً DNS اول را وارد کنید");
            return;
        }
        
        savePreferences(dns1, dns2, ipv6Dns1, ipv6Dns2);
        
        new Thread(() -> {
            boolean success = RootCommands.changeDns(dns1, dns2, ipv6Dns1, ipv6Dns2);
            LogHelper.log(getApplicationContext(), "Root DNS change result: " + success);
            runOnUiThread(() -> {
                boolean isEnglish = prefs.getBoolean("english_language", false);
                if (success) {
                    vpnActive = true;
                    prefs.edit().putBoolean("vpn_active", true).apply();
                    updateButton();
                    startLivePingUpdates();
                    showCustomToast(isEnglish ? "DNS changed successfully" : "DNS با موفقیت تغییر کرد", R.drawable.ic_check);
                } else {
                    showCustomToast(isEnglish ? "Failed to change DNS" : "تغییر DNS ناموفق بود", R.drawable.ic_error);
                }
            });
        }).start();
    }
    
    private void stopDnsConnection() {
        LogHelper.log(getApplicationContext(), "Stopping DNS connection");
        if (isRootMode) {
            new Thread(() -> {
                boolean success = RootCommands.restoreOriginalDns();
                LogHelper.log(getApplicationContext(), "Root DNS restore result: " + success);
                runOnUiThread(() -> {
                    boolean isEnglish = prefs.getBoolean("english_language", false);
                    if (success) {
                        vpnActive = false;
                        prefs.edit().putBoolean("vpn_active", false).apply();
                        updateButton();
                        stopLivePingUpdates();
                        showCustomToast(isEnglish ? "DNS restored successfully" : "DNS با موفقیت بازگردانی شد", R.drawable.ic_check);
                    } else {
                        showCustomToast(isEnglish ? "Failed to restore DNS" : "بازگردانی DNS ناموفق بود", R.drawable.ic_error);
                    }
                });
            }).start();
        } else {
            stopVpn();
        }
    }

    private void showServerSelectionDialog() {
        if (vpnActive) {
            boolean isEnglish = prefs.getBoolean("english_language", false);
            LogHelper.log(getApplicationContext(), "Cannot select server - VPN is active");
            showToast(isEnglish ? "Please disconnect first" : "لطفاً ابتدا اتصال را قطع کنید");
            return;
        }
        
        LogHelper.log(getApplicationContext(), "Opening server selection activity");
        Intent intent = new Intent(this, ServerselectionActivity.class);
        startActivityForResult(intent, 1);
    }

    private void performPingCheck() {
        boolean isEnglish = prefs.getBoolean("english_language", false);
        LogHelper.log(getApplicationContext(), "Performing ping check");

        String dns = dns1EditText.getText().toString().trim();
        if (dns.isEmpty()) {
            LogHelper.log(getApplicationContext(), "No DNS set for ping check");
            runOnUiThread(() -> {
                pingTextView.setText(isEnglish ? "No DNS set" : "DNS وارد نشده");
                pingTextView.setTextColor(Color.parseColor("#EEEEEE"));
            });
            return;
        }

        new Thread(() -> {
            boolean success = false;
            String resultText = "";
            int pingValue = -1;

            try {
                LogHelper.log(getApplicationContext(), "Attempting ICMP ping to: " + dns);
                Process process = Runtime.getRuntime().exec("ping -c 1 -W 2 " + dns);
                int resultCode = process.waitFor();

                java.io.InputStream inputStream = process.getInputStream();
                java.util.Scanner s = new java.util.Scanner(inputStream).useDelimiter("\\A");
                String output = s.hasNext() ? s.next() : "";

                if (resultCode == 0 && output.contains("time=")) {
                    int index = output.indexOf("time=");
                    int end = output.indexOf(" ms", index);
                    if (index > 0 && end > index) {
                        String timeText = output.substring(index + 5, end);
                        pingValue = (int) Float.parseFloat(timeText);
                        resultText = (isEnglish ? "Ping: " : "پینگ: ") + pingValue + "ms";
                        success = true;
                        LogHelper.log(getApplicationContext(), "ICMP ping successful: " + pingValue + "ms");
                    }
                }
            } catch (Exception e) {
                LogHelper.log(getApplicationContext(), "ICMP ping failed: " + e.getMessage());
            }

            if (!success) {
                try {
                    LogHelper.log(getApplicationContext(), "Attempting TCP ping to: " + dns);
                    long startTime = System.currentTimeMillis();
                    java.net.Socket socket = new java.net.Socket();
                    java.net.InetSocketAddress address = new java.net.InetSocketAddress(dns, 53);
                    socket.connect(address, 2000);
                    socket.close();
                    pingValue = (int) (System.currentTimeMillis() - startTime);
                    resultText = (isEnglish ? "Ping: " : "پینگ: ") + pingValue + "ms";
                    success = true;
                    LogHelper.log(getApplicationContext(), "TCP ping successful: " + pingValue + "ms");
                } catch (Exception e) {
                    LogHelper.log(getApplicationContext(), "TCP ping failed: " + e.getMessage());
                }
            }

            final boolean finalSuccess = success;
            final String finalResultText = success ? resultText : (isEnglish ? "Ping failed" : "پینگ ناموفق بود");
            final int finalPingValue = pingValue;

            runOnUiThread(() -> {
                pingTextView.setText(finalResultText);

                if (finalSuccess) {
                    if (finalPingValue < 80) {
                        pingTextView.setTextColor(Color.parseColor("#4CAF50")); 
                    } else if (finalPingValue < 120) {
                        pingTextView.setTextColor(Color.parseColor("#FFC107")); 
                    } else if (finalPingValue <= 200) {
                        pingTextView.setTextColor(Color.parseColor("#FF632e")); 
                    } else {
                        pingTextView.setTextColor(Color.parseColor("#FF0000")); 
                    }
                } else {
                    pingTextView.setTextColor(Color.parseColor("#EEEEEE"));
                }
            });
        }).start();
    }

    private void updateButton() {
        runOnUiThread(() -> {
            boolean isEnglish = prefs.getBoolean("english_language", false);
            LogHelper.log(getApplicationContext(), "Updating button state. VPN active: " + vpnActive + ", Root mode: " + isRootMode);
            
            if (vpnActive) {
                vpnButton.setText(isEnglish ? 
                    (isRootMode ? "Restore DNS" : "Disable DNS") : 
                    (isRootMode ? "بازگردانی DNS" : "قطع DNS"));
                vpnButton.setIconResource(R.drawable.ic_vpn_off);
                selectServerButton.setEnabled(false);
                selectServerButton.setAlpha(0.5f);
            
                pingTextView.setText(isEnglish ? "Status: Connected" : "وضعیت: متصل");
                pingTextView.setTextColor(Color.parseColor("#4CAF50"));
                
                if (!pingHandler.hasCallbacks(pingRunnable)) {
                    startLivePingUpdates();
                }
            } else {
                vpnButton.setText(isEnglish ? 
                    (isRootMode ? "Change DNS" : "Enable DNS") : 
                    (isRootMode ? "تغییر DNS" : "فعال کردن DNS"));
                vpnButton.setIconResource(R.drawable.ic_vpn_on);
                pingTextView.setText(isEnglish ? "Status: Not connected" : "وضعیت: متصل نیست");
                pingTextView.setTextColor(Color.parseColor("#EEEEEE"));
                selectServerButton.setEnabled(true);
                selectServerButton.setAlpha(1.0f);
            }
        });
    }

    private void startVpn() {
        String dns1 = dns1EditText.getText().toString().trim();
        String dns2 = dns2EditText.getText().toString().trim();
        String ipv6Dns1 = ipv6Dns1EditText.getText().toString().trim();
        String ipv6Dns2 = ipv6Dns2EditText.getText().toString().trim();
        LogHelper.log(getApplicationContext(), "Starting VPN with DNS: " + dns1 + ", " + dns2);

        if (TextUtils.isEmpty(dns1)) {
            boolean isEnglish = prefs.getBoolean("english_language", false);
            LogHelper.log(getApplicationContext(), "Primary DNS is empty");
            showToast(isEnglish ? "Please enter primary DNS" : "لطفاً DNS اول را وارد کنید");
            return;
        }

        savePreferences(dns1, dns2, ipv6Dns1, ipv6Dns2);
        boolean useTcp = prefs.getBoolean("dns_over_tcp", false);
    
        Intent intent = new Intent(this, MyVpnService.class);
        intent.putExtra("dns1", dns1);
        intent.putExtra("dns2", dns2);
        intent.putExtra("ipv6_dns1", ipv6Dns1);
        intent.putExtra("ipv6_dns2", ipv6Dns2);
        intent.putExtra("use_tcp", useTcp);
        intent.putExtra("use_dhcp", prefs.getBoolean("use_dhcp", false));
        intent.putExtra("ipv4", prefs.getString("ipv4_address", "10.0.0.2"));
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }

        boolean isEnglish = prefs.getBoolean("english_language", false);
        pingTextView.setText(isEnglish ? "Ping: Checking..." : "پینگ: در حال بررسی...");
        pingTextView.setTextColor(Color.parseColor("#EEEEEE"));

        pingHandler.postDelayed(() -> {
            refreshVpnStatus();
            showCustomToast(isEnglish ? "Connected successfully" : "اتصال با موفقیت برقرار شد", R.drawable.ic_check);
        }, 1500);

        if (useTcp) {
            new Thread(() -> {
                String result = DnsOverTcpHelper.resolveA("example.com", dns1);
                LogHelper.log(getApplicationContext(), "DNS over TCP Result: " + result);
            }).start();
        }
    }

    private void stopVpn() {
        LogHelper.log(getApplicationContext(), "Stopping VPN service");
        Intent intent = new Intent(this, MyVpnService.class);
        intent.setAction("DISCONNECT_VPN");
        startService(intent);
        
        vpnActive = false;
        prefs.edit().putBoolean("vpn_active", false).apply();
        updateButton();
        stopLivePingUpdates();
    }

    private void savePreferences(String dns1, String dns2, String ipv6Dns1, String ipv6Dns2) {
        LogHelper.log(getApplicationContext(), "Saving preferences: " + dns1 + ", " + dns2);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("dns1", dns1);
        editor.putString("dns2", dns2);
        editor.putString("ipv6_dns1", ipv6Dns1);
        editor.putString("ipv6_dns2", ipv6Dns2);
        editor.apply();
    }
    
    private void showErrorAndDisconnect(String error) {
        LogHelper.log(getApplicationContext(), "Showing error and disconnecting: " + error);
        runOnUiThread(() -> {
            stopDnsConnection();
            vpnActive = false;
            prefs.edit().putBoolean("vpn_active", false).apply();
            updateButton();
            stopLivePingUpdates();
        });
    }

    private void showToast(String message) {
        LogHelper.log(getApplicationContext(), "Showing toast: " + message);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void showCustomToast(String message, int iconRes) {
        LogHelper.log(getApplicationContext(), "Showing custom toast: " + message);
        Toast toast = new Toast(this);
        View view = LayoutInflater.from(this).inflate(R.layout.custom_toast, null);
        TextView text = view.findViewById(R.id.toast_text);
        ImageView icon = view.findViewById(R.id.toast_icon);
        
        text.setText(message);
        icon.setImageResource(iconRes);
        toast.setView(view);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        LogHelper.log(getApplicationContext(), "Activity result - requestCode: " + requestCode + ", resultCode: " + resultCode);
        
        if (requestCode == 1 && resultCode == RESULT_OK && data != null) {
            String dns1 = data.getStringExtra("dns1");
            String dns2 = data.getStringExtra("dns2");
            String ipv6Dns1 = data.getStringExtra("ipv6_dns1");
            String ipv6Dns2 = data.getStringExtra("ipv6_dns2");
            LogHelper.log(getApplicationContext(), "Received DNS from server selection: " + dns1 + ", " + dns2);
            
            dns1EditText.setText(dns1);
            dns2EditText.setText(dns2);
            ipv6Dns1EditText.setText(ipv6Dns1);
            ipv6Dns2EditText.setText(ipv6Dns2);
        } 
        else if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            LogHelper.log(getApplicationContext(), "Notification permission result received");
            checkAndRequestVpnPermissions();
        }
        else if (requestCode == VPN_PERMISSION_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                LogHelper.log(getApplicationContext(), "VPN permission granted");
                startVpn();
            } else {
                boolean isEnglish = prefs.getBoolean("english_language", false);
                LogHelper.log(getApplicationContext(), "VPN permission denied");
                showToast(isEnglish ? "VPN permission is required to change DNS" : "مجوز VPN برای تغییر DNS ضروری است");
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        LogHelper.log(getApplicationContext(), "Permission result - requestCode: " + requestCode);
        
        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                LogHelper.log(getApplicationContext(), "Notification permission granted");
                checkVpnPermission();
            } else {
                boolean isEnglish = prefs.getBoolean("english_language", false);
                LogHelper.log(getApplicationContext(), "Notification permission denied");
                showToast(isEnglish ? "Notification permission is required for VPN service" : "مجوز اعلانات برای سرویس VPN ضروری است");
            }
        }
        else if (requestCode == REQUEST_CODE_SPECIAL_USE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                LogHelper.log(getApplicationContext(), "Foreground service permission granted");
                checkVpnPermission();
            } else {
                boolean isEnglish = prefs.getBoolean("english_language", false);
                LogHelper.log(getApplicationContext(), "Foreground service permission denied");
                showToast(isEnglish ? "Foreground service permission is required" : "مجوز سرویس پیش‌زمینه ضروری است");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        LogHelper.log(getApplicationContext(), "Activity resumed");
        
        if (!isReceiverRegistered) {
            IntentFilter filter = new IntentFilter("VPN_STATE_CHANGED");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(vpnStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(vpnStateReceiver, filter);
            }
            isReceiverRegistered = true;
        }
        
        refreshVpnStatus();
        startHintTimer();
    }

    @Override
    protected void onPause() {
        super.onPause();
        LogHelper.log(getApplicationContext(), "Activity paused");
        stopLivePingUpdates();
        stopHintTimer();
        
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(vpnStateReceiver);
                isReceiverRegistered = false;
            } catch (Exception e) {
                LogHelper.log(getApplicationContext(), "Error unregistering receiver: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LogHelper.log(getApplicationContext(), "Activity destroyed");
        stopLivePingUpdates();
        stopHintTimer();
        
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(vpnStateReceiver);
                isReceiverRegistered = false;
            } catch (Exception e) {
                LogHelper.log(getApplicationContext(), "Error unregistering receiver: " + e.getMessage());
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            refreshVpnStatus();
        }
    }

    private void refreshVpnStatus() {
        isRootMode = prefs.getBoolean("root_mode", false);
        
        if (isRootMode) {
            String savedDns1 = prefs.getString("dns1", "");
            vpnActive = prefs.getBoolean("vpn_active", false) && 
                       !savedDns1.isEmpty() && 
                       RootCommands.isDnsChanged(savedDns1);
        } else {
            vpnActive = prefs.getBoolean("vpn_active", false) && 
                       MyVpnService.isRunning(this);
        }
        
        runOnUiThread(() -> {
            updateButton();
            if (vpnActive) {
                startLivePingUpdates();
            } else {
                stopLivePingUpdates();
            }
        });
    }
}
