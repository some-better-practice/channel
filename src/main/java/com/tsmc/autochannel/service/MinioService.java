package com.tsmc.autochannel.service;

import io.minio.*;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Slf4j
@Service
@Profile("!mock")
@RequiredArgsConstructor
public class MinioService implements ObjectStorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    public void uploadFile(String objectName, String localPath) throws Exception {
        ensureBucketExists();
        minioClient.uploadObject(
                UploadObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .filename(localPath)
                        .build());
        log.info("[minio] Uploaded {} → {}/{}", localPath, bucket, objectName);
    }

    public void downloadFile(String objectName, String localPath) throws Exception {
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .build())) {
            Files.copy(stream, Path.of(localPath), StandardCopyOption.REPLACE_EXISTING);
        }
        log.info("[minio] Downloaded {}/{} → {}", bucket, objectName, localPath);
    }

    public boolean objectExists(String objectName) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean hasStdfFiles(String prefix) {
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix(prefix)
                            .build());
            for (Result<Item> result : results) {
                if (result.get().objectName().endsWith(".stdf")) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("[minio] hasStdfFiles error: {}", e.getMessage());
            return false;
        }
    }

    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("[minio] Created bucket: {}", bucket);
        }
    }
}
