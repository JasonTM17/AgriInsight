package com.agriinsight.backend.integration.infrastructure;

import com.agriinsight.backend.integration.application.OutboxPublishingService;
import com.agriinsight.backend.integration.application.OutboxPublishingService.PublishResult;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "agriinsight.realtime",
        name = "publisher-enabled",
        havingValue = "true")
public class OutboxPublishingSchedule {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxPublishingSchedule.class);
    private final OutboxPublishingService publishingService;
    private final AtomicBoolean running = new AtomicBoolean();

    public OutboxPublishingSchedule(OutboxPublishingService publishingService) {
        this.publishingService =
                Objects.requireNonNull(publishingService, "publishingService is required");
    }

    @Scheduled(fixedDelayString = "${agriinsight.realtime.poll-delay:1s}")
    public void publishAvailable() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            PublishResult result = publishingService.publishAvailable();
            if (result.leased() > 0) {
                LOGGER.info(
                        "Outbox publication cycle leased={}, published={}, requeued={}, "
                                + "deadLettered={}, stale={}",
                        result.leased(),
                        result.published(),
                        result.requeued(),
                        result.deadLettered(),
                        result.stale());
            }
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Outbox publication cycle failed: {}",
                    exception.getClass().getSimpleName());
        } finally {
            running.set(false);
        }
    }
}
