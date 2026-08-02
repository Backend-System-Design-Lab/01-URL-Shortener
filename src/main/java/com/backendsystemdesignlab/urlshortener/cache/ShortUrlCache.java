package com.backendsystemdesignlab.urlshortener.cache;

import com.backendsystemdesignlab.urlshortener.metrics.RedirectMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class ShortUrlCache {

    private static final String KEY_PREFIX = "short-url:";
    private static final Duration TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;
    private final RedirectMetrics redirectMetrics;
    private final boolean cacheEnabled;

    public ShortUrlCache(
            StringRedisTemplate redisTemplate,
            RedirectMetrics redirectMetrics,
            @Value("${app.cache.enabled:true}") boolean cacheEnabled
    ) {
        this.redisTemplate = redisTemplate;
        this.redirectMetrics = redirectMetrics;
        this.cacheEnabled = cacheEnabled;
    }

    public Optional<String> find(String shortCode) {
        // DB Only 실험에서는 Redis 조회X
        if (!cacheEnabled) {
            return Optional.empty();
        }

        String longUrl = redisTemplate.opsForValue()
                .get(KEY_PREFIX +  shortCode);

        if (longUrl != null) {
            redirectMetrics.recordCacheHit();
            return Optional.of(longUrl);
        }

        redirectMetrics.recordCacheMiss();
        return Optional.empty();
    }

    public void save(String shortCode, String longUrl) {
        if (!cacheEnabled) {
            return;
        }

        redisTemplate.opsForValue()
                .set(KEY_PREFIX + shortCode, longUrl, TTL);
    }
}
