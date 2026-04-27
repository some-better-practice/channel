package com.tsmc.autochannel.service;

import com.tsmc.autochannel.entity.LogStatus;
import com.tsmc.autochannel.entity.OperationType;
import com.tsmc.autochannel.entity.TransferLog;
import com.tsmc.autochannel.repository.TransferLogRepository;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferLogService {

    private final TransferLogRepository repository;
    private final ObservationRegistry observationRegistry;

    @Transactional
    public TransferLog logStart(String channelId, OperationType operation, String filename) {
        TransferLog entity = TransferLog.builder()
                .channelId(channelId)
                .operation(operation)
                .filename(filename)
                .status(LogStatus.IN_PROGRESS)
                .build();

        Observation obs = Observation.createNotStarted("db.transfer_log", observationRegistry)
                .lowCardinalityKeyValue("operation", operation.name())
                .lowCardinalityKeyValue("action", "insert")
                .highCardinalityKeyValue("channelId", channelId)
                .highCardinalityKeyValue("filename", filename)
                .highCardinalityKeyValue("status", LogStatus.IN_PROGRESS.name())
                .start();
        try {
            TransferLog saved = repository.save(entity);
            obs.highCardinalityKeyValue("db.record.id", String.valueOf(saved.getId()));
            log.info("[db] INSERT transfer_log id={} channelId={} operation={} filename={}", saved.getId(), channelId, operation, filename);
            return saved;
        } catch (Exception e) {
            obs.error(e);
            throw e;
        } finally {
            obs.stop();
        }
    }

    @Transactional
    public void logSuccess(TransferLog transferLog, long fileSizeBytes, long durationMs) {
        transferLog.setStatus(LogStatus.SUCCESS);
        transferLog.setFileSizeBytes(fileSizeBytes);
        transferLog.setDurationMs(durationMs);

        Observation obs = Observation.createNotStarted("db.transfer_log", observationRegistry)
                .lowCardinalityKeyValue("operation", transferLog.getOperation().name())
                .lowCardinalityKeyValue("action", "update_success")
                .highCardinalityKeyValue("channelId", transferLog.getChannelId())
                .highCardinalityKeyValue("filename", transferLog.getFilename())
                .highCardinalityKeyValue("db.record.id", String.valueOf(transferLog.getId()))
                .highCardinalityKeyValue("status", LogStatus.SUCCESS.name())
                .highCardinalityKeyValue("file.size.bytes", String.valueOf(fileSizeBytes))
                .highCardinalityKeyValue("duration.ms", String.valueOf(durationMs))
                .start();
        try {
            repository.save(transferLog);
            log.info("[db] UPDATE transfer_log id={} status=SUCCESS durationMs={} fileSizeBytes={}", transferLog.getId(), durationMs, fileSizeBytes);
        } catch (Exception e) {
            obs.error(e);
            throw e;
        } finally {
            obs.stop();
        }
    }

    @Transactional
    public void logFailed(TransferLog transferLog, String errorMessage) {
        transferLog.setStatus(LogStatus.FAILED);
        transferLog.setErrorMessage(errorMessage);

        Observation obs = Observation.createNotStarted("db.transfer_log", observationRegistry)
                .lowCardinalityKeyValue("operation", transferLog.getOperation().name())
                .lowCardinalityKeyValue("action", "update_failed")
                .highCardinalityKeyValue("channelId", transferLog.getChannelId())
                .highCardinalityKeyValue("filename", transferLog.getFilename())
                .highCardinalityKeyValue("db.record.id", String.valueOf(transferLog.getId()))
                .highCardinalityKeyValue("status", LogStatus.FAILED.name())
                .highCardinalityKeyValue("error.message", errorMessage != null ? errorMessage : "unknown")
                .start();
        try {
            repository.save(transferLog);
            log.info("[db] UPDATE transfer_log id={} status=FAILED error={}", transferLog.getId(), errorMessage);
        } catch (Exception e) {
            obs.error(e);
            throw e;
        } finally {
            obs.stop();
        }
    }
}
