package dns.changer.deepcode;

import android.content.Context;
import android.net.VpnService;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.CertPathValidatorException;
import java.security.cert.X509Certificate;
import java.security.SecureRandom;
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
    private boolean emitLogs = true;

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
        byte[] response = queryInternal(dnsMessage);
        // Some DoH/DoT servers answer with a different (often zero) transaction ID. The system
        // resolver silently drops such answers, so always echo the ID of the original question.
        if (response != null && response.length >= 12 && dnsMessage != null && dnsMessage.length >= 2) {
            response[0] = dnsMessage[0];
            response[1] = dnsMessage[1];
        }
        return response;
    }

    private byte[] queryInternal(byte[] dnsMessage) {
        if (dnsMessage == null || dnsMessage.length < 12) return null;
        Exception last = null;

        if (protocol == DnsProtocol.DOH) {
            try {
                byte[] result = queryDoh(dnsMessage);
                if (result != null && result.length >= 12) return result;
            } catch (Exception e) {
                last = e;
            }
            if (last != null) {
                if (emitLogs) {
                    LogHelper.logRateLimited(context, "doh-failed:" + dohUrl,
                            "DoH endpoint unavailable (" + describe(last) + ")", 15000L);
                }
            }
            return null;
        }

        String[] targets = effectiveTargets();
        Set<String> tried = new HashSet<>();
        for (int i = 0; i < targets.length; i++) {
            String target = targets[i];
            if (target == null || target.trim().isEmpty()) continue;
            tried.add(target.trim());
            try {
                byte[] result = queryOnce(target.trim(), dnsMessage);
                if (result != null && result.length >= 12) return result;
            } catch (Exception e) {
                last = e;
            }
        }
        // DoT: the configured IPs may not actually host the TLS name; try what the hostname resolves to.
        if (protocol == DnsProtocol.DOT && hostname != null && !hostname.trim().isEmpty()) {
            for (String ip : resolver.resolveQuick(hostname.trim())) {
                if (!tried.add(ip)) continue;
                try {
                    byte[] result = queryOnce(ip, dnsMessage);
                    if (result != null && result.length >= 12) return result;
                } catch (Exception e) {
                    last = e;
                }
            }
        }
        if (last != null) {
            if (emitLogs) {
                LogHelper.logRateLimited(context, protocol.name() + "-failed",
                        protocol.label + " endpoints unavailable (" + describe(last) + ")", 15000L);
            }
        }
        return null;
    }

    private static String describe(Exception e) {
        if (e == null) return "no response";
        // The message on the outer SSLHandshakeException ("Chain validation failed") is generic.
        // The real reason lives further down the cause chain, in a CertPathValidatorException,
        // whose getReason() says exactly what failed (NO_TRUST_ANCHOR, EXPIRED, REVOKED, ...).
        StringBuilder sb = new StringBuilder();
        Throwable t = e;
        int depth = 0;
        while (t != null && depth < 6) {
            if (sb.length() > 0) sb.append(" <- caused by ");
            sb.append(t.getClass().getSimpleName());
            if (t instanceof CertPathValidatorException) {
                CertPathValidatorException cpve = (CertPathValidatorException) t;
                if (cpve.getReason() != null) sb.append("[reason=").append(cpve.getReason()).append("]");
                if (cpve.getIndex() >= 0) sb.append("[certIndex=").append(cpve.getIndex()).append("]");
            }
            String m = t.getMessage();
            if (m != null && !m.isEmpty()) {
                if (m.length() > 80) m = m.substring(0, 80);
                sb.append(": ").append(m);
            }
            if (t.getCause() == t) break;
            t = t.getCause();
            depth++;
        }
        return sb.length() > 0 ? sb.toString() : "no response";
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

    /** UI ping probes are diagnostics and should not fill the user-facing log. */
    public void setLoggingEnabled(boolean enabled) {
        emitLogs = enabled;
        resolver.setLoggingEnabled(enabled);
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
        // Connect the SSLSocket directly rather than layering it over a separately-connected
        // plain Socket. The older layered form (SSLSocketFactory.createSocket(Socket, host,
        // port, autoClose)) has known cross-OEM inconsistencies in how the SNI / hostname it is
        // given gets applied; a direct, unconnected SSLSocket with SNI and endpoint identification
        // set explicitly via SSLParameters is the documented, reliable way (the same mechanism
        // OkHttp and Netty use internally) and behaves the same on every vendor build.
        SSLSocket ssl = null;
        try {
            SSLSocketFactory factory = AppTrustManager.socketFactory(context);
            ssl = (SSLSocket) factory.createSocket();
            protect(ssl);
            SSLParameters params = ssl.getSSLParameters();
            params.setServerNames(Collections.<SNIServerName>singletonList(new SNIHostName(sni)));
            params.setEndpointIdentificationAlgorithm("HTTPS");
            ssl.setSSLParameters(params);
            ssl.setEnabledProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
            ssl.connect(new InetSocketAddress(InetAddress.getByName(ip), port), TIMEOUT_MS);
            ssl.setSoTimeout(TIMEOUT_MS);
            ssl.startHandshake();
        } catch (Exception e) {
            closeQuietly(ssl);
            // Diagnostics only: reveals whether the failure is a DPI block page / wrong IP
            // (unrelated certificate) versus a real, but untrusted, certificate for this host.
            if (isCertError(e)) {
                logPresentedCertificate(ip, sni);
            }
            throw e;
        }
        tlsSockets[slot] = ssl;
        if (emitLogs) {
            LogHelper.logRateLimited(context, "dot-handshake:" + sni + ":" + port,
                    "DoT connection ready", 60000L);
        }
        return ssl;
    }

    private static boolean isCertError(Exception e) {
        String cls = e.getClass().getName();
        return cls.contains("CertificateException") || cls.contains("SSLHandshakeException")
                || cls.contains("SSLPeerUnverifiedException");
    }

    /**
     * Connects once more with certificate validation turned off, purely to log what the
     * server actually presented. Never used for the real query — only for the log line.
     */
    private void logPresentedCertificate(String ip, String sni) {
        SSLSocket ssl = null;
        try {
            SSLContext trustAll = SSLContext.getInstance("TLS");
            trustAll.init(null, new TrustManager[]{new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }}, new SecureRandom());
            ssl = (SSLSocket) trustAll.getSocketFactory().createSocket();
            protect(ssl);
            SSLParameters params = ssl.getSSLParameters();
            params.setServerNames(Collections.<SNIServerName>singletonList(new SNIHostName(sni)));
            ssl.setSSLParameters(params);
            ssl.connect(new InetSocketAddress(InetAddress.getByName(ip), port), TIMEOUT_MS);
            ssl.setSoTimeout(TIMEOUT_MS);
            ssl.startHandshake();
            X509Certificate[] chain = (X509Certificate[]) ssl.getSession().getPeerCertificates();
            if (chain.length > 0 && emitLogs) {
                // Log every certificate the server actually sent, in order. This is what tells
                // us whether the server sent a complete chain (leaf + intermediate) or only the
                // leaf, which is the key fact for diagnosing a "chain validation failed" error.
                StringBuilder sb = new StringBuilder();
                sb.append("Server ").append(ip).append(" sent ").append(chain.length)
                        .append(" cert(s): ");
                for (int i = 0; i < chain.length; i++) {
                    if (i > 0) sb.append("  ->  ");
                    sb.append("[").append(i).append("] ").append(chain[i].getSubjectDN());
                }
                LogHelper.logRateLimited(context, "dot-cert-peek:" + ip, sb.toString(), 60000L);
            }
        } catch (Exception ignored) {
            // The connection itself failing here is its own answer (e.g. blocked at TCP level).
        } finally {
            closeQuietly(ssl);
        }
    }

    /** Set once a server proved it only accepts RFC 8484 GET requests. */
    private volatile boolean dohPreferGet = false;

    private static final class DohHttpException extends IOException {
        final int code;
        DohHttpException(int code) {
            super("DoH HTTP " + code);
            this.code = code;
        }
    }

    private byte[] queryDoh(byte[] dnsMessage) throws Exception {
        String url = dohUrl;
        if (url == null || url.isEmpty()) {
            throw new IllegalStateException("DoH URL is empty");
        }
        if (dohPreferGet) {
            return dohGet(url, dnsMessage);
        }
        try {
            return dohPost(url, dnsMessage);
        } catch (DohHttpException postError) {
            // The server is reachable but rejected POST (e.g. 400/405/415): try GET (RFC 8484).
            try {
                byte[] r = dohGet(url, dnsMessage);
                dohPreferGet = true;
                return r;
            } catch (Exception ignored) {
                throw postError;
            }
        }
    }

    private byte[] dohPost(String url, byte[] dnsMessage) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/dns-message")
                .post(RequestBody.create(dnsMessage, DNS_MESSAGE))
                .build();
        return executeDoh(request);
    }

    private byte[] dohGet(String url, byte[] dnsMessage) throws Exception {
        String b64 = android.util.Base64.encodeToString(dnsMessage,
                android.util.Base64.URL_SAFE | android.util.Base64.NO_PADDING | android.util.Base64.NO_WRAP);
        String full = url + (url.contains("?") ? "&" : "?") + "dns=" + b64;
        Request request = new Request.Builder()
                .url(full)
                .header("Accept", "application/dns-message")
                .get()
                .build();
        return executeDoh(request);
    }

    private byte[] executeDoh(Request request) throws Exception {
        Response response = http().newCall(request).execute();
        try {
            if (!response.isSuccessful() || response.body() == null) {
                throw new DohHttpException(response.code());
            }
            return response.body().bytes();
        } finally {
            response.close();
        }
    }

    private OkHttpClient http() {
        if (httpClient != null) return httpClient;
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .writeTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .followRedirects(true)
                .socketFactory(new ProtectedSocketFactory(vpn))
                .dns(new ProtectedDns());
        X509TrustManager tm = AppTrustManager.trustManager(context);
        if (tm != null) {
            builder.sslSocketFactory(AppTrustManager.socketFactory(context), tm);
        }
        httpClient = builder.build();
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
            if (protocol == DnsProtocol.DOH) {
                // Bootstrap IPs first, then whatever the DoH host really resolves to, so a
                // bootstrap IP that does not serve HTTPS no longer breaks the endpoint.
                for (String ip : resolver.resolveQuick(hostname)) {
                    if (!ips.contains(ip)) ips.add(ip);
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

        // A closed / half-closed connection must surface as an exception: the DoT pool
        // then drops the dead socket and reconnects instead of failing on it forever.
        int b1 = in.read();
        int b2 = in.read();
        if (b1 < 0 || b2 < 0) throw new EOFException("connection closed by server");
        int respLen = (b1 << 8) | b2;
        if (respLen <= 0 || respLen > 8192) throw new IOException("bad DNS length " + respLen);
        byte[] buf = new byte[respLen];
        int off = 0;
        while (off < respLen) {
            int n = in.read(buf, off, respLen - off);
            if (n < 0) throw new EOFException("connection closed by server");
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
