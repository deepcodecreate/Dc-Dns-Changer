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

    public DnsServer(String name, String dns1, String dns2, String ipv6Dns1, String ipv6Dns2) {
        this.name = name;
        this.dns1 = dns1;
        this.dns2 = dns2;
        this.ipv6Dns1 = ipv6Dns1;
        this.ipv6Dns2 = ipv6Dns2;
    }

    protected DnsServer(Parcel in) {
        name = in.readString();
        dns1 = in.readString();
        dns2 = in.readString();
        ipv6Dns1 = in.readString();
        ipv6Dns2 = in.readString();
        ping = in.readInt();
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
    }

    public String getName() { return name; }
    public String getDns1() { return dns1; }
    public String getDns2() { return dns2; }
    public String getIpv6Dns1() { return ipv6Dns1; }
    public String getIpv6Dns2() { return ipv6Dns2; }
    public int getPing() { return ping; }
    public void setPing(int ping) { this.ping = ping; }
    public boolean isChecking() { return ping == -1; }
    public boolean isChecked() { return ping >= -1; }
}