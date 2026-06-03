package com.supplychain.inventory.config;

import com.supplychain.common.security.JwtUtil;
import com.supplychain.common.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX    = "Bearer ";
    private static final String HEADER_TENANT    = "X-Tenant-Id";
    private static final String HEADER_ROLE      = "X-User-Role";
    private static final String HEADER_USERNAME  = "X-Username";

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        try {
            String tenantId = request.getHeader(HEADER_TENANT);
            String role     = request.getHeader(HEADER_ROLE);
            String username = request.getHeader(HEADER_USERNAME);

            if (tenantId == null || role == null) {
                String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
                if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
                    String token = authHeader.substring(BEARER_PREFIX.length());
                    if (jwtUtil.isValid(token)) {
                        tenantId = jwtUtil.getTenantId(token);
                        role     = jwtUtil.getRole(token);
                        username = jwtUtil.getSubject(token);
                    }
                }
            }

            if (tenantId != null && role != null) {
                TenantContext.setCurrentTenant(tenantId);

                var auth = new UsernamePasswordAuthenticationToken(
                        username != null ? username : "unknown",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            }

            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }
}
