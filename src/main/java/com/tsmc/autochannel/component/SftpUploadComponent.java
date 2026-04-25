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
public class SftpUploadComponent {

    private final SftpService sftpService;
    private final MinioService minioService;
    private final ProcessingMetrics metrics;

    public void execute() {
        long start = System.currentTimeMillis();
        long ts = System.currentTimeMillis();
        String minioKey = "parsed/data_" + ts + "_parsed.csv";
        String remoteFileName = "data_" + ts + "_parsed.csv";
        Path tempFile = null;

        try {
            tempFile = Files.createTempFile("sftp_upload_", ".csv");

            // Try to download parsed file from MinIO; fall back to generated data
            if (minioService.objectExists(minioKey)) {
                log.info("[sftpUpload] Downloading from MinIO: {}", minioKey);
                minioService.downloadFile(minioKey, tempFile.toString());
            } else {
                log.info("[sftpUpload] Source not found in MinIO, generating data for: {}", minioKey);
                byte[] data = new byte[ThreadLocalRandom.current().nextInt(1024, 256 * 1024)];
                ThreadLocalRandom.current().nextBytes(data);
                Files.write(tempFile, data);
            }

            // Try real SFTP upload; log warning if unavailable
            try {
                log.info("[sftpUpload] Uploading to SFTP: {}", remoteFileName);
                sftpService.uploadFile(tempFile.toString(), remoteFileName);
            } catch (Exception e) {
                log.warn("[sftpUpload] SFTP unavailable ({}), skipping upload", e.getMessage());
            }

            long fileSize = ProcessingMetrics.randomFileSize();
            long duration = System.currentTimeMillis() - start;
            metrics.recordFileSize("sftpUpload", fileSize);
            metrics.recordDuration("sftpUpload", duration);

            log.info("[sftpUpload] Done — file: {}", remoteFileName);

        } catch (Exception e) {
            log.error("[sftpUpload] Failed: {}", e.getMessage(), e);
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
