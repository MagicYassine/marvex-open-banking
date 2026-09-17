package com.marvex.constants;

/**
 * The refresh-token cookie's name/path/attributes, shared between the code
 * that issues it (AuthService) and the code that reads/clears it
 * (AuthController) — these must match exactly or refresh/logout silently
 * stop finding the cookie.
 */
public final class AuthCookies {

    private AuthCookies() {}

    public static final String REFRESH_TOKEN_COOKIE = "refreshToken";
    public static final String REFRESH_TOKEN_COOKIE_PATH = "/auth/refresh";
    public static final String SAME_SITE_ATTRIBUTE = "SameSite";
    public static final String SAME_SITE_STRICT = "Strict";
}
