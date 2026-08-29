package com.commandapi.api;

import com.commandapi.config.ApiConfig;

/**
 * Bearer-token check for incoming requests.
 *
 * <p>Authentication is a no-op when disabled or when no token is configured,
 * which preserves the previous default of an open local API.</p>
 */
public final class TokenAuthenticator {

    private static final String PREFIX = "Bearer ";

    private final ApiConfig config;

    public TokenAuthenticator(ApiConfig config) {
        this.config = config;
    }

    /** @param authorizationHeader the raw {@code Authorization} header, may be null */
    public boolean isAuthorized(String authorizationHeader) {
        if (!config.isAuthEnabled() || config.getToken().isEmpty()) {
            return true;
        }
        if (authorizationHeader == null || !authorizationHeader.startsWith(PREFIX)) {
            return false;
        }
        return constantTimeEquals(authorizationHeader.substring(PREFIX.length()), config.getToken());
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
