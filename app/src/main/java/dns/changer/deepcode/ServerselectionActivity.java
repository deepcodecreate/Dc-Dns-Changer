package dns.changer.deepcode;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerselectionActivity extends AppCompatActivity implements ServerAdapter.ServerSelectionListener {

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView serversRecyclerView;
    private MaterialButton addCustomServerButton;
    private MaterialButton importFromClipboardButton;
    private TextView protocolBanner;
    private List<DnsServer> serverList = new ArrayList<>();
    private List<DnsServer> customServers = new ArrayList<>();
    private SharedPreferences prefs;
    private static final String CUSTOM_SERVERS_KEY = "custom_servers";
    private ServerAdapter adapter;
    private ExecutorService executorService;
    private Handler mainHandler;
    private DnsProtocol currentProtocol = DnsProtocol.UDP;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE);
        ThemeManager.applyTheme(this);
        setContentView(R.layout.activity_server_selection);

        String protoExtra = getIntent() != null ? getIntent().getStringExtra("protocol") : null;
        if (protoExtra == null) protoExtra = prefs.getString("dns_protocol", "UDP");
        currentProtocol = DnsProtocol.from(protoExtra);

        boolean isEnglish = prefs.getBoolean("english_language", false);

        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        serversRecyclerView = findViewById(R.id.servers_recyclerview);
        addCustomServerButton = findViewById(R.id.add_custom_server_button);
        importFromClipboardButton = findViewById(R.id.import_from_clipboard_button);
        protocolBanner = findViewById(R.id.protocol_banner);

        executorService = Executors.newFixedThreadPool(5);
        mainHandler = new Handler(getMainLooper());

        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                checkAllServersPing();
            }
        });

        addCustomServerButton.setContentDescription(isEnglish ? "Add custom DNS" : "افزودن DNS سفارشی");
        importFromClipboardButton.setContentDescription(isEnglish ? "Import from clipboard" : "افزودن از کلیپ‌بورد");
        if (protocolBanner != null) {
            protocolBanner.setText(isEnglish
                    ? (currentProtocol.label + " servers")
                    : ("سرورهای " + currentProtocol.label));
        }

        importFromClipboardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { importFromClipboard(); }
        });
        addCustomServerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { showAddCustomServerDialog(); }
        });

        serversRecyclerView.setHorizontalScrollBarEnabled(false);
        serversRecyclerView.setVerticalScrollBarEnabled(false);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(isEnglish ? "Select DNS Server" : "انتخاب سرور DNS");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.colorAccent, typedValue, true);
        int colorAccent = typedValue.data;
        getTheme().resolveAttribute(android.R.attr.colorPrimary, typedValue, true);
        int colorPrimary = typedValue.data;
        swipeRefreshLayout.setColorSchemeColors(colorAccent, colorPrimary);
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(colorPrimary);

        loadCustomServers();
        setupServerList();
        handleDeepLinkIntent(getIntent());
        checkAllServersPing();
    }

    @Override
    protected void onDestroy() {
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
        if (adapter == null) {
            adapter = new ServerAdapter(this, serverList, this, new ServerAdapter.ServerLongClickListener() {
                @Override
                public void onLongClick(DnsServer server) {
                    showDeleteDialog(server);
                }
            });
            serversRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            serversRecyclerView.setAdapter(adapter);
        } else {
            adapter.updateServerList(new ArrayList<DnsServer>(serverList));
        }
    }

    private void checkAllServersPing() {
        for (DnsServer server : serverList) server.setPing(-1);
        adapter.notifyDataSetChanged();
        for (final DnsServer server : serverList) {
            executorService.execute(new Runnable() {
                @Override
                public void run() { checkServerPing(server); }
            });
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
        int pingValue = -2;
        try {
            DnsQueryEngine engine = new DnsQueryEngine(null, getApplicationContext(),
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
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (DnsServer s : serverList) {
                    if (s.getName().equals(server.getName()) && s.getDns1().equals(server.getDns1())) {
                        s.setPing(pingValue);
                        break;
                    }
                }
                adapter.notifyDataSetChanged();
                swipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    private void handleDeepLinkIntent(Intent intent) {
        if (intent == null || intent.getData() == null) return;
        Uri data = intent.getData();
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
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null && clipboard.hasPrimaryClip()) {
            CharSequence clip = clipboard.getPrimaryClip().getItemAt(0).getText();
            if (clip != null && clip.toString().startsWith("dnschanger://")) {
                try {
                    Intent fakeIntent = new Intent();
                    fakeIntent.setData(Uri.parse(clip.toString()));
                    handleDeepLinkIntent(fakeIntent);
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
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_server, null);

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
            ArrayAdapter<String> spinAdapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_dropdown_item,
                    new String[]{"UDP", "TCP", "DoT", "DoH"});
            protoSpinner.setAdapter(spinAdapter);
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

        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.CustomDialogTheme)
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
                    if (adapter != null) adapter.notifyItemInserted(serverList.size() - 1);
                    executorService.execute(new Runnable() {
                        @Override public void run() { checkServerPing(newServer); }
                    });
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
        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.CustomDialogTheme)
            .setTitle(isEnglish ? "Delete Server?" : "حذف سرور؟")
            .setMessage(isEnglish ? "Are you sure you want to delete this server?" : "آیا مطمئن هستید که می‌خواهید این سرور را حذف کنید؟")
            .setPositiveButton(isEnglish ? "Delete" : "حذف", (dialog1, which) -> {
                int position = serverList.indexOf(server);
                customServers.remove(server);
                for (int i = customServers.size() - 1; i >= 0; i--) {
                    DnsServer c = customServers.get(i);
                    if (c.getName().equals(server.getName()) && c.getDns1().equals(server.getDns1())) {
                        customServers.remove(i);
                    }
                }
                saveCustomServers();
                if (position != -1) {
                    serverList.remove(position);
                    if (adapter != null) adapter.notifyItemRemoved(position);
                }
                showToast(isEnglish ? "Server deleted" : "سرور حذف شد");
            })
            .setNegativeButton(isEnglish ? "Cancel" : "لغو", null)
            .create();
        dialog.show();
        styleDialogButtons(dialog);
    }

    private void styleDialogButtons(AlertDialog dialog) {
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.colorAccent, typedValue, true);
        int textColor = typedValue.data;
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(textColor);
        if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(textColor);
    }

    @Override
    public void onServerSelected(DnsServer server) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("dns1", server.getDns1());
        resultIntent.putExtra("dns2", server.getDns2());
        resultIntent.putExtra("ipv6_dns1", server.getIpv6Dns1());
        resultIntent.putExtra("ipv6_dns2", server.getIpv6Dns2());
        resultIntent.putExtra("protocol", server.getProtocol());
        resultIntent.putExtra("hostname", server.getHostname());
        resultIntent.putExtra("doh_url", server.getDohUrl());
        resultIntent.putExtra("dns_port", server.getPort());
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
