package com.tsmc.autochannel;

import com.tsmc.autochannel.component.AutoComponent;
import com.tsmc.autochannel.component.ContentParserComponent;
import com.tsmc.autochannel.component.GenerateFilesComponent;
import com.tsmc.autochannel.component.Stdf2CsvComponent;
import com.tsmc.autochannel.component.SftpDownloadComponent;
import com.tsmc.autochannel.component.SftpUploadComponent;
import com.tsmc.autochannel.constants.ChannelConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
@RequiredArgsConstructor
public class AutoChannelApplication implements ApplicationRunner {

    private final SftpDownloadComponent sftpDownloadComponent;
    private final Stdf2CsvComponent stdf2CsvComponent;
    private final ContentParserComponent contentParserComponent;
    private final SftpUploadComponent sftpUploadComponent;
    private final GenerateFilesComponent generateFilesComponent;
    private final AutoComponent autoComponent;

    public static void main(String[] args) {
        SpringApplication.run(AutoChannelApplication.class, args);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!args.containsOption("component")) {
            log.warn("No component specified. Usage: --component=<sftpDownload|stdf2csv|contentParser|sftpUpload|generateFiles|auto>");
            log.info("HTTP server is running on port 8080. Prometheus metrics available at /actuator/prometheus");
            return;
        }

        String component = args.getOptionValues("component").get(0);
        log.info("Starting component: {}", component);

        String channelId = args.containsOption("channelId")
                ? args.getOptionValues("channelId").get(0)
                : ChannelConstants.randomChannel().channelId();

        switch (component) {
            case "sftpDownload"   -> sftpDownloadComponent.execute(channelId);
            case "stdf2csv"       -> stdf2CsvComponent.execute(channelId);
            case "contentParser"  -> contentParserComponent.execute(channelId);
            case "sftpUpload"     -> sftpUploadComponent.execute(channelId);
            case "generateFiles"  -> {
                int count = args.containsOption("count")
                        ? Integer.parseInt(args.getOptionValues("count").get(0))
                        : 5;
                generateFilesComponent.execute(count);
            }
            case "auto"           -> { autoComponent.execute(); return; }  // schedulers keep the JVM alive
            default -> log.error("Unknown component: '{}'. Valid: sftpDownload, stdf2csv, contentParser, sftpUpload, generateFiles, auto", component);
        }

        log.info("Component '{}' finished. HTTP server remains up for Prometheus scraping.", component);
    }
}
