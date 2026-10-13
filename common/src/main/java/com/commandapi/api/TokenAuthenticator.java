package com.commandapi.api;

import com.commandapi.config.ApiConfig;

/**
 * Bearer-token check for incoming requests.
 *
 * <p>Read-only authentication may be disabled. Mutating operations always require a token.</p>
 */
public final class TokenAuthenticator {

    private static final String PREFIX = "Bearer ";

    private final ApiConfig config;

    public TokenAuthenticator(ApiConfig config) {
        this.config = config;
    }

    /** @param authorizationHeader the raw {@code Authorization} header, may be null */
    public boolean isAuthorized(String authorizationHeader) {
        return isAuthorized(authorizationHeader, false);
    }

    public boolean isAuthorized(String authorizationHeader, boolean mutation) {
        if (!mutation && !config.isAuthEnabled()) {
            return true;
        }
        if (config.getToken().isEmpty()) return false;
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
