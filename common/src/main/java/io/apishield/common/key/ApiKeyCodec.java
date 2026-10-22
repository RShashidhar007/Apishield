package io.apishield.common.key;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * API key format: ask_{prefix}_{secret}
 *  - prefix: 12 hex chars, public lookup identifier (non-secret, safe to display and log)
 *  - secret: 32 random bytes, base64url (256 bits of entropy)
 * Only HMAC-SHA256(pepper, rawKey) is stored. Because the secret has 256 bits of entropy a fast keyed hash is
 * appropriate (a slow password hash would only add DoS surface on the gateway hot path).
 */
public final class ApiKeyCodec {
    public static final String PREFIX = "ask_";
    private static final SecureRandom RNG = new SecureRandom();

    public record Generated(String rawKey, String prefix, String hash) {
        @Override public String toString() { return "Generated[prefix=" + prefix + ", rawKey=<redacted>]"; }
    }

    private ApiKeyCodec() {}

    public static Generated generate(String pepper) {
        byte[] p = new byte[6]; RNG.nextBytes(p);
        byte[] s = new byte[32]; RNG.nextBytes(s);
        String prefix = HexFormat.of().formatHex(p);
        String raw = PREFIX + prefix + "_" + Base64.getUrlEncoder().withoutPadding().encodeToString(s);
        return new Generated(raw, prefix, hash(raw, pepper));
    }

    public static Optional<String> extractPrefix(String rawKey) {
        if (rawKey == null || rawKey.length() < 20 || rawKey.length() > 128 || !rawKey.startsWith(PREFIX)) return Optional.empty();
        String[] parts = rawKey.split("_", 3);
        if (parts.length != 3 || !parts[1].matches("[0-9a-f]{12}") || parts[2].isEmpty()) return Optional.empty();
        return Optional.of(parts[1]);
    }

    public static String hash(String rawKey, String pepper) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(rawKey.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }

    public static boolean matches(String rawKey, String pepper, String storedHash) {
        return MessageDigest.isEqual(hash(rawKey, pepper).getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }
}
