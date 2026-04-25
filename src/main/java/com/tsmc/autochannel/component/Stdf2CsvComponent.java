package com.tsmc.autochannel.component;

import com.tsmc.autochannel.metrics.ProcessingMetrics;
import com.tsmc.autochannel.service.MinioService;
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

    private final MinioService minioService;
    private final ProcessingMetrics metrics;

    public void execute() {
        long start = System.currentTimeMillis();
        long ts = System.currentTimeMillis();
        String sourceKey = "raw/data_" + ts + ".stdf";
        String outputKey = "converted/data_" + ts + ".csv";
        Path stdfFile = null;
        Path csvFile = null;

        try {
            stdfFile = Files.createTempFile("stdf_", ".stdf");
            csvFile = Files.createTempFile("csv_", ".csv");

            // Try to download .stdf from MinIO; fall back to generated data
            if (minioService.objectExists(sourceKey)) {
                log.info("[stdf2csv] Downloading from MinIO: {}", sourceKey);
                minioService.downloadFile(sourceKey, stdfFile.toString());
            } else {
                log.info("[stdf2csv] Source not found in MinIO, generating data for: {}", sourceKey);
                byte[] data = new byte[ThreadLocalRandom.current().nextInt(1024, 256 * 1024)];
                ThreadLocalRandom.current().nextBytes(data);
                Files.write(stdfFile, data);
            }

            // "Convert": copy content with new name (simulated STDF→CSV conversion)
            Files.copy(stdfFile, csvFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            log.info("[stdf2csv] Converted {} → {}", sourceKey, outputKey);

            // Upload converted file back to MinIO
            minioService.uploadFile(outputKey, csvFile.toString());

            long fileSize = ProcessingMetrics.randomFileSize();
            long duration = System.currentTimeMillis() - start;
            metrics.recordFileSize("stdf2csv", fileSize);
            metrics.recordDuration("stdf2csv", duration);

            log.info("[stdf2csv] Done — uploaded to MinIO: {}", outputKey);

        } catch (Exception e) {
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
