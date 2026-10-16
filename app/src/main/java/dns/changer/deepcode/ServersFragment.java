package dns.changer.deepcode;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.ViewAnimationUtils;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.view.animation.OvershootInterpolator;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServersFragment extends Fragment implements ServerAdapter.ServerSelectionListener {

    private static final String CUSTOM_SERVERS_KEY = "custom_servers";

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView serversRecyclerView;
    private MaterialButton addCustomServerButton;
    private MaterialButton importFromClipboardButton;
    private FloatingActionButton serverFabToggle;
    private View serverFabActions;
    private boolean serverFabExpanded = false;
    private TextView protocolBanner;
    private List<DnsServer> serverList = new ArrayList<>();
    private List<DnsServer> customServers = new ArrayList<>();
    private SharedPreferences prefs;
    private ServerAdapter adapter;
    private ExecutorService executorService;
    private Handler mainHandler;
    private DnsProtocol currentProtocol = DnsProtocol.UDP;
    private Uri pendingDeepLink;

    // Search
    private View headerTitleRow, searchButton, searchBar, searchBack, searchClear;
    private EditText searchInput;
    private TextView searchEmpty;
    private boolean searchOpen = false;
    private String query = "";
    private Animator revealAnimator;
    private OnBackPressedCallback backCallback;

    /** Called by HomeFragment right before switching to this tab. */
    public void setProtocol(DnsProtocol protocol) {
        currentProtocol = protocol;
        if (isAdded() && adapter != null) {
            updateBanner();
            setupServerList();
            checkAllServersPing();
        }
    }

    /** Refreshes the server tab after Home or Settings changes the protocol. */
    public void syncProtocolFromPreferences() {
        if (prefs == null) {
            prefs = requireContext().getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE);
        }
        DnsProtocol saved = DnsProtocol.from(prefs.getString("dns_protocol", "UDP"));
        if (saved != currentProtocol) {
            setProtocol(saved);
        } else if (protocolBanner != null) {
            updateBanner();
        }
    }

    /** Called by MainActivity when a dnschanger://add link is opened. */
    public void handleDeepLink(Uri data) {
        if (!isAdded()) {
            pendingDeepLink = data;
            return;
        }
        handleDeepLinkIntent(data);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = requireContext().getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE);
        currentProtocol = DnsProtocol.from(prefs.getString("dns_protocol", "UDP"));
        executorService = Executors.newFixedThreadPool(5);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_servers, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        boolean isEnglish = prefs.getBoolean("english_language", false);

        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);
        serversRecyclerView = view.findViewById(R.id.servers_recyclerview);
        addCustomServerButton = view.findViewById(R.id.add_custom_server_button);
        importFromClipboardButton = view.findViewById(R.id.import_from_clipboard_button);
        serverFabToggle = view.findViewById(R.id.server_fab_toggle);
        serverFabActions = view.findViewById(R.id.server_fab_actions);
        protocolBanner = view.findViewById(R.id.protocol_banner);
        setupSearch(view, isEnglish);

        mainHandler = new Handler(requireContext().getMainLooper());

        swipeRefreshLayout.setOnRefreshListener(this::checkAllServersPing);

        setupFloatingServerActions(isEnglish);
        updateBanner();

        serversRecyclerView.setHorizontalScrollBarEnabled(false);
        serversRecyclerView.setVerticalScrollBarEnabled(false);

        TypedValue typedValue = new TypedValue();
        requireContext().getTheme().resolveAttribute(android.R.attr.colorAccent, typedValue, true);
        int colorAccent = typedValue.data;
        requireContext().getTheme().resolveAttribute(android.R.attr.colorPrimary, typedValue, true);
        int colorPrimary = typedValue.data;
        swipeRefreshLayout.setColorSchemeColors(colorAccent, colorPrimary);
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(colorPrimary);

        loadCustomServers();
        setupServerList();
        checkAllServersPing();

        if (pendingDeepLink != null) {
            handleDeepLinkIntent(pendingDeepLink);
            pendingDeepLink = null;
        }
    }

    private void setupFloatingServerActions(boolean isEnglish) {
        Context context = requireContext();
        ThemeManager.ThemeDef theme = ThemeManager.byId(ThemeManager.getSelectedId(context));
        int accentRipple = (theme.previewAccent & 0x00FFFFFF) | 0x33000000;

        addCustomServerButton.setText(isEnglish ? "Add server" : "افزودن سرور");
        addCustomServerButton.setContentDescription(
                isEnglish ? "Add custom server" : "افزودن سرور سفارشی");
        importFromClipboardButton.setText(
                isEnglish ? "Import clipboard" : "افزودن از کلیپ‌بورد");
        importFromClipboardButton.setContentDescription(
                isEnglish ? "Import from clipboard" : "افزودن از کلیپ‌بورد");

        for (MaterialButton action : new MaterialButton[]{
                addCustomServerButton, importFromClipboardButton}) {
            action.setBackgroundTintList(ColorStateList.valueOf(theme.previewSurface));
            action.setTextColor(ThemeManager.getDefaultTextColor(context));
            action.setIconTint(ColorStateList.valueOf(theme.previewAccent));
            action.setStrokeColor(ColorStateList.valueOf(accentRipple));
            action.setRippleColor(ColorStateList.valueOf(accentRipple));
        }

        serverFabToggle.setBackgroundTintList(ColorStateList.valueOf(theme.previewAccent));
        serverFabToggle.setImageTintList(ColorStateList.valueOf(theme.previewOnAccent));
        serverFabToggle.setRippleColor(ColorStateList.valueOf(accentRipple));
        serverFabToggle.setContentDescription(
                isEnglish ? "Open server actions" : "باز کردن گزینه‌های سرور");
        serverFabToggle.setOnClickListener(v -> setServerFabExpanded(!serverFabExpanded));
        addCustomServerButton.setOnClickListener(v -> {
            setServerFabExpanded(false);
            showAddCustomServerDialog();
        });
        importFromClipboardButton.setOnClickListener(v -> {
            setServerFabExpanded(false);
            importFromClipboard();
        });
    }

    private void setServerFabExpanded(boolean expanded) {
        if (serverFabActions == null || serverFabToggle == null || serverFabExpanded == expanded) {
            return;
        }
        serverFabExpanded = expanded;
        serverFabActions.animate().cancel();
        serverFabToggle.animate().cancel();

        boolean isEnglish = prefs.getBoolean("english_language", false);
        serverFabToggle.setContentDescription(expanded
                ? (isEnglish ? "Close server actions" : "بستن گزینه‌های سرور")
                : (isEnglish ? "Open server actions" : "باز کردن گزینه‌های سرور"));
        serverFabToggle.animate()
                .rotation(expanded ? 45f : 0f)
                .setDuration(220)
                .setInterpolator(new OvershootInterpolator(1.1f))
                .start();

        if (expanded) {
            serverFabActions.setVisibility(View.VISIBLE);
            serverFabActions.setAlpha(0f);
            serverFabActions.setScaleX(0.88f);
            serverFabActions.setScaleY(0.88f);
            serverFabActions.setTranslationY(dp(10f));
            serverFabActions.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setDuration(240)
                    .setInterpolator(new OvershootInterpolator(0.8f))
                    .start();
        } else {
            serverFabActions.animate()
                    .alpha(0f)
                    .scaleX(0.88f)
                    .scaleY(0.88f)
                    .translationY(dp(10f))
                    .setDuration(170)
                    .withEndAction(() -> {
                        if (!serverFabExpanded && serverFabActions != null) {
                            serverFabActions.setVisibility(View.GONE);
                        }
                    })
                    .start();
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private void updateBanner() {
        boolean isEnglish = prefs.getBoolean("english_language", false);
        if (protocolBanner != null) {
            protocolBanner.setText(isEnglish
                    ? (currentProtocol.label + " servers")
                    : ("سرورهای " + currentProtocol.label));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }

    private void loadCustomServers() {
        String json = prefs.getString(CUSTOM_SERVERS_KEY, null);
        if (json != null) {
            Type type = new TypeToken<List<DnsServer>>() {}.getType();
            customServers = new Gson().fromJson(json, type);
            if (customServers == null) customServers = new ArrayList<>();
        } else {
            customServers = new ArrayList<>();
        }
    }

    private void saveCustomServers() {
        prefs.edit().putString(CUSTOM_SERVERS_KEY, new Gson().toJson(customServers)).apply();
    }

    private void setupServerList() {
        serverList.clear();
        serverList.addAll(DnsCatalog.builtins(currentProtocol));
        for (DnsServer custom : customServers) {
            if (custom != null && custom.getProtocolEnum() == currentProtocol) {
                serverList.add(custom);
            }
        }
        List<DnsServer> visible = computeVisible();
        updateEmptyState(visible.isEmpty());
        if (adapter == null) {
            adapter = new ServerAdapter(requireContext(), visible, this, this::showDeleteDialog);
            serversRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
            serversRecyclerView.setLayoutAnimation(android.view.animation.AnimationUtils
                    .loadLayoutAnimation(requireContext(), R.anim.layout_animation_fall_in));
            serversRecyclerView.setAdapter(adapter);
        } else {
            adapter.updateServerList(visible);
        }
        serversRecyclerView.scheduleLayoutAnimation();
    }

    private void checkAllServersPing() {
        if (adapter == null) return;
        for (DnsServer server : serverList) server.setPing(-1);
        adapter.notifyDataSetChanged();
        for (final DnsServer server : serverList) {
            executorService.execute(() -> checkServerPing(server));
        }
    }

    private void checkServerPing(DnsServer server) {
        String dns = server.getDns1();
        boolean hasEndpointWithoutIp =
                (server.getProtocolEnum() == DnsProtocol.DOH && !server.getDohUrl().isEmpty())
                || (server.getProtocolEnum() == DnsProtocol.DOT && !server.getHostname().isEmpty());

        if ((dns == null || dns.isEmpty()) && !hasEndpointWithoutIp) {
            updateServerPing(server, -2);
            return;
        }
        int pingValue;
        try {
            DnsQueryEngine engine = new DnsQueryEngine(null, requireContext().getApplicationContext(),
                    server.getProtocolEnum(), server.getDns1(), server.getDns2(),
                    server.getHostname(), server.getDohUrl(), server.getPort());
            pingValue = engine.measureLatencyMs();
        } catch (Exception e) {
            pingValue = -2;
        }
        if (pingValue < 0 && HostValidator.isSafeHost(server.getDns1())) {
            pingValue = fallbackPing(server.getDns1(), server.getPort());
        }
        updateServerPing(server, pingValue);
    }

    private int fallbackPing(String dns, int port) {
        try {
            long startTime = System.currentTimeMillis();
            java.net.Socket socket = new java.net.Socket();
            socket.connect(new java.net.InetSocketAddress(dns, port > 0 ? port : 53), 2000);
            socket.close();
            return (int) (System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            return -2;
        }
    }

    private void updateServerPing(final DnsServer server, final int pingValue) {
        mainHandler.post(() -> {
            if (adapter == null) return;
            for (DnsServer s : serverList) {
                if (s.getName().equals(server.getName()) && s.getDns1().equals(server.getDns1())) {
                    s.setPing(pingValue);
                    break;
                }
            }
            adapter.notifyDataSetChanged();
            if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
        });
    }

    private void handleDeepLinkIntent(Uri data) {
        if (data == null) return;
        if (!"dnschanger".equals(data.getScheme()) || !"add".equals(data.getHost())) return;
        String name = data.getQueryParameter("name");
        String dns1 = data.getQueryParameter("dns1");
        String dns2 = data.getQueryParameter("dns2");
        String ipv6dns1 = data.getQueryParameter("ipv6dns1");
        String ipv6dns2 = data.getQueryParameter("ipv6dns2");
        String proto = data.getQueryParameter("protocol");
        String host = data.getQueryParameter("hostname");
        String url = data.getQueryParameter("doh");
        String port = data.getQueryParameter("port");
        if (name != null && (dns1 != null || url != null)) {
            if (proto != null) currentProtocol = DnsProtocol.from(proto);
            showAddCustomServerDialog(name, n(dns1), n(dns2), n(ipv6dns1), n(ipv6dns2),
                    n(host), n(url), port);
        }
    }

    private String n(String s) { return s == null ? "" : s; }

    private void importFromClipboard() {
        boolean isEnglish = prefs.getBoolean("english_language", false);
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null && clipboard.hasPrimaryClip()) {
            CharSequence clip = clipboard.getPrimaryClip().getItemAt(0).getText();
            if (clip != null && clip.toString().startsWith("dnschanger://")) {
                try {
                    handleDeepLinkIntent(Uri.parse(clip.toString()));
                } catch (Exception e) {
                    showToast(isEnglish ? "Invalid link" : "لینک نامعتبر است");
                }
            } else {
                showToast(isEnglish ? "Clipboard does not contain a DNSChanger link"
                        : "لینک مربوط به DNSChanger در کلیپ‌بورد یافت نشد");
            }
        }
    }

    private void showAddCustomServerDialog() {
        showAddCustomServerDialog("", "", "", "", "", "", "", "");
    }

    private void showAddCustomServerDialog(String name, String dns1, String dns2, String ipv6dns1, String ipv6dns2,
                                           String hostname, String dohUrl, String port) {
        final boolean isEnglish = prefs.getBoolean("english_language", false);
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_server, null);

        final TextInputEditText nameEdit = dialogView.findViewById(R.id.server_name_edittext);
        final TextInputEditText dns1Edit = dialogView.findViewById(R.id.dns1_edittext);
        final TextInputEditText dns2Edit = dialogView.findViewById(R.id.dns2_edittext);
        final TextInputEditText ipv6Dns1Edit = dialogView.findViewById(R.id.ipv6_dns1_edittext);
        final TextInputEditText ipv6Dns2Edit = dialogView.findViewById(R.id.ipv6_dns2_edittext);
        final TextInputEditText hostEdit = dialogView.findViewById(R.id.hostname_edittext);
        final TextInputEditText urlEdit = dialogView.findViewById(R.id.doh_url_edittext);
        final TextInputEditText portEdit = dialogView.findViewById(R.id.port_edittext);
        final Spinner protoSpinner = dialogView.findViewById(R.id.protocol_spinner);

        nameEdit.setText(name);
        dns1Edit.setText(dns1);
        dns2Edit.setText(dns2);
        ipv6Dns1Edit.setText(ipv6dns1);
        ipv6Dns2Edit.setText(ipv6dns2);
        if (hostEdit != null) hostEdit.setText(hostname);
        if (urlEdit != null) urlEdit.setText(dohUrl);
        if (portEdit != null) portEdit.setText(port == null || port.isEmpty() ? String.valueOf(currentProtocol.defaultPort) : port);

        final DnsProtocol[] protocols = new DnsProtocol[]{DnsProtocol.UDP, DnsProtocol.TCP, DnsProtocol.DOT, DnsProtocol.DOH};
        if (protoSpinner != null) {
            ArrayAdapter<String> spinAdapter = new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_spinner_dropdown_item,
                    new String[]{"UDP", "TCP", "DoT", "DoH"});
            spinAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
            protoSpinner.setAdapter(spinAdapter);
            styleSpinnerPopup(protoSpinner);
            int idx = 0;
            for (int i = 0; i < protocols.length; i++) {
                if (protocols[i] == currentProtocol) idx = i;
            }
            protoSpinner.setSelection(idx);
            protoSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    DnsProtocol p = protocols[position];
                    if (hostEdit != null) hostEdit.setVisibility(p == DnsProtocol.DOT ? View.VISIBLE : View.GONE);
                    if (urlEdit != null) urlEdit.setVisibility(p == DnsProtocol.DOH ? View.VISIBLE : View.GONE);
                    if (portEdit != null && (portEdit.getText() == null || portEdit.getText().toString().isEmpty())) {
                        portEdit.setText(String.valueOf(p.defaultPort));
                    }
                }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext(), R.style.CustomDialogTheme)
            .setTitle(isEnglish ? "Add Custom Server" : "سرور سفارشی")
            .setView(dialogView)
            .setPositiveButton(isEnglish ? "Add" : "افزودن", (dialog1, which) -> {
                String serverName = nameEdit.getText().toString().trim();
                String dns1Value = dns1Edit.getText().toString().trim();
                String dns2Value = dns2Edit.getText().toString().trim();
                String ipv6_1 = ipv6Dns1Edit.getText().toString().trim();
                String ipv6_2 = ipv6Dns2Edit.getText().toString().trim();
                DnsProtocol chosen = currentProtocol;
                if (protoSpinner != null) chosen = protocols[protoSpinner.getSelectedItemPosition()];
                String hostVal = hostEdit != null ? hostEdit.getText().toString().trim() : "";
                String urlVal = urlEdit != null ? urlEdit.getText().toString().trim() : "";
                int portVal = chosen.defaultPort;
                try {
                    if (portEdit != null && portEdit.getText() != null && portEdit.getText().length() > 0) {
                        portVal = Integer.parseInt(portEdit.getText().toString().trim());
                    }
                } catch (Exception ignored) {}

                if (serverName.isEmpty() || (dns1Value.isEmpty() && urlVal.isEmpty())) {
                    showToast(isEnglish ? "Please enter server name and endpoint" : "لطفاً نام سرور و آدرس را وارد کنید");
                    return;
                }

                DnsServer newServer = new DnsServer(serverName, dns1Value, dns2Value, ipv6_1, ipv6_2,
                        chosen.name(), hostVal, urlVal, portVal);
                customServers.add(newServer);
                saveCustomServers();
                if (chosen == currentProtocol) {
                    serverList.add(newServer);
                    applyFilter();
                    executorService.execute(() -> checkServerPing(newServer));
                }
            })
            .setNegativeButton(isEnglish ? "Cancel" : "انصراف", null)
            .create();
        dialog.show();
        styleDialogButtons(dialog);
    }

    private void showDeleteDialog(final DnsServer server) {
        boolean isEnglish = prefs.getBoolean("english_language", false);
        boolean isCustom = false;
        for (DnsServer c : customServers) {
            if (c.getName().equals(server.getName()) && c.getDns1().equals(server.getDns1())) {
                isCustom = true;
                break;
            }
        }
        if (!isCustom) {
            showToast(isEnglish ? "Cannot delete default server" : "نمی‌توان سرور پیش‌فرض را حذف کرد");
            return;
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext(), R.style.CustomDialogTheme)
            .setTitle(isEnglish ? "Delete Server?" : "حذف سرور؟")
            .setMessage(isEnglish ? "Are you sure you want to delete this server?" : "آیا مطمئن هستید که می‌خواهید این سرور را حذف کنید؟")
            .setPositiveButton(isEnglish ? "Delete" : "حذف", (dialog1, which) -> {
                customServers.remove(server);
                for (int i = customServers.size() - 1; i >= 0; i--) {
                    DnsServer c = customServers.get(i);
                    if (c.getName().equals(server.getName()) && c.getDns1().equals(server.getDns1())) {
                        customServers.remove(i);
                    }
                }
                saveCustomServers();
                serverList.remove(server);
                applyFilter();
                showToast(isEnglish ? "Server deleted" : "سرور حذف شد");
            })
            .setNegativeButton(isEnglish ? "Cancel" : "لغو", null)
            .create();
        dialog.show();
        styleDialogButtons(dialog);
    }

    private void styleSpinnerPopup(Spinner spinner) {
        TypedValue tv = new TypedValue();
        int bg = 0xFF1B100E;
        if (requireContext().getTheme().resolveAttribute(R.attr.dialogBackgroundColor, tv, true)
                && tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            bg = tv.data;
        }
        int accent = 0xFFFFA88B;
        if (requireContext().getTheme().resolveAttribute(android.R.attr.colorAccent, tv, true)) {
            accent = tv.data;
        }
        // slightly lighter than the dialog so the popup stands out, with a thin accent outline
        int popupBg = androidx.core.graphics.ColorUtils.blendARGB(bg, accent, 0.08f);
        float dp = getResources().getDisplayMetrics().density;
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(popupBg);
        d.setCornerRadius(16 * dp);
        d.setStroke((int) Math.max(1, dp), androidx.core.graphics.ColorUtils.setAlphaComponent(accent, 90));
        spinner.setPopupBackgroundDrawable(d);
    }

    private void styleDialogButtons(AlertDialog dialog) {
        TypedValue typedValue = new TypedValue();
        requireContext().getTheme().resolveAttribute(android.R.attr.colorAccent, typedValue, true);
        int textColor = typedValue.data;
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(textColor);
        if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(textColor);
    }

    // ------------------------------------------------------------------ search

    private void setupSearch(View view, boolean isEnglish) {
        headerTitleRow = view.findViewById(R.id.header_title_row);
        searchButton = view.findViewById(R.id.search_button);
        searchBar = view.findViewById(R.id.search_bar);
        searchBack = view.findViewById(R.id.search_back);
        searchClear = view.findViewById(R.id.search_clear);
        searchInput = view.findViewById(R.id.search_input);
        searchEmpty = view.findViewById(R.id.search_empty);

        searchInput.setHint(isEnglish ? "Search servers, games, hosts" : "جستجوی سرور، بازی، آدرس");
        searchEmpty.setText(isEnglish ? "No matching servers" : "سروری پیدا نشد");
        searchButton.setContentDescription(isEnglish ? "Search" : "جستجو");
        searchBack.setContentDescription(isEnglish ? "Close search" : "بستن جستجو");
        searchClear.setContentDescription(isEnglish ? "Clear" : "پاک کردن");
        searchClear.setClickable(false);

        searchButton.setOnClickListener(v -> openSearch());
        searchBack.setOnClickListener(v -> closeSearch());
        searchClear.setOnClickListener(v -> searchInput.setText(""));
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                query = s.toString();
                setClearVisible(!query.isEmpty());
                applyFilter();
            }
        });
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard();
                return true;
            }
            return false;
        });

        backCallback = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() { closeSearch(); }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backCallback);
    }

    private Animator createReveal(boolean opening) {
        int cx = searchButton.getLeft() + searchButton.getWidth() / 2 - searchBar.getLeft();
        int cy = searchBar.getHeight() / 2;
        int w = Math.max(searchBar.getWidth(), 1);
        int h = Math.max(searchBar.getHeight(), 1);
        float radius = (float) Math.hypot(Math.max(cx, w - cx), Math.max(cy, h - cy));
        Animator a = opening
                ? ViewAnimationUtils.createCircularReveal(searchBar, cx, cy, 0f, radius)
                : ViewAnimationUtils.createCircularReveal(searchBar, cx, cy, radius, 0f);
        a.setDuration(opening ? 320 : 240);
        a.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f)); // M3 emphasized
        return a;
    }

    private void openSearch() {
        if (searchOpen || searchBar == null) return;
        searchOpen = true;
        updateBackCallback();
        if (revealAnimator != null) revealAnimator.cancel();

        headerTitleRow.animate().cancel();
        headerTitleRow.animate().alpha(0f).setStartDelay(0).setDuration(140)
                .withEndAction(() -> { if (searchOpen) headerTitleRow.setVisibility(View.INVISIBLE); })
                .start();

        searchBar.setVisibility(View.VISIBLE);
        revealAnimator = createReveal(true);
        revealAnimator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (!searchOpen || searchInput == null) return;
                searchInput.requestFocus();
                InputMethodManager imm = (InputMethodManager) requireContext()
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
            }
        });
        revealAnimator.start();
    }

    private void closeSearch() {
        if (!searchOpen || searchBar == null) return;
        searchOpen = false;
        updateBackCallback();
        hideKeyboard();
        searchInput.setText("");
        if (revealAnimator != null) revealAnimator.cancel();

        headerTitleRow.animate().cancel();
        headerTitleRow.setVisibility(View.VISIBLE);
        headerTitleRow.animate().alpha(1f).setStartDelay(100).setDuration(220).start();

        revealAnimator = createReveal(false);
        revealAnimator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (!searchOpen) searchBar.setVisibility(View.INVISIBLE);
            }
        });
        revealAnimator.start();
    }

    private void setClearVisible(boolean show) {
        if (searchClear == null) return;
        searchClear.setClickable(show);
        searchClear.animate().cancel();
        searchClear.animate().alpha(show ? 1f : 0f).setDuration(150).start();
    }

    private void hideKeyboard() {
        if (searchInput == null || getContext() == null) return;
        InputMethodManager imm = (InputMethodManager) requireContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        searchInput.clearFocus();
    }

    private void updateBackCallback() {
        if (backCallback != null) backCallback.setEnabled(searchOpen && !isHidden());
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) {
            hideKeyboard();
            setServerFabExpanded(false);
        }
        updateBackCallback();
    }

    private List<DnsServer> computeVisible() {
        List<DnsServer> out = new ArrayList<>();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            out.addAll(serverList);
            return out;
        }
        String[] tokens = q.split("\\s+");
        for (DnsServer s : serverList) {
            String hay = (s.getName() + " " + s.getSearchTags() + " " + s.getDns1() + " " + s.getDns2()
                    + " " + s.getIpv6Dns1() + " " + s.getIpv6Dns2() + " " + s.getHostname()
                    + " " + s.getDohUrl() + " " + s.getProtocolEnum().label).toLowerCase(Locale.ROOT);
            boolean all = true;
            for (String t : tokens) {
                if (!hay.contains(t)) { all = false; break; }
            }
            if (all) out.add(s);
        }
        return out;
    }

    /** Re-filters the list with a diff animation (used while typing / after add & delete). */
    private void applyFilter() {
        if (adapter == null) return;
        List<DnsServer> next = computeVisible();
        adapter.submitFiltered(next);
        updateEmptyState(next.isEmpty());
    }

    private void updateEmptyState(boolean empty) {
        if (searchEmpty == null) return;
        searchEmpty.setVisibility(empty && !query.trim().isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onServerSelected(DnsServer server) {
        if (isConnected()) {
            showSwitchServerDialog(server);
            return;
        }
        applyServerSelection(server, false);
    }

    /** True while DNS protection is active (VPN service running, or root DNS applied). */
    private boolean isConnected() {
        if (!prefs.getBoolean("vpn_active", false)) return false;
        if (prefs.getBoolean("root_mode", false)) return true;
        return MyVpnService.isRunning(requireContext());
    }

    private void showSwitchServerDialog(final DnsServer server) {
        final boolean isEnglish = prefs.getBoolean("english_language", false);
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext(), R.style.CustomDialogTheme)
                .setTitle(isEnglish ? "Change server?" : "تغییر سرور؟")
                .setMessage(isEnglish
                        ? "You are currently connected. Selecting \"" + server.getName()
                        + "\" will disconnect the current connection first. Continue?"
                        : "در حال حاضر به یک سرور متصل هستید. با انتخاب «" + server.getName()
                        + "» ابتدا اتصال فعلی قطع می‌شود. ادامه می‌دهید؟")
                .setPositiveButton(isEnglish ? "Disconnect & select" : "قطع اتصال و انتخاب",
                        (d, w) -> applyServerSelection(server, true))
                .setNegativeButton(isEnglish ? "Cancel" : "انصراف", null)
                .create();
        dialog.show();
        styleDialogButtons(dialog);
    }

    private void applyServerSelection(DnsServer server, boolean disconnectFirst) {
        Bundle result = new Bundle();
        result.putString("dns1", server.getDns1());
        result.putString("dns2", server.getDns2());
        result.putString("ipv6_dns1", server.getIpv6Dns1());
        result.putString("ipv6_dns2", server.getIpv6Dns2());
        result.putString("protocol", server.getProtocol());
        result.putString("hostname", server.getHostname());
        result.putString("doh_url", server.getDohUrl());
        result.putInt("dns_port", server.getPort());
        result.putBoolean("disconnect_first", disconnectFirst);
        getParentFragmentManager().setFragmentResult("server_selected", result);
        ((MainActivity) requireActivity()).selectTab(R.id.nav_home);
    }

    private void showToast(String msg) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }
}
