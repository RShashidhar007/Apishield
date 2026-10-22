package io.apishield.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import java.net.InetSocketAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpResolverTest {
    private static GatewayProperties props(String... trusted) { GatewayProperties p = new GatewayProperties(); p.setTrustedProxies(List.of(trusted)); return p; }
    private static MockServerHttpRequest req(String peer, String xff) {
        var b = MockServerHttpRequest.get("/gateway/x").remoteAddress(new InetSocketAddress(peer, 1234));
        if (xff != null) b.header("X-Forwarded-For", xff);
        return b.build();
    }
    @Test void ignoresForwardedForFromUntrustedPeers() { assertEquals("198.51.100.7", new ClientIpResolver(props()).resolve(req("198.51.100.7", "1.2.3.4"))); }
    @Test void honoursForwardedForFromTrustedProxy() { assertEquals("1.2.3.4", new ClientIpResolver(props("10.0.0.0/8")).resolve(req("10.1.1.1", "1.2.3.4"))); }
    @Test void rightMostUntrustedHopWinsSoSpoofedLeftEntriesAreIgnored() {
        assertEquals("1.2.3.4", new ClientIpResolver(props("10.0.0.0/8")).resolve(req("10.1.1.1", "6.6.6.6, 1.2.3.4, 10.2.2.2")));
    }
    @Test void malformedForwardedForFallsBackToPeer() { assertEquals("10.1.1.1", new ClientIpResolver(props("10.0.0.0/8")).resolve(req("10.1.1.1", "not-an-ip"))); }
}
