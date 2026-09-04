package dns.changer.deepcode;

import android.os.ParcelFileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class TunDnsForwarder implements Runnable {
    private static final int IPV4 = 4;
    private static final int UDP = 17;

    private final ParcelFileDescriptor tun;
    private final DnsQueryEngine engine;
    private final AtomicBoolean running = new AtomicBoolean(true);

    private final ExecutorService workers = Executors.newFixedThreadPool(8);
    private FileOutputStream out;

    public TunDnsForwarder(ParcelFileDescriptor tun, DnsQueryEngine engine) {
        this.tun = tun;
        this.engine = engine;
    }

    public void stop() {
        running.set(false);
        workers.shutdownNow();
        engine.shutdown();
        try { tun.close(); } catch (Exception ignored) {}
    }

    @Override
    public void run() {
        try {
            FileInputStream in = new FileInputStream(tun.getFileDescriptor());
            out = new FileOutputStream(tun.getFileDescriptor());
            byte[] packet = new byte[32767];
            while (running.get()) {
                int length = in.read(packet);
                if (length < 28) continue;
                final byte[] copy = new byte[length];
                System.arraycopy(packet, 0, copy, 0, length);
                workers.execute(new Runnable() {
                    @Override
                    public void run() {
                        handlePacket(copy);
                    }
                });
            }
        } catch (Exception ignored) {
        }
    }

    private void handlePacket(byte[] packet) {
        try {
            int version = (packet[0] >> 4) & 0xF;
            if (version != IPV4) return;
            int ihl = (packet[0] & 0xF) * 4;
            if (ihl < 20 || packet.length < ihl + 8) return;
            int protocol = packet[9] & 0xFF;
            if (protocol != UDP) return;

            int destPort = ((packet[ihl + 2] & 0xFF) << 8) | (packet[ihl + 3] & 0xFF);
            if (destPort != 53) return;

            int udpLen = ((packet[ihl + 4] & 0xFF) << 8) | (packet[ihl + 5] & 0xFF);
            int dnsOff = ihl + 8;
            int dnsLen = Math.min(udpLen - 8, packet.length - dnsOff);
            if (dnsLen < 12) return;

            byte[] query = new byte[dnsLen];
            System.arraycopy(packet, dnsOff, query, 0, dnsLen);

            byte[] answer = engine.query(query);
            if (answer == null || answer.length < 12) return;

            byte[] response = buildUdpResponse(packet, ihl, answer);
            if (response == null) return;
            synchronized (this) {
                if (out != null) {
                    out.write(response);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private byte[] buildUdpResponse(byte[] request, int ihl, byte[] dns) {
        int total = ihl + 8 + dns.length;
        if (total > 32767) return null;
        byte[] outPkt = new byte[total];

        System.arraycopy(request, 0, outPkt, 0, ihl + 8);

        System.arraycopy(request, 16, outPkt, 12, 4);
        System.arraycopy(request, 12, outPkt, 16, 4);

        outPkt[ihl] = request[ihl + 2];
        outPkt[ihl + 1] = request[ihl + 3];
        outPkt[ihl + 2] = request[ihl];
        outPkt[ihl + 3] = request[ihl + 1];

        int udpLen = 8 + dns.length;
        outPkt[ihl + 4] = (byte) ((udpLen >> 8) & 0xFF);
        outPkt[ihl + 5] = (byte) (udpLen & 0xFF);
        outPkt[ihl + 6] = 0;
        outPkt[ihl + 7] = 0; // UDP checksum optional on IPv4

        System.arraycopy(dns, 0, outPkt, ihl + 8, dns.length);

        outPkt[2] = (byte) ((total >> 8) & 0xFF);
        outPkt[3] = (byte) (total & 0xFF);
        outPkt[10] = 0;
        outPkt[11] = 0;
        int csum = ipChecksum(outPkt, 0, ihl);
        outPkt[10] = (byte) ((csum >> 8) & 0xFF);
        outPkt[11] = (byte) (csum & 0xFF);
        return outPkt;
    }

    private static int ipChecksum(byte[] buf, int off, int len) {
        int sum = 0;
        int i = 0;
        while (i < len - 1) {
            sum += ((buf[off + i] & 0xFF) << 8) | (buf[off + i + 1] & 0xFF);
            i += 2;
        }
        if (i < len) sum += (buf[off + i] & 0xFF) << 8;
        while ((sum >> 16) != 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        return ~sum & 0xFFFF;
    }
}
