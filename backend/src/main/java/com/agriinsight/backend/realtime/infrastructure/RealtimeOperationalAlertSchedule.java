package com.agriinsight.backend.realtime.infrastructure;

import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertEvaluator;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs bounded alert evaluation only in the dedicated non-web worker profile. */
@Component
@Profile("realtime-worker")
@DependsOn("realtimeAlertWorkerRoleVerifier")
@ConditionalOnProperty(
        prefix = "agriinsight.realtime.alerts",
        name = "enabled",
        havingValue = "true")
public class RealtimeOperationalAlertSchedule {

    private final RealtimeOperationalAlertEvaluator evaluator;

    public RealtimeOperationalAlertSchedule(RealtimeOperationalAlertEvaluator evaluator) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator is required");
    }

    @Scheduled(fixedDelayString = "${agriinsight.realtime.alerts.evaluation-delay}")
    public void evaluate() {
        evaluator.evaluateAll();
    }
}
