package dns.changer.deepcode;

public enum DnsProtocol {
    UDP("UDP", 53),
    TCP("TCP", 53),
    DOT("DoT", 853),
    DOH("DoH", 443);

    public final String label;
    public final int defaultPort;

    DnsProtocol(String label, int defaultPort) {
        this.label = label;
        this.defaultPort = defaultPort;
    }

    public static DnsProtocol from(String value) {
        if (value == null) return UDP;
        String v = value.trim();
        if (v.equalsIgnoreCase("TCP")) return TCP;
        if (v.equalsIgnoreCase("DOT") || v.equalsIgnoreCase("DoT") || v.equalsIgnoreCase("TLS")) return DOT;
        if (v.equalsIgnoreCase("DOH") || v.equalsIgnoreCase("DoH") || v.equalsIgnoreCase("HTTPS")) return DOH;
        return UDP;
    }
}
