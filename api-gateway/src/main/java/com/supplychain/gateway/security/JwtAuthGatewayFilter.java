package com.supplychain.gateway.security;

import com.supplychain.common.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Global JWT authentication filter applied to all routes.
 *
 * <p>Flow:
 * <ol>
 *   <li>Skip public routes (auth endpoints)</li>
 *   <li>Extract Bearer token from Authorization header</li>
 *   <li>Validate JWT signature and expiry</li>
 *   <li>Forward tenantId and role as internal headers (X-Tenant-Id, X-User-Role)</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthGatewayFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    /** Routes that bypass JWT validation */
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/refresh",
            "/actuator/health",
            "/actuator/info"
    );

    private final JwtUtil jwtUtil;

    @Override
    public int getOrder() {
        return -100; // Before routing filters
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Skip public paths
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // Extract Authorization header
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return unauthorizedResponse(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        // Validate token
        if (!jwtUtil.isValid(token)) {
            return unauthorizedResponse(exchange, "Invalid or expired JWT token");
        }

        // Extract claims and forward as headers to downstream services
        String tenantId = jwtUtil.getTenantId(token);
        String role     = jwtUtil.getRole(token);
        String subject  = jwtUtil.getSubject(token);

        log.debug("Authenticated request: user={}, tenant={}, role={}, path={}", subject, tenantId, role, path);

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header("X-Tenant-Id", tenantId)
                .header("X-User-Role", role)
                .header("X-Username", subject)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = """
                {"success":false,"message":"%s","errorCode":"UNAUTHORIZED"}
                """.formatted(message).trim();

        var buffer = response.bufferFactory().wrap(body.getBytes());
        return response.writeWith(Mono.just(buffer));
    }
}
