package dns.changer.deepcode;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.VpnService;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

final class ProtectedHostResolver {
    private static final String[] BOOTSTRAP_RESOLVERS = {
            "178.22.122.100",
            "185.51.200.2",
            "78.157.42.100",
            "10.202.10.10",
            "1.1.1.1",
            "8.8.8.8",
            "9.9.9.9"
    };
    private static final int BOOTSTRAP_TIMEOUT_MS = 2000;
    private static final long CACHE_TTL_MS = 10 * 60 * 1000L;

    private final Context context;
    private final VpnService vpn;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private static final class CacheEntry {
        final List<String> ips;
        final long ts;
        CacheEntry(List<String> ips, long ts) { this.ips = ips; this.ts = ts; }
    }

    ProtectedHostResolver(Context context, VpnService vpn) {
        this.context = context;
        this.vpn = vpn;
    }

    List<String> resolve(String host) {
        if (host == null) return Collections.emptyList();
        String h = host.trim();
        if (h.isEmpty()) return Collections.emptyList();
        if (h.startsWith("[") && h.endsWith("]")) h = h.substring(1, h.length() - 1);
        if (HostValidator.isIpv4(h) || HostValidator.isIpv6(h)) {
            return Collections.singletonList(h);
        }

        CacheEntry cached = cache.get(h);
        if (cached != null && !cached.ips.isEmpty()
                && System.currentTimeMillis() - cached.ts < CACHE_TTL_MS) {
            return filterPoison(cached.ips);
        }

        List<String> result = filterPoison(resolveViaUnderlyingNetwork(h));
        if (result.isEmpty()) result = filterPoison(resolveViaBootstrap(h));

        if (!result.isEmpty()) {
            cache.put(h, new CacheEntry(result, System.currentTimeMillis()));
            return result;
        }

        if (cached != null && !cached.ips.isEmpty()) return filterPoison(cached.ips);
        return result;
    }

    private static List<String> filterPoison(List<String> ips) {
        if (ips == null || ips.isEmpty()) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (String ip : ips) {
            if (ip == null) continue;
            String t = ip.trim();
            if (t.startsWith("10.10.34.")) continue;
            if (t.equals("0.0.0.0") || t.equals("127.0.0.1")) continue;
            out.add(t);
        }
        return out;
    }

    private List<String> resolveViaUnderlyingNetwork(String host) {
        List<String> out = new ArrayList<>();
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return out;
            Network[] networks = cm.getAllNetworks();
            for (Network network : networks) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                if (caps == null) continue;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue;
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
                try {
                    InetAddress[] addrs = network.getAllByName(host);
                    for (InetAddress a : addrs) out.add(a.getHostAddress());
                    if (!out.isEmpty()) return out;
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            LogHelper.log(context, "underlying-network resolve failed for " + host + ": " + e.getMessage());
        }
        return out;
    }

    private List<String> resolveViaBootstrap(String host) {
        for (String resolverIp : BOOTSTRAP_RESOLVERS) {
            List<String> ips = queryBootstrap(resolverIp, host);
            if (!ips.isEmpty()) return ips;
        }
        return Collections.emptyList();
    }

    private List<String> queryBootstrap(String resolverIp, String host) {
        List<String> out = new ArrayList<>();
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            if (vpn != null) vpn.protect(socket);
            socket.setSoTimeout(BOOTSTRAP_TIMEOUT_MS);
            byte[] query = DnsPacketParser.buildQuery(host, 1);
            InetAddress addr = InetAddress.getByName(resolverIp);
            socket.send(new DatagramPacket(query, query.length, addr, 53));
            byte[] buf = new byte[1024];
            DatagramPacket resp = new DatagramPacket(buf, buf.length);
            socket.receive(resp);
            out.addAll(parseARecords(buf, resp.getLength()));
        } catch (Exception ignored) {
        } finally {
            if (socket != null) socket.close();
        }
        return out;
    }

    private static List<String> parseARecords(byte[] data, int length) {
        List<String> out = new ArrayList<>();
        try {
            if (length < 12) return out;
            int qdCount = ((data[4] & 0xFF) << 8) | (data[5] & 0xFF);
            int anCount = ((data[6] & 0xFF) << 8) | (data[7] & 0xFF);
            int pos = 12;
            for (int i = 0; i < qdCount && pos < length; i++) {
                pos = skipName(data, pos, length);
                pos += 4;
            }
            for (int i = 0; i < anCount && pos < length; i++) {
                pos = skipName(data, pos, length);
                if (pos + 10 > length) break;
                int type = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
                int rdLength = ((data[pos + 8] & 0xFF) << 8) | (data[pos + 9] & 0xFF);
                pos += 10;
                if (pos + rdLength > length) break;
                if (type == 1 && rdLength == 4) {
                    out.add((data[pos] & 0xFF) + "." + (data[pos + 1] & 0xFF) + "."
                            + (data[pos + 2] & 0xFF) + "." + (data[pos + 3] & 0xFF));
                }
                pos += rdLength;
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static int skipName(byte[] data, int pos, int length) {
        while (pos < length) {
            int len = data[pos] & 0xFF;
            if (len == 0) {
                pos++;
                break;
            }
            if ((len & 0xC0) == 0xC0) {
                pos += 2;
                break;
            }
            pos += 1 + len;
        }
        return pos;
    }
}
