package com.tsmc.autochannel.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Profile("mock")
public class LocalFileTransferService implements FileTransferService {

    @Value("${sftp.remote-dir}")
    private String remoteDir;

    private Path remoteDir() {
        return Path.of("mock_nas/sftp", remoteDir);
    }

    @Override
    public void downloadFile(String remoteFileName, String localPath) throws Exception {
        Path src = remoteDir().resolve(remoteFileName);
        if (!Files.exists(src)) {
            throw new IOException("File not found in mock SFTP: " + src);
        }
        Files.copy(src, Path.of(localPath), StandardCopyOption.REPLACE_EXISTING);
        log.info("[mock-sftp] download {} → {}", src, localPath);
    }

    @Override
    public void uploadFile(String localPath, String remoteFileName) throws Exception {
        Path dest = remoteDir().resolve(remoteFileName);
        Files.createDirectories(dest.getParent());
        Files.copy(Path.of(localPath), dest, StandardCopyOption.REPLACE_EXISTING);
        log.info("[mock-sftp] upload {} → {}", localPath, dest);
    }

    @Override
    public List<String> listFiles() throws Exception {
        Path dir = remoteDir();
        if (!Files.exists(dir)) return Collections.emptyList();
        try (var stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                         .map(p -> p.getFileName().toString())
                         .collect(Collectors.toList());
        }
    }
}
