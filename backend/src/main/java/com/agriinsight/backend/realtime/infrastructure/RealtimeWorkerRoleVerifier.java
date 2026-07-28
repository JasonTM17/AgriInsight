package com.agriinsight.backend.realtime.infrastructure;

import com.agriinsight.backend.integration.infrastructure.RealtimeWorkerProperties;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;

/** Fails worker startup when it is not using the deliberately restricted integration login. */
public final class RealtimeWorkerRoleVerifier {

    private static final String REQUIRED_LOGIN = "agriinsight_realtime";

    private final JdbcTemplate jdbcTemplate;
    private final RealtimeWorkerProperties workerProperties;
    private final RealtimeAlertWorkerProperties alertProperties;

    public RealtimeWorkerRoleVerifier(
            JdbcTemplate jdbcTemplate,
            RealtimeWorkerProperties workerProperties,
            RealtimeAlertWorkerProperties alertProperties) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
        this.workerProperties = Objects.requireNonNull(workerProperties, "workerProperties is required");
        this.alertProperties = Objects.requireNonNull(alertProperties, "alertProperties is required");
    }

    public void verify() {
        if (!workerProperties.consumerEnabled()) {
            throw new IllegalStateException("operational alert worker requires the realtime consumer");
        }
        if (workerProperties.deadLetterTopic().equals(alertProperties.observerFailureTopic())) {
            throw new IllegalStateException("observer failure topic must differ from the observed DLT topic");
        }
        Boolean verified = jdbcTemplate.queryForObject(
                """
                SELECT current_user = CAST(? AS name)
                   AND pg_has_role(current_user, 'agriinsight_integration', 'member')
                """,
                Boolean.class,
                REQUIRED_LOGIN);
        if (!Boolean.TRUE.equals(verified)) {
            throw new IllegalStateException("operational alert worker database role verification failed");
        }
    }
}
