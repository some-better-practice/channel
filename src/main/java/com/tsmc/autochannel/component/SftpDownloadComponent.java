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
public class SftpDownloadComponent {

    private final FileTransferService sftpService;
    private final ObjectStorageService minioService;
    private final ProcessingMetrics metrics;
    private final ObservationRegistry observationRegistry;
    private final TransferLogService transferLogService;

    public void execute(String channelId) {
        long start = System.currentTimeMillis();
        String filename = "data_" + System.currentTimeMillis() + ".stdf";
        String minioKey = "raw/" + filename;

        TransferLog transferLog = transferLogService.logStart(channelId, OperationType.SFTP_DOWNLOAD, filename);

        Observation obs = Observation.createNotStarted("sftp.download", observationRegistry)
                .lowCardinalityKeyValue("channelId", channelId)
                .lowCardinalityKeyValue("operation", "download")
                .highCardinalityKeyValue("filename", filename)
                .start();

        Path tempFile = null;
        try (Observation.Scope ignored = obs.openScope()) {
            tempFile = Files.createTempFile("sftp_download_", ".stdf");

            try {
                log.info("Connecting to SFTP to download: {}", filename);
                sftpService.downloadFile(filename, tempFile.toString());
            } catch (Exception e) {
                log.warn("SFTP unavailable ({}), using generated data", e.getMessage());
                byte[] data = new byte[ThreadLocalRandom.current().nextInt(1024, 512 * 1024)];
                ThreadLocalRandom.current().nextBytes(data);
                Files.write(tempFile, data);
            }

            minioService.uploadFile(minioKey, tempFile.toString());

            long fileSize = ProcessingMetrics.randomFileSize();
            long durationMs = System.currentTimeMillis() - start;
            metrics.recordFileSize(channelId, "download", fileSize);
            obs.highCardinalityKeyValue("file.size.bytes", String.valueOf(fileSize));

            transferLogService.logSuccess(transferLog, fileSize, durationMs);
            log.info("Uploaded to MinIO: {}", minioKey);

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
