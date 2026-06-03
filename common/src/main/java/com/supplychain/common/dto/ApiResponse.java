package com.supplychain.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Standard API response wrapper for all services.
 *
 * <p>
 * Success example:
 * 
 * <pre>
 * {
 *   "success": true,
 *   "message": "Inventory receipt processed",
 *   "data": { ... },
 *   "timestamp": "2024-01-01T10:00:00Z"
 * }
 * </pre>
 *
 * <p>
 * Error example:
 * 
 * <pre>
 * {
 *   "success": false,
 *   "message": "Material not found",
 *   "errorCode": "MATERIAL_NOT_FOUND",
 *   "timestamp": "2024-01-01T10:00:00Z"
 * }
 * </pre>
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;
    private final String errorCode;

    @Builder.Default
    private final Instant timestamp = Instant.now();

    // ─── Static factories ───────────────────────────

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> error(String message, String errorCode) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .errorCode(errorCode)
                .build();
    }

    public static <T> ApiResponse<T> error(String message) {
        return error(message, "INTERNAL_ERROR");
    }
}
