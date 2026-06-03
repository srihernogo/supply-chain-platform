package com.supplychain.blockchain.config;

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

@Slf4j
@Configuration
public class TenantDataSourceConfig {

    private static final List<String> TENANTS = List.of("toyota", "honda", "mitsubishi", "daihatsu");

    @Value("${spring.datasource.url}")
    private String baseUrl;

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
        router.setDefaultTargetDataSource(buildTenantDataSource("toyota"));
        router.afterPropertiesSet();

        return router;
    }

    private HikariDataSource buildTenantDataSource(String tenant) {
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

    static class TenantRoutingDataSource extends AbstractRoutingDataSource {
        @Override
        protected Object determineCurrentLookupKey() {
            return TenantContext.getCurrentTenant();
        }
    }
}
