package com.backendsystemdesignlab.urlshortener.cache;

import com.backendsystemdesignlab.urlshortener.metrics.RedirectMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ShortUrlCache {

    private static final String KEY_PREFIX = "short-url:";
    private static final Duration TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;
    private final RedirectMetrics redirectMetrics;

    public Optional<String> find(String shortCode) {
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
        redisTemplate.opsForValue()
                .set(KEY_PREFIX + shortCode, longUrl, TTL);
    }
}
