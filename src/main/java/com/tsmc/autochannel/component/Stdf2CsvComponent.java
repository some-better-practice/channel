package com.tsmc.autochannel.component;

import com.tsmc.autochannel.entity.OperationType;
import com.tsmc.autochannel.entity.TransferLog;
import com.tsmc.autochannel.metrics.ProcessingMetrics;
import com.tsmc.autochannel.service.ObjectStorageService;
import com.tsmc.autochannel.service.TransferLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
@RequiredArgsConstructor
public class Stdf2CsvComponent {

    private final ObjectStorageService minioService;
    private final ProcessingMetrics metrics;
    private final TransferLogService transferLogService;

    public void execute(String channelId) {
        long start = System.currentTimeMillis();
        long ts = System.currentTimeMillis();
        String sourceKey = "raw/data_" + ts + ".stdf";
        String outputKey = "converted/data_" + ts + ".csv";
        Path stdfFile = null;
        Path csvFile = null;

        TransferLog transferLog = transferLogService.logStart(channelId, OperationType.STDF_CONVERT, sourceKey);

        try {
            stdfFile = Files.createTempFile("stdf_", ".stdf");
            csvFile = Files.createTempFile("csv_", ".csv");

            if (minioService.objectExists(sourceKey)) {
                log.info("[stdf2csv] Downloading from MinIO: {}", sourceKey);
                minioService.downloadFile(sourceKey, stdfFile.toString());
            } else {
                log.info("[stdf2csv] Source not found in MinIO, generating data for: {}", sourceKey);
                byte[] data = new byte[ThreadLocalRandom.current().nextInt(1024, 256 * 1024)];
                ThreadLocalRandom.current().nextBytes(data);
                Files.write(stdfFile, data);
            }

            Files.copy(stdfFile, csvFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            log.info("[stdf2csv] Converted {} → {}", sourceKey, outputKey);

            minioService.uploadFile(outputKey, csvFile.toString());

            long fileSize = ProcessingMetrics.randomFileSize();
            long duration = System.currentTimeMillis() - start;
            metrics.recordFileSize(channelId, "convert", fileSize);
            metrics.recordDuration(channelId, "convert", duration);

            transferLogService.logSuccess(transferLog, fileSize, duration);
            log.info("[stdf2csv] Done — uploaded to MinIO: {}", outputKey);

        } catch (Exception e) {
            transferLogService.logFailed(transferLog, e.getMessage());
            log.error("[stdf2csv] Failed: {}", e.getMessage(), e);
        } finally {
            deleteSilently(stdfFile);
            deleteSilently(csvFile);
        }
    }

    private void deleteSilently(Path path) {
        try {
            if (path != null) Files.deleteIfExists(path);
        } catch (Exception ignored) {}
    }
}
