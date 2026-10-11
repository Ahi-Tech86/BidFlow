package com.ahicode.bidflow.gateway.config;

import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import javax.crypto.SecretKey;

@Configuration
public class JwtConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.secret-key}")
    private String secretKey;

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        byte[] keyBytes = Decoders.BASE64URL.decode(secretKey);
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);
        return NimbusReactiveJwtDecoder.withSecretKey(key).build();
    }
}
