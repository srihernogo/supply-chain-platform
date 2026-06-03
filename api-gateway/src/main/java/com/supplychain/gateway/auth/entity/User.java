package com.supplychain.gateway.auth.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Table("auth.users")
public class User {
    @Id
    private Long          id;
    private String        username;
    private String        password;
    private String        tenantId;
    private String        role;
    private boolean       active;
    private LocalDateTime createdAt;
}
