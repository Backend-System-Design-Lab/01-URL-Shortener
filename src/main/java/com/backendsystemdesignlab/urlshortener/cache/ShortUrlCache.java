package com.backendsystemdesignlab.urlshortener.cache;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class ShortUrlCache {

    private static final String KEY_PREFIX = "short-url:";
    private static final Duration TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;

    public ShortUrlCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Optional<String> find(String shortCode) {
        String longUrl = redisTemplate.opsForValue()
                .get(KEY_PREFIX +  shortCode);

        return Optional.ofNullable(longUrl);
    }

    public void save(String shortCode, String longUrl) {
        redisTemplate.opsForValue()
                .set(KEY_PREFIX + shortCode, longUrl, TTL);
    }
}
