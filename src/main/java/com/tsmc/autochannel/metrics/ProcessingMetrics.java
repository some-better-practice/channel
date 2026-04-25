package com.tsmc.autochannel.metrics;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessingMetrics {

    private final MeterRegistry registry;

    public void recordFileSize(String channelId, String operation, double bytes) {
        DistributionSummary.builder("file_processing_size")
                .baseUnit("bytes")
                .description("Size of files processed")
                .tag("channelId", channelId)
                .tag("operation", operation)
                .register(registry)
                .record(bytes);
        log.info("[metrics] channelId={} operation={} file_size={} bytes", channelId, operation, bytes);
    }

    public void recordDuration(String channelId, String operation, long millis) {
        Timer.builder("file_processing_duration")
                .description("Time taken to process files")
                .tag("channelId", channelId)
                .tag("operation", operation)
                .register(registry)
                .record(millis, TimeUnit.MILLISECONDS);
        log.info("[metrics] channelId={} operation={} duration={} ms", channelId, operation, millis);
    }

    public static long randomFileSize() {
        // 1 KB ~ 100 MB
        return ThreadLocalRandom.current().nextLong(1_024, 100 * 1_024 * 1_024L);
    }

    public static long randomDuration() {
        // 100 ms ~ 30 s
        return ThreadLocalRandom.current().nextLong(100, 30_000);
    }
}
