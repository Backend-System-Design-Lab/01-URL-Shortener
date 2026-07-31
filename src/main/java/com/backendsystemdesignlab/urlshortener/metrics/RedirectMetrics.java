package com.backendsystemdesignlab.urlshortener.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class RedirectMetrics {

    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;
    private final Counter dbLookupCounter;

    public RedirectMetrics(MeterRegistry meterRegistry) {
        this.cacheHitCounter = Counter.builder("short_url.cache.hit")
                .description("Redis cache hit count")
                .register(meterRegistry);

        this.cacheMissCounter = Counter.builder("short_url.cache.miss")
                .description("Redis cache miss count")
                .register(meterRegistry);

        this.dbLookupCounter = Counter.builder("short_url.db.lookup")
                .description("Short URL database lookup count")
                .register(meterRegistry);
    }

    public void recordCacheHit() {
        cacheHitCounter.increment();
    }
    public void recordCacheMiss() {
        cacheMissCounter.increment();
    }
    public void recordDbLookup() {
        dbLookupCounter.increment();
    }
}
