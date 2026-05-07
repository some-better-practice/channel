package com.tsmc.autochannel.service;

import io.minio.*;
import io.minio.errors.*;
import io.minio.messages.ErrorResponse;
import io.minio.messages.Item;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MinioServiceTest {

    @Mock private MinioClient minioClient;
    @TempDir Path tempDir;

    private TestObservationRegistry registry;
    private MinioService minioService;
    private String uploadLocalPath;

    @BeforeEach
    void setUp() throws IOException {
        registry = TestObservationRegistry.create();
        minioService = new MinioService(minioClient, registry);
        ReflectionTestUtils.setField(minioService, "bucket", "test-bucket");
        uploadLocalPath = Files.createFile(tempDir.resolve("obj.stdf")).toString();
    }

    // ── uploadFile ────────────────────────────────────────────────────────────

    @Test
    void uploadFile_bucketNotFound_errorTypeIsBucketNotFound() throws Exception {
        when(minioClient.bucketExists(any())).thenReturn(false);
        doThrow(makeErrorResponseException("NoSuchBucket")).when(minioClient).makeBucket(any());

        assertThrows(ErrorResponseException.class,
                () -> minioService.uploadFile("obj.stdf", uploadLocalPath));

        assertErrorType("upload", "bucket_not_found");
    }

    @Test
    void uploadFile_accessDenied_errorTypeIsAccessDenied() throws Exception {
        when(minioClient.bucketExists(any())).thenReturn(true);
        doThrow(makeErrorResponseException("AccessDenied")).when(minioClient).uploadObject(any());

        assertThrows(ErrorResponseException.class,
                () -> minioService.uploadFile("obj.stdf", uploadLocalPath));

        assertErrorType("upload", "access_denied");
    }

    @Test
    void uploadFile_invalidAccessKeyId_errorTypeIsAuthFailure() throws Exception {
        when(minioClient.bucketExists(any())).thenReturn(true);
        doThrow(makeErrorResponseException("InvalidAccessKeyId")).when(minioClient).uploadObject(any());

        assertThrows(ErrorResponseException.class,
                () -> minioService.uploadFile("obj.stdf", uploadLocalPath));

        assertErrorType("upload", "auth_failure");
    }

    @Test
    void uploadFile_signatureDoesNotMatch_errorTypeIsAuthFailure() throws Exception {
        when(minioClient.bucketExists(any())).thenReturn(true);
        doThrow(makeErrorResponseException("SignatureDoesNotMatch")).when(minioClient).uploadObject(any());

        assertThrows(ErrorResponseException.class,
                () -> minioService.uploadFile("obj.stdf", uploadLocalPath));

        assertErrorType("upload", "auth_failure");
    }

    @Test
    void uploadFile_entityTooLarge_errorTypeIsEntityTooLarge() throws Exception {
        when(minioClient.bucketExists(any())).thenReturn(true);
        doThrow(makeErrorResponseException("EntityTooLarge")).when(minioClient).uploadObject(any());

        assertThrows(ErrorResponseException.class,
                () -> minioService.uploadFile("obj.stdf", uploadLocalPath));

        assertErrorType("upload", "entity_too_large");
    }

    @Test
    void uploadFile_serverException_errorTypeIsServerError() throws Exception {
        when(minioClient.bucketExists(any())).thenReturn(true);
        doThrow(new ServerException("Internal Server Error", 500, ""))
                .when(minioClient).uploadObject(any());

        assertThrows(ServerException.class,
                () -> minioService.uploadFile("obj.stdf", uploadLocalPath));

        assertErrorType("upload", "server_error");
    }

    @Test
    void uploadFile_insufficientData_errorTypeIsInsufficientData() throws Exception {
        when(minioClient.bucketExists(any())).thenReturn(true);
        doThrow(new InsufficientDataException("Insufficient data"))
                .when(minioClient).uploadObject(any());

        assertThrows(InsufficientDataException.class,
                () -> minioService.uploadFile("obj.stdf", uploadLocalPath));

        assertErrorType("upload", "insufficient_data");
    }

    @Test
    void uploadFile_connectionTimeout_errorTypeIsConnectionTimeout() throws Exception {
        when(minioClient.bucketExists(any())).thenReturn(true);
        doThrow(new IOException("Connection timed out")).when(minioClient).uploadObject(any());

        assertThrows(IOException.class,
                () -> minioService.uploadFile("obj.stdf", uploadLocalPath));

        assertErrorType("upload", "connection_timeout");
    }

    @Test
    void uploadFile_connectionRefused_errorTypeIsConnectionRefused() throws Exception {
        when(minioClient.bucketExists(any())).thenReturn(true);
        doThrow(new IOException("Connection refused")).when(minioClient).uploadObject(any());

        assertThrows(IOException.class,
                () -> minioService.uploadFile("obj.stdf", uploadLocalPath));

        assertErrorType("upload", "connection_refused");
    }

    // ── downloadFile ──────────────────────────────────────────────────────────

    @Test
    void downloadFile_objectNotFound_errorTypeIsObjectNotFound() throws Exception {
        doThrow(makeErrorResponseException("NoSuchKey")).when(minioClient).getObject(any());

        assertThrows(ErrorResponseException.class,
                () -> minioService.downloadFile("missing.stdf", "/tmp/missing.stdf"));

        assertErrorType("download", "object_not_found");
    }

    @Test
    void downloadFile_bucketNotFound_errorTypeIsBucketNotFound() throws Exception {
        doThrow(makeErrorResponseException("NoSuchBucket")).when(minioClient).getObject(any());

        assertThrows(ErrorResponseException.class,
                () -> minioService.downloadFile("obj.stdf", "/tmp/obj.stdf"));

        assertErrorType("download", "bucket_not_found");
    }

    @Test
    void downloadFile_accessDenied_errorTypeIsAccessDenied() throws Exception {
        doThrow(makeErrorResponseException("AccessDenied")).when(minioClient).getObject(any());

        assertThrows(ErrorResponseException.class,
                () -> minioService.downloadFile("obj.stdf", "/tmp/obj.stdf"));

        assertErrorType("download", "access_denied");
    }

    @Test
    void downloadFile_serverException_errorTypeIsServerError() throws Exception {
        doThrow(new ServerException("gateway timeout", 504, ""))
                .when(minioClient).getObject(any());

        assertThrows(ServerException.class,
                () -> minioService.downloadFile("obj.stdf", "/tmp/obj.stdf"));

        assertErrorType("download", "server_error");
    }

    @Test
    void downloadFile_connectionTimeout_errorTypeIsConnectionTimeout() throws Exception {
        doThrow(new IOException("Read timed out")).when(minioClient).getObject(any());

        assertThrows(IOException.class,
                () -> minioService.downloadFile("obj.stdf", "/tmp/obj.stdf"));

        assertErrorType("download", "connection_timeout");
    }

    @Test
    void downloadFile_ioError_errorTypeIsIoError() throws Exception {
        doThrow(new IOException("Unexpected end of stream")).when(minioClient).getObject(any());

        assertThrows(IOException.class,
                () -> minioService.downloadFile("obj.stdf", "/tmp/obj.stdf"));

        assertErrorType("download", "io_error");
    }

    // ── objectExists ──────────────────────────────────────────────────────────

    @Test
    void objectExists_exists_returnsTrueAndErrorTypeIsNone() throws Exception {
        when(minioClient.statObject(any())).thenReturn(mock(StatObjectResponse.class));

        assertTrue(minioService.objectExists("existing.stdf"));

        assertErrorType("stat", "none");
    }

    @Test
    void objectExists_noSuchKey_returnsFalseAndNoErrorTag() throws Exception {
        doThrow(makeErrorResponseException("NoSuchKey")).when(minioClient).statObject(any());

        assertFalse(minioService.objectExists("missing.stdf"));

        assertErrorType("stat", "none");
    }

    @Test
    void objectExists_noSuchBucket_returnsFalseAndNoErrorTag() throws Exception {
        doThrow(makeErrorResponseException("NoSuchBucket")).when(minioClient).statObject(any());

        assertFalse(minioService.objectExists("obj.stdf"));

        assertErrorType("stat", "none");
    }

    @Test
    void objectExists_accessDenied_returnsFalseAndErrorTypeIsAccessDenied() throws Exception {
        doThrow(makeErrorResponseException("AccessDenied")).when(minioClient).statObject(any());

        assertFalse(minioService.objectExists("protected.stdf"));

        assertErrorType("stat", "access_denied");
    }

    @Test
    void objectExists_serverException_returnsFalseAndErrorTypeIsServerError() throws Exception {
        doThrow(new ServerException("server error", 500, "")).when(minioClient).statObject(any());

        assertFalse(minioService.objectExists("obj.stdf"));

        assertErrorType("stat", "server_error");
    }

    @Test
    void objectExists_connectionRefused_returnsFalseAndErrorTypeIsConnectionRefused() throws Exception {
        doThrow(new IOException("Connection refused")).when(minioClient).statObject(any());

        assertFalse(minioService.objectExists("obj.stdf"));

        assertErrorType("stat", "connection_refused");
    }

    // ── hasStdfFiles ──────────────────────────────────────────────────────────

    @Test
    void hasStdfFiles_stdfPresent_returnsTrue() throws Exception {
        Item item = mock(Item.class);
        when(item.objectName()).thenReturn("prefix/sample.stdf");
        Result<Item> result = new Result<>(item);
        when(minioClient.listObjects(any())).thenReturn(List.of(result));

        assertTrue(minioService.hasStdfFiles("prefix/"));

        assertErrorType("list", "none");
    }

    @Test
    void hasStdfFiles_noStdfPresent_returnsFalse() throws Exception {
        Item item = mock(Item.class);
        when(item.objectName()).thenReturn("prefix/sample.csv");
        Result<Item> result = new Result<>(item);
        when(minioClient.listObjects(any())).thenReturn(List.of(result));

        assertFalse(minioService.hasStdfFiles("prefix/"));

        assertErrorType("list", "none");
    }

    @Test
    void hasStdfFiles_resultGetThrowsIoException_returnsFalseAndErrorTypeIsIoError() throws Exception {
        Result<Item> result = new Result<>(new IOException("Unexpected end of stream"));
        when(minioClient.listObjects(any())).thenReturn(List.of(result));

        assertFalse(minioService.hasStdfFiles("prefix/"));

        assertErrorType("list", "io_error");
    }

    @Test
    void hasStdfFiles_resultGetThrowsServerException_returnsFalseAndErrorTypeIsServerError() throws Exception {
        Result<Item> result = new Result<>(new ServerException("server error", 500, ""));
        when(minioClient.listObjects(any())).thenReturn(List.of(result));

        assertFalse(minioService.hasStdfFiles("prefix/"));

        assertErrorType("list", "server_error");
    }

    @Test
    void hasStdfFiles_resultGetThrowsInsufficientData_returnsFalseAndErrorTypeIsInsufficientData() throws Exception {
        Result<Item> result = new Result<>(new InsufficientDataException("Insufficient data"));
        when(minioClient.listObjects(any())).thenReturn(List.of(result));

        assertFalse(minioService.hasStdfFiles("prefix/"));

        assertErrorType("list", "insufficient_data");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ErrorResponseException makeErrorResponseException(String code) {
        ErrorResponseException ex = mock(ErrorResponseException.class);
        ErrorResponse errResp = new ErrorResponse(code, "Test error", null, null, null, null, null);
        when(ex.errorResponse()).thenReturn(errResp);
        return ex;
    }

    private void assertErrorType(String operation, String expectedErrorType) {
        TestObservationRegistryAssert.assertThat(registry)
                .hasObservationWithNameEqualTo("minio")
                .that()
                .hasLowCardinalityKeyValue("operation", operation)
                .hasLowCardinalityKeyValue("errorType", expectedErrorType);
    }
}
