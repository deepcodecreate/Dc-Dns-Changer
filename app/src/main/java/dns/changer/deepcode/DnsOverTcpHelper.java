package dns.changer.deepcode;

import org.xbill.DNS.*;

public class DnsOverTcpHelper {

    public static String resolveA(String domain, String dnsServer) {
        try {
            Lookup lookup = new Lookup(domain, Type.A);
            SimpleResolver resolver = new SimpleResolver(dnsServer);
            resolver.setTCP(true);
            lookup.setResolver(resolver);
            lookup.setCache(null);

            org.xbill.DNS.Record[] records = lookup.run();

            if (records == null || records.length == 0) {
                return "No A records found.";
            }

            StringBuilder sb = new StringBuilder();
            for (org.xbill.DNS.Record record : records) {
                if (record instanceof ARecord) {
                    sb.append(((ARecord) record).getAddress().getHostAddress()).append("\n");
                }
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}