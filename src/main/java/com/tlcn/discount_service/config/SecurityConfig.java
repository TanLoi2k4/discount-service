package com.tlcn.discount_service.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // 1. JWT Converter Setup
        JwtAuthenticationConverter jwtAuthConverter = new JwtAuthenticationConverter();
        jwtAuthConverter.setJwtGrantedAuthoritiesConverter(new KeycloakConverter());

        http
            .csrf(csrf -> csrf.disable())

            // 2. Authorization Rules (role checks are enforced via @PreAuthorize on controllers)
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("/actuator/**").permitAll()

                // API endpoints
                .requestMatchers(
                    "/discounts/**",
                    "/flash-sales/**"
                ).authenticated()

                // Everything else
                .anyRequest().authenticated()
            )

            // 3. Configure OAuth2 Resource Server for JWT processing
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter))
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            )

            // 4. Stateless session (REST API)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // 5. Exception Handling (return 401 instead of redirect)
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            )

            // 6. Add custom JWT blacklist filter
            .addFilterBefore(new JwtBlacklistFilter(redisTemplate), BasicAuthenticationFilter.class);

        return http.build();
    }
}