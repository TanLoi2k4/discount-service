package com.tlcn.discount_service.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class KeycloakConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final Logger logger = LoggerFactory.getLogger(KeycloakConverter.class);

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> realmAccess = (Map<String, Object>) jwt.getClaims().get("realm_access");
        if (realmAccess == null || realmAccess.isEmpty()) {
            logger.debug("No realm_access found in JWT claims");
            return Collections.emptyList();
        }
        List<String> roles = (List<String>) realmAccess.get("roles");
        if (roles == null) {
            logger.debug("No roles found in realm_access");
            return Collections.emptyList();
        }
        logger.info("Converted roles: {}", roles);
        return roles.stream()
                .map(roleName -> new SimpleGrantedAuthority("ROLE_" + roleName))
                .collect(Collectors.toList());
    }
}