package com.agriinsight.backend.realtime.infrastructure;

import com.agriinsight.backend.integration.infrastructure.RealtimeWorkerProperties;
import com.agriinsight.backend.realtime.application.RealtimeDeadLetterEnvelopeValidator;
import com.agriinsight.backend.realtime.application.RealtimeEventValidationException;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertEvaluator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Observes DLT records without trusting framework headers or retaining raw values. */
@Component
@Profile("realtime-worker")
@DependsOn("realtimeAlertWorkerRoleVerifier")
@ConditionalOnProperty(
        prefix = "agriinsight.realtime.alerts",
        name = "enabled",
        havingValue = "true")
public class RealtimeDeadLetterAlertObserver {

    private static final Logger LOGGER = LoggerFactory.getLogger(RealtimeDeadLetterAlertObserver.class);

    private final RealtimeDeadLetterEnvelopeValidator validator;
    private final RealtimeOperationalAlertEvaluator evaluator;
    private final RealtimeWorkerProperties workerProperties;
    private final Counter unattributableRecordCounter;

    public RealtimeDeadLetterAlertObserver(
            RealtimeDeadLetterEnvelopeValidator validator,
            RealtimeOperationalAlertEvaluator evaluator,
            RealtimeWorkerProperties workerProperties,
            MeterRegistry meterRegistry) {
        this.validator = Objects.requireNonNull(validator, "validator is required");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator is required");
        this.workerProperties = Objects.requireNonNull(workerProperties, "workerProperties is required");
        this.unattributableRecordCounter = Counter.builder(
                        "agriinsight.realtime.alerts.dlt.unattributable")
                .description("DLT records that cannot prove a tenant from their bounded value")
                .register(Objects.requireNonNull(meterRegistry, "meterRegistry is required"));
    }

    @KafkaListener(
            topics = "${agriinsight.realtime.dead-letter-topic}",
            groupId = "${agriinsight.realtime.alerts.observer-group-id}",
            containerFactory = "realtimeDeadLetterAlertKafkaListenerContainerFactory")
    public void observe(ConsumerRecord<byte[], byte[]> record) {
        try {
            evaluator.observeDeadLetter(validator.parse(record, workerProperties.maxRecordBytes()));
        } catch (RealtimeEventValidationException exception) {
            unattributableRecordCounter.increment();
            LOGGER.warn("realtime_alert_dlt_unattributable reasonType={}",
                    exception.getClass().getSimpleName());
        }
    }
}
