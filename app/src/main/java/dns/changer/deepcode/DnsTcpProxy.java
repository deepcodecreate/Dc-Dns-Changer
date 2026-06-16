package dns.changer.deepcode;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class DnsTcpProxy extends Thread {

    private final Context context;
    private volatile boolean running = true;
    private SharedPreferences prefs;

    public DnsTcpProxy(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE);
    }

    @Override
    public void run() {
        String server = prefs.getString("dot_server", "127.0.0.1");
        int port = Integer.parseInt(prefs.getString("dot_port", "5353"));
        
        LogHelper.log(context, "🚀 Starting DNS-over-TCP Proxy");
        LogHelper.log(context, "⚙️ Configuration - Server: " + server + ", Port: " + port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            LogHelper.log(context, "🔌 Listening on port " + port);
            
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    if (!running) {
                        LogHelper.log(context, "🛑 Shutdown signal received");
                        return;
                    }

                    LogHelper.log(context, "🔗 New client connected: " + 
                        clientSocket.getInetAddress().getHostAddress());

                    new Thread(() -> handleClient(clientSocket)).start();
                } catch (IOException e) {
                    if (running) {
                        LogHelper.log(context, "⚠️ Error accepting client: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            LogHelper.log(context, "❌ Failed to start proxy server: " + e.getMessage());
        }
        LogHelper.log(context, "🛑 Proxy server stopped");
    }

    private void handleClient(Socket clientSocket) {
        String server = prefs.getString("dot_server", "127.0.0.1");
        int port = Integer.parseInt(prefs.getString("dot_port", "5353"));

        try (Socket dnsServerSocket = new Socket()) {
            LogHelper.log(context, "🔗 Connecting to DNS server: " + server + ":" + port);
            dnsServerSocket.connect(new InetSocketAddress(server, port), 2000);

            InputStream clientIn = clientSocket.getInputStream();
            OutputStream clientOut = clientSocket.getOutputStream();
            InputStream dnsIn = dnsServerSocket.getInputStream();
            OutputStream dnsOut = dnsServerSocket.getOutputStream();

            byte[] buffer = new byte[512];
            int length = clientIn.read(buffer);

            if (length > 0) {
                String domain = extractDomainFromQuery(buffer, length);
                LogHelper.log(context, "🔍 DNS Query → " + domain);

                dnsOut.write(buffer, 0, length);
                dnsOut.flush();
                LogHelper.log(context, "📤 Forwarded query to DNS server");

                int responseLength = dnsIn.read(buffer);
                if (responseLength > 0) {
                    clientOut.write(buffer, 0, responseLength);
                    clientOut.flush();
                    LogHelper.log(context, "📥 Received and forwarded response");
                }
            }

        } catch (IOException e) {
            LogHelper.log(context, "❌ Error handling client request: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
                LogHelper.log(context, "🔌 Client connection closed");
            } catch (IOException e) {
                LogHelper.log(context, "⚠️ Error closing client socket: " + e.getMessage());
            }
        }
    }

    private String extractDomainFromQuery(byte[] data, int length) {
        try {
            int position = 12;
            StringBuilder domain = new StringBuilder();

            while (position < length && data[position] != 0) {
                int labelLength = data[position++] & 0xFF;
                if (labelLength == 0 || position + labelLength > length) break;
                if (domain.length() > 0) domain.append(".");
                for (int i = 0; i < labelLength; i++) {
                    domain.append((char) data[position++]);
                }
            }

            return domain.length() > 0 ? domain.toString() : "Unknown Domain";
        } catch (Exception e) {
            LogHelper.log(context, "⚠️ Error parsing DNS query: " + e.getMessage());
            return "Invalid DNS Query";
        }
    }

    public void shutdown() {
        running = false;
        try {
            new Socket("127.0.0.1", Integer.parseInt(prefs.getString("dot_port", "5353"))).close();
            LogHelper.log(context, "🛑 Sent shutdown signal to proxy");
        } catch (IOException e) {
            LogHelper.log(context, "⚠️ Error sending shutdown signal: " + e.getMessage());
        }
    }
}