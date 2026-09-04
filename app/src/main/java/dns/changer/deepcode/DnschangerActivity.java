package dns.changer.deepcode;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TextSwitcher;
import android.widget.ViewSwitcher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class DnschangerActivity extends AppCompatActivity {

    private static final int NOTIFICATION_PERMISSION_CODE = 101;
    private static final int VPN_PERMISSION_REQUEST_CODE = 102;
    private static final int SERVER_PICK_CODE = 1;
    private static final int PING_INTERVAL = 4000;
    private static final int HINT_SLIDE_INTERVAL = 8000;

    private TextInputEditText dns1EditText, dns2EditText, ipv6Dns1EditText, ipv6Dns2EditText;
    private TextInputEditText hostnameEditText, dohUrlEditText, portEditText;
    private TextInputLayout dns1Layout, dns2Layout, ipv6Dns1Layout, ipv6Dns2Layout;
    private TextInputLayout hostnameLayout, dohUrlLayout, portLayout;
    private MaterialButton vpnButton, selectServerButton;
    private CircularProgressIndicator vpnButtonProgress;
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private Runnable connectTimeoutRunnable;
    private MaterialButtonToggleGroup protocolGroup;
    private TextView pingTextView, protocolSubtitle, configTitle, connectionTitle;
    private ImageView logoImage, settingsImage;
    private boolean vpnActive = false;
    private boolean isRootMode = false;
    private SharedPreferences prefs;
    private Handler pingHandler = new Handler();
    private Runnable pingRunnable;
    private boolean isReceiverRegistered = false;
    private boolean connecting = false;

    private TextSwitcher textHintSwitcher;
    private Handler hintHandler = new Handler();
    private Runnable hintRunnable;
    private int currentHintIndex = 0;
    private DnsProtocol currentProtocol = DnsProtocol.UDP;

    private final String[] hintsFa = {
        "آیا می‌دانستید با دی‌سی می‌توانید به راحتی سرورهایی که خودتان وارد کرده‌اید را به اشتراک بگذارید؟ این قابلیت فقط مال دی‌سیه!",
        "برای تجربه سرعت بالاتر در بازی‌ها، توصیه می‌شود از سرورهای پیشنهادی نزدیک به موقعیت خود استفاده کنید.",
        "دی‌سی چنجر به هیچ عنوان داده‌های شخصی شما را ذخیره نکرده و حریم خصوصی شما کاملاً امن است.",
        "سرویس دی ان اس shekan یک اشغاله بیخیالش بشید (:",
        "دی سی قرار بود فقط برای پابجی باشه اما الان بهترین سرویس برای همه بازی هاست (:",
        "با DoT و DoH ترافیک DNS شما رمزنگاری می‌شود و از دست ISP در امان است."
    };

    private final String[] hintsEn = {
        "Did you know with DC you can easily share the servers you entered? This feature is exclusive to DC!",
        "For a faster gaming experience, it is recommended to use recommended servers close to your location.",
        "DC Changer never stores your personal data and your privacy is completely secure.",
        "Shekan DNS service is a mess, don't worry about it (:",
        "DC was supposed to be just for PUBG, but now it's the best service for all games (:",
        "DoT and DoH encrypt your DNS so your ISP cannot snoop queries."

    };

    private BroadcastReceiver vpnStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (MyVpnService.ACTION_STATE.equals(intent.getAction())) {
                boolean newState = intent.getBooleanExtra("isActive", false);
                vpnActive = newState;
                connecting = false;
                cancelConnectTimeout();
                prefs.edit().putBoolean("vpn_active", vpnActive).apply();
                final String error = intent.getStringExtra("error");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setButtonLoading(false);
                        updateButton();
                        if (vpnActive) {
                            startLivePingUpdates();
                            if ("connected_degraded".equals(error)) {
                                showCustomToast(isEn() ? "Connected, DNS is slow to answer" : "متصل شد، پاسخ DNS کند است", R.drawable.ic_info);
                            } else {
                                showCustomToast(isEn() ? "Connected successfully" : "اتصال با موفقیت برقرار شد", R.drawable.ic_check);
                            }
                        } else {
                            stopLivePingUpdates();
                            if (error != null && !"connected_degraded".equals(error)) {
                                showCustomToast(error, R.drawable.ic_error);
                            }
                        }
                    }
                });
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE);
        ThemeManager.applyTheme(this);
        setContentView(R.layout.change);
        LogHelper.log(getApplicationContext(), "Activity created");
        isRootMode = prefs.getBoolean("root_mode", false);
        currentProtocol = DnsProtocol.from(prefs.getString("dns_protocol",
                prefs.getBoolean("dns_over_tcp", false) ? "TCP" : "UDP"));

        if (isRootMode) {
            String savedDns1 = prefs.getString("dns1", "");
            vpnActive = prefs.getBoolean("vpn_active", false)
                    && !savedDns1.isEmpty()
                    && RootCommands.isDnsChanged(savedDns1);
        } else {
            vpnActive = prefs.getBoolean("vpn_active", false) && MyVpnService.isRunning(this);
        }

        initializeViews();
        setupTextSwitcher();
        loadSavedPreferences();
        applyProtocolUi(currentProtocol, false);
        setupButtonListeners();
        updateUIForLanguage();
        updateButton();

        pingRunnable = new Runnable() {
            @Override
            public void run() {
                if (vpnActive) performPingCheck();
                pingHandler.postDelayed(this, PING_INTERVAL);
            }
        };
    }

    private boolean isEn() {
        return prefs.getBoolean("english_language", false);
    }

    private void startLivePingUpdates() {
        pingHandler.removeCallbacks(pingRunnable);
        pingTextView.setText(isEn() ? "Ping: Checking..." : "پینگ: در حال بررسی...");
        pingTextView.setTextColor(ThemeManager.getDefaultTextColor(this));
        pingHandler.postDelayed(pingRunnable, 400);
    }

    private void stopLivePingUpdates() {
        pingHandler.removeCallbacks(pingRunnable);
        pingTextView.setText(isEn() ? "Status: Not connected" : "وضعیت: متصل نیست");
        pingTextView.setTextColor(ThemeManager.getDefaultTextColor(this));
    }

    private void updateUIForLanguage() {
        pingTextView.setText(vpnActive
                ? (isEn() ? "Status: Connected" : "وضعیت: متصل")
                : (isEn() ? "Status: Not connected" : "وضعیت: متصل نیست"));
        selectServerButton.setText(isEn() ? "Select recommended server" : "انتخاب سرور پیشنهادی");
        if (configTitle != null) configTitle.setText(isEn() ? "DNS Configuration" : "پیکربندی DNS");
        if (connectionTitle != null) connectionTitle.setText(isEn() ? "Connection" : "اتصال");
        applyProtocolUi(currentProtocol, false);
        showCurrentHint();
    }

    private void initializeViews() {
        dns1EditText = findViewById(R.id.dns1_edittext);
        dns2EditText = findViewById(R.id.dns2_edittext);
        ipv6Dns1EditText = findViewById(R.id.ipv6_dns1_edittext);
        ipv6Dns2EditText = findViewById(R.id.ipv6_dns2_edittext);
        hostnameEditText = findViewById(R.id.hostname_edittext);
        dohUrlEditText = findViewById(R.id.doh_url_edittext);
        portEditText = findViewById(R.id.port_edittext);

        dns1Layout = findViewById(R.id.dns1_layout);
        dns2Layout = findViewById(R.id.dns2_layout);
        ipv6Dns1Layout = findViewById(R.id.ipv6_dns1_layout);
        ipv6Dns2Layout = findViewById(R.id.ipv6_dns2_layout);
        hostnameLayout = findViewById(R.id.hostname_layout);
        dohUrlLayout = findViewById(R.id.doh_url_layout);
        portLayout = findViewById(R.id.port_layout);

        vpnButton = findViewById(R.id.vpn_button);
        vpnButtonProgress = findViewById(R.id.vpn_button_progress);
        selectServerButton = findViewById(R.id.select_server_button);
        settingsImage = findViewById(R.id.settingsimage);
        pingTextView = findViewById(R.id.ping_textview);
        logoImage = findViewById(R.id.logo_image);
        textHintSwitcher = findViewById(R.id.text_hint_switcher);
        protocolGroup = findViewById(R.id.protocol_group);
        protocolSubtitle = findViewById(R.id.protocol_subtitle);
        configTitle = findViewById(R.id.config_title);
        connectionTitle = findViewById(R.id.connection_title);
    }

    private void applyProtocolUi(DnsProtocol protocol, boolean persist) {
        currentProtocol = protocol;
        if (persist) {
            prefs.edit()
                    .putString("dns_protocol", protocol.name())
                    .putBoolean("dns_over_tcp", protocol == DnsProtocol.TCP)
                    .apply();
        }
        if (protocolGroup != null) {
            int id = R.id.btn_proto_udp;
            if (protocol == DnsProtocol.TCP) id = R.id.btn_proto_tcp;
            else if (protocol == DnsProtocol.DOT) id = R.id.btn_proto_dot;
            else if (protocol == DnsProtocol.DOH) id = R.id.btn_proto_doh;
            if (protocolGroup.getCheckedButtonId() != id) {
                protocolGroup.check(id);
            }
        }

        boolean udpOrTcp = protocol == DnsProtocol.UDP || protocol == DnsProtocol.TCP;
        dns2Layout.setVisibility(udpOrTcp ? View.VISIBLE : View.GONE);
        ipv6Dns1Layout.setVisibility(protocol == DnsProtocol.UDP ? View.VISIBLE : View.GONE);
        ipv6Dns2Layout.setVisibility(protocol == DnsProtocol.UDP ? View.VISIBLE : View.GONE);
        hostnameLayout.setVisibility(protocol == DnsProtocol.DOT ? View.VISIBLE : View.GONE);
        dohUrlLayout.setVisibility(protocol == DnsProtocol.DOH ? View.VISIBLE : View.GONE);
        portLayout.setVisibility(protocol == DnsProtocol.DOT || protocol == DnsProtocol.TCP ? View.VISIBLE : View.GONE);

        if (isEn()) {
            dns1Layout.setHint(protocol == DnsProtocol.DOH ? "Bootstrap IP" : "Primary DNS / IP");
            dns2Layout.setHint("Secondary DNS (Optional)");
            hostnameLayout.setHint("DoT hostname (SNI)");
            dohUrlLayout.setHint("DoH URL");
            portLayout.setHint(protocol == DnsProtocol.DOT ? "DoT port (853)" : "TCP port (53)");
            ipv6Dns1Layout.setHint("IPv6 Primary DNS");
            ipv6Dns2Layout.setHint("IPv6 Secondary DNS");
            if (protocolSubtitle != null) {
                if (protocol == DnsProtocol.UDP) protocolSubtitle.setText("UDP · classic DNS on port 53");
                else if (protocol == DnsProtocol.TCP) protocolSubtitle.setText("TCP · DNS over TCP, harder to filter");
                else if (protocol == DnsProtocol.DOT) protocolSubtitle.setText("DoT · DNS over TLS on port 853");
                else protocolSubtitle.setText("DoH · DNS over HTTPS, looks like web traffic");
            }
        } else {
            dns1Layout.setHint(protocol == DnsProtocol.DOH ? "آی‌پی بوت‌استرپ" : "DNS / آی‌پی اول");
            dns2Layout.setHint("DNS دوم (اختیاری)");
            hostnameLayout.setHint("نام میزبان DoT");
            dohUrlLayout.setHint("آدرس DoH");
            portLayout.setHint(protocol == DnsProtocol.DOT ? "پورت DoT (۸۵۳)" : "پورت TCP (۵۳)");
            ipv6Dns1Layout.setHint("DNS اول IPv6");
            ipv6Dns2Layout.setHint("DNS دوم IPv6");
            if (protocolSubtitle != null) {
                if (protocol == DnsProtocol.UDP) protocolSubtitle.setText("UDP · دی‌ان‌اس کلاسیک روی پورت ۵۳");
                else if (protocol == DnsProtocol.TCP) protocolSubtitle.setText("TCP · دی‌ان‌اس روی TCP، سخت‌تر فیلتر می‌شود");
                else if (protocol == DnsProtocol.DOT) protocolSubtitle.setText("DoT · دی‌ان‌اس رمزنگاری‌شده روی پورت ۸۵۳");
                else protocolSubtitle.setText("DoH · دی‌ان‌اس روی HTTPS مثل ترافیک وب");
            }
        }

        if (portEditText != null && (portEditText.getText() == null || portEditText.getText().toString().trim().isEmpty())) {
            portEditText.setText(String.valueOf(protocol.defaultPort));
        }
    }

    private void setupTextSwitcher() {
        if (textHintSwitcher == null) return;
        textHintSwitcher.setFactory(new ViewSwitcher.ViewFactory() {
            @Override
            public View makeView() {
                TextView textView = new TextView(getApplicationContext());
                textView.setTextSize(14);
                textView.setTextColor(ThemeManager.getDefaultTextColor(DnschangerActivity.this));
                textView.setGravity(Gravity.CENTER);
                textView.setLineSpacing(4f, 1.1f);
                return textView;
            }
        });
        hintRunnable = new Runnable() {
            @Override
            public void run() {
                String[] currentArray = isEn() ? hintsEn : hintsFa;
                currentHintIndex++;
                if (currentHintIndex >= currentArray.length) currentHintIndex = 0;
                textHintSwitcher.setText(currentArray[currentHintIndex]);
                hintHandler.postDelayed(this, HINT_SLIDE_INTERVAL);
            }
        };
    }

    private void showCurrentHint() {
        if (textHintSwitcher == null) return;
        String[] currentArray = isEn() ? hintsEn : hintsFa;
        if (currentHintIndex >= currentArray.length) currentHintIndex = 0;
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
        dns1EditText.setText(prefs.getString("dns1", "78.157.42.101"));
        dns2EditText.setText(prefs.getString("dns2", "78.157.42.100"));
        ipv6Dns1EditText.setText(prefs.getString("ipv6_dns1", ""));
        ipv6Dns2EditText.setText(prefs.getString("ipv6_dns2", ""));
        if (hostnameEditText != null) hostnameEditText.setText(prefs.getString("dot_hostname", ""));
        if (dohUrlEditText != null) dohUrlEditText.setText(prefs.getString("doh_url", ""));
        if (portEditText != null) portEditText.setText(prefs.getString("dns_port", String.valueOf(currentProtocol.defaultPort)));
    }

    private void setupButtonListeners() {
        vpnButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (!vpnActive) {
                        setButtonLoading(true);
                        if (isRootMode) changeDnsWithRoot();
                        else checkAndRequestVpnPermissions();
                    } else {
                        setButtonLoading(true);
                        stopDnsConnection();
                    }
                    if (!isRootMode) {
                        scheduleConnectTimeout();
                    }
                } catch (Exception e) {
                    setButtonLoading(false);
                    showCustomToast(e.getMessage(), R.drawable.ic_error);
                }
            }
        });

        selectServerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (vpnActive) {
                    showToast(isEn() ? "Please disconnect first" : "لطفاً ابتدا اتصال را قطع کنید");
                    return;
                }
                persistCurrentFields();
                Intent intent = new Intent(DnschangerActivity.this, ServerselectionActivity.class);
                intent.putExtra("protocol", currentProtocol.name());
                startActivityForResult(intent, SERVER_PICK_CODE);
            }
        });

        settingsImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (vpnActive) {
                    showToast(isEn() ? "Please disconnect first to change settings" : "لطفاً ابتدا اتصال را قطع کنید");
                } else {
                    startActivity(new Intent(DnschangerActivity.this, SettingsActivity.class));
                }
            }
        });

        logoImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(DnschangerActivity.this, DnsLogActivity.class));
            }
        });

        if (protocolGroup != null) {
            protocolGroup.addOnButtonCheckedListener(new MaterialButtonToggleGroup.OnButtonCheckedListener() {
                @Override
                public void onButtonChecked(MaterialButtonToggleGroup group, int checkedId, boolean isChecked) {
                    if (!isChecked) return;
                    if (vpnActive) {
                        showToast(isEn() ? "Disconnect first to change protocol" : "برای تغییر پروتکل ابتدا قطع کنید");
                        applyProtocolUi(currentProtocol, false);
                        return;
                    }
                    DnsProtocol next = DnsProtocol.UDP;
                    if (checkedId == R.id.btn_proto_tcp) next = DnsProtocol.TCP;
                    else if (checkedId == R.id.btn_proto_dot) next = DnsProtocol.DOT;
                    else if (checkedId == R.id.btn_proto_doh) next = DnsProtocol.DOH;
                    if (portEditText != null) portEditText.setText(String.valueOf(next.defaultPort));
                    applyProtocolUi(next, true);
                }
            });
        }
    }

    private void persistCurrentFields() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("dns1", textOf(dns1EditText));
        editor.putString("dns2", textOf(dns2EditText));
        editor.putString("ipv6_dns1", textOf(ipv6Dns1EditText));
        editor.putString("ipv6_dns2", textOf(ipv6Dns2EditText));
        editor.putString("dot_hostname", textOf(hostnameEditText));
        editor.putString("doh_url", textOf(dohUrlEditText));
        editor.putString("dns_port", textOf(portEditText));
        editor.putString("dns_protocol", currentProtocol.name());
        editor.apply();
    }

    private String textOf(TextInputEditText edit) {
        if (edit == null || edit.getText() == null) return "";
        return edit.getText().toString().trim();
    }

    private void checkAndRequestVpnPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_CODE);
                return;
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!NotificationHelper.areNotificationsEnabled(this)) {
                openNotificationSettings();
                return;
            }
        }
        checkVpnPermission();
    }

    private void checkVpnPermission() {
        Intent vpnIntent = VpnService.prepare(this);
        if (vpnIntent != null) {
            startActivityForResult(vpnIntent, VPN_PERMISSION_REQUEST_CODE);
        } else {
            startVpn();
        }
    }

    private void openNotificationSettings() {
        new MaterialAlertDialogBuilder(this, R.style.CustomDialogTheme)
                .setTitle(isEn() ? "Notification Permission Required" : "مجوز اعلانات مورد نیاز است")
                .setMessage(isEn()
                        ? "Please enable notifications for this app to show VPN status."
                        : "لطفاً نوتیفیکیشن‌های این برنامه را برای نمایش وضعیت VPN فعال کنید.")
                .setPositiveButton(isEn() ? "Open Settings" : "باز کردن تنظیمات", (dialog, which) -> {
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
                    abortConnectingUi();
                })
                .setNegativeButton(isEn() ? "Cancel" : "لغو", (dialog, which) -> abortConnectingUi())
                .setOnCancelListener(dialog -> abortConnectingUi())
                .setBackground(getResources().getDrawable(R.drawable.dialog_background))
                .show();
    }

    private void changeDnsWithRoot() {
        String dns1 = textOf(dns1EditText);
        if (TextUtils.isEmpty(dns1)) {
            showToast(isEn() ? "Please enter primary DNS" : "لطفاً DNS اول را وارد کنید");
            abortConnectingUi();
            return;
        }
        persistCurrentFields();
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean success = RootCommands.changeDns(dns1, textOf(dns2EditText),
                        textOf(ipv6Dns1EditText), textOf(ipv6Dns2EditText));
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setButtonLoading(false);
                        if (success) {
                            vpnActive = true;
                            prefs.edit().putBoolean("vpn_active", true).apply();
                            updateButton();
                            startLivePingUpdates();
                            showCustomToast(isEn() ? "DNS changed successfully" : "DNS با موفقیت تغییر کرد", R.drawable.ic_check);
                        } else {
                            updateButton();
                            showCustomToast(isEn() ? "Failed to change DNS" : "تغییر DNS ناموفق بود", R.drawable.ic_error);
                        }
                    }
                });
            }
        }).start();
    }

    private void stopDnsConnection() {
        if (isRootMode) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    boolean success = RootCommands.restoreOriginalDns();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setButtonLoading(false);
                            if (success) {
                                vpnActive = false;
                                prefs.edit().putBoolean("vpn_active", false).apply();
                                updateButton();
                                stopLivePingUpdates();
                                showCustomToast(isEn() ? "DNS restored successfully" : "DNS با موفقیت بازگردانی شد", R.drawable.ic_check);
                            } else {
                                updateButton();
                                showCustomToast(isEn() ? "Failed to restore DNS" : "بازگردانی DNS ناموفق بود", R.drawable.ic_error);
                            }
                        }
                    });
                }
            }).start();
        } else {
            stopVpn();
        }
    }

    private void performPingCheck() {
        String dns = textOf(dns1EditText);
        boolean dohReady = currentProtocol == DnsProtocol.DOH && !textOf(dohUrlEditText).isEmpty();
        boolean dotReady = currentProtocol == DnsProtocol.DOT && !textOf(hostnameEditText).isEmpty();

        if (dns.isEmpty() && !dohReady && !dotReady) {
            pingTextView.setText(isEn() ? "No DNS set" : "DNS وارد نشده");
            return;
        }
        if (!dns.isEmpty() && !HostValidator.isSafeHost(dns) && currentProtocol != DnsProtocol.DOH) {
            pingTextView.setText(isEn() ? "Invalid host" : "آدرس نامعتبر");
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                int pingValue = measureDnsLatency(dns);
                final boolean success = pingValue >= 0;
                final String resultText = success
                        ? ((isEn() ? "Ping: " : "پینگ: ") + pingValue + "ms · " + currentProtocol.label)
                        : (isEn() ? "Ping failed · " + currentProtocol.label : "پینگ ناموفق · " + currentProtocol.label);
                final int finalPing = pingValue;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        pingTextView.setText(resultText);
                        if (success) {
                            if (finalPing < 80) pingTextView.setTextColor(Color.parseColor("#4CAF50"));
                            else if (finalPing < 120) pingTextView.setTextColor(Color.parseColor("#FFC107"));
                            else if (finalPing <= 200) pingTextView.setTextColor(Color.parseColor("#FF632e"));
                            else pingTextView.setTextColor(Color.parseColor("#FF0000"));
                        } else {
                            pingTextView.setTextColor(ThemeManager.getDefaultTextColor(DnschangerActivity.this));
                        }
                    }
                });
            }
        }).start();
    }

    private int measureDnsLatency(String dns) {
        try {
            DnsQueryEngine engine = new DnsQueryEngine(null, getApplicationContext(), currentProtocol,
                    dns, textOf(dns2EditText), textOf(hostnameEditText), textOf(dohUrlEditText), parsePort());
            return engine.measureLatencyMs();
        } catch (Exception e) {
            return pingIcmpOrTcp(dns);
        }
    }

    private int pingIcmpOrTcp(String dns) {
        if (!HostValidator.isSafeHost(dns)) return -2;
        try {
            ProcessBuilder pb = new ProcessBuilder("ping", "-c", "1", "-W", "2", dns);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            java.io.InputStream inputStream = process.getInputStream();
            java.util.Scanner s = new java.util.Scanner(inputStream).useDelimiter("\\A");
            String output = s.hasNext() ? s.next() : "";
            s.close();
            int resultCode = process.waitFor();
            if (resultCode == 0 && output.contains("time=")) {
                int index = output.indexOf("time=");
                int end = output.indexOf(" ms", index);
                if (index > 0 && end > index) {
                    return (int) Float.parseFloat(output.substring(index + 5, end));
                }
            }
        } catch (Exception ignored) {}
        try {
            long startTime = System.currentTimeMillis();
            java.net.Socket socket = new java.net.Socket();
            socket.connect(new java.net.InetSocketAddress(dns, currentProtocol.defaultPort), 2000);
            socket.close();
            return (int) (System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            return -2;
        }
    }

    private int parsePort() {
        try {
            int p = Integer.parseInt(textOf(portEditText));
            if (p > 0 && p < 65536) return p;
        } catch (Exception ignored) {}
        return currentProtocol.defaultPort;
    }

    private void setButtonLoading(boolean loading) {
        connecting = loading;
        if (vpnButtonProgress != null) {
            vpnButtonProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (vpnButton != null) {
            vpnButton.setEnabled(!loading);
            if (loading) {
                vpnButton.setText("");
                vpnButton.setIcon(null);
            }
        }
        if (loading) {
            if (selectServerButton != null) {
                selectServerButton.setEnabled(false);
                selectServerButton.setAlpha(0.5f);
            }
            if (protocolGroup != null) protocolGroup.setEnabled(false);
        }
    }

    private void abortConnectingUi() {
        cancelConnectTimeout();
        setButtonLoading(false);
        updateButton();
    }

    private void scheduleConnectTimeout() {
        cancelConnectTimeout();
        connectTimeoutRunnable = new Runnable() {
            @Override
            public void run() {
                if (connecting) {
                    connecting = false;
                    setButtonLoading(false);
                    updateButton();
                    showCustomToast(isEn() ? "Connection timed out, please try again"
                            : "اتصال بیش از حد طول کشید، دوباره تلاش کنید", R.drawable.ic_error);
                }
            }
        };
        pingHandler.postDelayed(connectTimeoutRunnable, CONNECT_TIMEOUT_MS);
    }

    private void cancelConnectTimeout() {
        if (connectTimeoutRunnable != null) {
            pingHandler.removeCallbacks(connectTimeoutRunnable);
            connectTimeoutRunnable = null;
        }
    }

    private void updateButton() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (vpnActive) {
                    vpnButton.setText(isEn()
                            ? (isRootMode ? "Restore DNS" : "Disable " + currentProtocol.label)
                            : (isRootMode ? "بازگردانی DNS" : "قطع " + currentProtocol.label));
                    vpnButton.setIconResource(R.drawable.ic_vpn_off);
                    selectServerButton.setEnabled(false);
                    selectServerButton.setAlpha(0.5f);
                    protocolGroup.setEnabled(false);
                    pingTextView.setText(isEn() ? "Status: Connected" : "وضعیت: متصل");
                    pingTextView.setTextColor(Color.parseColor("#4CAF50"));
                } else {
                    vpnButton.setText(isEn()
                            ? (isRootMode ? "Change DNS" : "Enable " + currentProtocol.label)
                            : (isRootMode ? "تغییر DNS" : "فعال کردن " + currentProtocol.label));
                    vpnButton.setIconResource(R.drawable.ic_vpn_on);
                    pingTextView.setText(isEn() ? "Status: Not connected" : "وضعیت: متصل نیست");
                    pingTextView.setTextColor(ThemeManager.getDefaultTextColor(DnschangerActivity.this));
                    selectServerButton.setEnabled(true);
                    selectServerButton.setAlpha(1.0f);
                    protocolGroup.setEnabled(true);
                }
            }
        });
    }

    private void startVpn() {
        String dns1 = textOf(dns1EditText);

        boolean dotHasHostname = currentProtocol == DnsProtocol.DOT && !textOf(hostnameEditText).isEmpty();
        if (TextUtils.isEmpty(dns1) && currentProtocol != DnsProtocol.DOH && !dotHasHostname) {
            showToast(isEn() ? "Please enter primary DNS" : "لطفاً DNS اول را وارد کنید");
            abortConnectingUi();
            return;
        }
        if (currentProtocol == DnsProtocol.DOH && textOf(dohUrlEditText).isEmpty()) {
            showToast(isEn() ? "Please enter a DoH URL" : "لطفاً آدرس DoH را وارد کنید");
            abortConnectingUi();
            return;
        }
        if (currentProtocol == DnsProtocol.DOT && textOf(hostnameEditText).isEmpty() && dns1.isEmpty()) {
            showToast(isEn() ? "Enter DoT hostname or IP" : "نام میزبان یا آی‌پی DoT را وارد کنید");
            abortConnectingUi();
            return;
        }
        persistCurrentFields();
        connecting = true;
        pingTextView.setText(isEn() ? "Connecting…" : "در حال اتصال…");

        Intent intent = new Intent(this, MyVpnService.class);
        intent.putExtra("dns1", dns1);
        intent.putExtra("dns2", textOf(dns2EditText));
        intent.putExtra("ipv6_dns1", textOf(ipv6Dns1EditText));
        intent.putExtra("ipv6_dns2", textOf(ipv6Dns2EditText));
        intent.putExtra("protocol", currentProtocol.name());
        intent.putExtra("hostname", textOf(hostnameEditText));
        intent.putExtra("doh_url", textOf(dohUrlEditText));
        intent.putExtra("dns_port", parsePort());
        intent.putExtra("use_dhcp", prefs.getBoolean("use_dhcp", false));
        intent.putExtra("ipv4", prefs.getString("ipv4_address", "10.0.0.2"));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void stopVpn() {
        Intent intent = new Intent(this, MyVpnService.class);
        intent.setAction(MyVpnService.ACTION_STOP_VPN);
        startService(intent);

    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void showCustomToast(String message, int iconRes) {
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
        if (requestCode == SERVER_PICK_CODE && resultCode == RESULT_OK && data != null) {
            dns1EditText.setText(n(data.getStringExtra("dns1")));
            dns2EditText.setText(n(data.getStringExtra("dns2")));
            ipv6Dns1EditText.setText(n(data.getStringExtra("ipv6_dns1")));
            ipv6Dns2EditText.setText(n(data.getStringExtra("ipv6_dns2")));
            if (hostnameEditText != null) hostnameEditText.setText(n(data.getStringExtra("hostname")));
            if (dohUrlEditText != null) dohUrlEditText.setText(n(data.getStringExtra("doh_url")));
            int port = data.getIntExtra("dns_port", currentProtocol.defaultPort);
            if (portEditText != null) portEditText.setText(String.valueOf(port));
            String proto = data.getStringExtra("protocol");
            if (proto != null) applyProtocolUi(DnsProtocol.from(proto), true);
            persistCurrentFields();
        } else if (requestCode == VPN_PERMISSION_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                startVpn();
            } else {
                showToast(isEn() ? "VPN permission is required to change DNS" : "مجوز VPN برای تغییر DNS ضروری است");
                abortConnectingUi();
            }
        }
    }

    private String n(String s) { return s == null ? "" : s; }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkVpnPermission();
            } else {
                showToast(isEn() ? "Notification permission is required for VPN service" : "مجوز اعلانات برای سرویس VPN ضروری است");
                abortConnectingUi();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isReceiverRegistered) {
            IntentFilter filter = new IntentFilter(MyVpnService.ACTION_STATE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(vpnStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(vpnStateReceiver, filter);
            }
            isReceiverRegistered = true;
        }
        currentProtocol = DnsProtocol.from(prefs.getString("dns_protocol", currentProtocol.name()));
        applyProtocolUi(currentProtocol, false);
        refreshVpnStatus();
        startHintTimer();
    }

    @Override
    protected void onPause() {
        super.onPause();
        persistCurrentFields();
        stopLivePingUpdates();
        stopHintTimer();
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(vpnStateReceiver);
                isReceiverRegistered = false;
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelConnectTimeout();
        stopLivePingUpdates();
        stopHintTimer();
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(vpnStateReceiver);
                isReceiverRegistered = false;
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) refreshVpnStatus();
    }

    private void refreshVpnStatus() {
        if (connecting) return;
        isRootMode = prefs.getBoolean("root_mode", false);
        if (isRootMode) {
            String savedDns1 = prefs.getString("dns1", "");
            vpnActive = prefs.getBoolean("vpn_active", false)
                    && !savedDns1.isEmpty()
                    && RootCommands.isDnsChanged(savedDns1);
        } else {
            vpnActive = prefs.getBoolean("vpn_active", false) && MyVpnService.isRunning(this);
        }
        updateButton();
        if (vpnActive) startLivePingUpdates();
        else stopLivePingUpdates();
    }
}
