package com.supplychain.gateway.auth;

import com.supplychain.common.dto.ApiResponse;
import com.supplychain.gateway.auth.dto.LoginRequest;
import com.supplychain.gateway.auth.dto.LoginResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Authentication controller — exposed at /api/auth
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/auth/login  — issue JWT token</li>
 *   <li>POST /api/auth/refresh — refresh token (future)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Authenticate user and return JWT.
     *
     * <p>Request:
     * <pre>
     * {
     *   "username": "warehouse@toyota",
     *   "password": "password123"
     * }
     * </pre>
     */
    @PostMapping("/login")
    public Mono<ResponseEntity<ApiResponse<LoginResponse>>> login(
            @Valid @RequestBody LoginRequest request) {

        return authService.authenticate(request)
                .map(response -> ResponseEntity.ok(ApiResponse.ok("Login successful", response)))
                .onErrorResume(e -> Mono.just(
                        ResponseEntity.status(401)
                                .body(ApiResponse.error(e.getMessage(), "AUTHENTICATION_FAILED"))
                ));
    }
}
