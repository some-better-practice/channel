package com.tsmc.autochannel.service;

import com.tsmc.autochannel.entity.LogStatus;
import com.tsmc.autochannel.entity.OperationType;
import com.tsmc.autochannel.entity.TransferLog;
import com.tsmc.autochannel.repository.TransferLogRepository;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferLogServiceTest {

    @Mock private TransferLogRepository repository;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private TransactionTemplate readonlyTransactionTemplate;

    private TestObservationRegistry registry;
    private TransferLogService service;
    private TransferLog sampleLog;

    @BeforeEach
    void setUp() {
        registry = TestObservationRegistry.create();
        service = new TransferLogService(repository, registry, transactionTemplate, readonlyTransactionTemplate);
        sampleLog = TransferLog.builder()
                .id(1L)
                .channelId("CH1")
                .operation(OperationType.SFTP_DOWNLOAD)
                .filename("sample.stdf")
                .status(LogStatus.IN_PROGRESS)
                .build();
    }

    // ── logStart ──────────────────────────────────────────────────────────────

    @Test
    void logStart_connectionFailure_errorTypeIsConnectionFailure() {
        when(transactionTemplate.execute(any())).thenThrow(new CannotGetJdbcConnectionException("connection refused"));

        assertThrows(CannotGetJdbcConnectionException.class,
                () -> service.logStart("CH1", OperationType.SFTP_DOWNLOAD, "sample.stdf"));

        assertErrorType("insert", "connection_failure");
    }

    @Test
    void logStart_transactionException_errorTypeIsConnectionFailure() {
        when(transactionTemplate.execute(any())).thenThrow(new CannotCreateTransactionException("tx failed"));

        assertThrows(CannotCreateTransactionException.class,
                () -> service.logStart("CH1", OperationType.SFTP_DOWNLOAD, "sample.stdf"));

        assertErrorType("insert", "connection_failure");
    }

    @Test
    void logStart_deadlock_errorTypeIsDeadlock() {
        when(transactionTemplate.execute(any())).thenThrow(new CannotAcquireLockException("deadlock detected"));

        assertThrows(CannotAcquireLockException.class,
                () -> service.logStart("CH1", OperationType.SFTP_DOWNLOAD, "sample.stdf"));

        assertErrorType("insert", "deadlock");
    }

    @Test
    void logStart_queryTimeout_errorTypeIsQueryTimeout() {
        when(transactionTemplate.execute(any())).thenThrow(new QueryTimeoutException("query timeout"));

        assertThrows(QueryTimeoutException.class,
                () -> service.logStart("CH1", OperationType.SFTP_DOWNLOAD, "sample.stdf"));

        assertErrorType("insert", "query_timeout");
    }

    @Test
    void logStart_constraintViolation_errorTypeIsConstraintViolation() {
        when(transactionTemplate.execute(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThrows(DataIntegrityViolationException.class,
                () -> service.logStart("CH1", OperationType.SFTP_DOWNLOAD, "sample.stdf"));

        assertErrorType("insert", "constraint_violation");
    }

    @Test
    void logStart_transientError_errorTypeIsTransientError() {
        when(transactionTemplate.execute(any())).thenThrow(new RecoverableDataAccessException("transient"));

        assertThrows(RecoverableDataAccessException.class,
                () -> service.logStart("CH1", OperationType.SFTP_DOWNLOAD, "sample.stdf"));

        assertErrorType("insert", "transient_error");
    }

    // ── logSuccess ────────────────────────────────────────────────────────────

    @Test
    void logSuccess_connectionFailure_errorTypeIsConnectionFailure() {
        doThrow(new CannotGetJdbcConnectionException("connection refused"))
                .when(transactionTemplate).executeWithoutResult(any());

        assertThrows(CannotGetJdbcConnectionException.class,
                () -> service.logSuccess(sampleLog, 1024L, 500L));

        assertErrorType("update_success", "connection_failure");
    }

    @Test
    void logSuccess_deadlock_errorTypeIsDeadlock() {
        doThrow(new CannotAcquireLockException("deadlock"))
                .when(transactionTemplate).executeWithoutResult(any());

        assertThrows(CannotAcquireLockException.class,
                () -> service.logSuccess(sampleLog, 1024L, 500L));

        assertErrorType("update_success", "deadlock");
    }

    @Test
    void logSuccess_queryTimeout_errorTypeIsQueryTimeout() {
        doThrow(new QueryTimeoutException("timeout"))
                .when(transactionTemplate).executeWithoutResult(any());

        assertThrows(QueryTimeoutException.class,
                () -> service.logSuccess(sampleLog, 1024L, 500L));

        assertErrorType("update_success", "query_timeout");
    }

    // ── logFailed ─────────────────────────────────────────────────────────────

    @Test
    void logFailed_connectionFailure_errorTypeIsConnectionFailure() {
        doThrow(new CannotGetJdbcConnectionException("connection refused"))
                .when(transactionTemplate).executeWithoutResult(any());

        assertThrows(CannotGetJdbcConnectionException.class,
                () -> service.logFailed(sampleLog, "something went wrong"));

        assertErrorType("update_failed", "connection_failure");
    }

    @Test
    void logFailed_constraintViolation_errorTypeIsConstraintViolation() {
        doThrow(new DataIntegrityViolationException("constraint"))
                .when(transactionTemplate).executeWithoutResult(any());

        assertThrows(DataIntegrityViolationException.class,
                () -> service.logFailed(sampleLog, "constraint violation"));

        assertErrorType("update_failed", "constraint_violation");
    }

    @Test
    void logFailed_transientError_errorTypeIsTransientError() {
        doThrow(new RecoverableDataAccessException("transient"))
                .when(transactionTemplate).executeWithoutResult(any());

        assertThrows(RecoverableDataAccessException.class,
                () -> service.logFailed(sampleLog, "transient error"));

        assertErrorType("update_failed", "transient_error");
    }

    // ── queryByStatus ─────────────────────────────────────────────────────────

    @Test
    void queryByStatus_connectionFailure_errorTypeIsConnectionFailure() {
        when(readonlyTransactionTemplate.execute(any())).thenThrow(new CannotGetJdbcConnectionException("connection refused"));

        assertThrows(CannotGetJdbcConnectionException.class,
                () -> service.queryByStatus(LogStatus.IN_PROGRESS));

        assertErrorType("query_by_status", "connection_failure");
    }

    @Test
    void queryByStatus_queryTimeout_errorTypeIsQueryTimeout() {
        when(readonlyTransactionTemplate.execute(any())).thenThrow(new QueryTimeoutException("timeout"));

        assertThrows(QueryTimeoutException.class,
                () -> service.queryByStatus(LogStatus.IN_PROGRESS));

        assertErrorType("query_by_status", "query_timeout");
    }

    // ── countByChannelAndStatus ───────────────────────────────────────────────

    @Test
    void countByChannelAndStatus_connectionFailure_errorTypeIsConnectionFailure() {
        when(readonlyTransactionTemplate.execute(any())).thenThrow(new CannotGetJdbcConnectionException("connection refused"));

        assertThrows(CannotGetJdbcConnectionException.class,
                () -> service.countByChannelAndStatus("CH1", LogStatus.SUCCESS));

        assertErrorType("count_by_channel_status", "connection_failure");
    }

    @Test
    void countByChannelAndStatus_deadlock_errorTypeIsDeadlock() {
        when(readonlyTransactionTemplate.execute(any())).thenThrow(new CannotAcquireLockException("deadlock"));

        assertThrows(CannotAcquireLockException.class,
                () -> service.countByChannelAndStatus("CH1", LogStatus.SUCCESS));

        assertErrorType("count_by_channel_status", "deadlock");
    }

    // ── sumFileSizeByChannel ──────────────────────────────────────────────────

    @Test
    void sumFileSizeByChannel_connectionFailure_errorTypeIsConnectionFailure() {
        when(readonlyTransactionTemplate.execute(any())).thenThrow(new CannotGetJdbcConnectionException("connection refused"));

        assertThrows(CannotGetJdbcConnectionException.class,
                () -> service.sumFileSizeByChannel("CH1"));

        assertErrorType("sum_file_size", "connection_failure");
    }

    @Test
    void sumFileSizeByChannel_queryTimeout_errorTypeIsQueryTimeout() {
        when(readonlyTransactionTemplate.execute(any())).thenThrow(new QueryTimeoutException("timeout"));

        assertThrows(QueryTimeoutException.class,
                () -> service.sumFileSizeByChannel("CH1"));

        assertErrorType("sum_file_size", "query_timeout");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void assertErrorType(String action, String expectedErrorType) {
        TestObservationRegistryAssert.assertThat(registry)
                .hasObservationWithNameEqualTo("mariadb")
                .that()
                .hasLowCardinalityKeyValue("action", action)
                .hasLowCardinalityKeyValue("errorType", expectedErrorType);
    }
}
