package com.tsmc.autochannel.component;

import com.tsmc.autochannel.constants.ChannelConstants;
import com.tsmc.autochannel.service.ObjectStorageService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class AutoComponent {

    private static final int    GENERATE_THREADS      = 2;
    private static final int    GENERATE_COUNT        = 3;
    private static final long   GENERATE_INTERVAL_SEC = 15;
    private static final long   SCHEDULE_PERIOD_SEC   = 3 * 60;  // each job runs every 3 min

    private final GenerateFilesComponent generateFilesComponent;
    private final SftpDownloadComponent  sftpDownloadComponent;
    private final Stdf2CsvComponent      stdf2CsvComponent;
    private final SftpUploadComponent    sftpUploadComponent;
    private final ObjectStorageService   minioService;

    private final ScheduledExecutorService generateExecutor =
            Executors.newScheduledThreadPool(GENERATE_THREADS);
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(3);

    public void execute() {
        log.info("[auto] Starting auto mode — generate×{} threads, 3 scheduled jobs offset by 1 min each", GENERATE_THREADS);

        // ── Continuous generateFiles (2 threads, staggered start) ──────────────
        for (int i = 0; i < GENERATE_THREADS; i++) {
            long offsetSec = (long) i * (GENERATE_INTERVAL_SEC / GENERATE_THREADS);
            generateExecutor.scheduleWithFixedDelay(
                    this::runGenerate,
                    offsetSec,
                    GENERATE_INTERVAL_SEC,
                    TimeUnit.SECONDS);
            log.info("[auto] generateFiles thread-{} scheduled (initialDelay={}s, delay={}s)", i, offsetSec, GENERATE_INTERVAL_SEC);
        }

        // ── sftp-download: starts immediately, every 3 min ─────────────────────
        scheduler.scheduleAtFixedRate(
                this::runSftpDownload,
                0, SCHEDULE_PERIOD_SEC, TimeUnit.SECONDS);
        log.info("[auto] sftpDownload scheduled (initialDelay=0s, period={}s)", SCHEDULE_PERIOD_SEC);

        // ── stdf2csv: starts after 1 min, every 3 min (only if stdf in MinIO) ──
        scheduler.scheduleAtFixedRate(
                this::runStdf2Csv,
                60, SCHEDULE_PERIOD_SEC, TimeUnit.SECONDS);
        log.info("[auto] stdf2csv scheduled (initialDelay=60s, period={}s)", SCHEDULE_PERIOD_SEC);

        // ── sftp-upload: starts after 2 min, every 3 min ───────────────────────
        scheduler.scheduleAtFixedRate(
                this::runSftpUpload,
                120, SCHEDULE_PERIOD_SEC, TimeUnit.SECONDS);
        log.info("[auto] sftpUpload scheduled (initialDelay=120s, period={}s)", SCHEDULE_PERIOD_SEC);

        log.info("[auto] All schedulers running. Press Ctrl+C to stop.");
    }

    private void runGenerate() {
        try {
            log.debug("[auto] generateFiles start");
            generateFilesComponent.execute(GENERATE_COUNT);
        } catch (Exception e) {
            log.error("[auto] generateFiles error: {}", e.getMessage(), e);
        }
    }

    private void runSftpDownload() {
        try {
            String channelId = ChannelConstants.randomChannel().channelId();
            log.info("[auto] sftpDownload start (channelId={})", channelId);
            sftpDownloadComponent.execute(channelId);
        } catch (Exception e) {
            log.error("[auto] sftpDownload error: {}", e.getMessage(), e);
        }
    }

    private void runStdf2Csv() {
        try {
            if (!minioService.hasStdfFiles("raw/")) {
                log.info("[auto] stdf2csv skipped — no .stdf files in MinIO raw/");
                return;
            }
            String channelId = ChannelConstants.randomChannel().channelId();
            log.info("[auto] stdf2csv start (channelId={})", channelId);
            stdf2CsvComponent.execute(channelId);
        } catch (Exception e) {
            log.error("[auto] stdf2csv error: {}", e.getMessage(), e);
        }
    }

    private void runSftpUpload() {
        try {
            String channelId = ChannelConstants.randomChannel().channelId();
            log.info("[auto] sftpUpload start (channelId={})", channelId);
            sftpUploadComponent.execute(channelId);
        } catch (Exception e) {
            log.error("[auto] sftpUpload error: {}", e.getMessage(), e);
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("[auto] Shutting down executors...");
        generateExecutor.shutdownNow();
        scheduler.shutdownNow();
    }
}
