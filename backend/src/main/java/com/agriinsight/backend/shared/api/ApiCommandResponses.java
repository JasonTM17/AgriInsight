package com.agriinsight.backend.shared.api;

import com.agriinsight.backend.shared.application.CommandExecutionResult;
import com.agriinsight.backend.shared.application.CommandTarget;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

public final class ApiCommandResponses {

    private ApiCommandResponses() {
    }

    public static <T> CommandExecutionResult.Completed<T> requireCompleted(
            CommandExecutionResult<T> result) {
        Objects.requireNonNull(result, "result is required");
        if (result instanceof CommandExecutionResult.Conflict<?>) {
            throw new IdempotencyConflictException();
        }
        return completed(result);
    }

    public static <T, R> ResponseEntity<R> body(
            CommandExecutionResult.Completed<T> result,
            R body) {
        return body(result, body, result.target().resourceVersion());
    }

    public static <T, R> ResponseEntity<R> body(
            CommandExecutionResult.Completed<T> result,
            R body,
            long representationVersion) {
        Objects.requireNonNull(result, "result is required");
        return ResponseEntity.status(result.responseStatus())
                .headers(headers(representationVersion))
                .body(body);
    }

    public static ResponseEntity<Void> empty(CommandExecutionResult.Completed<?> result) {
        Objects.requireNonNull(result, "result is required");
        return ResponseEntity.status(result.responseStatus())
                .headers(headers(result.target()))
                .build();
    }

    public static HttpHeaders headers(CommandTarget target) {
        Objects.requireNonNull(target, "target is required");
        return headers(target.resourceVersion());
    }

    public static HttpHeaders headers(long resourceVersion) {
        if (resourceVersion < 0) {
            throw new IllegalArgumentException("resourceVersion must not be negative");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setETag("\"" + resourceVersion + "\"");
        return headers;
    }

    @SuppressWarnings("unchecked")
    private static <T> CommandExecutionResult.Completed<T> completed(CommandExecutionResult<T> result) {
        return (CommandExecutionResult.Completed<T>) result;
    }
}
