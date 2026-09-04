package dns.changer.deepcode;

import android.os.Parcel;
import android.os.Parcelable;

public class DnsServer implements Parcelable {
    private String name;
    private String dns1;
    private String dns2;
    private String ipv6Dns1;
    private String ipv6Dns2;
    private int ping = -2; // -2: not checked, -1: checking, >=0: ping value
    private String protocol;
    private String hostname;
    private String dohUrl;
    private int port;

    public DnsServer(String name, String dns1, String dns2, String ipv6Dns1, String ipv6Dns2) {
        this(name, dns1, dns2, ipv6Dns1, ipv6Dns2, "UDP", "", "", 53);
    }

    public DnsServer(String name, String dns1, String dns2, String ipv6Dns1, String ipv6Dns2,
                     String protocol, String hostname, String dohUrl, int port) {
        this.name = name;
        this.dns1 = dns1 == null ? "" : dns1;
        this.dns2 = dns2 == null ? "" : dns2;
        this.ipv6Dns1 = ipv6Dns1 == null ? "" : ipv6Dns1;
        this.ipv6Dns2 = ipv6Dns2 == null ? "" : ipv6Dns2;
        this.protocol = protocol == null ? "UDP" : protocol;
        this.hostname = hostname == null ? "" : hostname;
        this.dohUrl = dohUrl == null ? "" : dohUrl;
        this.port = port > 0 ? port : DnsProtocol.from(this.protocol).defaultPort;
    }

    protected DnsServer(Parcel in) {
        name = in.readString();
        dns1 = in.readString();
        dns2 = in.readString();
        ipv6Dns1 = in.readString();
        ipv6Dns2 = in.readString();
        ping = in.readInt();
        protocol = in.readString();
        hostname = in.readString();
        dohUrl = in.readString();
        port = in.readInt();
    }

    public static final Creator<DnsServer> CREATOR = new Creator<DnsServer>() {
        @Override
        public DnsServer createFromParcel(Parcel in) {
            return new DnsServer(in);
        }

        @Override
        public DnsServer[] newArray(int size) {
            return new DnsServer[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeString(dns1);
        dest.writeString(dns2);
        dest.writeString(ipv6Dns1);
        dest.writeString(ipv6Dns2);
        dest.writeInt(ping);
        dest.writeString(getProtocol());
        dest.writeString(getHostname());
        dest.writeString(getDohUrl());
        dest.writeInt(getPort());
    }

    public String getName() { return name; }
    public String getDns1() { return dns1 == null ? "" : dns1; }
    public String getDns2() { return dns2 == null ? "" : dns2; }
    public String getIpv6Dns1() { return ipv6Dns1 == null ? "" : ipv6Dns1; }
    public String getIpv6Dns2() { return ipv6Dns2 == null ? "" : ipv6Dns2; }
    public int getPing() { return ping; }
    public void setPing(int ping) { this.ping = ping; }
    public boolean isChecking() { return ping == -1; }
    public boolean isChecked() { return ping >= -1; }

    public String getProtocol() {
        return protocol == null || protocol.isEmpty() ? "UDP" : protocol;
    }

    public DnsProtocol getProtocolEnum() {
        return DnsProtocol.from(getProtocol());
    }

    public String getHostname() { return hostname == null ? "" : hostname; }
    public String getDohUrl() { return dohUrl == null ? "" : dohUrl; }

    public int getPort() {
        if (port > 0) return port;
        return getProtocolEnum().defaultPort;
    }

    public String getEndpointSummary() {
        DnsProtocol p = getProtocolEnum();
        if (p == DnsProtocol.DOH) {
            String url = getDohUrl();
            return url.isEmpty() ? getDns1() : url;
        }
        if (p == DnsProtocol.DOT) {
            String host = getHostname().isEmpty() ? getDns1() : getHostname();
            return host + ":" + getPort();
        }
        String extra = getDns2().isEmpty() ? "" : " / " + getDns2();
        return getDns1() + extra;
    }
}
