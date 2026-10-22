package io.apishield.common;

import io.apishield.common.key.ApiKeyCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiKeyCodecTest {
    private static final String PEPPER = "unit-test-pepper-unit-test-pepper";

    @Test void generatesVerifiableKeyAndStoresOnlyHash() {
        var g = ApiKeyCodec.generate(PEPPER);
        assertTrue(g.rawKey().startsWith("ask_" + g.prefix() + "_"));
        assertEquals(g.prefix(), ApiKeyCodec.extractPrefix(g.rawKey()).orElseThrow());
        assertTrue(ApiKeyCodec.matches(g.rawKey(), PEPPER, g.hash()));
        assertFalse(g.hash().contains(g.rawKey()));
        assertFalse(g.toString().contains(g.rawKey()), "toString must not leak the raw key");
    }
    @Test void wrongKeyOrPepperDoesNotMatch() {
        var g = ApiKeyCodec.generate(PEPPER);
        assertFalse(ApiKeyCodec.matches(g.rawKey() + "x", PEPPER, g.hash()));
        assertFalse(ApiKeyCodec.matches(g.rawKey(), "another-pepper-another-pepper-12", g.hash()));
    }
    @Test void keysAreUnique() { assertNotEquals(ApiKeyCodec.generate(PEPPER).rawKey(), ApiKeyCodec.generate(PEPPER).rawKey()); }
    @Test void rejectsMalformedKeys() {
        assertTrue(ApiKeyCodec.extractPrefix(null).isEmpty());
        assertTrue(ApiKeyCodec.extractPrefix("").isEmpty());
        assertTrue(ApiKeyCodec.extractPrefix("Bearer abc").isEmpty());
        assertTrue(ApiKeyCodec.extractPrefix("ask_ZZZZZZZZZZZZ_secretsecretsecret").isEmpty());
        assertTrue(ApiKeyCodec.extractPrefix("ask_" + "a".repeat(200)).isEmpty());
    }
}
