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

    public static <T> ResponseEntity<T> body(
            CommandExecutionResult.Completed<T> result,
            T body) {
        Objects.requireNonNull(result, "result is required");
        return ResponseEntity.status(result.responseStatus())
                .headers(headers(result.target()))
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
        HttpHeaders headers = new HttpHeaders();
        headers.setETag("\"" + target.resourceVersion() + "\"");
        return headers;
    }

    @SuppressWarnings("unchecked")
    private static <T> CommandExecutionResult.Completed<T> completed(CommandExecutionResult<T> result) {
        return (CommandExecutionResult.Completed<T>) result;
    }
}
