package com.supplychain.common.tenant;

/**
 * Thread-local holder for the current tenant identifier.
 * Set by the JWT filter on each incoming request and cleared after request completes.
 *
 * <p>Usage pattern:
 * <pre>
 *   TenantContext.setCurrentTenant("toyota");
 *   try {
 *       // ... business logic
 *   } finally {
 *       TenantContext.clear();
 *   }
 * </pre>
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new InheritableThreadLocal<>();

    private TenantContext() {
        // Utility class — no instantiation
    }

    /**
     * Set the tenant for the current thread.
     *
     * @param tenantId must be a valid, non-null tenant identifier (e.g. "toyota")
     */
    public static void setCurrentTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant ID must not be null or blank");
        }
        CURRENT_TENANT.set(tenantId.toLowerCase());
    }

    /**
     * Get the tenant for the current thread.
     *
     * @return tenant identifier, or null if not set
     */
    public static String getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    /**
     * Clear the tenant from the current thread.
     * Must be called in a finally block to prevent thread pool leaks.
     */
    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
