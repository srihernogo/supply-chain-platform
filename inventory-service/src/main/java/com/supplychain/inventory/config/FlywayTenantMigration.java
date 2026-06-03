package com.supplychain.inventory.config;

import com.supplychain.common.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
@Slf4j
@Configuration
public class FlywayTenantMigration implements ApplicationRunner {

    private final DataSource dataSource;

    @Value("${spring.flyway.locations:classpath:db/migration}")
    private String flywayLocations;

    public FlywayTenantMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String tenant : TenantSchemas.ALL) {
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
