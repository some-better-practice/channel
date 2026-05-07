package com.tsmc.autochannel.service;

import com.tsmc.autochannel.metrics.MinioObservationTag;
import io.minio.*;
import io.minio.errors.*;
import io.minio.messages.Item;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
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
    private final ObservationRegistry observationRegistry;

    @Value("${minio.bucket}")
    private String bucket;

    public void uploadFile(String objectName, String localPath) throws Exception {
        Observation obs = startObservation("upload");
        try (Observation.Scope ignored = obs.openScope()) {
            ensureBucketExists();
            minioClient.uploadObject(
                    UploadObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .filename(localPath)
                            .build());
            log.info("[minio] Uploaded {} → {}/{}", localPath, bucket, objectName);
        } catch (ErrorResponseException e) {
            tagError(obs, classifyErrorResponse(e));
            obs.error(e);
            throw e;
        } catch (MinioException | IOException e) {
            tagError(obs, classifyMinioException(e));
            obs.error(e);
            throw e;
        } catch (Exception e) {
            tagError(obs, "unknown");
            obs.error(e);
            throw e;
        } finally {
            obs.stop();
        }
    }

    public void downloadFile(String objectName, String localPath) throws Exception {
        Observation obs = startObservation("download");
        try (Observation.Scope ignored = obs.openScope()) {
            try (InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build())) {
                Files.copy(stream, Path.of(localPath), StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("[minio] Downloaded {}/{} → {}", bucket, objectName, localPath);
        } catch (ErrorResponseException e) {
            tagError(obs, classifyErrorResponse(e));
            obs.error(e);
            throw e;
        } catch (MinioException | IOException e) {
            tagError(obs, classifyMinioException(e));
            obs.error(e);
            throw e;
        } catch (Exception e) {
            tagError(obs, "unknown");
            obs.error(e);
            throw e;
        } finally {
            obs.stop();
        }
    }

    public boolean objectExists(String objectName) {
        Observation obs = startObservation("stat");
        try (Observation.Scope ignored = obs.openScope()) {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build());
            return true;
        } catch (ErrorResponseException e) {
            String code = e.errorResponse().code();
            if ("NoSuchKey".equals(code) || "NoSuchBucket".equals(code)) {
                return false;
            }
            tagError(obs, classifyErrorResponse(e));
            obs.error(e);
            return false;
        } catch (Exception e) {
            tagError(obs, classifyMinioException(e));
            obs.error(e);
            return false;
        } finally {
            obs.stop();
        }
    }

    public boolean hasStdfFiles(String prefix) {
        Observation obs = startObservation("list");
        try (Observation.Scope ignored = obs.openScope()) {
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
            tagError(obs, classifyMinioException(e));
            obs.error(e);
            return false;
        } finally {
            obs.stop();
        }
    }

    private Observation startObservation(String operation) {
        return Observation.createNotStarted("minio", observationRegistry)
                .lowCardinalityKeyValue("operation", operation)
                .lowCardinalityKeyValue(MinioObservationTag.errorType.name(), "none")
                .start();
    }

    private void tagError(Observation obs, String errorType) {
        obs.lowCardinalityKeyValue(MinioObservationTag.errorType.name(), errorType);
    }

    private String classifyErrorResponse(ErrorResponseException e) {
        return switch (e.errorResponse().code()) {
            case "NoSuchBucket"          -> "bucket_not_found";
            case "NoSuchKey"             -> "object_not_found";
            case "AccessDenied"          -> "access_denied";
            case "EntityTooLarge"        -> "entity_too_large";
            case "InvalidBucketName"     -> "invalid_bucket_name";
            case "BucketNotEmpty"        -> "bucket_not_empty";
            case "InvalidAccessKeyId",
                 "SignatureDoesNotMatch" -> "auth_failure";
            default                      -> "server_error";
        };
    }

    private String classifyMinioException(Exception e) {
        if (e instanceof InsufficientDataException) return "insufficient_data";
        if (e instanceof ServerException)           return "server_error";
        if (e instanceof InvalidResponseException)  return "invalid_response";
        if (e instanceof XmlParserException)        return "xml_parse_error";
        if (e instanceof IOException ioe)           return classifyIoException(ioe);
        return "unknown";
    }

    private String classifyIoException(IOException e) {
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        if (msg.contains("timeout") || msg.contains("timed out")) return "connection_timeout";
        if (msg.contains("connection refused"))                    return "connection_refused";
        return "io_error";
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
