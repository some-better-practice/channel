package com.tsmc.autochannel.service;

import com.tsmc.autochannel.entity.LogStatus;
import com.tsmc.autochannel.entity.OperationType;
import com.tsmc.autochannel.entity.TransferLog;
import com.tsmc.autochannel.metrics.MariaDbObservationTag;
import com.tsmc.autochannel.repository.TransferLogRepository;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferLogService {

    private final TransferLogRepository repository;
    private final ObservationRegistry observationRegistry;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate readonlyTransactionTemplate;

    public TransferLog logStart(String channelId, OperationType operation, String filename) {
        Observation obs = Observation.createNotStarted("mariadb", observationRegistry)
                .lowCardinalityKeyValue("operation", operation.name())
                .lowCardinalityKeyValue("action", "insert")
                .lowCardinalityKeyValue(MariaDbObservationTag.errorType.name(), "none")
                .highCardinalityKeyValue("channelId", channelId)
                .highCardinalityKeyValue("filename", filename)
                .highCardinalityKeyValue("status", LogStatus.IN_PROGRESS.name())
                .start();
        try {
            TransferLog saved = transactionTemplate.execute(status -> {
                TransferLog entity = TransferLog.builder()
                        .channelId(channelId)
                        .operation(operation)
                        .filename(filename)
                        .status(LogStatus.IN_PROGRESS)
                        .build();
                return repository.save(entity);
            });
            obs.highCardinalityKeyValue("db.record.id", String.valueOf(saved.getId()));
            log.info("[db] INSERT transfer_log id={} channelId={} operation={} filename={}", saved.getId(), channelId, operation, filename);
            return saved;
        } catch (DataAccessException | TransactionException e) {
            obs.lowCardinalityKeyValue(MariaDbObservationTag.errorType.name(), classifyError(e));
            obs.error(e);
            throw e;
        } finally {
            obs.stop();
        }
    }

    public void logSuccess(TransferLog transferLog, long fileSizeBytes, long durationMs) {
        transferLog.setStatus(LogStatus.SUCCESS);
        transferLog.setFileSizeBytes(fileSizeBytes);
        transferLog.setDurationMs(durationMs);

        Observation obs = Observation.createNotStarted("mariadb", observationRegistry)
                .lowCardinalityKeyValue("operation", transferLog.getOperation().name())
                .lowCardinalityKeyValue("action", "update_success")
                .lowCardinalityKeyValue(MariaDbObservationTag.errorType.name(), "none")
                .highCardinalityKeyValue("channelId", transferLog.getChannelId())
                .highCardinalityKeyValue("filename", transferLog.getFilename())
                .highCardinalityKeyValue("db.record.id", String.valueOf(transferLog.getId()))
                .highCardinalityKeyValue("status", LogStatus.SUCCESS.name())
                .highCardinalityKeyValue("file.size.bytes", String.valueOf(fileSizeBytes))
                .highCardinalityKeyValue("duration.ms", String.valueOf(durationMs))
                .start();
        try {
            transactionTemplate.executeWithoutResult(status -> repository.save(transferLog));
            log.info("[db] UPDATE transfer_log id={} status=SUCCESS durationMs={} fileSizeBytes={}", transferLog.getId(), durationMs, fileSizeBytes);
        } catch (DataAccessException | TransactionException e) {
            obs.lowCardinalityKeyValue(MariaDbObservationTag.errorType.name(), classifyError(e));
            obs.error(e);
            throw e;
        } finally {
            obs.stop();
        }
    }

    public void logFailed(TransferLog transferLog, String errorMessage) {
        transferLog.setStatus(LogStatus.FAILED);
        transferLog.setErrorMessage(errorMessage);

        Observation obs = Observation.createNotStarted("mariadb", observationRegistry)
                .lowCardinalityKeyValue("operation", transferLog.getOperation().name())
                .lowCardinalityKeyValue("action", "update_failed")
                .lowCardinalityKeyValue(MariaDbObservationTag.errorType.name(), "none")
                .highCardinalityKeyValue("channelId", transferLog.getChannelId())
                .highCardinalityKeyValue("filename", transferLog.getFilename())
                .highCardinalityKeyValue("db.record.id", String.valueOf(transferLog.getId()))
                .highCardinalityKeyValue("status", LogStatus.FAILED.name())
                .highCardinalityKeyValue("error.message", errorMessage != null ? errorMessage : "unknown")
                .start();
        try {
            transactionTemplate.executeWithoutResult(status -> repository.save(transferLog));
            log.info("[db] UPDATE transfer_log id={} status=FAILED error={}", transferLog.getId(), errorMessage);
        } catch (DataAccessException | TransactionException e) {
            obs.lowCardinalityKeyValue(MariaDbObservationTag.errorType.name(), classifyError(e));
            obs.error(e);
            throw e;
        } finally {
            obs.stop();
        }
    }

    public List<TransferLog> queryByStatus(LogStatus status) {
        Observation obs = Observation.createNotStarted("mariadb", observationRegistry)
                .lowCardinalityKeyValue("action", "query_by_status")
                .lowCardinalityKeyValue("status", status.name())
                .lowCardinalityKeyValue(MariaDbObservationTag.errorType.name(), "none")
                .start();
        try {
            List<TransferLog> result = readonlyTransactionTemplate.execute(s -> repository.findByStatus(status));
            obs.highCardinalityKeyValue("result.count", String.valueOf(result.size()));
            log.info("[db] QUERY transfer_log status={} count={}", status, result.size());
            return result;
        } catch (DataAccessException | TransactionException e) {
            obs.lowCardinalityKeyValue(MariaDbObservationTag.errorType.name(), classifyError(e));
            obs.error(e);
            throw e;
        } finally {
            obs.stop();
        }
    }

    public long countByChannelAndStatus(String channelId, LogStatus status) {
        Observation obs = Observation.createNotStarted("mariadb", observationRegistry)
                .lowCardinalityKeyValue("action", "count_by_channel_status")
                .lowCardinalityKeyValue("status", status.name())
                .lowCardinalityKeyValue(MariaDbObservationTag.errorType.name(), "none")
                .highCardinalityKeyValue("channelId", channelId)
                .start();
        try {
            long count = readonlyTransactionTemplate.execute(s -> repository.countByChannelIdAndStatus(channelId, status));
            obs.highCardinalityKeyValue("result.count", String.valueOf(count));
            log.info("[db] COUNT transfer_log channelId={} status={} count={}", channelId, status, count);
            return count;
        } catch (DataAccessException | TransactionException e) {
            obs.lowCardinalityKeyValue(MariaDbObservationTag.errorType.name(), classifyError(e));
            obs.error(e);
            throw e;
        } finally {
            obs.stop();
        }
    }

    public long sumFileSizeByChannel(String channelId) {
        Observation obs = Observation.createNotStarted("mariadb", observationRegistry)
                .lowCardinalityKeyValue("action", "sum_file_size")
                .lowCardinalityKeyValue(MariaDbObservationTag.errorType.name(), "none")
                .highCardinalityKeyValue("channelId", channelId)
                .start();
        try {
            long total = readonlyTransactionTemplate.execute(s -> repository.sumFileSizeByChannelId(channelId));
            obs.highCardinalityKeyValue("result.bytes", String.valueOf(total));
            log.info("[db] SUM file_size channelId={} totalBytes={}", channelId, total);
            return total;
        } catch (DataAccessException | TransactionException e) {
            obs.lowCardinalityKeyValue(MariaDbObservationTag.errorType.name(), classifyError(e));
            obs.error(e);
            throw e;
        } finally {
            obs.stop();
        }
    }

    private String classifyError(RuntimeException e) {
        if (e instanceof CannotGetJdbcConnectionException) return "connection_failure";
        if (e instanceof TransactionException) return "connection_failure";
        if (e instanceof CannotAcquireLockException) return "deadlock";
        if (e instanceof QueryTimeoutException) return "query_timeout";
        if (e instanceof DataIntegrityViolationException) return "constraint_violation";
        if (e instanceof TransientDataAccessException) return "transient_error";
        return "unknown";
    }

}