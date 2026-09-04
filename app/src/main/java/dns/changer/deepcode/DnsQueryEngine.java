package dns.changer.deepcode;

import android.content.Context;
import android.net.VpnService;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import okhttp3.Dns;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class DnsQueryEngine {
    private static final MediaType DNS_MESSAGE = MediaType.parse("application/dns-message");
    private static volatile int TIMEOUT_MS = 4000;

    
    public static void configureTimeoutMs(int ms) {
        if (ms >= 1000 && ms <= 15000) TIMEOUT_MS = ms;
    }

    public static int getConfiguredTimeoutMs() {
        return TIMEOUT_MS;
    }

    private final VpnService vpn;
    private final Context context;
    private final DnsProtocol protocol;
    private final String primary;
    private final String secondary;
    private final String hostname;
    private final String dohUrl;
    private final int port;

    private static final int TLS_POOL_SIZE = 3;
    private OkHttpClient httpClient;
    private final Object[] tlsLocks = new Object[TLS_POOL_SIZE];
    private final SSLSocket[] tlsSockets = new SSLSocket[TLS_POOL_SIZE];
    private final AtomicInteger tlsRoundRobin = new AtomicInteger(0);

    private final ProtectedHostResolver resolver;

    public DnsQueryEngine(VpnService vpn, Context context, DnsProtocol protocol,
                          String primary, String secondary, String hostname,
                          String dohUrl, int port) {
        this.vpn = vpn;
        this.context = context;
        this.protocol = protocol == null ? DnsProtocol.UDP : protocol;
        this.primary = primary;
        this.secondary = secondary;
        this.hostname = hostname;
        this.dohUrl = dohUrl;
        this.port = port > 0 ? port : this.protocol.defaultPort;
        this.resolver = new ProtectedHostResolver(context, vpn);
        for (int i = 0; i < TLS_POOL_SIZE; i++) {
            tlsLocks[i] = new Object();
        }
    }

    public byte[] query(byte[] dnsMessage) {
        if (dnsMessage == null || dnsMessage.length < 12) return null;
        Exception last = null;

        if (protocol == DnsProtocol.DOH) {
            try {
                byte[] result = queryDoh(dnsMessage);
                if (result != null && result.length >= 12) return result;
            } catch (Exception e) {
                last = e;
                LogHelper.log(context, "DoH query failed: " + e.getMessage());
            }
            if (last != null) {
                LogHelper.log(context, "DoH endpoint failed: " + last.getMessage());
            }
            return null;
        }

        String[] targets = effectiveTargets();
        for (int i = 0; i < targets.length; i++) {
            String target = targets[i];
            if (target == null || target.trim().isEmpty()) continue;
            try {
                byte[] result = queryOnce(target.trim(), dnsMessage);
                if (result != null && result.length >= 12) return result;
            } catch (Exception e) {
                last = e;
                LogHelper.log(context, protocol.label + " query failed (" + target + "): " + e.getMessage());
            }
        }
        if (last != null) {
            LogHelper.log(context, "All " + protocol.label + " endpoints failed: " + last.getMessage());
        }
        return null;
    }

    
    private String[] effectiveTargets() {
        List<String> out = new ArrayList<>();
        addResolvedTarget(out, primary);
        addResolvedTarget(out, secondary);
        if (out.isEmpty() && protocol == DnsProtocol.DOT
                && hostname != null && !hostname.trim().isEmpty()) {
            out.addAll(resolver.resolve(hostname.trim()));
        }
        return out.toArray(new String[0]);
    }

    private void addResolvedTarget(List<String> out, String candidate) {
        if (candidate == null || candidate.trim().isEmpty()) return;
        String c = candidate.trim();
        if (HostValidator.isIpv4(c) || HostValidator.isIpv6(c)) {
            out.add(c);
        } else {
            out.addAll(resolver.resolve(c));
        }
    }

    public int measureLatencyMs() {
        byte[] probe = DnsPacketParser.buildQuery("captive.apple.com", 1);
        long t0 = System.currentTimeMillis();
        byte[] resp = query(probe);
        if (resp == null) return -2;
        return (int) Math.max(1, System.currentTimeMillis() - t0);
    }

    public void shutdown() {
        for (int i = 0; i < TLS_POOL_SIZE; i++) {
            synchronized (tlsLocks[i]) {
                closeQuietly(tlsSockets[i]);
                tlsSockets[i] = null;
            }
        }
    }

    private byte[] queryOnce(String target, byte[] dnsMessage) throws Exception {
        switch (protocol) {
            case TCP:
                return queryTcp(target, dnsMessage);
            case DOT:
                return queryDot(target, dnsMessage);
            case DOH:
                return queryDoh(dnsMessage);
            case UDP:
            default:
                return queryUdp(target, dnsMessage);
        }
    }

    private byte[] queryUdp(String ip, byte[] dnsMessage) throws Exception {
        DatagramSocket socket = new DatagramSocket();
        try {
            protect(socket);
            socket.setSoTimeout(TIMEOUT_MS);
            InetAddress addr = InetAddress.getByName(ip);
            DatagramPacket packet = new DatagramPacket(dnsMessage, dnsMessage.length, addr, 53);
            socket.send(packet);
            byte[] buf = new byte[4096];
            DatagramPacket resp = new DatagramPacket(buf, buf.length);
            socket.receive(resp);
            byte[] out = new byte[resp.getLength()];
            System.arraycopy(buf, 0, out, 0, resp.getLength());
            return out;
        } finally {
            socket.close();
        }
    }

    private byte[] queryTcp(String ip, byte[] dnsMessage) throws Exception {
        Socket socket = new Socket();
        try {
            protect(socket);
            socket.connect(new InetSocketAddress(ip, 53), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);
            return exchangeLengthPrefixed(socket, dnsMessage);
        } finally {
            socket.close();
        }
    }

    private byte[] queryDot(String ip, byte[] dnsMessage) throws Exception {
        int slot = Math.floorMod(tlsRoundRobin.getAndIncrement(), TLS_POOL_SIZE);
        Object lock = tlsLocks[slot];
        synchronized (lock) {
            SSLSocket ssl = ensureTls(ip, slot);
            try {
                return exchangeLengthPrefixed(ssl, dnsMessage);
            } catch (Exception e) {
                closeQuietly(tlsSockets[slot]);
                tlsSockets[slot] = null;
                ssl = ensureTls(ip, slot);
                return exchangeLengthPrefixed(ssl, dnsMessage);
            }
        }
    }

    private SSLSocket ensureTls(String ip, int slot) throws Exception {
        SSLSocket existing = tlsSockets[slot];
        if (existing != null && existing.isConnected() && !existing.isClosed()) {
            return existing;
        }
        String sni = (hostname != null && !hostname.isEmpty()) ? hostname : ip;
        Socket plain = new Socket();
        protect(plain);
        plain.connect(new InetSocketAddress(ip, port), TIMEOUT_MS);
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket ssl = (SSLSocket) factory.createSocket(plain, sni, port, true);
        ssl.setEnabledProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
        ssl.setSoTimeout(TIMEOUT_MS);
        ssl.startHandshake();
        tlsSockets[slot] = ssl;
        LogHelper.log(context, "DoT handshake OK → " + sni + ":" + port + " (slot " + slot + ")");
        return ssl;
    }

    private byte[] queryDoh(byte[] dnsMessage) throws Exception {
        String url = dohUrl;
        if (url == null || url.isEmpty()) {
            throw new IllegalStateException("DoH URL is empty");
        }
        OkHttpClient client = http();
        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/dns-message")
                .post(RequestBody.create(dnsMessage, DNS_MESSAGE))
                .build();
        Response response = client.newCall(request).execute();
        try {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IllegalStateException("DoH HTTP " + response.code());
            }
            return response.body().bytes();
        } finally {
            response.close();
        }
    }

    private OkHttpClient http() {
        if (httpClient != null) return httpClient;
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .writeTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .followRedirects(true)
                .socketFactory(new ProtectedSocketFactory(vpn))
                .dns(new ProtectedDns())
                .build();
        return httpClient;
    }

    
    private final class ProtectedDns implements Dns {
        @Override
        public List<InetAddress> lookup(String hostname) throws UnknownHostException {
            List<String> ips = new ArrayList<>();
            if (protocol == DnsProtocol.DOH) {
                if (primary != null && !primary.isEmpty() && HostValidator.isIpv4(primary)) {
                    ips.add(primary);
                }
                if (secondary != null && !secondary.isEmpty() && HostValidator.isIpv4(secondary)) {
                    ips.add(secondary);
                }
            }
            if (ips.isEmpty()) {
                ips = resolver.resolve(hostname);
            }
            List<InetAddress> out = new ArrayList<>();
            for (String ip : ips) {
                try {
                    out.add(InetAddress.getByName(ip));
                } catch (Exception ignored) {}
            }
            if (out.isEmpty()) {
                throw new UnknownHostException("Unable to resolve " + hostname);
            }
            return out;
        }
    }

    private byte[] exchangeLengthPrefixed(Socket socket, byte[] dnsMessage) throws Exception {
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();
        int len = dnsMessage.length;
        out.write((len >> 8) & 0xFF);
        out.write(len & 0xFF);
        out.write(dnsMessage);
        out.flush();

        int b1 = in.read();
        int b2 = in.read();
        if (b1 < 0 || b2 < 0) return null;
        int respLen = (b1 << 8) | b2;
        if (respLen <= 0 || respLen > 8192) return null;
        byte[] buf = new byte[respLen];
        int off = 0;
        while (off < respLen) {
            int n = in.read(buf, off, respLen - off);
            if (n < 0) return null;
            off += n;
        }
        return buf;
    }

    private void protect(Socket socket) {
        if (vpn != null) {
            vpn.protect(socket);
        }
    }

    private void protect(DatagramSocket socket) {
        if (vpn != null) {
            vpn.protect(socket);
        }
    }

    private static void closeQuietly(Socket socket) {
        if (socket == null) return;
        try { socket.close(); } catch (Exception ignored) {}
    }
}
