package dns.changer.deepcode;

import android.content.Context;

import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

final class AppTrustManager {
    private static volatile SSLSocketFactory socketFactory;
    private static volatile X509TrustManager trustManager;
    private static final Object LOCK = new Object();

    private AppTrustManager() {}

    static X509TrustManager trustManager(Context context) {
        init(context);
        return trustManager;
    }

    static SSLSocketFactory socketFactory(Context context) {
        init(context);
        return socketFactory;
    }

    private static void init(Context context) {
        if (socketFactory != null) return;
        synchronized (LOCK) {
            if (socketFactory != null) return;
            try {
                KeyStore merged = KeyStore.getInstance(KeyStore.getDefaultType());
                merged.load(null, null);

                int systemAnchorCount = 0;
                try {
                    KeyStore system = KeyStore.getInstance("AndroidCAStore");
                    system.load(null, null);
                    Enumeration<String> aliases = system.aliases();
                    while (aliases.hasMoreElements()) {
                        String alias = aliases.nextElement();
                        merged.setCertificateEntry(alias, system.getCertificate(alias));
                        systemAnchorCount++;
                    }
                } catch (Exception ignored) {

                }

                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                X509Certificate extra;
                try (java.io.InputStream in = context.getResources().openRawResource(R.raw.isrg_root_x1)) {
                    extra = (X509Certificate) cf.generateCertificate(in);
                }
                merged.setCertificateEntry("isrg_root_x1_bundled", extra);

                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(merged);
                X509TrustManager tm = null;
                for (TrustManager t : tmf.getTrustManagers()) {
                    if (t instanceof X509TrustManager) {
                        tm = (X509TrustManager) t;
                        break;
                    }
                }
                if (tm == null) throw new IllegalStateException("No X509TrustManager available");

                SSLContext ctx = SSLContext.getInstance("TLS");
                ctx.init(null, new TrustManager[]{tm}, null);

                trustManager = tm;
                socketFactory = ctx.getSocketFactory();
                LogHelper.logRateLimited(context, "trust-store-init",
                        "Custom trust store ready: " + systemAnchorCount + " system anchors + bundled root",
                        3600000L);
            } catch (Exception e) {

                trustManager = null;
                socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                LogHelper.logRateLimited(context, "trust-store-init-failed",
                        "Custom trust store failed (" + e.getClass().getSimpleName()
                                + ": " + e.getMessage() + "), using platform default", 3600000L);
            }
        }
    }
}
