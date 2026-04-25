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

    public void recordFileSize(String component, long bytes) {
        DistributionSummary.builder("file.processing.size")
                .baseUnit("bytes")
                .description("Size of files processed")
                .tag("component", component)
                .register(registry)
                .record(bytes);
        log.info("[metrics] component={} file_size={} bytes", component, bytes);
    }

    public void recordDuration(String component, long millis) {
        Timer.builder("file.processing.duration")
                .description("Time taken to process files")
                .tag("component", component)
                .register(registry)
                .record(millis, TimeUnit.MILLISECONDS);
        log.info("[metrics] component={} duration={} ms", component, millis);
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
