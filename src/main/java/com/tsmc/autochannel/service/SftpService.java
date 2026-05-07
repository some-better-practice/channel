package com.tsmc.autochannel.service;

import com.jcraft.jsch.*;
import com.tsmc.autochannel.metrics.SftpObservationTag;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

@Slf4j
@Service
@Profile("!mock")
@RequiredArgsConstructor
public class SftpService implements FileTransferService {

    @Value("${sftp.host}")
    private String host;

    @Value("${sftp.port}")
    private int port;

    @Value("${sftp.username}")
    private String username;

    @Value("${sftp.password}")
    private String password;

    @Value("${sftp.remote-dir}")
    private String remoteDir;

    private final JSchFactory jschFactory;
    private final ObservationRegistry observationRegistry;

    @Override
    public void downloadFile(String remoteFileName, String localPath) throws Exception {
        Observation obs = startObservation("download");
        try (Observation.Scope ignored = obs.openScope()) {
            Session session = createSession();
            ChannelSftp channel = openSftpChannel(session);
            try {
                channel.get(remoteDir + "/" + remoteFileName, localPath);
                log.info("[sftp] Downloaded {}/{} → {}", remoteDir, remoteFileName, localPath);
            } finally {
                channel.disconnect();
                session.disconnect();
            }
        } catch (JSchException e) {
            tagError(obs, classifyJSchException(e));
            obs.error(e);
            throw e;
        } catch (SftpException e) {
            tagError(obs, classifySftpException(e));
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

    @Override
    public void uploadFile(String localPath, String remoteFileName) throws Exception {
        Observation obs = startObservation("upload");
        try (Observation.Scope ignored = obs.openScope()) {
            Session session = createSession();
            ChannelSftp channel = openSftpChannel(session);
            try {
                channel.put(localPath, remoteDir + "/" + remoteFileName);
                log.info("[sftp] Uploaded {} → {}/{}", localPath, remoteDir, remoteFileName);
            } finally {
                channel.disconnect();
                session.disconnect();
            }
        } catch (JSchException e) {
            tagError(obs, classifyJSchException(e));
            obs.error(e);
            throw e;
        } catch (SftpException e) {
            tagError(obs, classifySftpException(e));
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

    @Override
    public List<String> listFiles() throws Exception {
        Observation obs = startObservation("list");
        try (Observation.Scope ignored = obs.openScope()) {
            Session session = createSession();
            ChannelSftp channel = openSftpChannel(session);
            try {
                Vector<ChannelSftp.LsEntry> entries = channel.ls(remoteDir);
                List<String> files = new ArrayList<>();
                for (ChannelSftp.LsEntry entry : entries) {
                    if (!entry.getAttrs().isDir()) {
                        files.add(entry.getFilename());
                    }
                }
                return files;
            } finally {
                channel.disconnect();
                session.disconnect();
            }
        } catch (JSchException e) {
            tagError(obs, classifyJSchException(e));
            obs.error(e);
            throw e;
        } catch (SftpException e) {
            tagError(obs, classifySftpException(e));
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

    private Observation startObservation(String operation) {
        return Observation.createNotStarted("sftp.channel", observationRegistry)
                .lowCardinalityKeyValue("operation", operation)
                .lowCardinalityKeyValue(SftpObservationTag.errorType.name(), "none")
                .start();
    }

    private void tagError(Observation obs, String errorType) {
        obs.lowCardinalityKeyValue(SftpObservationTag.errorType.name(), errorType);
    }

    private String classifyJSchException(JSchException e) {
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        if (msg.contains("timeout") || msg.contains("timed out")) return "connection_timeout";
        if (msg.contains("auth") || msg.contains("userauth"))     return "auth_failure";
        if (msg.contains("channel"))                               return "channel_failure";
        return "connection_failure";
    }

    private String classifySftpException(SftpException e) {
        return switch (e.id) {
            case ChannelSftp.SSH_FX_NO_SUCH_FILE      -> "file_not_found";
            case ChannelSftp.SSH_FX_PERMISSION_DENIED -> "permission_denied";
            case ChannelSftp.SSH_FX_NO_CONNECTION,
                 ChannelSftp.SSH_FX_CONNECTION_LOST   -> "connection_lost";
            case ChannelSftp.SSH_FX_FAILURE            -> "sftp_failure";
            case ChannelSftp.SSH_FX_OP_UNSUPPORTED     -> "op_unsupported";
            default                                    -> "sftp_error";
        };
    }

    private Session createSession() throws JSchException {
        JSch jsch = jschFactory.create();
        Session session = jsch.getSession(username, host, port);
        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(10_000);
        return session;
    }

    private ChannelSftp openSftpChannel(Session session) throws JSchException {
        ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
        channel.connect();
        return channel;
    }
}
