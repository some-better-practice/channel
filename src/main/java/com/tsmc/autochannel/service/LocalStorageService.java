package com.tsmc.autochannel.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Slf4j
@Service
@Profile("mock")
public class LocalStorageService implements ObjectStorageService {

    @Value("${minio.bucket}")
    private String bucket;

    private Path resolve(String objectName) {
        return Path.of("mock_nas/minio", bucket, objectName);
    }

    @Override
    public void uploadFile(String objectName, String localPath) throws Exception {
        Path dest = resolve(objectName);
        Files.createDirectories(dest.getParent());
        Files.copy(Path.of(localPath), dest, StandardCopyOption.REPLACE_EXISTING);
        log.info("[mock-minio] upload {} → {}", localPath, dest);
    }

    @Override
    public void downloadFile(String objectName, String localPath) throws Exception {
        Path src = resolve(objectName);
        if (!Files.exists(src)) {
            throw new IOException("Object not found: " + objectName);
        }
        Files.copy(src, Path.of(localPath), StandardCopyOption.REPLACE_EXISTING);
        log.info("[mock-minio] download {} → {}", src, localPath);
    }

    @Override
    public boolean objectExists(String objectName) {
        return Files.exists(resolve(objectName));
    }

    @Override
    public boolean hasStdfFiles(String prefix) {
        try {
            Path dir = Path.of("mock_nas/minio", bucket, prefix.replaceAll("/$", ""));
            if (!Files.exists(dir)) return false;
            try (var stream = Files.list(dir)) {
                return stream.anyMatch(p -> p.toString().endsWith(".stdf"));
            }
        } catch (Exception e) {
            log.warn("[mock-minio] hasStdfFiles error: {}", e.getMessage());
            return false;
        }
    }
}
