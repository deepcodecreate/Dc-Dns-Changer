package dns.changer.deepcode;

import java.net.InetAddress;
import java.util.regex.Pattern;

public final class HostValidator {
    private static final Pattern IPV4 = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|[0-1]?\\d{1,2})\\.){3}(25[0-5]|2[0-4]\\d|[0-1]?\\d{1,2})$");
    private static final Pattern HOSTNAME = Pattern.compile(
            "^(?=.{1,253}$)([A-Za-z0-9](?:[A-Za-z0-9\\-]{0,61}[A-Za-z0-9])?)(?:\\.[A-Za-z0-9](?:[A-Za-z0-9\\-]{0,61}[A-Za-z0-9])?)*\\.?$");
    private static final Pattern DOH_URL = Pattern.compile(
            "^https://[A-Za-z0-9._~:/?#\\[\\]@!$&'()*+,;=%\\-]+$");
    private static final Pattern IPV6 = Pattern.compile(
            "^(([0-9A-Fa-f]{1,4}:){7}[0-9A-Fa-f]{1,4}|" +
            "([0-9A-Fa-f]{1,4}:){1,7}:|" +
            "([0-9A-Fa-f]{1,4}:){1,6}:[0-9A-Fa-f]{1,4}|" +
            "([0-9A-Fa-f]{1,4}:){1,5}(:[0-9A-Fa-f]{1,4}){1,2}|" +
            "([0-9A-Fa-f]{1,4}:){1,4}(:[0-9A-Fa-f]{1,4}){1,3}|" +
            "([0-9A-Fa-f]{1,4}:){1,3}(:[0-9A-Fa-f]{1,4}){1,4}|" +
            "([0-9A-Fa-f]{1,4}:){1,2}(:[0-9A-Fa-f]{1,4}){1,5}|" +
            "[0-9A-Fa-f]{1,4}:((:[0-9A-Fa-f]{1,4}){1,6})|" +
            ":((:[0-9A-Fa-f]{1,4}){1,7}|:))$");

    private HostValidator() {}

    public static boolean isSafeHost(String host) {
        if (host == null) return false;
        String h = host.trim();
        if (h.isEmpty() || h.length() > 253) return false;
        if (h.indexOf(' ') >= 0 || h.indexOf(';') >= 0 || h.indexOf('|') >= 0
                || h.indexOf('&') >= 0 || h.indexOf('$') >= 0 || h.indexOf('`') >= 0
                || h.indexOf('\n') >= 0 || h.indexOf('\r') >= 0) {
            return false;
        }
        if (h.startsWith("[") && h.endsWith("]")) {
            h = h.substring(1, h.length() - 1);
        }
        return isIpv4(h) || isIpv6(h) || HOSTNAME.matcher(h).matches();
    }

    public static boolean isIpv4(String host) {
        return host != null && IPV4.matcher(host.trim()).matches();
    }

    public static boolean isIpv6(String host) {

        if (host == null || host.isEmpty()) return false;
        return IPV6.matcher(host.trim()).matches();
    }

    public static boolean isDohUrl(String url) {
        return url != null && DOH_URL.matcher(url.trim()).matches();
    }

    public static boolean isValidDnsAddress(String address) {
        if (address == null || address.trim().isEmpty()) return false;
        if (!isSafeHost(address)) return false;
        try {
            InetAddress.getByName(address.trim());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
