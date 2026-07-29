package com.agriinsight.backend.realtime.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        name = "RealtimeOperationalAlertAcknowledgementRequest",
        description = "Exact empty object. Acknowledgement scope and observation are server-derived.",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record RealtimeOperationalAlertAcknowledgementRequest() {
}
