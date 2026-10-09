package com.ahicode.bidflow.auth.utils;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Component
@ConfigurationProperties(prefix = "application.security.jwt")
public class JwtProperties {

    private final RefreshTokenProperties refreshToken = new RefreshTokenProperties();
    private final AccessTokenProperties accessToken = new AccessTokenProperties();

    @Getter
    @Setter
    public static class RefreshTokenProperties {
        private String secretKey;
        private Duration expiration;
    }

    @Getter
    @Setter
    public static class AccessTokenProperties {
        private String secretKey;
        private Duration expiration;
    }
}
