package com.tsmc.autochannel.metrics;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoggingObservationHandler implements ObservationHandler<Observation.Context> {

    @Override
    public void onStart(Observation.Context ctx) {
        log.info("[obs] START name={} keys={}, {}", ctx.getName(), ctx.getLowCardinalityKeyValues(), ctx.getHighCardinalityKeyValues());
    }

    @Override
    public void onStop(Observation.Context ctx) {
        log.info("[obs] STOP name={} keys={}, {}", ctx.getName(), ctx.getLowCardinalityKeyValues(), ctx.getHighCardinalityKeyValues());
    }

    @Override
    public void onError(Observation.Context ctx) {
        Throwable error = ctx.getError();
        log.error("[obs] ERROR name={} keys={}, {}, error={}",
                ctx.getName(), ctx.getLowCardinalityKeyValues(),
                ctx.getHighCardinalityKeyValues(),
                error != null ? error.getMessage() : "unknown");
    }

    private static final java.util.Set<String> BLOCKED_PREFIXES = java.util.Set.of(
            "name=http.server.requests"
    );

    @Override
    public boolean supportsContext(Observation.Context ctx) {
        String name = ctx.getName();
        return name != null && BLOCKED_PREFIXES.stream().noneMatch(name::startsWith);
    }
}
