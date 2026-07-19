package com.agriinsight.backend.authorization.application;

@FunctionalInterface
public interface TenantAuditPublisher {

    void publish(TenantAuditEvent event);
}
