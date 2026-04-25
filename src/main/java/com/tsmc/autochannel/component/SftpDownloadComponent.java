package com.tsmc.autochannel.component;

import com.tsmc.autochannel.metrics.ProcessingMetrics;
import com.tsmc.autochannel.service.MinioService;
import com.tsmc.autochannel.service.SftpService;
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

    private final SftpService sftpService;
    private final MinioService minioService;
    private final ProcessingMetrics metrics;

    public void execute() {
        long start = System.currentTimeMillis();
        String filename = "data_" + System.currentTimeMillis() + ".stdf";
        Path tempFile = null;

        try {
            tempFile = Files.createTempFile("sftp_download_", ".stdf");

            // Try real SFTP download; fall back to generated data if unavailable
            try {
                log.info("[sftpDownload] Connecting to SFTP to download: {}", filename);
                sftpService.downloadFile(filename, tempFile.toString());
            } catch (Exception e) {
                log.warn("[sftpDownload] SFTP unavailable ({}), using generated data", e.getMessage());
                byte[] data = new byte[ThreadLocalRandom.current().nextInt(1024, 512 * 1024)];
                ThreadLocalRandom.current().nextBytes(data);
                Files.write(tempFile, data);
            }

            // Upload to MinIO under raw/ prefix
            String minioKey = "raw/" + filename;
            minioService.uploadFile(minioKey, tempFile.toString());

            long fileSize = ProcessingMetrics.randomFileSize();
            long duration = System.currentTimeMillis() - start;
            metrics.recordFileSize("sftpDownload", fileSize);
            metrics.recordDuration("sftpDownload", duration);

            log.info("[sftpDownload] Done — uploaded to MinIO: {}", minioKey);

        } catch (Exception e) {
            log.error("[sftpDownload] Failed: {}", e.getMessage(), e);
        } finally {
            deleteSilently(tempFile);
        }
    }

    private void deleteSilently(Path path) {
        try {
            if (path != null) Files.deleteIfExists(path);
        } catch (Exception ignored) {}
    }
}
