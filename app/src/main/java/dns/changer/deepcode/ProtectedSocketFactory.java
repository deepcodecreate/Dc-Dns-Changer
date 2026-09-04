package dns.changer.deepcode;

import android.net.VpnService;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import javax.net.SocketFactory;

final class ProtectedSocketFactory extends SocketFactory {
    private final VpnService vpn;

    ProtectedSocketFactory(VpnService vpn) {
        this.vpn = vpn;
    }

    private Socket protectedSocket() {
        Socket socket = new Socket();
        if (vpn != null) vpn.protect(socket);
        return socket;
    }

    @Override
    public Socket createSocket() {
        return protectedSocket();
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        Socket socket = protectedSocket();
        socket.connect(new InetSocketAddress(host, port), 4000);
        return socket;
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        Socket socket = protectedSocket();
        if (localHost != null) socket.bind(new InetSocketAddress(localHost, localPort));
        socket.connect(new InetSocketAddress(host, port), 4000);
        return socket;
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        Socket socket = protectedSocket();
        socket.connect(new InetSocketAddress(host, port), 4000);
        return socket;
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        Socket socket = protectedSocket();
        if (localAddress != null) socket.bind(new InetSocketAddress(localAddress, localPort));
        socket.connect(new InetSocketAddress(address, port), 4000);
        return socket;
    }
}
