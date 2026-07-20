package com.agriinsight.backend.persistence.support;

import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.RUNTIME;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.RUNTIME_PASSWORD;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.jdbcUrl;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.authorization.infrastructure.TenantTransactionAspect;
import com.agriinsight.backend.shared.application.TenantAuthorizationDeniedRecorder;
import com.agriinsight.backend.shared.persistence.TenantContextBinder;
import com.agriinsight.backend.shared.persistence.TenantContextState;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.postgresql.PostgreSQLContainer;

public final class TenantTransactionTestHarness implements AutoCloseable {

    private final HikariDataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final TenantContextState contextState;
    private final DataSourceTransactionManager transactionManager;

    private TenantTransactionTestHarness(HikariDataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.contextState = new TenantContextState();
        this.transactionManager = new DataSourceTransactionManager(dataSource);
    }

    public static TenantTransactionTestHarness runtime(
            PostgreSQLContainer container,
            String database) {
        HikariConfig configuration = new HikariConfig();
        configuration.setJdbcUrl(jdbcUrl(container, database));
        configuration.setUsername(RUNTIME);
        configuration.setPassword(RUNTIME_PASSWORD);
        configuration.setMaximumPoolSize(1);
        configuration.setMinimumIdle(1);
        return new TenantTransactionTestHarness(new HikariDataSource(configuration));
    }

    public JdbcTemplate jdbcTemplate() {
        return jdbcTemplate;
    }

    public TenantContextState contextState() {
        return contextState;
    }

    public DataSourceTransactionManager transactionManager() {
        return transactionManager;
    }

    @SuppressWarnings("unchecked")
    public <T> T withinTenant(ThrowingSupplier<T> operation) throws Throwable {
        return withinTenant(decision -> { }, operation);
    }

    @SuppressWarnings("unchecked")
    public <T> T withinTenant(
            TenantAuthorizationDeniedRecorder deniedRecorder,
            ThrowingSupplier<T> operation) throws Throwable {
        TenantTransactionAspect scopedAspect = new TenantTransactionAspect(
                new TenantContextBinder(jdbcTemplate),
                contextState,
                transactionManager,
                deniedRecorder);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenAnswer(invocation -> operation.get());
        return (T) scopedAspect.withinTenantTransaction(joinPoint);
    }

    @Override
    public void close() {
        dataSource.close();
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {

        T get() throws Throwable;
    }
}
