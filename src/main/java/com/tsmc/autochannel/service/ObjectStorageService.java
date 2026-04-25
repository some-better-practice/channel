package com.tsmc.autochannel.service;

public interface ObjectStorageService {
    void uploadFile(String objectName, String localPath) throws Exception;
    void downloadFile(String objectName, String localPath) throws Exception;
    boolean objectExists(String objectName);
    boolean hasStdfFiles(String prefix);
}
