package com.tsmc.autochannel.component;

import com.tsmc.autochannel.config.Stdf2CsvConfig;
import com.tsmc.autochannel.entity.OperationType;
import com.tsmc.autochannel.entity.TransferLog;
import com.tsmc.autochannel.metrics.ProcessingMetrics;
import com.tsmc.autochannel.service.ObjectStorageService;
import com.tsmc.autochannel.service.TransferLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class Stdf2CsvComponent {

    private final ObjectStorageService minioService;
    private final ProcessingMetrics metrics;
    private final TransferLogService transferLogService;
    private final ProcessStarter processStarter;

    @Value("${stdf2csv.timeout-seconds:300}")
    private long timeoutSeconds;

    public void execute(String channelId, Stdf2CsvConfig.Converter_TYPE converterType) {
        long start = System.currentTimeMillis();
        long ts = System.currentTimeMillis();
        String sourceKey = "raw/data_" + ts + ".stdf";
        String outputKey = "converted/data_" + ts + ".csv";
        Path stdfFile = null;
        Path csvFile = null;

        TransferLog transferLog = transferLogService.logStart(channelId, OperationType.STDF_CONVERT, sourceKey);

        try {
            stdfFile = Files.createTempFile("stdf_", ".stdf");
            csvFile = Files.createTempFile("csv_", ".csv");

            if (minioService.objectExists(sourceKey)) {
                log.info("[stdf2csv] Downloading from MinIO: {}", sourceKey);
                minioService.downloadFile(sourceKey, stdfFile.toString());
            } else {
                log.info("[stdf2csv] Source not found in MinIO, skipping download: {}", sourceKey);
            }

            runConversion(stdfFile.toString(), csvFile.toString(), converterType);
            log.info("[stdf2csv] Converted {} → {}", sourceKey, outputKey);

            minioService.uploadFile(outputKey, csvFile.toString());

            long fileSize = ProcessingMetrics.randomFileSize();
            long duration = System.currentTimeMillis() - start;
            metrics.recordFileSize(channelId, "convert", fileSize);
            metrics.recordDuration(channelId, "convert", duration);

            transferLogService.logSuccess(transferLog, fileSize, duration);
            log.info("[stdf2csv] Done — uploaded to MinIO: {}", outputKey);

        } catch (Exception e) {
            transferLogService.logFailed(transferLog, e.getMessage());
            log.error("[stdf2csv] Failed: {}", e.getMessage(), e);
        } finally {
            deleteSilently(stdfFile);
            deleteSilently(csvFile);
        }
    }

    void runConversion(String inputFile, String outputFile, Stdf2CsvConfig.Converter_TYPE converterType)
            throws IOException, InterruptedException, TimeoutException {
        ProcessBuilder pb = getStdf2CsvBuilder(inputFile, outputFile, converterType);
        Process process = processStarter.start(pb);
        try {
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new TimeoutException(
                        "stdf2csv timed out after " + timeoutSeconds + "s: " + inputFile);
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new RuntimeException("stdf2csv exited with code " + exitCode + ": " + inputFile);
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    ProcessBuilder getStdf2CsvBuilder(String inputFile, String outputFile,
                                      Stdf2CsvConfig.Converter_TYPE converterType) {
        ProcessBuilder processBuilder;
        switch (converterType) {
            case TESTNAME:
                processBuilder = new ProcessBuilder(
                        "dotnet", "stdf2csv.dll", inputFile, outputFile, "/S", "/1", "/M");
                break;
            case TESTNO:
                processBuilder = new ProcessBuilder(
                        "dotnet", "stdf2csv.dll", inputFile, outputFile, "/S", "/1", "/N");
                break;
            case NONCHANNEL:
                processBuilder = new ProcessBuilder(
                        "dotnet", "stdf2csv.dll", inputFile, outputFile, "/S", "/1", "/CN");
                break;
            case NONMERGE:
                processBuilder = new ProcessBuilder(
                        "dotnet", "stdf2csv.dll", inputFile, outputFile, "/S", "/1");
                break;
            default:
                log.error("No ConverterType defined, please check!");
                processBuilder = new ProcessBuilder(
                        "dotnet", "stdf2csv.dll", inputFile, outputFile, "/S", "/1", "/N");
        }
        processBuilder.directory(new File(System.getProperty("user.dir")));
        return processBuilder;
    }

    private void deleteSilently(Path path) {
        try {
            if (path != null) Files.deleteIfExists(path);
        } catch (Exception ignored) {}
    }
}
