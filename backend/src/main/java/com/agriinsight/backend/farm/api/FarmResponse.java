package com.agriinsight.backend.farm.api;

import com.agriinsight.backend.farm.application.FarmRecord;
import java.util.Objects;
import java.util.UUID;

public record FarmResponse(
        UUID id,
        String code,
        String displayName,
        boolean active,
        long version) {

    public FarmResponse {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(code, "code is required");
        Objects.requireNonNull(displayName, "displayName is required");
    }

    public static FarmResponse from(FarmRecord farm) {
        Objects.requireNonNull(farm, "farm is required");
        return new FarmResponse(
                farm.id(),
                farm.code(),
                farm.displayName(),
                farm.active(),
                farm.version());
    }
}
