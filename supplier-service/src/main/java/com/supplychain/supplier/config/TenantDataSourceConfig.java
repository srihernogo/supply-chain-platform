package com.supplychain.supplier.config;

import com.supplychain.common.security.JwtUtil;
import com.supplychain.common.tenant.TenantContext;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Multi-tenant DataSource configuration using AbstractRoutingDataSource.
 *
 * <p>Strategy: schema-per-tenant in the same PostgreSQL database.
 * Each tenant maps to a HikariCP DataSource configured with
 * {@code currentSchema=<tenantId>} so that all JPA queries
 * automatically target the correct schema.
 *
 * <p>Flyway runs migration per schema on startup.
 */
@Slf4j
@Configuration
public class TenantDataSourceConfig {

    // Known tenants — in production, load from a central registry DB
    private static final List<String> TENANTS = List.of("toyota", "honda", "mitsubishi", "daihatsu");

    @Value("${spring.datasource.url}")
    private String baseUrl; // jdbc:postgresql://host:5432/supplychain

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration-ms:86400000}")
    private long jwtExpirationMs;

    @Bean
    public JwtUtil jwtUtil() {
        return new JwtUtil(jwtSecret, jwtExpirationMs);
    }

    /**
     * Returns a DataSource per tenant, each with its own HikariCP pool
     * pointing to the tenant's schema.
     */
    @Bean
    @Primary
    public DataSource dataSource() {
        Map<Object, Object> targetDataSources = new HashMap<>();

        for (String tenant : TENANTS) {
            DataSource ds = buildTenantDataSource(tenant);
            targetDataSources.put(tenant, ds);
            log.info("Registered DataSource for tenant: {}", tenant);
        }

        TenantRoutingDataSource router = new TenantRoutingDataSource();
        router.setTargetDataSources(targetDataSources);

        // Default to "toyota" for health checks / Flyway init
        router.setDefaultTargetDataSource(buildTenantDataSource("toyota"));
        router.afterPropertiesSet();

        return router;
    }

    private HikariDataSource buildTenantDataSource(String tenant) {
        // Strip any existing currentSchema from the URL to avoid duplication
        String baseJdbcUrl = baseUrl.replaceAll("[?&]currentSchema=[^&]*", "");
        String separator   = baseJdbcUrl.contains("?") ? "&" : "?";

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(baseJdbcUrl + separator + "currentSchema=" + tenant);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setPoolName("HikariPool-" + tenant);
        ds.setMaximumPoolSize(5);
        ds.setMinimumIdle(1);
        ds.setConnectionTimeout(30_000);
        ds.setIdleTimeout(600_000);
        ds.setMaxLifetime(1_800_000);
        return ds;
    }

    /**
     * Routes each DB request to the DataSource for the current tenant.
     */
    static class TenantRoutingDataSource extends AbstractRoutingDataSource {
        @Override
        protected Object determineCurrentLookupKey() {
            String tenant = TenantContext.getCurrentTenant();
            if (tenant == null) {
                log.warn("No tenant in context — using default datasource");
            }
            return tenant;
        }
    }
}
