package com.supplychain.supplier.config;

import com.supplychain.common.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.List;

/**
 * Runs Flyway migration for EVERY tenant schema on startup.
 *
 * <p>This ensures all schemas stay in sync with the latest migration scripts.
 * In production, new tenants would be provisioned via a tenant management API
 * that calls this migration runner.
 */
@Slf4j
@Configuration
public class FlywayTenantMigration implements ApplicationRunner {

    private static final List<String> TENANTS = List.of("toyota", "honda", "mitsubishi", "daihatsu");

    private final DataSource dataSource;

    @Value("${spring.flyway.locations:classpath:db/migration}")
    private String flywayLocations;

    public FlywayTenantMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String tenant : TENANTS) {
            try {
                log.info("Running Flyway migration for tenant schema: {}", tenant);
                TenantContext.setCurrentTenant(tenant);

                Flyway flyway = Flyway.configure()
                        .dataSource(dataSource)
                        .schemas(tenant)
                        .locations(flywayLocations)
                        .baselineOnMigrate(true)
                        .outOfOrder(false)
                        .load();

                flyway.migrate();
                log.info("Flyway migration completed for tenant: {}", tenant);
            } catch (Exception e) {
                log.error("Flyway migration failed for tenant: {}", tenant, e);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
