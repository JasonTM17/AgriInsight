package com.agriinsight.backend.shared.application;

public class ResourceStateConflictException extends RuntimeException {

    public ResourceStateConflictException(String message) {
        super(message);
    }
}
