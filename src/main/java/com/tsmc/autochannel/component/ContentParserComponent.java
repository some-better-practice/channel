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
public class ContentParserComponent {

    private final ObjectStorageService minioService;
    private final ProcessingMetrics metrics;
    private final TransferLogService transferLogService;

    public void execute(String channelId) {
        long start = System.currentTimeMillis();
        long ts = System.currentTimeMillis();
        String sourceKey = "converted/data_" + ts + ".csv";
        String outputKey = "parsed/data_" + ts + "_parsed.csv";
        Path inputFile = null;
        Path parsedFile = null;

        TransferLog transferLog = transferLogService.logStart(channelId, OperationType.CONTENT_PARSE, sourceKey);

        try {
            inputFile = Files.createTempFile("content_input_", ".csv");
            parsedFile = Files.createTempFile("content_parsed_", ".csv");

            if (minioService.objectExists(sourceKey)) {
                log.info("[contentParser] Downloading from MinIO: {}", sourceKey);
                minioService.downloadFile(sourceKey, inputFile.toString());
            } else {
                log.info("[contentParser] Source not found in MinIO, generating data for: {}", sourceKey);
                byte[] data = new byte[ThreadLocalRandom.current().nextInt(1024, 256 * 1024)];
                ThreadLocalRandom.current().nextBytes(data);
                Files.write(inputFile, data);
            }

            Files.copy(inputFile, parsedFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            log.info("[contentParser] Parsed {} → {}", sourceKey, outputKey);

            minioService.uploadFile(outputKey, parsedFile.toString());

            long fileSize = ProcessingMetrics.randomFileSize();
            long duration = System.currentTimeMillis() - start;
            metrics.recordFileSize(channelId, "parse", fileSize);
            metrics.recordDuration(channelId, "parse", duration);

            transferLogService.logSuccess(transferLog, fileSize, duration);
            log.info("[contentParser] Done — uploaded to MinIO: {}", outputKey);

        } catch (Exception e) {
            transferLogService.logFailed(transferLog, e.getMessage());
            log.error("[contentParser] Failed: {}", e.getMessage(), e);
        } finally {
            deleteSilently(inputFile);
            deleteSilently(parsedFile);
        }
    }

    private void deleteSilently(Path path) {
        try {
            if (path != null) Files.deleteIfExists(path);
        } catch (Exception ignored) {}
    }
}
