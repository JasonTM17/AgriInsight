package com.agriinsight.backend.shared.application;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resourceType) {
        super(resourceType + " was not found");
    }
}
