package dns.changer.deepcode;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.VpnService;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.system.OsConstants;
import androidx.core.app.NotificationCompat;
import java.io.IOException;
import java.net.InetAddress;

public class MyVpnService extends VpnService implements Runnable {

    private static final String TAG = "MyVpnService";
    private static final int NOTIF_ID = 1;
    private static final String CHANNEL_ID = "vpn_channel";
    public static final String ACTION_STOP_VPN = "DISCONNECT_VPN";
    public static final String ACTION_STATE = "VPN_STATE_CHANGED";
    private static final String VPN_DNS = "10.0.0.1";
    private static final String VPN_ADDR = "10.0.0.2";

    private Thread vpnThread;
    private ParcelFileDescriptor vpnInterface;
    private TunDnsForwarder forwarder;
    private DnsQueryEngine engine;
    private volatile boolean isRunning = false;

    private String dns1 = "78.157.42.101";
    private String dns2 = "78.157.42.100";
    private String ipv6Dns1 = "";
    private String ipv6Dns2 = "";
    private String ipv4 = VPN_ADDR;
    private boolean useDhcp = false;
    private DnsProtocol protocol = DnsProtocol.UDP;
    private String hostname = "";
    private String dohUrl = "";
    private int dnsPort = 53;

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private volatile long lastReconnectAt = 0L;
    private static final long RECONNECT_DEBOUNCE_MS = 4000L;

    private volatile boolean ignoreNextNetworkCallback = false;

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        LogHelper.log(getApplicationContext(), "VPN Service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP_VPN.equals(intent.getAction())) {
            stopVpn();
            return START_NOT_STICKY;
        }

