package com.ahicode.bidflow.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@AutoConfigureWebTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ApiGatewayIntegrationTest {

    @MockitoBean
    private ReactiveValueOperations<String, String> valueOperations;
    @MockitoBean
    private ReactiveStringRedisTemplate redisTemplate;
    @Autowired
    private JwtTestUtil jwtTestUtil;
    @Autowired
    private WebTestClient webClient;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void shouldAllowAccessToPublicEndpointWithoutToken() {
        when(valueOperations.get(anyString())).thenReturn(Mono.empty());

        webClient.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "email": "user@example.com",
                          "password": "1234"
                        }""")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void shouldRejectAccessToProtectedEndpointsWithoutToken() {
        webClient.post()
                .uri("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                        \t"refreshToken": "some.refresh.token"
                        }""")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void shouldRejectAccessToProtectedEndpointWithBlacklistedToken() {
        String validAccessToken = jwtTestUtil.generateValidAccessToken(
                "46b37f10-387d-4547-ba0a-ba6653a86404",
                "ROLE_USER",
                "ACTIVE"
        );

        when(valueOperations.get(anyString()))
                .thenReturn(Mono.just("blacklisted"));

        webClient.post()
                .uri("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + validAccessToken)
                .bodyValue("""
                        {
                        \t"refreshToken": "some.refresh.token"
                        }""")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void shouldAllowAndProceedForValidTokenNotInBlacklist() {
        String validAccessToken = jwtTestUtil.generateValidAccessToken(
                "46b37f10-387d-4547-ba0a-ba6653a86404",
                "ROLE_USER",
                "ACTIVE"
        );

        when(valueOperations.get(anyString())).thenReturn(Mono.empty());

        webClient.post()
                .uri("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + validAccessToken)
                .bodyValue("""
                        {
                        \t"refreshToken": "some.refresh.token"
                        }""")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
