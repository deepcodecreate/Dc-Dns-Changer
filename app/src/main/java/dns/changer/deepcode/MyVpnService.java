package dns.changer.deepcode;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import dns.changer.deepcode.R;
import android.content.pm.ServiceInfo;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;

public class MyVpnService extends VpnService implements Runnable {

    private static final String TAG = "MyVpnService";
    private static final int NOTIF_ID = 1;
    private static final String CHANNEL_ID = "vpn_channel";
    private static final String ACTION_STOP_VPN = "DISCONNECT_VPN";

    private DnsTcpProxy dnsTcpProxy;
    private boolean useTcp = false;

    private Thread vpnThread;
    private ParcelFileDescriptor vpnInterface;
    private boolean isRunning = false;

    private String dns1 = "78.157.42.101";
    private String dns2 = "78.157.42.100";
    private String ipv6Dns1 = "";
    private String ipv6Dns2 = "";
    private String ipv4 = "10.0.0.2";
    private boolean useDhcp = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
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
        useTcp = prefs.getBoolean("dns_over_tcp", false);
        
        LogHelper.log(getApplicationContext(), "Starting VPN with settings - TCP: " + useTcp);

        // اضافه شدن تنظیم پارامترهای ارتباطی مورد نیاز پروکسی قبل از استارت آن
        if (useTcp) {
            loadDnsSettings(intent); // لود اولیه مقادیر dns1 جهت انتقال به پروکسی
            prefs.edit()
                 .putString("dot_server", (dns1 != null && !dns1.isEmpty()) ? dns1 : "78.157.42.101")
                 .putString("dot_port", "5353")
                 .apply();
            startDnsTcpProxy();
        }

