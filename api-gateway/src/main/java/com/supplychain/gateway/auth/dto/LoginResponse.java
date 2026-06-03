package com.supplychain.gateway.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {
    private String accessToken;
    private String tokenType;
    private String tenantId;
    private String role;
    private String username;
}
