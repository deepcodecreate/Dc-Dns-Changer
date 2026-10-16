package dns.changer.deepcode;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.graphics.Color;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import com.google.android.material.button.MaterialButton;
import android.content.res.ColorStateList;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.button.MaterialButtonToggleGroup;
import androidx.transition.AutoTransition;
import androidx.transition.TransitionManager;
import java.io.DataOutputStream;
import java.util.Locale;

public class SettingsFragment extends Fragment {

    private MaterialSwitch dhcpSwitch, rootSwitch, langSwitch;
    private MaterialSwitch autoReconnectSwitch;
    private TextInputEditText ipv4Input, logLimitInput;
    private TextInputLayout ipv4Layout, logLimitLayout;
    private MaterialButton saveButton;
    private TextView settingsTitleText;
    private LinearLayout themePickerRow;
    private TextView themePickerLabel, themePickerValue;
    private TextView connectionSectionLabel, protocolLabel;
    private MaterialButtonToggleGroup protocolToggleGroup;
    private TextInputLayout dotHostnameLayout, dohUrlLayout, dnsPortLayout, dnsTimeoutLayout;
    private TextInputEditText dotHostnameInput, dohUrlInput, dnsPortInput, dnsTimeoutInput;
    private ViewGroup settingsContentRoot;
    private View headerVpnMode, headerAppearance, headerConnection, headerAdvanced, headerAbout;
    private View bodyVpnMode, bodyAppearance, bodyConnection, bodyAdvanced, bodyAbout;
    private ImageView chevronVpnMode, chevronAppearance, chevronConnection, chevronAdvanced, chevronAbout;
    private TextView titleVpnMode, titleAppearance, titleAdvanced;
    private SharedPreferences prefs;
    private boolean isEnglish;
    private boolean isBindingSetup = false;
    private ActivityResultLauncher<Intent> themePickerLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = requireContext().getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE);
        themePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == android.app.Activity.RESULT_OK) {
                        requireActivity().recreate();
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializeViews(view);
        setupAccordion();
        loadSettings();
        setupListeners();
        ThemeManager.styleToggleGroup(requireContext(), protocolToggleGroup);
        setupSwitchColors(ThemeManager.byId(ThemeManager.getSelectedId(requireContext())).previewAccent);
        updateThemePickerValue();
        updateUIForLanguage();
    }

    /** Wraps each settings block in its own collapsible, animated card section. */
    private void setupAccordion() {
        setupSection(headerVpnMode, bodyVpnMode, chevronVpnMode, false);
        setupSection(headerAppearance, bodyAppearance, chevronAppearance, false);
        setupSection(headerConnection, bodyConnection, chevronConnection, false);
        setupSection(headerAdvanced, bodyAdvanced, chevronAdvanced, false);
        setupSection(headerAbout, bodyAbout, chevronAbout, false);
    }

    private void setupSection(View header, View body, ImageView chevron, boolean startExpanded) {
        if (header == null || body == null || chevron == null) return;
        body.setVisibility(startExpanded ? View.VISIBLE : View.GONE);
        chevron.setRotation(startExpanded ? 180f : 0f);
        header.setOnClickListener(v -> toggleSection(body, chevron));
    }

    private void toggleSection(View body, ImageView chevron) {
        if (settingsContentRoot != null) {
            AutoTransition transition = new AutoTransition();
            transition.setDuration(240);
            transition.setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f));
            TransitionManager.beginDelayedTransition(settingsContentRoot, transition);
        }
        boolean expanding = body.getVisibility() != View.VISIBLE;
        body.setVisibility(expanding ? View.VISIBLE : View.GONE);
        chevron.animate().rotation(expanding ? 180f : 0f).setDuration(240).start();
    }

    private void initializeViews(View view) {
        settingsContentRoot = view.findViewById(R.id.settings_content_root);

        headerVpnMode = view.findViewById(R.id.header_vpn_mode);
        headerAppearance = view.findViewById(R.id.header_appearance);
        headerConnection = view.findViewById(R.id.header_connection);
        headerAdvanced = view.findViewById(R.id.header_advanced);
        headerAbout = view.findViewById(R.id.header_about);

        bodyVpnMode = view.findViewById(R.id.body_vpn_mode);
        bodyAppearance = view.findViewById(R.id.body_appearance);
        bodyConnection = view.findViewById(R.id.body_connection);
        bodyAdvanced = view.findViewById(R.id.body_advanced);
        bodyAbout = view.findViewById(R.id.body_about);

        chevronVpnMode = view.findViewById(R.id.chevron_vpn_mode);
        chevronAppearance = view.findViewById(R.id.chevron_appearance);
        chevronConnection = view.findViewById(R.id.chevron_connection);
        chevronAdvanced = view.findViewById(R.id.chevron_advanced);
        chevronAbout = view.findViewById(R.id.chevron_about);

        titleVpnMode = view.findViewById(R.id.title_vpn_mode);
        titleAppearance = view.findViewById(R.id.title_appearance);
        titleAdvanced = view.findViewById(R.id.title_advanced);

        dhcpSwitch = view.findViewById(R.id.dhcp_switch);
        rootSwitch = view.findViewById(R.id.root_switch);
        langSwitch = view.findViewById(R.id.lang_switch);
        themePickerRow = view.findViewById(R.id.theme_picker_row);
        themePickerLabel = view.findViewById(R.id.theme_picker_label);
        themePickerValue = view.findViewById(R.id.theme_picker_value);
        ipv4Input = view.findViewById(R.id.ipv4_input);
        logLimitInput = view.findViewById(R.id.log_limit_input);
        ipv4Layout = view.findViewById(R.id.ipv4_layout);
        logLimitLayout = view.findViewById(R.id.log_limit_layout);
        saveButton = view.findViewById(R.id.save_button);
        final TextView aboutTitle = view.findViewById(R.id.about_title);
        final TextView githubLink = view.findViewById(R.id.github_link);
        final TextView telegramLink = view.findViewById(R.id.telegram_link);
        settingsTitleText = view.findViewById(R.id.settings_title_text);

        connectionSectionLabel = view.findViewById(R.id.connection_section_label);
        protocolLabel = view.findViewById(R.id.protocol_label);
        protocolToggleGroup = view.findViewById(R.id.protocol_toggle_group);
        autoReconnectSwitch = view.findViewById(R.id.auto_reconnect_switch);
        dotHostnameLayout = view.findViewById(R.id.dot_hostname_layout);
        dohUrlLayout = view.findViewById(R.id.doh_url_layout);
        dnsPortLayout = view.findViewById(R.id.dns_port_layout);
        dnsTimeoutLayout = view.findViewById(R.id.dns_timeout_layout);
        dotHostnameInput = view.findViewById(R.id.dot_hostname_input);
        dohUrlInput = view.findViewById(R.id.doh_url_input);
        dnsPortInput = view.findViewById(R.id.dns_port_input);
        dnsTimeoutInput = view.findViewById(R.id.dns_timeout_input);

        isEnglish = prefs.getBoolean("english_language", false);
        if (aboutTitle != null) {
            aboutTitle.setText(isEnglish ? "About" : "درباره");
        }
        if (githubLink != null) {
            githubLink.setText(isEnglish ? "GitHub: deepcodecreate" : "گیت‌هاب: deepcodecreate");
            githubLink.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/deepcodecreate")));
                } catch (Exception ignored) {}
            });
        }
        if (telegramLink != null) {
            telegramLink.setText(isEnglish ? "Telegram: @deepcodecreate" : "تلگرام: @deepcodecreate");
            telegramLink.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/deepcodecreate")));
                } catch (Exception ignored) {}
            });
        }
    }

    private void loadSettings() {
        isBindingSetup = true;
        dhcpSwitch.setChecked(prefs.getBoolean("use_dhcp", false));
        rootSwitch.setChecked(prefs.getBoolean("root_mode", false));
        langSwitch.setChecked(prefs.getBoolean("english_language", false));
        ipv4Input.setText(prefs.getString("ipv4_address", "10.0.0.2"));
        logLimitInput.setText(String.valueOf(prefs.getInt("log_limit", 1000)));

        autoReconnectSwitch.setChecked(prefs.getBoolean("auto_reconnect", true));

        DnsProtocol protocol = DnsProtocol.from(prefs.getString("dns_protocol", "UDP"));
        setProtocolToggle(protocol);
        dotHostnameInput.setText(prefs.getString("dot_hostname", ""));
        dohUrlInput.setText(prefs.getString("doh_url", ""));
        dnsPortInput.setText(prefs.getString("dns_port", String.valueOf(protocol.defaultPort)));
        dnsTimeoutInput.setText(prefs.getString("dns_timeout_ms", "4000"));
        updateProtocolFieldVisibility(protocol);

        isBindingSetup = false;
    }

    private void setProtocolToggle(DnsProtocol protocol) {
        int id;
        switch (protocol) {
            case TCP: id = R.id.btn_protocol_tcp; break;
            case DOT: id = R.id.btn_protocol_dot; break;
            case DOH: id = R.id.btn_protocol_doh; break;
            default: id = R.id.btn_protocol_udp; break;
        }
        protocolToggleGroup.check(id);
    }

    private DnsProtocol protocolFromCheckedId(int checkedId) {
        if (checkedId == R.id.btn_protocol_tcp) return DnsProtocol.TCP;
        if (checkedId == R.id.btn_protocol_dot) return DnsProtocol.DOT;
        if (checkedId == R.id.btn_protocol_doh) return DnsProtocol.DOH;
        return DnsProtocol.UDP;
    }

    private void updateProtocolFieldVisibility(DnsProtocol protocol) {
        dotHostnameLayout.setVisibility(protocol == DnsProtocol.DOT ? View.VISIBLE : View.GONE);
        dohUrlLayout.setVisibility(protocol == DnsProtocol.DOH ? View.VISIBLE : View.GONE);
        dnsPortLayout.setVisibility(protocol == DnsProtocol.DOH ? View.GONE : View.VISIBLE);
    }

    private void setupSwitchColors(int accentColor) {
        int thumbActiveColor = accentColor;
        int thumbInactiveColor = Color.parseColor("#BDBDBD");
        int trackActiveColor = (accentColor & 0x00FFFFFF) | 0x4D000000;
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

        MaterialSwitch[] switches = {dhcpSwitch, rootSwitch, langSwitch, autoReconnectSwitch};
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

        langSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isBindingSetup) return;
            prefs.edit().putBoolean("english_language", isChecked).apply();
            updateLanguage(isChecked);
            restartApp();
        });

        themePickerRow.setOnClickListener(v -> {
            themePickerLauncher.launch(new Intent(requireContext(), ThemeActivity.class));
            requireActivity().overridePendingTransition(R.anim.app_slide_in_right, R.anim.app_slide_out_left);
        });

        autoReconnectSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isBindingSetup) return;
            prefs.edit().putBoolean("auto_reconnect", isChecked).apply();
        });

        protocolToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isBindingSetup || !isChecked) return;
            if (isConnected()) {
                showToast(isEnglish ? "Disconnect first to change protocol"
                        : "برای تغییر پروتکل ابتدا اتصال را قطع کنید");
                isBindingSetup = true;
                setProtocolToggle(DnsProtocol.from(prefs.getString("dns_protocol", "UDP")));
                isBindingSetup = false;
                return;
            }
            DnsProtocol protocol = protocolFromCheckedId(checkedId);
            prefs.edit().putString("dns_protocol", protocol.label.toUpperCase(Locale.ROOT)).apply();
            if (dnsPortInput.getText() == null || dnsPortInput.getText().toString().trim().isEmpty()) {
                dnsPortInput.setText(String.valueOf(protocol.defaultPort));
            }
            updateProtocolFieldVisibility(protocol);
        });

        saveButton.setOnClickListener(v -> saveConnectionSettings());
    }

    /** True while DNS protection is active (VPN service running, or root DNS applied). */
    private boolean isConnected() {
        if (!prefs.getBoolean("vpn_active", false)) return false;
        if (prefs.getBoolean("root_mode", false)) return true;
        return MyVpnService.isRunning(requireContext());
    }

    private void showRootWarningDialog() {
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext(), R.style.CustomDialogTheme)
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
            if (getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
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

    private void checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) requireContext().getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(requireContext().getPackageName())) {
                AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext(), R.style.CustomDialogTheme)
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
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext(), R.style.CustomDialogTheme)
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
        int accentColor = ThemeManager.byId(ThemeManager.getSelectedId(requireContext())).previewAccent;
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
            langSwitch.setText("English Language");
            themePickerLabel.setText("App Theme");
            ipv4Layout.setHint("IPv4 Address");
            logLimitLayout.setHint("Log Limit");
            saveButton.setText("Save Settings");

            connectionSectionLabel.setText("Connection");
            protocolLabel.setText("DNS Protocol");
            dotHostnameLayout.setHint("DoT Hostname (SNI)");
            dohUrlLayout.setHint("DoH URL");
            dnsPortLayout.setHint("Port");
            dnsTimeoutLayout.setHint("Timeout (ms)");
            autoReconnectSwitch.setText("Auto-reconnect on network change");
            if (titleVpnMode != null) titleVpnMode.setText("VPN Mode");
            if (titleAppearance != null) titleAppearance.setText("Appearance");
            if (titleAdvanced != null) titleAdvanced.setText("Advanced");
        } else {
            settingsTitleText.setText("تنظیمات");
            dhcpSwitch.setText("اتصال DHCP (غیر ضروری)");
            rootSwitch.setText("حالت روت (سوپر یوزر)");
            langSwitch.setText("استفاده از زبان انگلیسی");
            themePickerLabel.setText("تم برنامه");
            ipv4Layout.setHint("آدرس داخلی IPv4");
            logLimitLayout.setHint("محدودیت تعداد لاگ");
            saveButton.setText("ذخیره تنظیمات");

            connectionSectionLabel.setText("اتصال");
            protocolLabel.setText("پروتکل DNS");
            dotHostnameLayout.setHint("هاست‌نیم DoT (SNI)");
            dohUrlLayout.setHint("آدرس DoH");
            dnsPortLayout.setHint("پورت");
            dnsTimeoutLayout.setHint("مهلت زمانی (میلی‌ثانیه)");
            autoReconnectSwitch.setText("اتصال مجدد خودکار با تغییر شبکه");
            if (titleVpnMode != null) titleVpnMode.setText("حالت VPN");
            if (titleAppearance != null) titleAppearance.setText("ظاهر برنامه");
            if (titleAdvanced != null) titleAdvanced.setText("پیشرفته");
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

    private void updateThemePickerValue() {
        ThemeManager.ThemeDef def = ThemeManager.byId(ThemeManager.getSelectedId(requireContext()));
        themePickerValue.setText(isEnglish ? def.nameEn : def.nameFa);
    }

    private void restartApp() {
        requireActivity().recreate();
    }

    private void showToast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onPause() {
        super.onPause();
        // A hidden Settings tab still gets onPause when the app goes to the background; its
        // inputs may be stale then (Home/Servers changed the prefs) and must not overwrite them.
        if (!isHidden()) saveConnectionSettings();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) saveConnectionSettings();
        else reloadConnectionInputs();
    }

    /** Pulls the DNS fields Home / Servers may have changed while this tab was hidden. */
    private void reloadConnectionInputs() {
        if (dotHostnameInput == null) return;
        isBindingSetup = true;
        DnsProtocol protocol = DnsProtocol.from(prefs.getString("dns_protocol", "UDP"));
        setProtocolToggle(protocol);
        dotHostnameInput.setText(prefs.getString("dot_hostname", ""));
        dohUrlInput.setText(prefs.getString("doh_url", ""));
        dnsPortInput.setText(prefs.getString("dns_port", String.valueOf(protocol.defaultPort)));
        dnsTimeoutInput.setText(prefs.getString("dns_timeout_ms", "4000"));
        updateProtocolFieldVisibility(protocol);
        isBindingSetup = false;
    }

    private void saveConnectionSettings() {
        if (logLimitInput == null) return;
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

        SharedPreferences.Editor editor = prefs.edit();

        String hostname = dotHostnameInput.getText() != null ? dotHostnameInput.getText().toString().trim() : "";
        editor.putString("dot_hostname", hostname);

        String dohUrl = dohUrlInput.getText() != null ? dohUrlInput.getText().toString().trim() : "";
        editor.putString("doh_url", dohUrl);

        String portText = dnsPortInput.getText() != null ? dnsPortInput.getText().toString().trim() : "";
        try {
            int port = Integer.parseInt(portText);
            if (port >= 1 && port <= 65535) {
                editor.putString("dns_port", String.valueOf(port));
            } else {
                showToast(isEnglish ? "Port must be between 1 and 65535" : "پورت باید بین ۱ تا ۶۵۵۳۵ باشد");
            }
        } catch (NumberFormatException ignored) {

        }

        String timeoutText = dnsTimeoutInput.getText() != null ? dnsTimeoutInput.getText().toString().trim() : "";
        try {
            int timeout = Integer.parseInt(timeoutText);
            if (timeout >= 1000 && timeout <= 15000) {
                editor.putString("dns_timeout_ms", String.valueOf(timeout));
            } else {
                showToast(isEnglish ? "Timeout must be between 1000 and 15000 ms" : "مهلت زمانی باید بین ۱۰۰۰ تا ۱۵۰۰۰ میلی‌ثانیه باشد");
            }
        } catch (NumberFormatException ignored) {

        }

        editor.apply();
    }
}
