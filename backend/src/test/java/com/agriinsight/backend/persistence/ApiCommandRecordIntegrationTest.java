package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.ApiCommandIntegrationFixture.PRINCIPAL_ID;
import static com.agriinsight.backend.persistence.support.ApiCommandIntegrationFixture.ROUTE;
import static com.agriinsight.backend.persistence.support.ApiCommandIntegrationFixture.TARGET_ID;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.bootstrapRoles;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.count;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.execute;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.migrate;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.operatorConnection;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.scalar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.persistence.support.ApiCommandIntegrationFixture;
import com.agriinsight.backend.persistence.support.PostgresIntegrationSupport;
import com.agriinsight.backend.persistence.support.SqlTestResources;
import com.agriinsight.backend.shared.application.ApiCommandRecordStore;
import com.agriinsight.backend.shared.application.CommandCompletion;
import com.agriinsight.backend.shared.application.CommandExecutionResult;
import com.agriinsight.backend.shared.application.IdempotencyKey;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class ApiCommandRecordIntegrationTest {

    private static final String BODY = "{\"displayName\":\"body-secret-do-not-store\"}";

    @Container
    private static final PostgreSQLContainer POSTGRESQL = PostgresIntegrationSupport.container();

    @BeforeAll
    static void prepareDatabase() throws Exception {
        bootstrapRoles(POSTGRESQL, "agriinsight");
        migrate(POSTGRESQL, "agriinsight");
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            execute(operator, SqlTestResources.projectFile("backend/src/test/resources/sql/rls-fixtures.sql"));
        }
    }

    @Test
    void concurrentSameKeyAndHashConvergeOnOneCommittedMutation() throws Exception {
        String key = "concurrent-command-0001";
        CountDownLatch mutationEntered = new CountDownLatch(1);
        CountDownLatch releaseMutation = new CountDownLatch(1);
        CountDownLatch duplicateClaimed = new CountDownLatch(1);
        AtomicInteger mutations = new AtomicInteger();
        AtomicInteger replays = new AtomicInteger();
        try (var first = ApiCommandIntegrationFixture.open(POSTGRESQL);
                var duplicate = ApiCommandIntegrationFixture.open(POSTGRESQL, duplicateClaimed::countDown);
                var executor = Executors.newFixedThreadPool(2)) {
            var request = first.request(key, PRINCIPAL_ID.toString(), "\"7\"", BODY);
            var appliedFuture = executor.submit(() -> first.execute(
                    request,
                    () -> {
                        mutations.incrementAndGet();
                        mutationEntered.countDown();
                        await(releaseMutation);
                        return completion("applied");
                    },
                    target -> Optional.of("unexpected")));
            assertThat(mutationEntered.await(5, TimeUnit.SECONDS)).isTrue();
            var replayFuture = executor.submit(() -> duplicate.execute(
                    request,
                    () -> {
                        mutations.incrementAndGet();
                        return completion("duplicate");
                    },
                    target -> {
                        replays.incrementAndGet();
                        return Optional.of("replayed-current-view");
                    }));
            assertThat(duplicateClaimed.await(5, TimeUnit.SECONDS)).isTrue();
            releaseMutation.countDown();

            var applied = completed(appliedFuture.get(10, TimeUnit.SECONDS));
            var replayed = completed(replayFuture.get(10, TimeUnit.SECONDS));
            assertThat(applied.replayed()).isFalse();
            assertThat(replayed.replayed()).isTrue();
            assertThat(replayed.commandId()).isEqualTo(applied.commandId());
            assertThat(replayed.representation()).contains("replayed-current-view");
        }
        assertThat(mutations).hasValue(1);
        assertThat(replays).hasValue(1);
        assertThat(commandCount(key)).isEqualTo(1);
    }

    @Test
    void rollbackFreesKeyAndResponseLossReplaysCommittedMetadata() throws Exception {
        String key = "rollback-command-0001";
        try (var fixture = ApiCommandIntegrationFixture.open(POSTGRESQL)) {
            var request = fixture.request(key, PRINCIPAL_ID.toString(), "\"3\"", BODY);
            assertThatThrownBy(() -> fixture.execute(
                            request,
                            () -> { throw new IllegalStateException("domain rollback"); },
                            target -> Optional.of("unexpected")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("domain rollback");
            assertThat(commandCount(key)).isZero();

            var applied = completed(fixture.execute(
                    request,
                    () -> completion("response-that-was-lost"),
                    target -> Optional.of("unexpected")));
            var replayed = completed(fixture.execute(
                    request,
                    () -> completion("duplicate"),
                    target -> Optional.of("fresh-authorized-view")));
            assertThat(replayed.commandId()).isEqualTo(applied.commandId());
            assertThat(replayed.replayed()).isTrue();
            assertThat(replayed.representation()).contains("fresh-authorized-view");
        }
        assertThat(commandCount(key)).isEqualTo(1);
    }

    @Test
    void changedPreconditionOrPathConflictsAndLeavesNoSensitiveSnapshot() throws Exception {
        String key = "conflict-command-raw-key-0001";
        try (var fixture = ApiCommandIntegrationFixture.open(POSTGRESQL)) {
            fixture.execute(
                    fixture.request(key, PRINCIPAL_ID.toString(), "\"5\"", BODY),
                    () -> completion("applied"),
                    target -> Optional.of("unexpected"));
            var changedHeader = fixture.execute(
                    fixture.request(key, PRINCIPAL_ID.toString(), "\"6\"", BODY),
                    () -> completion("duplicate"),
                    target -> Optional.of("unexpected"));
            var changedPath = fixture.execute(
                    fixture.request(key, TARGET_ID.toString(), "\"5\"", BODY),
                    () -> completion("duplicate"),
                    target -> Optional.of("unexpected"));
            assertThat(changedHeader).isInstanceOf(CommandExecutionResult.Conflict.class);
            assertThat(changedPath).isInstanceOf(CommandExecutionResult.Conflict.class);
        }
        assertDurableRedactedContract(key);
    }

    private static CommandCompletion<String> completion(String body) {
        return CommandCompletion.withRepresentation(200, "USER_PROFILE", TARGET_ID, 7, body);
    }

    private static CommandExecutionResult.Completed<String> completed(CommandExecutionResult<String> result) {
        assertThat(result).isInstanceOf(CommandExecutionResult.Completed.class);
        return (CommandExecutionResult.Completed<String>) result;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent command");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for concurrent command", exception);
        }
    }

    private static long commandCount(String rawKey) throws Exception {
        String digest = IdempotencyKey.parse(rawKey).digest();
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            return count(operator, "SELECT count(*) FROM api_command_records WHERE idempotency_key_digest = '"
                    + digest + "'");
        }
    }

    private static void assertDurableRedactedContract(String rawKey) throws Exception {
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            assertThat(count(operator, "SELECT count(*) FROM tenant_audit_events WHERE action = "
                    + "'IDEMPOTENCY_CONFLICT' AND target_reference = '" + ROUTE + "'"))
                    .isEqualTo(2);
            assertThat(count(operator, "SELECT count(*) FROM api_command_records WHERE "
                    + "concat_ws('|', http_method, route_template, idempotency_key_digest, command_hash, state) "
                    + "LIKE '%" + rawKey + "%' OR concat_ws('|', http_method, route_template, command_hash) "
                    + "LIKE '%body-secret-do-not-store%'"))
                    .isZero();
            assertThat(scalar(operator, "SELECT string_agg(column_name, ',' ORDER BY ordinal_position) "
                    + "FROM information_schema.columns WHERE table_schema = 'public' "
                    + "AND table_name = 'api_command_records'"))
                    .isEqualTo("id,tenant_id,principal_id,http_method,route_template,idempotency_key_digest,"
                            + "canonical_schema_version,command_hash,state,response_status,target_resource_type,"
                            + "target_resource_id,target_version,created_at,updated_at");
            assertThat(count(operator, "SELECT count(*) WHERE has_table_privilege("
                    + "'agriinsight_runtime', 'api_command_records', 'DELETE')"))
                    .isZero();
        }
        assertThat(ApiCommandRecordStore.class.getDeclaredMethods())
                .noneMatch(method -> method.getName().matches("(?i).*(delete|purge|expire).*"));
    }
}
