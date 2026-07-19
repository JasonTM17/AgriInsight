package com.agriinsight.backend.shared.application;

public class VersionConflictException extends RuntimeException {

    private final long expectedVersion;
    private final long currentVersion;

    public VersionConflictException(long expectedVersion, long currentVersion) {
        super("Resource version does not match the current version");
        this.expectedVersion = expectedVersion;
        this.currentVersion = currentVersion;
    }

    public long expectedVersion() {
        return expectedVersion;
    }

    public long currentVersion() {
        return currentVersion;
    }
}
