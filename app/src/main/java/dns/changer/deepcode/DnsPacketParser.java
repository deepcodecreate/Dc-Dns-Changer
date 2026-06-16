package dns.changer.deepcode;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.util.List;

public class DnsPacketParser {
    public static class DnsQuery {
        public String domain;
        public int qType;
        public int transactionId;

        public DnsQuery(String domain, int qType, int transactionId) {
            this.domain = domain;
            this.qType = qType;
            this.transactionId = transactionId;
        }
    }

    public static DnsQuery parseQuery(byte[] data) {
        try {
            int transactionId = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
            int qdCount = ((data[4] & 0xFF) << 8) | (data[5] & 0xFF);
            if (qdCount != 1) return null;

            int pos = 12;
            StringBuilder domain = new StringBuilder();
            while (data[pos] != 0) {
                int len = data[pos++];
                for (int i = 0; i < len; i++) {
                    domain.append((char) data[pos++]);
                }
                domain.append('.');
            }
            pos++; // skip null
            int qType = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);

            return new DnsQuery(domain.toString(), qType, transactionId);
        } catch (Exception e) {
            return null;
        }
    }

    public static byte[] buildResponse(byte[] query, List<String> results, int qType) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Copy header
            out.write(query[0]); out.write(query[1]); // Transaction ID
            out.write(0x81); out.write(0x80); // Standard response, no error
            out.write(0x00); out.write(0x01); // QDCOUNT
            out.write((results.size() >> 8) & 0xFF);
            out.write(results.size() & 0xFF); // ANCOUNT
            out.write(0x00); out.write(0x00); // NSCOUNT
            out.write(0x00); out.write(0x00); // ARCOUNT

            // Question section
            int pos = 12;
            while (query[pos] != 0) {
                int len = query[pos++] & 0xFF;
                out.write(len);
                for (int i = 0; i < len; i++) {
                    out.write(query[pos++]);
                }
            }
            out.write(0); pos++; // end of domain
            out.write(query[pos]); out.write(query[pos + 1]); // QTYPE
            out.write(query[pos + 2]); out.write(query[pos + 3]); // QCLASS

            // Answer section
            for (String ip : results) {
                out.write(0xC0); out.write(0x0C); // name pointer
                out.write(0x00); out.write(qType); // TYPE
                out.write(0x00); out.write(0x01); // CLASS IN
                out.write(0x00); out.write(0x00); out.write(0x00); out.write(60); // TTL 60s
                if (qType == 1) { // A record
                    byte[] addr = InetAddress.getByName(ip).getAddress();
                    out.write(0x00); out.write(addr.length);
                    out.write(addr);
                } else {
                    // Unsupported
                    out.write(0x00); out.write(0x00);
                }
            }

            return out.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }
}