package com.ahicode.bidflow.gateway;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final SecurityProperties securityProperties;

    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange((authorize) -> authorize
                        .pathMatchers(securityProperties.getPublicEndpoints().toArray(new String[0])).permitAll()
                        .anyExchange().authenticated()
                );

        return http.build();
    }
}
