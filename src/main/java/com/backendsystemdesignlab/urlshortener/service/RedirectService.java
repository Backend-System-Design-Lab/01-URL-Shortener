package com.backendsystemdesignlab.urlshortener.service;

import com.backendsystemdesignlab.urlshortener.cache.ShortUrlCache;
import com.backendsystemdesignlab.urlshortener.exception.ShortUrlNotFoundException;
import com.backendsystemdesignlab.urlshortener.metrics.RedirectMetrics;
import com.backendsystemdesignlab.urlshortener.url.domain.ShortUrl;
import com.backendsystemdesignlab.urlshortener.url.repository.ShortUrlRepository;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RedirectService {

    private final ShortUrlRepository shortUrlRepository;
    private final ShortUrlCache shortUrlCache;
    private final RedirectMetrics redirectMetrics;
    private final CircuitBreaker redisCircuitBreaker;

    public String findLongUrl(String shortCode) {
        try {
            Optional<String> cachedLongUrl = redisCircuitBreaker.executeSupplier(() -> shortUrlCache.find(shortCode));

            if (cachedLongUrl.isPresent()) {
                return cachedLongUrl.get();
            }
        } catch (CallNotPermittedException e) { // Circuit OPEN
          redirectMetrics.recordCircuitBreakerRejected();
          redirectMetrics.recordCacheFallback();

          return findFromDatabase(shortCode, false);
        } catch (DataAccessException e) {
            redirectMetrics.recordCacheGetError();
            redirectMetrics.recordCacheFallback();

            /*
             * Redis GET이 실패했으므로 같은 요청에서는
             * Redis SET을 다시 시도하지 않고 MySQL만 조회
             */
            return findFromDatabase(shortCode, false);
        }

        /*
         * Redis 조회에는 성공했지만 Cache Miss인 경우
         * MySQL 조회 후 Redis에 다시 저장
         */
        return findFromDatabase(shortCode, true);
    }

    private String findFromDatabase(String shortCode, boolean saveToCache) {
        redirectMetrics.recordDbLookup();

        ShortUrl shortUrl = shortUrlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortUrlNotFoundException(shortCode));

        String longUrl = shortUrl.getLongUrl();

        // Redis가 정상 작동할 때만 저장
        if (saveToCache) {
            saveToCacheSafely(shortCode, longUrl);
        }

        return longUrl;
    }

    private void saveToCacheSafely(String shortCode, String longUrl) {
        try {
            shortUrlCache.save(shortCode, longUrl);
        } catch (DataAccessException e) {
            // Redis 저장 실패가 사용자 리다이렉트 실패로 이어지지 않게 한다.
            redirectMetrics.recordCacheSetError();
        }
    }
}
