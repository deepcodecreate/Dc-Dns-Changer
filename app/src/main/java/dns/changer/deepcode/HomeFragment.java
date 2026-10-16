package dns.changer.deepcode;

import android.Manifest;
import android.app.Activity;
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
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TextSwitcher;
import android.widget.ViewSwitcher;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Scanner;

public class HomeFragment extends Fragment {

    private static final int PING_INTERVAL = 4000;
    private static final int HINT_SLIDE_INTERVAL = 8000;
    private static final int CONNECT_TIMEOUT_MS = 15000;

    private TextInputEditText dns1EditText, dns2EditText, ipv6Dns1EditText, ipv6Dns2EditText;
    private TextInputEditText hostnameEditText, dohUrlEditText, portEditText;
    private TextInputLayout dns1Layout, dns2Layout, ipv6Dns1Layout, ipv6Dns2Layout;
    private TextInputLayout hostnameLayout, dohUrlLayout, portLayout;
    private MaterialButton vpnButton, selectServerButton;
    private ExpressPowerArcView powerArcView;
    private ImageView vpnButtonIcon;
    private CircularProgressIndicator vpnButtonProgress;
    private Runnable connectTimeoutRunnable;
    private MaterialButtonToggleGroup protocolGroup;
    private TextView pingTextView, protocolSubtitle, configTitle, connectionTitle, heroStatusTitle;
    private ImageView logoImage, settingsImage;
    private boolean vpnActive = false;
    private boolean isRootMode = false;
    private SharedPreferences prefs;
    private final Handler pingHandler = new Handler();
    private Runnable pingRunnable;
    private boolean isReceiverRegistered = false;
    private boolean connecting = false;

    private TextSwitcher textHintSwitcher;
    private final Handler hintHandler = new Handler();
    private Runnable hintRunnable;
    private int currentHintIndex = 0;
    private DnsProtocol currentProtocol = DnsProtocol.UDP;

    private ActivityResultLauncher<Intent> vpnPermissionLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;

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

