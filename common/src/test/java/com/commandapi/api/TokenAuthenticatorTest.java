package com.commandapi.api;

import com.commandapi.config.ApiConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenAuthenticatorTest {

    private static TokenAuthenticator withAuth(String token) {
        return new TokenAuthenticator(new ApiConfig("127.0.0.1", 8080, token, true));
    }

    @Test
    void acceptsMatchingBearerToken() {
        assertTrue(withAuth("secret").isAuthorized("Bearer secret"));
    }

    @Test
    void rejectsWrongOrMalformedHeader() {
        TokenAuthenticator auth = withAuth("secret");
        assertFalse(auth.isAuthorized("Bearer wrong"));
        assertFalse(auth.isAuthorized("Bearer secret-extra"));
        assertFalse(auth.isAuthorized("secret"));
        assertFalse(auth.isAuthorized("Basic secret"));
        assertFalse(auth.isAuthorized(null));
    }

    @Test
    void allowsEverythingWhenAuthDisabled() {
        TokenAuthenticator auth = new TokenAuthenticator(new ApiConfig("127.0.0.1", 8080, "secret", false));
        assertTrue(auth.isAuthorized(null));
        assertTrue(auth.isAuthorized("Bearer wrong"));
    }

    @Test
    void allowsEverythingWhenAuthEnabledButNoTokenConfigured() {
        assertTrue(withAuth("").isAuthorized(null));
    }
}
