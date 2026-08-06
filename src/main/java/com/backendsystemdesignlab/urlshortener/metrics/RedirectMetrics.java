package com.backendsystemdesignlab.urlshortener.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class RedirectMetrics {

    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;
    private final Counter cacheGetErrorCounter;
    private final Counter cacheSetErrorCounter;
    private final Counter cacheFallbackCounter;
    private final Counter dbLookupCounter;

    public RedirectMetrics(MeterRegistry meterRegistry) {
        this.cacheHitCounter = Counter.builder("short_url.cache.hit")
                .description("Redis cache hit count")
                .register(meterRegistry);

        this.cacheMissCounter = Counter.builder("short_url.cache.miss")
                .description("Redis cache miss count")
                .register(meterRegistry);

        this.cacheGetErrorCounter = Counter.builder("short_url.cache.error")
                .description("Redis cache operation error count")
                .tag("operation", "get")
                .register(meterRegistry);

        this.cacheSetErrorCounter = Counter.builder("short_url.cache.error")
                .description("Redis cache operation error count")
                .tag("operation", "set")
                .register(meterRegistry);

        this.cacheFallbackCounter = Counter.builder("short_url.cache.fallback")
                .description("MySQL fallback count caused by Redis errors")
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
    public void recordCacheGetError() { cacheGetErrorCounter.increment(); }
    public void recordCacheSetError() { cacheSetErrorCounter.increment(); }
    public void recordCacheFallback() { cacheFallbackCounter.increment(); }
    public void recordDbLookup() {
        dbLookupCounter.increment();
    }
}