        SharedPreferences prefs = getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE);
        boolean runInBackground = prefs.getBoolean("run_in_background", true);
        if (!runInBackground && !isRunning) {
            stopSelf();
            return START_NOT_STICKY;
        }

        loadSettings(intent, prefs);

        try {
            startForegroundServiceWithNotification();
        } catch (SecurityException e) {
            LogHelper.log(getApplicationContext(), "Foreground start failed: " + e.getMessage());
            sendVpnStateBroadcast(false, "Missing notification permission");
            stopSelf();
            return START_NOT_STICKY;
        }

        if (vpnThread == null || !vpnThread.isAlive()) {
            isRunning = true;
            vpnThread = new Thread(this, "MyVpnThread");
            vpnThread.start();
        }
        registerAutoReconnect(prefs);
        return START_STICKY;
    }

    
    private void registerAutoReconnect(SharedPreferences prefs) {
        if (!prefs.getBoolean("auto_reconnect", true)) {
            unregisterAutoReconnect();
            return;
        }
        if (networkCallback != null) return; // already registered
        try {
            connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager == null) return;
            ignoreNextNetworkCallback = true;
            lastReconnectAt = System.currentTimeMillis();
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    if (ignoreNextNetworkCallback) {
                        ignoreNextNetworkCallback = false;
                        return;
                    }
                    long now = System.currentTimeMillis();
                    if (!isRunning || now - lastReconnectAt < RECONNECT_DEBOUNCE_MS) return;
                    lastReconnectAt = now;
                    LogHelper.log(getApplicationContext(), "Network changed — reconnecting DNS VPN");

                    stopVpn();
                    try {
                        startService(new Intent(MyVpnService.this, MyVpnService.class));
                    } catch (Exception ignored) {}
                }
            };
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        } catch (Exception e) {
            LogHelper.log(getApplicationContext(), "Auto-reconnect registration failed: " + e.getMessage());
        }
    }

    private void unregisterAutoReconnect() {
        if (connectivityManager != null && networkCallback != null) {
            try { connectivityManager.unregisterNetworkCallback(networkCallback); } catch (Exception ignored) {}
        }
        networkCallback = null;
        ignoreNextNetworkCallback = false;
    }

    private void loadSettings(Intent intent, SharedPreferences prefs) {
        protocol = DnsProtocol.from(prefs.getString("dns_protocol",
                prefs.getBoolean("dns_over_tcp", false) ? "TCP" : "UDP"));
        hostname = prefs.getString("dot_hostname", "");
        dohUrl = prefs.getString("doh_url", "");
        try {
            dnsPort = Integer.parseInt(prefs.getString("dns_port",
                    String.valueOf(protocol.defaultPort)));
        } catch (Exception e) {
            dnsPort = protocol.defaultPort;
        }
        try {
            DnsQueryEngine.configureTimeoutMs(
                    Integer.parseInt(prefs.getString("dns_timeout_ms", "4000")));
        } catch (Exception ignored) {}

        if (intent != null) {
            String p = intent.getStringExtra("protocol");
            if (p != null) protocol = DnsProtocol.from(p);
            if (intent.getStringExtra("dns1") != null) dns1 = intent.getStringExtra("dns1");
            if (intent.getStringExtra("dns2") != null) dns2 = intent.getStringExtra("dns2");
            if (intent.getStringExtra("ipv6_dns1") != null) ipv6Dns1 = intent.getStringExtra("ipv6_dns1");
            if (intent.getStringExtra("ipv6_dns2") != null) ipv6Dns2 = intent.getStringExtra("ipv6_dns2");
            if (intent.getStringExtra("hostname") != null) hostname = intent.getStringExtra("hostname");
            if (intent.getStringExtra("doh_url") != null) dohUrl = intent.getStringExtra("doh_url");
            if (intent.getIntExtra("dns_port", 0) > 0) dnsPort = intent.getIntExtra("dns_port", dnsPort);
            String ip = intent.getStringExtra("ipv4");
            if (ip != null && !ip.isEmpty()) ipv4 = ip;
            useDhcp = intent.getBooleanExtra("use_dhcp", prefs.getBoolean("use_dhcp", false));
        } else {
            dns1 = prefs.getString("dns1", dns1);
            dns2 = prefs.getString("dns2", dns2);
            ipv6Dns1 = prefs.getString("ipv6_dns1", "");
            ipv6Dns2 = prefs.getString("ipv6_dns2", "");
            ipv4 = prefs.getString("ipv4_address", VPN_ADDR);
            useDhcp = prefs.getBoolean("use_dhcp", false);
        }
        if (dns1 == null || dns1.isEmpty()) dns1 = "1.1.1.1";
        if (dns2 == null) dns2 = "";
        if (ipv6Dns1 == null) ipv6Dns1 = "";
        if (ipv6Dns2 == null) ipv6Dns2 = "";
        LogHelper.log(getApplicationContext(),
                "VPN start protocol=" + protocol.label + " dns1=" + dns1 + " host=" + hostname);
    }

    private boolean needsIntercept() {
        return protocol == DnsProtocol.TCP
                || protocol == DnsProtocol.DOT
                || protocol == DnsProtocol.DOH;
    }

    @Override
    public void run() {
        try {
            if (vpnInterface != null) {
                try { vpnInterface.close(); } catch (IOException ignored) {}
                vpnInterface = null;
            }
            vpnInterface = establishVpn();
            if (vpnInterface == null) {
                LogHelper.log(getApplicationContext(), "builder.establish() returned null");
                sendVpnStateBroadcast(false, "Failed to establish VPN");
                saveVpnState(false);
                stopSelf();
                return;
            }

            engine = new DnsQueryEngine(this, getApplicationContext(), protocol,
                    dns1, dns2, hostname, dohUrl, dnsPort);

            boolean healthy = verifyDns();
            if (!healthy) {
                LogHelper.log(getApplicationContext(), "DNS health check failed — staying up, will retry");
            }

            saveVpnState(true);
            sendVpnStateBroadcast(true, healthy ? null : "connected_degraded");
            updateNotification();

            if (needsIntercept()) {
                forwarder = new TunDnsForwarder(vpnInterface, engine);
                forwarder.run();
            } else {
                while (!Thread.interrupted() && isRunning) {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            LogHelper.log(getApplicationContext(), "VPN error: " + e.getMessage());
            sendVpnStateBroadcast(false, e.getMessage());
        } finally {
            stopVpn();
        }
    }

    private boolean verifyDns() {
        try {
            int ms = engine.measureLatencyMs();
            LogHelper.log(getApplicationContext(), "DNS health " + protocol.label + " = " + ms + "ms");
            return ms > 0;
        } catch (Exception e) {
            LogHelper.log(getApplicationContext(), "DNS health exception: " + e.getMessage());
            return false;
        }
    }

    private ParcelFileDescriptor establishVpn() throws IOException {
        Builder builder = new Builder();
        builder.setSession(getString(R.string.app_name))
                .setMtu(1500)
                .allowFamily(OsConstants.AF_INET);

        String addr = (!useDhcp && ipv4 != null && !ipv4.isEmpty()) ? ipv4 : VPN_ADDR;
        builder.addAddress(addr, 32);

        if (needsIntercept()) {
            builder.allowFamily(OsConstants.AF_INET6);
            builder.addAddress("fd66:6463::2", 128);
            builder.addDnsServer(VPN_DNS);
            builder.addRoute(VPN_DNS, 32);
            builder.setBlocking(true);
        } else {
            addPlainDns(builder);
            builder.addRoute(addr, 32);
            builder.setBlocking(false);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false);
        }
        try {
            builder.addDisallowedApplication(getPackageName());
        } catch (Exception e) {
            LogHelper.log(getApplicationContext(), "disallow self failed: " + e.getMessage());
        }
        return builder.establish();
    }

    private void addPlainDns(Builder builder) {
        boolean added = false;
        if (HostValidator.isValidDnsAddress(dns1)) {
            try { builder.addDnsServer(dns1); added = true; } catch (Exception e) {
                LogHelper.log(getApplicationContext(), "addDns " + dns1 + " failed");
            }
        }
        if (HostValidator.isValidDnsAddress(dns2)) {
            try { builder.addDnsServer(dns2); } catch (Exception ignored) {}
        }
        if (HostValidator.isValidDnsAddress(ipv6Dns1)) {
            try { builder.addDnsServer(ipv6Dns1); } catch (Exception ignored) {}
        }
        if (HostValidator.isValidDnsAddress(ipv6Dns2)) {
            try { builder.addDnsServer(ipv6Dns2); } catch (Exception ignored) {}
        }
        if (!added) {
            builder.addDnsServer("1.1.1.1");
        }
    }

    private void startForegroundServiceWithNotification() {
        createNotificationChannel();
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } catch (Exception e) {
                startForeground(NOTIF_ID, notification);
            }
        } else {
            startForeground(NOTIF_ID, notification);
        }
    }

    private void updateNotification() {
        try {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.notify(NOTIF_ID, buildNotification());
        } catch (Exception ignored) {}
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        SharedPreferences prefs = getSharedPreferences("vpn_prefs", MODE_PRIVATE);
        boolean isEnglish = prefs.getBoolean("english_language", false);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                isEnglish ? "VPN Service Channel" : "کانال سرویس VPN",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(isEnglish
                ? "Channel for DNS VPN service notifications"
                : "کانال اطلاعرسانی سرویس تغییر DNS");
        channel.setShowBadge(false);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        SharedPreferences prefs = getSharedPreferences("vpn_prefs", MODE_PRIVATE);
        boolean isEnglish = prefs.getBoolean("english_language", false);
        String title = isEnglish ? "DNS Active · " + protocol.label : "DNS فعال · " + protocol.label;
        String content = buildNotificationContent(isEnglish);
        String actionText = isEnglish ? "Disconnect" : "قطع اتصال";

        Intent stopIntent = new Intent(this, MyVpnService.class);
        stopIntent.setAction(ACTION_STOP_VPN);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 0, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent mainIntent = new Intent(this, DnschangerActivity.class);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 1, mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                .setSmallIcon(R.drawable.ic_shield)
                .addAction(R.drawable.ic_vpn_off, actionText, stopPendingIntent)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setWhen(System.currentTimeMillis())
                .build();
    }

    private String buildNotificationContent(boolean isEnglish) {
        StringBuilder content = new StringBuilder();
        content.append(protocol.label).append(" · ");
        if (protocol == DnsProtocol.DOH && dohUrl != null && !dohUrl.isEmpty()) {
            content.append(dohUrl);
        } else if (protocol == DnsProtocol.DOT) {
            String host = (hostname != null && !hostname.isEmpty()) ? hostname : dns1;
            content.append(host).append(":").append(dnsPort);
        } else {
            content.append(dns1);
            if (dns2 != null && !dns2.isEmpty()) content.append(", ").append(dns2);
        }
        return content.toString();
    }

    private void stopVpn() {
        if (!isRunning && vpnInterface == null && engine == null && forwarder == null) {
            return;
        }
        isRunning = false;
        LogHelper.log(getApplicationContext(), "Stopping VPN");
        final TunDnsForwarder f = forwarder;
        final DnsQueryEngine e = engine;
        final ParcelFileDescriptor iface = vpnInterface;
        final Thread t = vpnThread;
        forwarder = null;
        engine = null;
        vpnInterface = null;
        vpnThread = null;
        try {
            stopForeground(true);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.cancel(NOTIF_ID);
        } catch (Exception ignored) {}
        saveVpnState(false);
        sendVpnStateBroadcast(false, null);
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (f != null) {
                    try { f.stop(); } catch (Exception ignored) {}
                }
                if (e != null) {
                    try { e.shutdown(); } catch (Exception ignored) {}
                }
                if (iface != null) {
                    try { iface.close(); } catch (Exception ignored) {}
                }
                if (t != null && t != Thread.currentThread()) {
                    try { t.interrupt(); } catch (Exception ignored) {}
                }
            }
        }, "VpnCleanup").start();
        stopSelf();
    }

    private void saveVpnState(boolean isActive) {
        getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("vpn_active", isActive).apply();
    }

    private void sendVpnStateBroadcast(boolean isActive, String errorMessage) {
        Intent intent = new Intent(ACTION_STATE);
        intent.setPackage(getPackageName());
        intent.putExtra("isActive", isActive);
        intent.putExtra("protocol", protocol.label);
        if (errorMessage != null) intent.putExtra("error", errorMessage);
        sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterAutoReconnect();
        stopVpn();
    }

    @Override
    public void onRevoke() {
        super.onRevoke();
        stopVpn();
    }

    public static boolean isRunning(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return false;
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (MyVpnService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
