package io.apishield.gateway;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/** X-Forwarded-For is honoured ONLY when the direct peer is a configured trusted proxy; the right-most untrusted hop wins. */
@Component
public class ClientIpResolver {
    private record Cidr(byte[] net, int bits) {
        boolean contains(InetAddress a) {
            byte[] b = a.getAddress();
            if (b.length != net.length) return false;
            int full = bits / 8, rem = bits % 8;
            for (int i = 0; i < full; i++) if (b[i] != net[i]) return false;
            return rem == 0 || ((b[full] ^ net[full]) & (0xFF << (8 - rem))) == 0;
        }
    }
    private final List<Cidr> trusted = new ArrayList<>();

    public ClientIpResolver(GatewayProperties props) {
        for (String t : props.getTrustedProxies()) {
            if (t == null || t.isBlank()) continue;
            try {
                String[] p = t.trim().split("/");
                InetAddress a = InetAddress.getByName(p[0]);
                trusted.add(new Cidr(a.getAddress(), p.length > 1 ? Integer.parseInt(p[1]) : a.getAddress().length * 8));
            } catch (UnknownHostException | NumberFormatException e) { throw new IllegalArgumentException("Invalid trusted proxy: " + t); }
        }
    }

    private boolean isTrusted(InetAddress a) { return trusted.stream().anyMatch(c -> c.contains(a)); }

    public String resolve(ServerHttpRequest req) {
        var remote = req.getRemoteAddress();
        if (remote == null || remote.getAddress() == null) return "unknown";
        InetAddress peer = remote.getAddress();
        if (trusted.isEmpty() || !isTrusted(peer)) return peer.getHostAddress();
        String xff = req.getHeaders().getFirst("X-Forwarded-For");
        if (xff == null) return peer.getHostAddress();
        String[] hops = xff.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String h = hops[i].trim();
            if (h.isEmpty() || h.length() > 45 || !h.matches("[0-9a-fA-F:.]+")) return peer.getHostAddress();
            try { InetAddress a = InetAddress.getByName(h); if (!isTrusted(a)) return a.getHostAddress(); }
            catch (UnknownHostException e) { return peer.getHostAddress(); }
        }
        return peer.getHostAddress();
    }
}
