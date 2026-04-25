package com.tsmc.autochannel.component;

import com.tsmc.autochannel.metrics.ProcessingMetrics;
import com.tsmc.autochannel.service.FileTransferService;
import com.tsmc.autochannel.service.ObjectStorageService;
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

    public void execute(String channelId) {
        long start = System.currentTimeMillis();
        String filename = "data_" + System.currentTimeMillis() + ".stdf";
        Path tempFile = null;

        try {
            tempFile = Files.createTempFile("sftp_download_", ".stdf");

            try {
                log.info("[sftpDownload] Connecting to SFTP to download: {}", filename);
                sftpService.downloadFile(filename, tempFile.toString());
            } catch (Exception e) {
                log.warn("[sftpDownload] SFTP unavailable ({}), using generated data", e.getMessage());
                byte[] data = new byte[ThreadLocalRandom.current().nextInt(1024, 512 * 1024)];
                ThreadLocalRandom.current().nextBytes(data);
                Files.write(tempFile, data);
            }

            String minioKey = "raw/" + filename;
            minioService.uploadFile(minioKey, tempFile.toString());

            long fileSize = ProcessingMetrics.randomFileSize();
            long duration = System.currentTimeMillis() - start;
            metrics.recordFileSize(channelId, "download", fileSize);
            metrics.recordDuration(channelId, "download", duration);

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
