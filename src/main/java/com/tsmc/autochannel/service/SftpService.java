package com.tsmc.autochannel.service;

import com.jcraft.jsch.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

@Slf4j
@Service
public class SftpService {

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

    public void downloadFile(String remoteFileName, String localPath) throws Exception {
        Session session = createSession();
        ChannelSftp channel = openSftpChannel(session);
        try {
            channel.get(remoteDir + "/" + remoteFileName, localPath);
            log.info("[sftp] Downloaded {}/{} → {}", remoteDir, remoteFileName, localPath);
        } finally {
            channel.disconnect();
            session.disconnect();
        }
    }

    public void uploadFile(String localPath, String remoteFileName) throws Exception {
        Session session = createSession();
        ChannelSftp channel = openSftpChannel(session);
        try {
            channel.put(localPath, remoteDir + "/" + remoteFileName);
            log.info("[sftp] Uploaded {} → {}/{}", localPath, remoteDir, remoteFileName);
        } finally {
            channel.disconnect();
            session.disconnect();
        }
    }

    @SuppressWarnings("unchecked")
    public List<String> listFiles() throws Exception {
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
    }

    private Session createSession() throws JSchException {
        JSch jsch = new JSch();
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
