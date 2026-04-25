package com.tsmc.autochannel.service;

import java.util.List;

public interface FileTransferService {
    void downloadFile(String remoteFileName, String localPath) throws Exception;
    void uploadFile(String localPath, String remoteFileName) throws Exception;
    List<String> listFiles() throws Exception;
}
