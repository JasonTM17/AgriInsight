package com.agriinsight.backend.identity.api;

import com.agriinsight.backend.shared.application.CommandTarget;
import java.util.Objects;
import java.util.UUID;

public record CommandReceiptResponse(
        String resourceType,
        UUID resourceId,
        long committedVersion) {

    public CommandReceiptResponse {
        Objects.requireNonNull(resourceType, "resourceType is required");
        Objects.requireNonNull(resourceId, "resourceId is required");
    }

    public static CommandReceiptResponse from(CommandTarget target) {
        Objects.requireNonNull(target, "target is required");
        return new CommandReceiptResponse(
                target.resourceType(),
                target.resourceId(),
                target.resourceVersion());
    }
}
