package de.seuhd.campuscoffee.api.exceptions;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import org.jspecify.annotations.NonNull;

import java.time.LocalDateTime;

/**
 * Standardized error response structure for all API exceptions.
 * Provides consistent error information to API consumers.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL) // excludes null fields from JSON
public class ErrorResponse {
    /**
     * Machine-readable error code based on exception class name (e.g., PosNotFoundException).
     * Enables clients to handle specific error types programmatically.
     */
    @NonNull
    private String errorCode;

    /**
     * Human-readable error message.
     */
    @NonNull
    private String message;

    /**
     * HTTP status code (e.g., 400, 404, 500).
     */
    private int statusCode;

    /**
     * HTTP status message (e.g., "Bad Request", "Not Found").
     */
    private String statusMessage;

    /**
     * Timestamp when the error occurred.
     */
    private LocalDateTime timestamp;

    /**
     * Request path that caused the error.
     */
    private String path;
}
