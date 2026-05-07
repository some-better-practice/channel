package com.tsmc.autochannel.service;

import com.jcraft.jsch.*;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SftpServiceTest {

    @Mock private JSch jsch;
    @Mock private Session session;
    @Mock private ChannelSftp channelSftp;

    private TestObservationRegistry registry;
    private SftpService sftpService;

    @BeforeEach
    void setUp() {
        registry = TestObservationRegistry.create();
        sftpService = new SftpService(() -> jsch, registry);
        ReflectionTestUtils.setField(sftpService, "host", "localhost");
        ReflectionTestUtils.setField(sftpService, "port", 2222);
        ReflectionTestUtils.setField(sftpService, "username", "autochannel");
        ReflectionTestUtils.setField(sftpService, "password", "password");
        ReflectionTestUtils.setField(sftpService, "remoteDir", "upload");
    }

    // ── JSchException 分類 ────────────────────────────────────────────────────

    @Test
    void downloadFile_connectionTimeout_errorTypeIsConnectionTimeout() throws Exception {
        when(jsch.getSession(any(), any(), anyInt())).thenReturn(session);
        doThrow(new JSchException("timeout waiting for connection")).when(session).connect(anyInt());

        assertThrows(JSchException.class,
                () -> sftpService.downloadFile("test.stdf", "/tmp/test.stdf"));

        assertErrorType("download", "connection_timeout");
    }

    @Test
    void downloadFile_authFailure_errorTypeIsAuthFailure() throws Exception {
        when(jsch.getSession(any(), any(), anyInt())).thenReturn(session);
        doThrow(new JSchException("Auth fail")).when(session).connect(anyInt());

        assertThrows(JSchException.class,
                () -> sftpService.downloadFile("test.stdf", "/tmp/test.stdf"));

        assertErrorType("download", "auth_failure");
    }

    @Test
    void downloadFile_channelOpenFails_errorTypeIsChannelFailure() throws Exception {
        when(jsch.getSession(any(), any(), anyInt())).thenReturn(session);
        doNothing().when(session).connect(anyInt());
        doThrow(new JSchException("channel is not opened")).when(session).openChannel("sftp");

        assertThrows(JSchException.class,
                () -> sftpService.downloadFile("test.stdf", "/tmp/test.stdf"));

        assertErrorType("download", "channel_failure");
    }

    @Test
    void uploadFile_connectionRefused_errorTypeIsConnectionFailure() throws Exception {
        when(jsch.getSession(any(), any(), anyInt())).thenReturn(session);
        doThrow(new JSchException("Connection refused")).when(session).connect(anyInt());

        assertThrows(JSchException.class,
                () -> sftpService.uploadFile("/tmp/test.csv", "test.csv"));

        assertErrorType("upload", "connection_failure");
    }

    @Test
    void listFiles_connectionRefused_errorTypeIsConnectionFailure() throws Exception {
        when(jsch.getSession(any(), any(), anyInt())).thenReturn(session);
        doThrow(new JSchException("Connection refused")).when(session).connect(anyInt());

        assertThrows(JSchException.class, () -> sftpService.listFiles());

        assertErrorType("list", "connection_failure");
    }

    // ── SftpException 分類 ────────────────────────────────────────────────────

    @Test
    void downloadFile_fileNotFound_errorTypeIsFileNotFound() throws Exception {
        givenChannelReady();
        doThrow(new SftpException(ChannelSftp.SSH_FX_NO_SUCH_FILE, "No such file"))
                .when(channelSftp).get(any(), any(String.class));

        assertThrows(SftpException.class,
                () -> sftpService.downloadFile("missing.stdf", "/tmp/missing.stdf"));

        assertErrorType("download", "file_not_found");
    }

    @Test
    void uploadFile_permissionDenied_errorTypeIsPermissionDenied() throws Exception {
        givenChannelReady();
        doThrow(new SftpException(ChannelSftp.SSH_FX_PERMISSION_DENIED, "Permission denied"))
                .when(channelSftp).put(any(String.class), any(String.class));

        assertThrows(SftpException.class,
                () -> sftpService.uploadFile("/tmp/test.csv", "test.csv"));

        assertErrorType("upload", "permission_denied");
    }

    @Test
    void listFiles_connectionLost_errorTypeIsConnectionLost() throws Exception {
        givenChannelReady();
        doThrow(new SftpException(ChannelSftp.SSH_FX_CONNECTION_LOST, "Connection lost"))
                .when(channelSftp).ls(any());

        assertThrows(SftpException.class, () -> sftpService.listFiles());

        assertErrorType("list", "connection_lost");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void givenChannelReady() throws JSchException {
        when(jsch.getSession(any(), any(), anyInt())).thenReturn(session);
        doNothing().when(session).connect(anyInt());
        when(session.openChannel("sftp")).thenReturn(channelSftp);
        doNothing().when(channelSftp).connect();
    }

    private void assertErrorType(String operation, String expectedErrorType) {
        TestObservationRegistryAssert.assertThat(registry)
                .hasObservationWithNameEqualTo("sftp.channel")
                .that()
                .hasLowCardinalityKeyValue("operation", operation)
                .hasLowCardinalityKeyValue("errorType", expectedErrorType);
    }
}
