package com.agriinsight.backend.shared.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public sealed interface CommandExecutionResult<T>
        permits CommandExecutionResult.Completed, CommandExecutionResult.Conflict {

    record Completed<T>(
            UUID commandId,
            boolean replayed,
            int responseStatus,
            CommandTarget target,
            Optional<T> representation) implements CommandExecutionResult<T> {

        public Completed {
            Objects.requireNonNull(commandId, "commandId is required");
            if (responseStatus < 200 || responseStatus > 299) {
                throw new IllegalArgumentException("responseStatus must be successful");
            }
            Objects.requireNonNull(target, "target is required");
            representation = Objects.requireNonNull(representation, "representation is required");
        }
    }

    record Conflict<T>(UUID commandId) implements CommandExecutionResult<T> {

        public Conflict {
            Objects.requireNonNull(commandId, "commandId is required");
        }
    }
}