    private final BroadcastReceiver vpnStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (MyVpnService.ACTION_STATE.equals(intent.getAction())) {
                boolean newState = intent.getBooleanExtra("isActive", false);
                vpnActive = newState;
                connecting = false;
                cancelConnectTimeout();
                prefs.edit().putBoolean("vpn_active", vpnActive).apply();
                final String error = intent.getStringExtra("error");
                requireActivity().runOnUiThread(() -> {
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
                });
            }
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = requireContext().getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE);
        isRootMode = prefs.getBoolean("root_mode", false);
        currentProtocol = DnsProtocol.from(prefs.getString("dns_protocol",
                prefs.getBoolean("dns_over_tcp", false) ? "TCP" : "UDP"));

        if (isRootMode) {
            String savedDns1 = prefs.getString("dns1", "");
            vpnActive = prefs.getBoolean("vpn_active", false)
                    && !savedDns1.isEmpty()
                    && RootCommands.isDnsChanged(savedDns1);
        } else {
            vpnActive = prefs.getBoolean("vpn_active", false) && MyVpnService.isRunning(requireContext());
        }

        pingRunnable = new Runnable() {
            @Override
            public void run() {
                if (vpnActive) performPingCheck();
                pingHandler.postDelayed(this, PING_INTERVAL);
            }
        };

        vpnPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        startVpn();
                    } else {
                        showToast(isEn() ? "VPN permission is required to change DNS" : "مجوز VPN برای تغییر DNS ضروری است");
                        abortConnectingUi();
                    }
                });

        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted) {
                        checkVpnPermission();
                    } else {
                        showToast(isEn() ? "Notification permission is required for VPN service" : "مجوز اعلانات برای سرویس VPN ضروری است");
                        abortConnectingUi();
                    }
                });

        getParentFragmentManager().setFragmentResultListener("server_selected", this, (requestKey, bundle) -> {
            dns1EditText.setText(n(bundle.getString("dns1")));
            dns2EditText.setText(n(bundle.getString("dns2")));
            ipv6Dns1EditText.setText(n(bundle.getString("ipv6_dns1")));
            ipv6Dns2EditText.setText(n(bundle.getString("ipv6_dns2")));
            if (hostnameEditText != null) hostnameEditText.setText(n(bundle.getString("hostname")));
            if (dohUrlEditText != null) dohUrlEditText.setText(n(bundle.getString("doh_url")));
            int port = bundle.getInt("dns_port", currentProtocol.defaultPort);
            if (portEditText != null) portEditText.setText(String.valueOf(port));
            String proto = bundle.getString("protocol");
            if (proto != null) applyProtocolUi(DnsProtocol.from(proto), true);
            persistCurrentFields();

            if (bundle.getBoolean("disconnect_first", false)) {
                disconnectForServerSwitch();
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializeViews(view);
        if (powerArcView != null) {
            int accent = ThemeManager.resolveColor(requireContext(), android.R.attr.colorAccent);
            if (accent == 0) accent = 0xFF7C8CFF;
            int inactive = ThemeManager.isLight(requireContext()) ? 0x33000000 : 0x33FFFFFF;
            powerArcView.setThemeColors(accent, inactive);
        }
        ThemeManager.styleToggleGroup(requireContext(), protocolGroup);
        setupTextSwitcher();
        loadSavedPreferences();
        applyProtocolUi(currentProtocol, false);
        setupButtonListeners();
        updateUIForLanguage();
        updateButton();
    }

    private boolean isEn() {
        return prefs.getBoolean("english_language", false);
    }

    private void startLivePingUpdates() {
        pingHandler.removeCallbacks(pingRunnable);
        pingTextView.setText(isEn() ? "Ping: Checking..." : "پینگ: در حال بررسی...");
        pingTextView.setTextColor(ThemeManager.getDefaultTextColor(requireContext()));
        pingHandler.postDelayed(pingRunnable, 400);
    }

    private void stopLivePingUpdates() {
        pingHandler.removeCallbacks(pingRunnable);
        if (pingTextView == null) return;
        pingTextView.setText(isEn() ? "Status: Not connected" : "وضعیت: متصل نیست");
        pingTextView.setTextColor(ThemeManager.getDefaultTextColor(requireContext()));
    }

    private void updateUIForLanguage() {
        pingTextView.setText(vpnActive
                ? (isEn() ? "Status: Connected" : "وضعیت: متصل")
                : (isEn() ? "Status: Not connected" : "وضعیت: متصل نیست"));
        if (selectServerButton != null) {
            selectServerButton.setText(isEn() ? "Select recommended server" : "انتخاب سرور پیشنهادی");
        }
        if (configTitle != null) configTitle.setText(isEn() ? "DNS protection" : "محافظت DNS");
        if (connectionTitle != null) connectionTitle.setText(isEn() ? "DNS configuration" : "پیکربندی DNS");
        applyProtocolUi(currentProtocol, false);
        showCurrentHint();
    }

    private void initializeViews(View view) {
        dns1EditText = view.findViewById(R.id.dns1_edittext);
        dns2EditText = view.findViewById(R.id.dns2_edittext);
        ipv6Dns1EditText = view.findViewById(R.id.ipv6_dns1_edittext);
        ipv6Dns2EditText = view.findViewById(R.id.ipv6_dns2_edittext);
        hostnameEditText = view.findViewById(R.id.hostname_edittext);
        dohUrlEditText = view.findViewById(R.id.doh_url_edittext);
        portEditText = view.findViewById(R.id.port_edittext);

        dns1Layout = view.findViewById(R.id.dns1_layout);
        dns2Layout = view.findViewById(R.id.dns2_layout);
        ipv6Dns1Layout = view.findViewById(R.id.ipv6_dns1_layout);
        ipv6Dns2Layout = view.findViewById(R.id.ipv6_dns2_layout);
        hostnameLayout = view.findViewById(R.id.hostname_layout);
        dohUrlLayout = view.findViewById(R.id.doh_url_layout);
        portLayout = view.findViewById(R.id.port_layout);

        vpnButton = view.findViewById(R.id.vpn_button);
        powerArcView = view.findViewById(R.id.power_arc_view);
        vpnButtonIcon = view.findViewById(R.id.vpn_button_icon);
        vpnButtonProgress = view.findViewById(R.id.vpn_button_progress);
        selectServerButton = view.findViewById(R.id.select_server_button);
        settingsImage = view.findViewById(R.id.settingsimage);
        pingTextView = view.findViewById(R.id.ping_textview);
        logoImage = view.findViewById(R.id.logo_image);
        textHintSwitcher = view.findViewById(R.id.text_hint_switcher);
        protocolGroup = view.findViewById(R.id.protocol_group);
        protocolSubtitle = view.findViewById(R.id.protocol_subtitle);
        configTitle = view.findViewById(R.id.config_title);
        connectionTitle = view.findViewById(R.id.connection_title);
        heroStatusTitle = view.findViewById(R.id.hero_status_title);
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
        textHintSwitcher.setFactory(() -> {
            TextView textView = new TextView(requireContext().getApplicationContext());
            textView.setTextSize(14);
            textView.setTextColor(ThemeManager.getDefaultTextColor(requireContext()));
            textView.setGravity(Gravity.CENTER);
            textView.setLineSpacing(4f, 1.1f);
            return textView;
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
        if (hintRunnable == null) return;
        hintHandler.removeCallbacks(hintRunnable);
        hintHandler.postDelayed(hintRunnable, HINT_SLIDE_INTERVAL);
    }

    private void stopHintTimer() {
        if (hintRunnable == null) return;
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
        vpnButton.setOnClickListener(v -> {
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
        });

        if (protocolGroup != null) {
            protocolGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
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
            });
        }
    }

    private void disconnectForServerSwitch() {
        isRootMode = prefs.getBoolean("root_mode", false);
        setButtonLoading(true);
        stopDnsConnection();
        cancelConnectTimeout();

        connectTimeoutRunnable = () -> {
            if (connecting) {
                connecting = false;
                setButtonLoading(false);
                refreshVpnStatus();
            }
        };
        pingHandler.postDelayed(connectTimeoutRunnable, 5000);
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
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                return;
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!NotificationHelper.areNotificationsEnabled(requireContext())) {
                openNotificationSettings();
                return;
            }
        }
        checkVpnPermission();
    }

    private void checkVpnPermission() {
        Intent vpnIntent = VpnService.prepare(requireContext());
        if (vpnIntent != null) {
            vpnPermissionLauncher.launch(vpnIntent);
        } else {
            startVpn();
        }
    }

    private void openNotificationSettings() {
        new MaterialAlertDialogBuilder(requireContext(), R.style.CustomDialogTheme)
                .setTitle(isEn() ? "Notification Permission Required" : "مجوز اعلانات مورد نیاز است")
                .setMessage(isEn()
                        ? "Please enable notifications for this app to show VPN status."
                        : "لطفاً نوتیفیکیشن‌های این برنامه را برای نمایش وضعیت VPN فعال کنید.")
                .setPositiveButton(isEn() ? "Open Settings" : "باز کردن تنظیمات", (dialog, which) -> {
                    Intent intent = new Intent();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                        intent.putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
                    } else {
                        intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
                        intent.putExtra("app_package", requireContext().getPackageName());
                        intent.putExtra("app_uid", requireContext().getApplicationInfo().uid);
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
        new Thread(() -> {
            boolean success = RootCommands.changeDns(dns1, textOf(dns2EditText),
                    textOf(ipv6Dns1EditText), textOf(ipv6Dns2EditText));
            requireActivity().runOnUiThread(() -> {
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
            });
        }).start();
    }

    private void stopDnsConnection() {
        if (isRootMode) {
            new Thread(() -> {
                boolean success = RootCommands.restoreOriginalDns();
                requireActivity().runOnUiThread(() -> {
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
                });
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
        new Thread(() -> {
            int pingValue = measureDnsLatency(dns);
            final boolean success = pingValue >= 0;
            final String resultText = success
                    ? ((isEn() ? "Ping: " : "پینگ: ") + pingValue + "ms · " + currentProtocol.label)
                    : (isEn() ? "Ping failed · " + currentProtocol.label : "پینگ ناموفق · " + currentProtocol.label);
            final int finalPing = pingValue;
            if (getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
                if (pingTextView == null) return;
                pingTextView.setText(resultText);
                if (success) {
                    if (finalPing < 80) pingTextView.setTextColor(Color.parseColor("#4CAF50"));
                    else if (finalPing < 120) pingTextView.setTextColor(Color.parseColor("#FFC107"));
                    else if (finalPing <= 200) pingTextView.setTextColor(Color.parseColor("#FF632e"));
                    else pingTextView.setTextColor(Color.parseColor("#FF0000"));
                } else {
                    pingTextView.setTextColor(ThemeManager.getDefaultTextColor(requireContext()));
                }
            });
        }).start();
    }

    private int measureDnsLatency(String dns) {
        try {
            DnsQueryEngine engine = new DnsQueryEngine(null, requireContext().getApplicationContext(), currentProtocol,
                    dns, textOf(dns2EditText), textOf(hostnameEditText), textOf(dohUrlEditText), parsePort());

            engine.setLoggingEnabled(false);
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
            InputStream inputStream = process.getInputStream();
            Scanner s = new Scanner(inputStream).useDelimiter("\\A");
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
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(dns, currentProtocol.defaultPort), 2000);
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
        if (powerArcView != null && loading) {
            powerArcView.setState(ExpressPowerArcView.STATE_CONNECTING);
        }
        if (vpnButtonProgress != null) {
            vpnButtonProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (vpnButton != null) {
            vpnButton.setEnabled(!loading);
            vpnButton.setText("");
        }
        if (vpnButtonIcon != null) {
            vpnButtonIcon.animate().cancel();
            vpnButtonIcon.animate()
                    .alpha(loading ? 0f : 1f)
                    .scaleX(loading ? 0.7f : 1f)
                    .scaleY(loading ? 0.7f : 1f)
                    .setDuration(150)
                    .withEndAction(() -> vpnButtonIcon.setVisibility(loading ? View.INVISIBLE : View.VISIBLE))
                    .start();
        }
        if (vpnButtonProgress != null && loading) {
            vpnButtonProgress.setAlpha(0f);
            vpnButtonProgress.animate().alpha(1f).setDuration(200).start();
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
        connectTimeoutRunnable = () -> {
            if (connecting) {
                connecting = false;
                setButtonLoading(false);
                updateButton();
                showCustomToast(isEn() ? "Connection timed out, please try again"
                        : "اتصال بیش از حد طول کشید، دوباره تلاش کنید", R.drawable.ic_error);
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

    private void animateIconSwap(ImageView icon, int newIconRes) {
        if (icon == null) return;
        icon.animate().cancel();
        icon.animate()
                .scaleX(0.55f).scaleY(0.55f).alpha(0f)
                .setDuration(110)
                .withEndAction(() -> {
                    icon.setImageResource(newIconRes);
                    icon.setVisibility(View.VISIBLE);
                    icon.animate()
                            .scaleX(1f).scaleY(1f).alpha(1f)
                            .setDuration(220)
                            .setInterpolator(new OvershootInterpolator(1.8f))
                            .start();
                }).start();
    }

    private void animateTextCrossfade(TextView textView, String newText) {
        if (textView == null) return;
        if (newText.equals(textView.getText().toString())) return;
        textView.animate().cancel();
        textView.animate().alpha(0f).setDuration(90).withEndAction(() -> {
            textView.setText(newText);
            textView.animate().alpha(1f).setDuration(180).start();
        }).start();
    }

    private void updateButton() {
        if (getActivity() == null) return;
        requireActivity().runOnUiThread(() -> {
            if (vpnButton == null) return;
            if (vpnActive) {
                if (powerArcView != null) {
                    powerArcView.setState(ExpressPowerArcView.STATE_CONNECTED);
                }
                vpnButton.setText("");
                if (vpnButtonIcon != null) {
                    animateIconSwap(vpnButtonIcon, R.drawable.ic_vpn_off);
                }
                if (heroStatusTitle != null) {
                    heroStatusTitle.setText(isEn() ? "Protected" : "محافظت فعال");
                }
                if (selectServerButton != null) {
                    selectServerButton.setEnabled(false);
                    selectServerButton.setAlpha(0.5f);
                }
                protocolGroup.setEnabled(false);
                animateTextCrossfade(pingTextView, isEn()
                        ? "Protected · " + currentProtocol.label
                        : "محافظت فعال · " + currentProtocol.label);
                pingTextView.setTextColor(Color.parseColor("#4CAF50"));
            } else {
                if (powerArcView != null && !connecting) {
                    powerArcView.setState(ExpressPowerArcView.STATE_DISCONNECTED);
                }
                vpnButton.setText("");
                if (vpnButtonIcon != null) {
                    animateIconSwap(vpnButtonIcon, R.drawable.ic_vpn_on);
                }
                if (heroStatusTitle != null) {
                    heroStatusTitle.setText(isEn() ? "Not protected" : "محافظت فعال نیست");
                }
                animateTextCrossfade(pingTextView, isEn() ? "Not protected" : "محافظت فعال نیست");
                pingTextView.setTextColor(ThemeManager.getDefaultTextColor(requireContext()));
                if (selectServerButton != null) {
                    selectServerButton.setEnabled(true);
                    selectServerButton.setAlpha(1.0f);
                }
                protocolGroup.setEnabled(true);
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

        Intent intent = new Intent(requireContext(), MyVpnService.class);
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
            requireContext().startForegroundService(intent);
        } else {
            requireContext().startService(intent);
        }
    }

    private void stopVpn() {
        Intent intent = new Intent(requireContext(), MyVpnService.class);
        intent.setAction(MyVpnService.ACTION_STOP_VPN);
        requireContext().startService(intent);
    }

    private void showToast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void showCustomToast(String message, int iconRes) {
        Toast toast = new Toast(requireContext());
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.custom_toast, null);
        TextView text = view.findViewById(R.id.toast_text);
        ImageView icon = view.findViewById(R.id.toast_icon);
        text.setText(message);
        icon.setImageResource(iconRes);
        toast.setView(view);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.show();
    }

    private String n(String s) { return s == null ? "" : s; }

    @Override
    public void onResume() {
        super.onResume();
        if (!isHidden()) onBecomeVisible();
    }

    @Override
    public void onPause() {
        super.onPause();
        onBecomeInvisible();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) onBecomeInvisible();
        else onBecomeVisible();
    }

    private void onBecomeVisible() {
        if (!isReceiverRegistered) {
            IntentFilter filter = new IntentFilter(MyVpnService.ACTION_STATE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requireContext().registerReceiver(vpnStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                requireContext().registerReceiver(vpnStateReceiver, filter);
            }
            isReceiverRegistered = true;
        }
        currentProtocol = DnsProtocol.from(prefs.getString("dns_protocol", currentProtocol.name()));
        applyProtocolUi(currentProtocol, false);
        refreshVpnStatus();
        startHintTimer();
    }

    private void onBecomeInvisible() {
        persistCurrentFields();
        stopLivePingUpdates();
        stopHintTimer();
        if (isReceiverRegistered) {
            try {
                requireContext().unregisterReceiver(vpnStateReceiver);
            } catch (Exception ignored) {}
            isReceiverRegistered = false;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelConnectTimeout();
        stopHintTimer();
        if (isReceiverRegistered) {
            try {
                requireContext().unregisterReceiver(vpnStateReceiver);
            } catch (Exception ignored) {}
            isReceiverRegistered = false;
        }
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
            vpnActive = prefs.getBoolean("vpn_active", false) && MyVpnService.isRunning(requireContext());
        }
        updateButton();
        if (vpnActive) startLivePingUpdates();
        else stopLivePingUpdates();
    }
}
