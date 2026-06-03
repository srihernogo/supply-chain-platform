package com.supplychain.gateway.auth;

import com.supplychain.common.security.JwtUtil;
import com.supplychain.gateway.auth.dto.LoginRequest;
import com.supplychain.gateway.auth.dto.LoginResponse;
import com.supplychain.gateway.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Handles authentication — validates credentials against auth.users table and issues JWT.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository  userRepository;
    private final JwtUtil         jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public Mono<LoginResponse> authenticate(LoginRequest request) {
        return userRepository.findByUsername(request.getUsername())
                .filter(user -> user.isActive() &&
                                passwordEncoder.matches(request.getPassword(), user.getPassword()))
                .switchIfEmpty(Mono.error(new RuntimeException("Invalid username or password")))
                .map(user -> {
                    String token = jwtUtil.generateToken(user.getUsername(), user.getTenantId(), user.getRole());
                    log.info("Login successful: user={}, tenant={}, role={}", user.getUsername(), user.getTenantId(), user.getRole());
                    return LoginResponse.builder()
                            .accessToken(token)
                            .tokenType("Bearer")
                            .tenantId(user.getTenantId())
                            .role(user.getRole())
                            .username(user.getUsername())
                            .build();
                });
    }
}
