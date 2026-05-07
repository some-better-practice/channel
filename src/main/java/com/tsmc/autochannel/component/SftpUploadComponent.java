package com.tsmc.autochannel.component;

import com.tsmc.autochannel.entity.OperationType;
import com.tsmc.autochannel.entity.TransferLog;
import com.tsmc.autochannel.metrics.ProcessingMetrics;
import com.tsmc.autochannel.service.FileTransferService;
import com.tsmc.autochannel.service.ObjectStorageService;
import com.tsmc.autochannel.service.TransferLogService;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
@RequiredArgsConstructor
public class SftpUploadComponent {

    private final FileTransferService sftpService;
    private final ObjectStorageService minioService;
    private final ProcessingMetrics metrics;
    private final ObservationRegistry observationRegistry;
    private final TransferLogService transferLogService;

    public void execute(String channelId) {
        long start = System.currentTimeMillis();
        long ts = System.currentTimeMillis();
        String minioKey = "parsed/data_" + ts + "_parsed.csv";
        String remoteFileName = "data_" + ts + "_parsed.csv";

        TransferLog transferLog = transferLogService.logStart(channelId, OperationType.SFTP_UPLOAD, remoteFileName);

        Observation obs = Observation.createNotStarted("sftp.upload", observationRegistry)
                .lowCardinalityKeyValue("channelId", channelId)
                .lowCardinalityKeyValue("operation", "upload")
                .highCardinalityKeyValue("filename", remoteFileName)
                .start();

        Path tempFile = null;
        try (Observation.Scope ignored = obs.openScope()) {
            tempFile = Files.createTempFile("sftp_upload_", ".csv");

            if (minioService.objectExists(minioKey)) {
                log.info("Downloading from MinIO: {}", minioKey);
                minioService.downloadFile(minioKey, tempFile.toString());
            } else {
                log.info("Source not found in MinIO, generating data for: {}", minioKey);
                byte[] data = new byte[ThreadLocalRandom.current().nextInt(1024, 256 * 1024)];
                ThreadLocalRandom.current().nextBytes(data);
                Files.write(tempFile, data);
            }

            log.info("Uploading to SFTP: {}", remoteFileName);
            sftpService.uploadFile(tempFile.toString(), remoteFileName);

            long fileSize = ProcessingMetrics.randomFileSize();
            long durationMs = System.currentTimeMillis() - start;
            metrics.recordFileSize(channelId, "upload", fileSize);
            obs.highCardinalityKeyValue("file.size.bytes", String.valueOf(fileSize));

            transferLogService.logSuccess(transferLog, fileSize, durationMs);
            log.info("Done — file: {}", remoteFileName);

        } catch (Exception e) {
            obs.error(e);
            transferLogService.logFailed(transferLog, e.getMessage());
            log.error("Failed: {}", e.getMessage(), e);
        } finally {
            deleteSilently(tempFile);
            obs.stop();
        }
    }

    private void deleteSilently(Path path) {
        try {
            if (path != null) Files.deleteIfExists(path);
        } catch (Exception ignored) {}
    }
}
