package dns.changer.deepcode;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;
import android.util.TypedValue;
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
    private List<DnsServer> serverList = new ArrayList<>();
    private List<DnsServer> customServers = new ArrayList<>();
    private SharedPreferences prefs;
    private static final String CUSTOM_SERVERS_KEY = "custom_servers";
    private ServerAdapter adapter;
    private ExecutorService executorService;
    private Handler mainHandler;

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

        setContentView(R.layout.activity_server_selection);

        boolean isEnglish = prefs.getBoolean("english_language", false);

        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        serversRecyclerView = findViewById(R.id.servers_recyclerview);
        addCustomServerButton = findViewById(R.id.add_custom_server_button);
        importFromClipboardButton = findViewById(R.id.import_from_clipboard_button);

        executorService = Executors.newFixedThreadPool(5);
        mainHandler = new Handler(getMainLooper());

        swipeRefreshLayout.setOnRefreshListener(this::checkAllServersPing);

        addCustomServerButton.setText(isEnglish ? "Add Custom DNS" : "افزودن DNS سفارشی");
        importFromClipboardButton.setText(isEnglish ? "Import from Clipboard" : "افزودن از کلیپ‌بورد");

        importFromClipboardButton.setOnClickListener(v -> importFromClipboard());
        addCustomServerButton.setOnClickListener(v -> showAddCustomServerDialog());
        
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

        getTheme().resolveAttribute(android.R.attr.colorPrimary, typedValue, true);
        int colorPrimaryDark = typedValue.data;

        swipeRefreshLayout.setColorSchemeColors(colorAccent, colorPrimary);
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(colorPrimaryDark);
        
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
        } else {
            customServers = new ArrayList<>();
        }
    }

    private void saveCustomServers() {
        String json = new Gson().toJson(customServers);
        prefs.edit().putString(CUSTOM_SERVERS_KEY, json).apply();
    }

    private void setupServerList() {
        serverList.clear();
        serverList.add(new DnsServer("Google IPv4", "8.8.8.8", "8.8.4.4", "", ""));
        serverList.add(new DnsServer("Electro IPv4", "78.157.42.101", "78.157.42.100", "", ""));
        serverList.add(new DnsServer("Radar IPv4", "10.202.10.10", "10.202.10.11", "", ""));
        serverList.add(new DnsServer("Comodo IPv4", "8.26.56.26", "8.20.247.20", "", ""));
        serverList.add(new DnsServer("NTT IPv4", "129.250.35.250", "129.250.35.251", "", ""));
        serverList.add(new DnsServer("DYN IPv4", "216.146.35.35", "216.146.36.36", "", ""));
        serverList.add(new DnsServer("Us Open IPv4", "208.67.222.220", "208.67.220.222", "", ""));
        serverList.add(new DnsServer("Yandex IPv4", "77.88.8.1", "77.88.8.8", "", ""));
        serverList.add(new DnsServer("Ad Guard IPv4", "94.140.14.14", "94.140.15.15", "", ""));
        serverList.add(new DnsServer("Censurfri IPv4", "89.233.43.71", "91.239.100.100", "", ""));
        serverList.add(new DnsServer("Beshkan IPv4", "181.41.194.177", "181.41.194.186", "", ""));
        serverList.add(new DnsServer("DC Custom IPv4", "1.1.1.1", "77.88.8.8", "", ""));
        serverList.add(new DnsServer("Clean IPv6", "185.228.168.9", "185.228.169.9", "2a0d:2a00:1::2", "2a0d:2a00:2::2"));
        serverList.add(new DnsServer("Pubg IPv6", "156.154.70.1", "156.154.71.1", "2620:115:53::53", "2620:115:35::35"));
        serverList.add(new DnsServer("Google IPv6", "8.8.8.8", "8.8.4.4", "2001:4860:4860::8888", "2001:4860:4860::8844"));
        serverList.add(new DnsServer("Cloudflare IPv6", "1.1.1.1", "1.0.0.1", "2606:4700:4700::1111", "2606:4700:4700::1001"));
        serverList.addAll(customServers);

        if (adapter == null) {
            adapter = new ServerAdapter(this, serverList, this, this::showDeleteDialog);
            serversRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            serversRecyclerView.setAdapter(adapter);
        } else {
            adapter.updateServerList(new ArrayList<>(serverList));
        }
    }

    private void checkAllServersPing() {
        for (DnsServer server : serverList) {
            server.setPing(-1);
        }
        adapter.notifyDataSetChanged();

        for (DnsServer server : serverList) {
            executorService.execute(() -> checkServerPing(server));
        }
    }

    private void checkServerPing(DnsServer server) {
        String dns = server.getDns1();
        if (dns.isEmpty()) {
            updateServerPing(server, -2);
            return;
        }

        int pingValue = -2;
        
        try {
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
                }
            }
        } catch (Exception e) {
            LogHelper.log(getApplicationContext(), "ICMP ping failed: " + e.getMessage());
        }

        if (pingValue == -2) {
            try {
                long startTime = System.currentTimeMillis();
                java.net.Socket socket = new java.net.Socket();
                socket.connect(new java.net.InetSocketAddress(dns, 53), 2000);
                socket.close();
                pingValue = (int) (System.currentTimeMillis() - startTime);
            } catch (Exception e) {
                pingValue = -2;
            }
        }

        updateServerPing(server, pingValue);
    }

    private void updateServerPing(DnsServer server, int pingValue) {
        mainHandler.post(() -> {
            for (DnsServer s : serverList) {
                if (s.getName().equals(server.getName()) && s.getDns1().equals(server.getDns1())) {
                    s.setPing(pingValue);
                    break;
                }
            }
            adapter.notifyDataSetChanged();
            swipeRefreshLayout.setRefreshing(false);
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

        if (name != null && dns1 != null) {
            showAddCustomServerDialog(name, dns1, dns2, ipv6dns1, ipv6dns2);
        }
    }

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
                showToast(isEnglish ? "Clipboard does not contain a DNSChanger link" : "لینک مربوط به DNSChanger در کلیپ‌بورد یافت نشد");
            }
        }
    }

    private void showAddCustomServerDialog() {
        showAddCustomServerDialog("", "", "", "", "");
    }

    private void showAddCustomServerDialog(String name, String dns1, String dns2, String ipv6dns1, String ipv6dns2) {
        boolean isEnglish = prefs.getBoolean("english_language", false);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_server, null);

        TextInputEditText nameEdit = dialogView.findViewById(R.id.server_name_edittext);
        TextInputEditText dns1Edit = dialogView.findViewById(R.id.dns1_edittext);
        TextInputEditText dns2Edit = dialogView.findViewById(R.id.dns2_edittext);
        TextInputEditText ipv6Dns1Edit = dialogView.findViewById(R.id.ipv6_dns1_edittext);
        TextInputEditText ipv6Dns2Edit = dialogView.findViewById(R.id.ipv6_dns2_edittext);

        nameEdit.setText(name);
        dns1Edit.setText(dns1);
        dns2Edit.setText(dns2);
        ipv6Dns1Edit.setText(ipv6dns1);
        ipv6Dns2Edit.setText(ipv6dns2);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.CustomDialogTheme)
            .setTitle(isEnglish ? "Add Custom Server" : "سرور سفارشی")
            .setView(dialogView)
            .setPositiveButton(isEnglish ? "Add" : "افزودن", (dialog1, which) -> {
                String serverName = nameEdit.getText().toString().trim();
                String dns1Value = dns1Edit.getText().toString().trim();
                String dns2Value = dns2Edit.getText().toString().trim();
                String ipv6_1 = ipv6Dns1Edit.getText().toString().trim();
                String ipv6_2 = ipv6Dns2Edit.getText().toString().trim();

                if (serverName.isEmpty() || dns1Value.isEmpty()) {
                    showToast(isEnglish ? "Please enter server name and primary DNS" : "لطفاً نام سرور و DNS اول را وارد کنید");
                    return;
                }

                boolean isDuplicate = false;
                for (DnsServer server : serverList) {
                    if (server.getDns1().equals(dns1Value)) {
                        isDuplicate = true;
                        break;
                    }
                }

                if (isDuplicate) {
                    showToast(isEnglish ? "This DNS server already exists" : "این سرور DNS قبلاً اضافه شده است");
                    return;
                }

                DnsServer newServer = new DnsServer(serverName, dns1Value, dns2Value, ipv6_1, ipv6_2);
                customServers.add(newServer);
                saveCustomServers();
                serverList.add(newServer);
                
                if (adapter != null) {
                    adapter.notifyItemInserted(serverList.size() - 1);
                }
                
                executorService.execute(() -> checkServerPing(newServer));
            })
            .setNegativeButton(isEnglish ? "Cancel" : "انصراف", null)
            .create();
        
        dialog.show();
        styleDialogButtons(dialog);
    }

    private void showDeleteDialog(DnsServer server) {
        boolean isEnglish = prefs.getBoolean("english_language", false);

        if (!customServers.contains(server)) {
            showToast(isEnglish ? "Cannot delete default server" : "نمی‌توان سرور پیش‌فرض را حذف کرد");
            return;
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.CustomDialogTheme)
            .setTitle(isEnglish ? "Delete Server?" : "حذف سرور؟")
            .setMessage(isEnglish ? "Are you sure you want to delete this server?" : "آیا مطمئن هستید که می‌خواهید این سرور را حذف کنید؟")
            .setPositiveButton(isEnglish ? "Delete" : "حذف", (dialog1, which) -> {
                int position = serverList.indexOf(server);
                if (position != -1) {
                    customServers.remove(server);
                    saveCustomServers();
                    serverList.remove(position);
                    
                    if (adapter != null) {
                        adapter.notifyItemRemoved(position);
                    }
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
        
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(textColor);
        }
        if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(textColor);
        }
        if (dialog.getButton(AlertDialog.BUTTON_NEUTRAL) != null) {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(textColor);
        }
    }

    @Override
    public void onServerSelected(DnsServer server) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("dns1", server.getDns1());
        resultIntent.putExtra("dns2", server.getDns2());
        resultIntent.putExtra("ipv6_dns1", server.getIpv6Dns1());
        resultIntent.putExtra("ipv6_dns2", server.getIpv6Dns2());
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
