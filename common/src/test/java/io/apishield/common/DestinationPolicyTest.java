package io.apishield.common;

import io.apishield.common.net.DestinationPolicy;
import io.apishield.common.net.DestinationRejectedException;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DestinationPolicyTest {
    private final DestinationPolicy policy = new DestinationPolicy(Set.of("catalog-service", "*.partner.example"), Set.of(), true, false);
    private final DestinationPolicy strict = new DestinationPolicy(Set.of("catalog-service"), Set.of(8081), false, false);

    private static String code(Runnable r) { return assertThrows(DestinationRejectedException.class, r::run).code(); }

    @Test void acceptsAllowlistedHost() { assertDoesNotThrow(() -> policy.validateUrl("http://catalog-service:8081/v1")); }
    @Test void acceptsWildcardHost() { assertDoesNotThrow(() -> policy.validateUrl("https://api.partner.example")); }
    @Test void rejectsBareWildcardSuffixLookalike() { assertEquals("DESTINATION_HOST_NOT_ALLOWED", code(() -> policy.validateUrl("https://evilpartner.example"))); }
    @Test void rejectsMetadataHost() { assertEquals("DESTINATION_HOST_NOT_ALLOWED", code(() -> policy.validateUrl("http://169.254.169.254/latest/meta-data"))); }
    @Test void rejectsBadScheme() { assertEquals("DESTINATION_SCHEME", code(() -> policy.validateUrl("file:///etc/passwd"))); }
    @Test void rejectsUserInfo() { assertEquals("DESTINATION_USERINFO", code(() -> policy.validateUrl("http://user:pw@catalog-service"))); }
    @Test void rejectsDisallowedPort() { assertEquals("DESTINATION_PORT", code(() -> strict.validateUrl("http://catalog-service:22"))); }
    @Test void rejectsTraversalInBasePath() { assertEquals("DESTINATION_INVALID", code(() -> policy.validateUrl("http://catalog-service/a/../b"))); }

    @Test void blocksMetadataAddressEvenWhenPrivateAllowed() throws Exception {
        assertEquals("DESTINATION_LINK_LOCAL", code(() -> policy.validateAddress("x", ip("169.254.169.254"))));
        assertEquals("DESTINATION_LINK_LOCAL", code(() -> policy.validateAddress("x", ip("100.100.100.200"))));
    }
    @Test void blocksLoopback() throws Exception { assertEquals("DESTINATION_LOOPBACK", code(() -> policy.validateAddress("x", ip("127.0.0.1")))); }
    @Test void blocksIpv6Loopback() throws Exception { assertEquals("DESTINATION_LOOPBACK", code(() -> policy.validateAddress("x", ip("::1")))); }
    @Test void blocksUnspecified() throws Exception { assertEquals("DESTINATION_ANY_LOCAL", code(() -> policy.validateAddress("x", ip("0.0.0.0")))); }
    @Test void blocksCgnat() throws Exception { assertEquals("DESTINATION_RESERVED", code(() -> policy.validateAddress("x", ip("100.64.1.1")))); }
    @Test void blocksPrivateWhenNotPermitted() throws Exception {
        assertEquals("DESTINATION_PRIVATE", code(() -> strict.validateAddress("x", ip("10.0.0.5"))));
        assertEquals("DESTINATION_PRIVATE", code(() -> strict.validateAddress("x", ip("192.168.1.1"))));
        assertEquals("DESTINATION_PRIVATE", code(() -> strict.validateAddress("x", ip("fd12:3456::1"))));
    }
    @Test void allowsPrivateForTrustedDeployments() throws Exception { assertDoesNotThrow(() -> policy.validateAddress("x", ip("172.18.0.4"))); }
    @Test void allowsPublic() throws Exception { assertDoesNotThrow(() -> strict.validateAddress("x", ip("93.184.216.34"))); }

    /** DNS rebinding: a host that first looked fine can later resolve to a forbidden address; every resolution is validated. */
    @Test void validatesEveryResolvedAddress() {
        DestinationPolicy.Resolver rebinding = h -> new InetAddress[]{ ip("93.184.216.34"), ip("169.254.169.254") };
        assertEquals("DESTINATION_LINK_LOCAL", code(() -> strict.validateResolved("catalog-service", rebinding)));
    }
    @Test void unresolvableIsRejected() {
        assertEquals("DESTINATION_UNRESOLVABLE", code(() -> strict.validateResolved("nope", h -> { throw new java.net.UnknownHostException(h); })));
    }

    private static InetAddress ip(String s) { try { return InetAddress.getByName(s); } catch (Exception e) { throw new RuntimeException(e); } }
}
