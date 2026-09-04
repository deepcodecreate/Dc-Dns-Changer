package dns.changer.deepcode;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.util.List;
import java.util.Random;

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
        if (data == null || data.length < 12) return null;
        try {
            int transactionId = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
            int qdCount = ((data[4] & 0xFF) << 8) | (data[5] & 0xFF);
            if (qdCount < 1) return null;

            int pos = 12;
            StringBuilder domain = new StringBuilder();
            int hops = 0;
            while (pos < data.length && data[pos] != 0 && hops++ < 64) {
                int len = data[pos] & 0xFF;
                if ((len & 0xC0) == 0xC0) {
                    break;
                }
                pos++;
                if (len == 0 || pos + len > data.length) break;
                if (domain.length() > 0) domain.append('.');
                for (int i = 0; i < len; i++) {
                    domain.append((char) data[pos++]);
                }
            }
            if (pos < data.length && data[pos] == 0) pos++;
            if (pos + 2 > data.length) return null;
            int qType = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
            return new DnsQuery(domain.toString(), qType, transactionId);
        } catch (Exception e) {
            return null;
        }
    }

    public static byte[] buildQuery(String domain, int qType) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int id = new Random().nextInt(0xFFFF);
            out.write((id >> 8) & 0xFF);
            out.write(id & 0xFF);
            out.write(0x01); out.write(0x00); // recursion desired
            out.write(0x00); out.write(0x01); // QD
            out.write(0x00); out.write(0x00);
            out.write(0x00); out.write(0x00);
            out.write(0x00); out.write(0x00);
            String[] labels = domain.split("\\.");
            for (int i = 0; i < labels.length; i++) {
                byte[] lb = labels[i].getBytes("UTF-8");
                out.write(lb.length);
                out.write(lb);
            }
            out.write(0);
            out.write((qType >> 8) & 0xFF);
            out.write(qType & 0xFF);
            out.write(0x00); out.write(0x01);
            return out.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    public static boolean looksLikeSuccess(byte[] response) {
        if (response == null || response.length < 12) return false;
        int rcode = response[3] & 0x0F;
        int ancount = ((response[6] & 0xFF) << 8) | (response[7] & 0xFF);
        return rcode == 0 && ancount >= 0;
    }

    public static byte[] buildResponse(byte[] query, List<String> results, int qType) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(query[0]); out.write(query[1]);
            out.write(0x81); out.write(0x80);
            out.write(0x00); out.write(0x01);
            out.write((results.size() >> 8) & 0xFF);
            out.write(results.size() & 0xFF);
            out.write(0x00); out.write(0x00);
            out.write(0x00); out.write(0x00);

            int pos = 12;
            while (query[pos] != 0) {
                int len = query[pos++] & 0xFF;
                out.write(len);
                for (int i = 0; i < len; i++) {
                    out.write(query[pos++]);
                }
            }
            out.write(0); pos++;
            out.write(query[pos]); out.write(query[pos + 1]);
            out.write(query[pos + 2]); out.write(query[pos + 3]);

            for (int r = 0; r < results.size(); r++) {
                String ip = results.get(r);
                out.write(0xC0); out.write(0x0C);
                out.write(0x00); out.write(qType);
                out.write(0x00); out.write(0x01);
                out.write(0x00); out.write(0x00); out.write(0x00); out.write(60);
                if (qType == 1) {
                    byte[] addr = InetAddress.getByName(ip).getAddress();
                    out.write(0x00); out.write(addr.length);
                    out.write(addr);
                } else {
                    out.write(0x00); out.write(0x00);
                }
            }
            return out.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }
}