        if (!runInBackground && !isRunning) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkNotificationPermission();
        }

        loadDnsSettings(intent);
        
        try {
            startForegroundServiceWithNotification();
        } catch (SecurityException e) {
            LogHelper.log(getApplicationContext(), "Failed to start foreground service: " + e.getMessage());
            stopVpn();
            sendVpnStateBroadcast(false, "Failed to start VPN due to missing permissions");
            return START_NOT_STICKY;
        }

        if (vpnThread == null || !vpnThread.isAlive()) {
            startVpnThread();
        }

        saveVpnState(true);
        sendVpnStateBroadcast(true, null);
        return START_STICKY;
    }

    private void startDnsTcpProxy() {
        if (dnsTcpProxy == null) {
            dnsTcpProxy = new DnsTcpProxy(getApplicationContext());
            dnsTcpProxy.start();
            LogHelper.log(getApplicationContext(), "DNS over TCP proxy started");
        }
    }


    private void checkNotificationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            LogHelper.log(getApplicationContext(), "Notification permission not granted");
            stopVpn();
            sendVpnStateBroadcast(false, "Notification permission required");
        }
    }

    private void loadDnsSettings(Intent intent) {
        if (intent != null) {
            dns1 = intent.getStringExtra("dns1");
            dns2 = intent.getStringExtra("dns2");
            ipv6Dns1 = intent.getStringExtra("ipv6_dns1");
            ipv6Dns2 = intent.getStringExtra("ipv6_dns2");
            String ip = intent.getStringExtra("ipv4");
            useDhcp = intent.getBooleanExtra("use_dhcp", false);

            if (ip != null && !ip.isEmpty()) {
                ipv4 = ip;
            }
        }
    }

    private void startVpnThread() {
        isRunning = true;
        vpnThread = new Thread(this, "MyVpnThread");
        vpnThread.start();
        LogHelper.log(getApplicationContext(), "VPN thread started");
    }

    private void startForegroundServiceWithNotification() {
        createNotificationChannel();

        SharedPreferences prefs = getSharedPreferences("vpn_prefs", MODE_PRIVATE);
        boolean isEnglish = prefs.getBoolean("english_language", false);

        String title = isEnglish ? "DNS Active" : "DNS فعال است";
        String contentText = buildNotificationContent(isEnglish);
        String actionText = isEnglish ? "Disconnect" : "قطع اتصال";

        Notification notification = buildNotification(title, contentText, actionText);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } catch (Exception e) {
                LogHelper.log(getApplicationContext(), "Failed to start with special use type, falling back: " + e.getMessage());
                startForeground(NOTIF_ID, notification);
            }
        } else {
            startForeground(NOTIF_ID, notification);
        }
    }

    private String buildNotificationContent(boolean isEnglish) {
        SharedPreferences prefs = getSharedPreferences("vpn_prefs", MODE_PRIVATE);
        StringBuilder content = new StringBuilder();

        content.append(isEnglish ? "IPv4 DNS: " : "DNS IPv4: ")
                .append(dns1)
                .append(dns2.isEmpty() ? "" : ", " + dns2);

        if (!ipv6Dns1.isEmpty()) {
            content.append(isEnglish ? "\nIPv6 DNS: " : "\nDNS IPv6: ")
                    .append(ipv6Dns1)
                    .append(ipv6Dns2.isEmpty() ? "" : ", " + ipv6Dns2);
        }

        if (useTcp) {
            String dotServer = prefs.getString("dot_server", "127.0.0.1");
            String dotPort = prefs.getString("dot_port", "5353");
            content.append("\nTCP Proxy: ")
                    .append(dotServer)
                    .append(":")
                    .append(dotPort);
        }

        return content.toString();
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private void createNotificationChannel() {
        SharedPreferences prefs = getSharedPreferences("vpn_prefs", MODE_PRIVATE);
        boolean isEnglish = prefs.getBoolean("english_language", false);

        String channelName = isEnglish ? "VPN Service Channel" : "کانال سرویس VPN";
        String channelDescription = isEnglish ?
                "Channel for DNS VPN service notifications" :
                "کانال اطلاعرسانی سرویس تغییر DNS";

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                channelName,
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(channelDescription);
        channel.setShowBadge(false);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String title, String contentText, String actionText) {
        Intent stopIntent = new Intent(this, MyVpnService.class);
        stopIntent.setAction(ACTION_STOP_VPN);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this,
                0,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent mainIntent = new Intent(this, DnschangerActivity.class);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                1,
                mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_shield)
                .addAction(R.drawable.ic_vpn_off, actionText, stopPendingIntent)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setWhen(System.currentTimeMillis())
                .build();
    }

    @Override
    public void run() {
        try {
            LogHelper.log(getApplicationContext(), "Starting VPN connection with DNS: " + dns1 + ", " + dns2 +
                    " and IPv6 DNS: " + ipv6Dns1 + ", " + ipv6Dns2);

            if (vpnInterface != null) {
                vpnInterface.close();
                vpnInterface = null;
            }

            vpnInterface = establishVpn();

            if (vpnInterface != null) {
                LogHelper.log(getApplicationContext(), "VPN connection established successfully");
                maintainVpnConnection();
            } else {
                handleVpnEstablishFailure();
            }
        } catch (Exception e) {
            handleVpnError(e);
        } finally {
            cleanupVpnResources();
        }
    }

    private void maintainVpnConnection() throws InterruptedException {
        while (!Thread.interrupted() && isRunning) {
            if (useTcp && dnsTcpProxy != null) {
                handleTcpQueries();
            }
            Thread.sleep(1000);
        }
    }

    private void handleTcpQueries() {
        // Optional
    }

    private void handleVpnEstablishFailure() {
        LogHelper.log(getApplicationContext(), "Failed to establish VPN connection");
        sendVpnStateBroadcast(false, "Failed to establish VPN connection");
    }

    private void handleVpnError(Exception e) {
        LogHelper.log(getApplicationContext(), "Error in VPN connection: " + e.getMessage());
        sendVpnStateBroadcast(false, "VPN connection error: " + e.getMessage());
    }

    private void cleanupVpnResources() {
        stopVpn();
    }

    private ParcelFileDescriptor establishVpn() throws IOException {
        Builder builder = new Builder();
        configureVpnBuilder(builder);
        return builder.establish();
    }

    private void configureVpnBuilder(Builder builder) {
        builder.setSession(getString(R.string.app_name))
                .setConfigureIntent(null)
                .setBlocking(true)
                .setMtu(1500);

        configureVpnAddresses(builder);
        configureDnsServers(builder);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false);
        }
    }

    private void configureVpnAddresses(Builder builder) {
        if (!useDhcp && ipv4 != null && !ipv4.isEmpty()) {
            builder.addAddress(ipv4, 24);
        } else {
            builder.addAddress("192.168.1.100", 24);
        }
        
        // Add default route for all traffic
        builder.addRoute("0.0.0.0", 24);
        
        //IPv6 routes if IPv6
        if (!ipv6Dns1.isEmpty() || !ipv6Dns2.isEmpty()) {
            try {
                builder.addRoute("::", 0);
            } catch (Exception e) {
                LogHelper.log(getApplicationContext(), "Failed to add IPv6 route: " + e.getMessage());
            }
        }
    }

    private void configureDnsServers(Builder builder) {
        List<String> dnsServers = new ArrayList<>();
        
        // در صورتی که قابلیت TCP فعال باشد، ترافیک دی‌ان‌اس مستقیما به لوکال‌هاست و پروکسی فرستاده می‌شود
        if (useTcp) {
            dnsServers.add("127.0.0.1");
        } else {
            if (isValidDnsAddress(dns1)) dnsServers.add(dns1);
            if (isValidDnsAddress(dns2)) dnsServers.add(dns2);
            if (isValidDnsAddress(ipv6Dns1)) dnsServers.add(ipv6Dns1);
            if (isValidDnsAddress(ipv6Dns2)) dnsServers.add(ipv6Dns2);
        }

        if (dnsServers.isEmpty()) {
            dnsServers.add("8.8.8.8");
        }

        for (String dns : dnsServers) {
            try {
                builder.addDnsServer(dns);
            } catch (Exception e) {
                LogHelper.log(getApplicationContext(), "Failed to add DNS server " + dns + ": " + e.getMessage());
            }
        }
    }

    private boolean isValidDnsAddress(String address) {
        if (address == null || address.isEmpty()) {
            return false;
        }
        try {
            InetAddress.getByName(address);
            return true;
        } catch (Exception e) {
            LogHelper.log(getApplicationContext(), "Invalid DNS address: " + address);
            return false;
        }
    }

    private void stopVpn() {
        isRunning = false;
        LogHelper.log(getApplicationContext(), "Stopping VPN service completely");
        
        closeVpnInterface();
        stopVpnThread();
        stopDnsProxies();
        stopForegroundService();
        sendVpnStateBroadcast(false, null);
        stopSelf();
    }

    private void closeVpnInterface() {
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
                LogHelper.log(getApplicationContext(), "VPN interface closed");
            } catch (IOException e) {
                LogHelper.log(getApplicationContext(), "Error closing VPN interface: " + e.getMessage());
            }
            vpnInterface = null;
        }
    }

    private void stopVpnThread() {
        if (vpnThread != null) {
            vpnThread.interrupt();
            try {
                vpnThread.join(1000);
            } catch (InterruptedException e) {
                LogHelper.log(getApplicationContext(), "Error stopping VPN thread: " + e.getMessage());
            }
            vpnThread = null;
            LogHelper.log(getApplicationContext(), "VPN thread stopped");
        }
    }

    private void stopDnsProxies() {
        if (dnsTcpProxy != null) {
            try {
                dnsTcpProxy.shutdown();
                LogHelper.log(getApplicationContext(), "DNS over TCP proxy stopped");
            } catch (Exception e) {
                LogHelper.log(getApplicationContext(), "Error stopping DNS over TCP proxy: " + e.getMessage());
            }
            dnsTcpProxy = null;
        }

    }

    private void stopForegroundService() {
        try {
            stopForeground(true);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.cancel(NOTIF_ID);
            }
            LogHelper.log(getApplicationContext(), "Foreground service stopped");
        } catch (Exception e) {
            LogHelper.log(getApplicationContext(), "Error stopping foreground service: " + e.getMessage());
        }
    }

    private void saveVpnState(boolean isActive) {
        SharedPreferences prefs = getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("vpn_active", isActive).apply();
        LogHelper.log(getApplicationContext(), "VPN state saved: " + isActive);
    }
    
    
    private void sendVpnStateBroadcast(boolean isActive, String errorMessage) {
        Intent intent = new Intent("VPN_STATE_CHANGED");
        intent.putExtra("isActive", isActive);
        if (errorMessage != null) {
            intent.putExtra("error", errorMessage);
        }
        sendBroadcast(intent);
        LogHelper.log(getApplicationContext(), "VPN state broadcast sent: " + isActive + 
            (errorMessage != null ? " with error: " + errorMessage : ""));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LogHelper.log(getApplicationContext(), "VPN Service destroyed");
        stopVpn();
    }

    public static boolean isRunning(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (MyVpnService.class.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }
}
