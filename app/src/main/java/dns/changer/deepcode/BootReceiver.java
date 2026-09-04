package dns.changer.deepcode;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            SharedPreferences prefs = context.getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE);
            boolean runOnBoot = prefs.getBoolean("run_on_boot", false);
            boolean vpnActive = prefs.getBoolean("vpn_active", false);

            if (runOnBoot && vpnActive) {
                Intent vpnIntent = new Intent(context, MyVpnService.class);
                vpnIntent.putExtra("dns1", prefs.getString("dns1", "1.1.1.1"));
                vpnIntent.putExtra("dns2", prefs.getString("dns2", ""));
                vpnIntent.putExtra("ipv6_dns1", prefs.getString("ipv6_dns1", ""));
                vpnIntent.putExtra("ipv6_dns2", prefs.getString("ipv6_dns2", ""));
                vpnIntent.putExtra("protocol", prefs.getString("dns_protocol", "UDP"));
                vpnIntent.putExtra("hostname", prefs.getString("dot_hostname", ""));
                vpnIntent.putExtra("doh_url", prefs.getString("doh_url", ""));
                try {
                    vpnIntent.putExtra("dns_port", Integer.parseInt(prefs.getString("dns_port", "53")));
                } catch (Exception ignored) {}
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(vpnIntent);
                } else {
                    context.startService(vpnIntent);
                }
            }
        }
    }
}
