package com.tlcn.discount_service.config;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class JwtBlacklistFilter extends OncePerRequestFilter {

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Unified blacklist key scheme across services.
     * New format: token:blacklist:{sha256(accessToken)}
     */
    private static final String UNIFIED_BLACKLIST_KEY_PREFIX = "token:blacklist:";

    /** Legacy prefixes kept for backwards compatibility during rollout. */
    private static final String LEGACY_VENDOR_BLACKLIST_KEY_PREFIX = "vendor:token:blacklist:";
    private static final String LEGACY_CUSTOMER_BLACKLIST_KEY_PREFIX = "customer:token:blacklist:";
    private static final String LEGACY_ADMIN_BLACKLIST_KEY_PREFIX = "admin:token:blacklist:";

    public JwtBlacklistFilter(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String accessToken = authHeader.replace("Bearer ", "").trim();
            String tokenHash = sha256Hex(accessToken);

            String unifiedKey = UNIFIED_BLACKLIST_KEY_PREFIX + tokenHash;
            String legacyVendorKey = LEGACY_VENDOR_BLACKLIST_KEY_PREFIX + tokenHash;
            String legacyCustomerKey = LEGACY_CUSTOMER_BLACKLIST_KEY_PREFIX + tokenHash;
                String legacyAdminKey = LEGACY_ADMIN_BLACKLIST_KEY_PREFIX + tokenHash;

            if (redisTemplate.hasKey(unifiedKey)
                    || redisTemplate.hasKey(legacyVendorKey)
                    || redisTemplate.hasKey(legacyCustomerKey)
                    || redisTemplate.hasKey(legacyAdminKey)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"success\": false, \"message\": \"Token is blacklisted\", \"data\": null}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return toHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private static String toHex(byte[] bytes) {
        char[] hexArray = "0123456789abcdef".toCharArray();
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
}