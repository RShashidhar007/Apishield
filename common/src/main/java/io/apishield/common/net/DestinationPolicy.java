package io.apishield.common.net;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Destination policy used for SSRF protection. It is applied (1) when an API is registered,
 * (2) when the gateway loads configuration and (3) at connection time on every resolved address,
 * which defeats DNS rebinding because the address that is validated is the address that is connected to.
 *
 * Rules:
 *  - scheme must be http or https; no user-info; no query/fragment
 *  - host must be explicitly allow-listed (exact or "*.suffix")
 *  - port must be in the allowed ports when a port allow-list is configured
 *  - resolved addresses are NEVER allowed to be: any-local, multicast, link-local (incl. cloud metadata),
 *    CGNAT/reserved ranges, and loopback (unless allowLoopback)
 *  - private (RFC1918 / ULA) addresses are only allowed when allowPrivateForAllowedHosts is true
 */
public final class DestinationPolicy {

    @FunctionalInterface
    public interface Resolver { InetAddress[] resolve(String host) throws UnknownHostException; }

    private final Set<String> allowedHosts;
    private final Set<Integer> allowedPorts;
    private final boolean allowPrivateForAllowedHosts;
    private final boolean allowLoopback;

    public DestinationPolicy(Set<String> allowedHosts, Set<Integer> allowedPorts,
                             boolean allowPrivateForAllowedHosts, boolean allowLoopback) {
        this.allowedHosts = allowedHosts.stream().map(h -> h.trim().toLowerCase(Locale.ROOT))
                .filter(h -> !h.isEmpty()).collect(Collectors.toUnmodifiableSet());
        this.allowedPorts = Set.copyOf(allowedPorts);
        this.allowPrivateForAllowedHosts = allowPrivateForAllowedHosts;
        this.allowLoopback = allowLoopback;
    }

    /** Validates the textual URL only (no DNS). Returns the parsed URI. */
    public URI validateUrl(String url) {
        if (url == null || url.isBlank()) throw reject("DESTINATION_EMPTY", "Upstream URL is required");
        URI uri;
        try { uri = URI.create(url.trim()); } catch (IllegalArgumentException e) {
            throw reject("DESTINATION_INVALID", "Upstream URL is not a valid URI");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) throw reject("DESTINATION_SCHEME", "Only http and https upstreams are allowed");
        if (uri.getRawUserInfo() != null) throw reject("DESTINATION_USERINFO", "User-info in upstream URLs is not allowed");
        if (uri.getRawFragment() != null || uri.getRawQuery() != null) throw reject("DESTINATION_INVALID", "Upstream URL must not contain a query or fragment");
        String host = uri.getHost();
        if (host == null) throw reject("DESTINATION_INVALID", "Upstream URL has no host");
        validateHostAllowed(host);
        int port = effectivePort(uri);
        if (!allowedPorts.isEmpty() && !allowedPorts.contains(port)) throw reject("DESTINATION_PORT", "Upstream port " + port + " is not allowed");
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (path.contains("..") || path.contains("\\")) throw reject("DESTINATION_INVALID", "Upstream path contains forbidden sequences");
        return uri;
    }

    public void validateHostAllowed(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        if (h.startsWith("[") && h.endsWith("]")) h = h.substring(1, h.length() - 1);
        for (String allowed : allowedHosts) {
            if (allowed.equals(h)) return;
            if (allowed.startsWith("*.") && h.endsWith(allowed.substring(1)) && h.length() > allowed.length() - 1) return;
        }
        throw reject("DESTINATION_HOST_NOT_ALLOWED", "Host '" + host + "' is not in the upstream allow-list");
    }

    /** Validates one resolved address. Called at registration time and at connection time. */
    public void validateAddress(String host, InetAddress a) {
        if (a.isAnyLocalAddress()) throw reject("DESTINATION_ANY_LOCAL", "Unspecified addresses are forbidden");
        if (a.isMulticastAddress()) throw reject("DESTINATION_MULTICAST", "Multicast addresses are forbidden");
        if (a.isLinkLocalAddress() || isMetadata(a)) throw reject("DESTINATION_LINK_LOCAL", "Link-local and cloud-metadata addresses are forbidden");
        if (a.isLoopbackAddress()) {
            if (!allowLoopback) throw reject("DESTINATION_LOOPBACK", "Loopback addresses are forbidden");
            return;
        }
        if (isReserved(a)) throw reject("DESTINATION_RESERVED", "Reserved or shared address space is forbidden");
        if (isPrivate(a) && !allowPrivateForAllowedHosts) throw reject("DESTINATION_PRIVATE", "Private network addresses are forbidden");
    }

    /** Resolves a host with the supplied resolver and validates EVERY returned address. */
    public InetAddress[] validateResolved(String host, Resolver resolver) {
        InetAddress[] addrs;
        try { addrs = resolver.resolve(host); } catch (UnknownHostException e) {
            throw reject("DESTINATION_UNRESOLVABLE", "Host '" + host + "' cannot be resolved");
        }
        if (addrs == null || addrs.length == 0) throw reject("DESTINATION_UNRESOLVABLE", "Host '" + host + "' cannot be resolved");
        for (InetAddress a : addrs) validateAddress(host, a);
        return addrs;
    }

    public static int effectivePort(URI uri) {
        if (uri.getPort() != -1) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static boolean isMetadata(InetAddress a) {
        byte[] b = a.getAddress();
        if (a instanceof Inet4Address) return (b[0] & 0xFF) == 169 && (b[1] & 0xFF) == 254
                || ((b[0] & 0xFF) == 100 && (b[1] & 0xFF) == 100 && (b[2] & 0xFF) == 100 && (b[3] & 0xFF) == 200);
        // AWS IPv6 metadata fd00:ec2::254
        return b.length == 16 && (b[0] & 0xFF) == 0xfd && (b[1] & 0xFF) == 0x00 && (b[2] & 0xFF) == 0x0e && (b[3] & 0xFF) == 0xc2;
    }

    private static boolean isPrivate(InetAddress a) {
        if (a.isSiteLocalAddress()) return true;
        if (a instanceof Inet6Address) return (a.getAddress()[0] & 0xFE) == 0xFC; // fc00::/7 unique-local
        return false;
    }

    private static boolean isReserved(InetAddress a) {
        if (!(a instanceof Inet4Address)) return false;
        byte[] b = a.getAddress();
        int o1 = b[0] & 0xFF, o2 = b[1] & 0xFF;
        if (o1 == 0) return true;                       // 0.0.0.0/8
        if (o1 == 100 && o2 >= 64 && o2 <= 127) return true; // 100.64.0.0/10 CGNAT
        if (o1 == 192 && o2 == 0 && (b[2] & 0xFF) == 0) return true; // 192.0.0.0/24
        if (o1 == 198 && (o2 == 18 || o2 == 19)) return true;  // benchmarking
        return o1 >= 240;                                  // 240.0.0.0/4 + broadcast
    }

    private static DestinationRejectedException reject(String code, String msg) { return new DestinationRejectedException(code, msg); }
}
