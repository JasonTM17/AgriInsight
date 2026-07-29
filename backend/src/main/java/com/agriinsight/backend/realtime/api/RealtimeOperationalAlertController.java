package com.agriinsight.backend.realtime.api;

import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertNotFoundException;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertService;
import com.agriinsight.backend.shared.api.ApiCommandFingerprintFactory;
import com.agriinsight.backend.shared.api.ApiCommandResponses;
import com.agriinsight.backend.shared.api.ApiVersion;
import com.agriinsight.backend.shared.api.RequestCorrelation;
import com.agriinsight.backend.shared.domain.CanonicalCommandBody;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiVersion.PREFIX + "/realtime/alerts")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class RealtimeOperationalAlertController {

    private static final String ALERTS = ApiVersion.PREFIX + "/realtime/alerts";
    private static final String ACKNOWLEDGEMENTS = ALERTS + "/{id}/acknowledgements";

    private final RealtimeOperationalAlertService alerts;
    private final ApiCommandFingerprintFactory fingerprints;

    public RealtimeOperationalAlertController(
            RealtimeOperationalAlertService alerts,
            ApiCommandFingerprintFactory fingerprints) {
        this.alerts = Objects.requireNonNull(alerts, "alerts is required");
        this.fingerprints = Objects.requireNonNull(fingerprints, "fingerprints is required");
    }

    @Operation(
            summary = "Get current realtime operational alerts",
            description = "Returns the fixed latest 50 open, payload-free alerts for the current tenant and profile.")
    @GetMapping
    RealtimeOperationalAlertFeedResponse feed(HttpServletRequest request) {
        requireNoQueryParameters(request);
        return RealtimeOperationalAlertFeedResponse.from(alerts.feed());
    }

    @Operation(
            summary = "Acknowledge the current alert observation",
            description = "Writes one immutable current-profile acknowledgement revision and returns the current open alert.")
    @ApiResponse(
            responseCode = "200",
            description = "Current open operational alert",
            content = @io.swagger.v3.oas.annotations.media.Content(
                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                            implementation = RealtimeOperationalAlertResponse.class)))
    @ApiResponse(
            responseCode = "404",
            ref = "#/components/responses/NotFound")
    @PostMapping("/{id}/acknowledgements")
    ResponseEntity<RealtimeOperationalAlertResponse> acknowledge(
            @Parameter(description = "Operational alert identifier", required = true)
            @PathVariable("id") UUID alertId,
            @RequestBody RealtimeOperationalAlertAcknowledgementRequest body,
            @Parameter(
                    description = "Unique replay key for this authenticated tenant command",
                    required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        requireNoQueryParameters(request);
        if (body == null) {
            throw new IllegalArgumentException("Request body must be an empty JSON object");
        }
        String correlationId = RequestCorrelation.resolve(request);
        var execution = alerts.acknowledge(
                fingerprints.create(
                        principal,
                        idempotencyKey,
                        "POST",
                        ACKNOWLEDGEMENTS,
                        Map.of("id", alertId.toString()),
                        Map.of(),
                        CanonicalCommandBody.of(Map.of()),
                        Map.of(),
                        correlationId),
                alertId);
        var completed = ApiCommandResponses.requireCompleted(execution);
        var current = completed.representation()
                .orElseThrow(RealtimeOperationalAlertNotFoundException::new);
        return ResponseEntity.status(completed.responseStatus())
                .body(RealtimeOperationalAlertResponse.from(current));
    }

    private static void requireNoQueryParameters(HttpServletRequest request) {
        if (!request.getParameterMap().isEmpty()) {
            throw new IllegalArgumentException("Query parameters are not supported");
        }
    }
}
