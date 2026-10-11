package com.ahicode.bidflow.gateway.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final String blacklistRedisPrefix = "blacklist:";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(authentication -> (Jwt) authentication.getPrincipal())
                .flatMap(jwt -> {
                    String jti = jwt.getId();
                    log.info("Found JWT with jti: {}", jti);

                    return redisTemplate.opsForValue().get(blacklistRedisPrefix + jti)
                            .hasElement()
                            .flatMap(isBlacklisted -> {
                                if (isBlacklisted) {
                                    log.warn("Attempt to use blacklisted token with jti: {}", jti);

                                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                                    return exchange.getResponse().setComplete().then(Mono.just(true));

                                } else {
                                    log.info("Token is valid and not blacklisted. Proceeding with the request");

                                    String userId = jwt.getSubject();
                                    String userRole = jwt.getClaimAsString("role");
                                    String userStatus = jwt.getClaimAsString("status");

                                    ServerHttpRequest newRequest = exchange.getRequest().mutate()
                                            .header("X-User-Id", userId)
                                            .header("X-User-Role", userRole)
                                            .header("X-User-Status", userStatus)
                                            .build();

                                    return chain.filter(exchange.mutate().request(newRequest).build()).then(Mono.just(true));
                                }
                            });
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("No SecurityContext - this is a public endpoint");

                    return chain.filter(exchange).then(Mono.just(true));
                }))
                .then();
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
