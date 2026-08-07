package com.backendsystemdesignlab.urlshortener.service;

import com.backendsystemdesignlab.urlshortener.cache.ShortUrlCache;
import com.backendsystemdesignlab.urlshortener.config.RedisCircuitBreakerConfig;
import com.backendsystemdesignlab.urlshortener.exception.ShortUrlNotFoundException;
import com.backendsystemdesignlab.urlshortener.metrics.RedirectMetrics;
import com.backendsystemdesignlab.urlshortener.url.domain.ShortUrl;
import com.backendsystemdesignlab.urlshortener.url.repository.ShortUrlRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class RedirectServiceTest {

    @InjectMocks
    private RedirectService redirectService;

    @Mock
    private ShortUrlCache shortUrlCache;
    @Mock
    private ShortUrlRepository shortUrlRepository;
    @Mock
    private RedirectMetrics redirectMetrics;

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        circuitBreaker = new RedisCircuitBreakerConfig().redisCircuitBreaker();
        redirectService = new RedirectService(shortUrlRepository, shortUrlCache, redirectMetrics, circuitBreaker);
    }

    @Test
    void 캐시에_URL이_있으면_DB를_조회하지_않는다() {
        String shortCode = "2TX";
        String longUrl = "https://www.google.com";

        given(shortUrlCache.find(shortCode))
                .willReturn(Optional.of(longUrl));

        String result = redirectService.findLongUrl(shortCode);

        assertThat(result).isEqualTo(longUrl);

        then(shortUrlCache).should().find(shortCode);
        then(shortUrlRepository).shouldHaveNoInteractions();
        then(redirectMetrics).shouldHaveNoInteractions();
    }

    @Test
    void 캐시에_URL이_없으면_DB를_조회하고_캐시에_저장한다() {
        String shortCode = "2TX";
        String longUrl = "https://www.google.com";

        ShortUrl shortUrl = ShortUrl.create(longUrl);

        given(shortUrlCache.find(shortCode))
                .willReturn(Optional.empty());

        given(shortUrlRepository.findByShortCode(shortCode))
                .willReturn(Optional.of(shortUrl));

        String result = redirectService.findLongUrl(shortCode);

        assertThat(result).isEqualTo(longUrl);

        then(shortUrlCache).should().find(shortCode);
        then(redirectMetrics).should().recordDbLookup();
        then(shortUrlRepository).should().findByShortCode(shortCode);
        then(shortUrlCache).should().save(shortCode, longUrl);
    }

    @Test
    void 캐시와_DB에_URL이_없으면_예외가_발생한다() {
        String shortCode = "2TX";

        given(shortUrlCache.find(shortCode))
                .willReturn(Optional.empty());

        given(shortUrlRepository.findByShortCode(shortCode))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> redirectService.findLongUrl(shortCode))
                .isInstanceOf(ShortUrlNotFoundException.class);

        then(shortUrlCache).should().find(shortCode);
        then(redirectMetrics).should().recordDbLookup();
        then(shortUrlRepository).should().findByShortCode(shortCode);
        then(shortUrlCache).shouldHaveNoMoreInteractions();
    }

    @Test
    void Redis_조회에_실패하면_DB로_Fallback한다() {
        String shortCode = "2TX";
        String longUrl = "https://www.google.com";

        ShortUrl shortUrl = ShortUrl.create(longUrl);

        given(shortUrlCache.find(shortCode)).willThrow(new RedisConnectionFailureException("Redis connection failure"));
        given(shortUrlRepository.findByShortCode(shortCode)).willReturn(Optional.of(shortUrl));

        String result = redirectService.findLongUrl(shortCode);

        assertThat(result).isEqualTo(longUrl);
        then(shortUrlCache).should().find(shortCode);
        then(redirectMetrics).should().recordCacheGetError();
        then(redirectMetrics).should().recordCacheFallback();
        then(redirectMetrics).should().recordDbLookup();
        then(shortUrlRepository).should().findByShortCode(shortCode);
        then(shortUrlCache).shouldHaveNoMoreInteractions();
    }

    @Test
    void Redis_저장에_실패해도_DB에서_조회한_URL을_반환한다() {
        String shortCode = "2TX";
        String longUrl = "https://www.google.com";

        ShortUrl shortUrl = ShortUrl.create(longUrl);

        given(shortUrlCache.find(shortCode)).willReturn(Optional.empty());
        given(shortUrlRepository.findByShortCode(shortCode)).willReturn(Optional.of(shortUrl));

        willThrow(new RedisConnectionFailureException("Redis connection failure")).given(shortUrlCache).save(shortCode, longUrl);

        String result = redirectService.findLongUrl(shortCode);

        assertThat(result).isEqualTo(longUrl);
        then(shortUrlCache).should().find(shortCode);
        then(redirectMetrics).should().recordDbLookup();
        then(shortUrlRepository).should().findByShortCode(shortCode);
        then(shortUrlCache).should().save(shortCode, longUrl);
        then(redirectMetrics).should().recordCacheSetError();;
    }

    @Test
    void Redis_장애가_반복되면_Circuit이_열리고_이후_Redis_호출을_차단한다() {
        String shortCode = "2TX";
        String longUrl = "https://www.google.com";

        ShortUrl shortUrl = ShortUrl.create(longUrl);

        given(shortUrlCache.find(shortCode))
                .willThrow(new DataAccessResourceFailureException("Redis down"));

        given(shortUrlRepository.findByShortCode(shortCode)).willReturn(Optional.of(shortUrl));

        for (int i = 0; i < 5; i++) {
            redirectService.findLongUrl(shortCode);
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        redirectService.findLongUrl(shortCode);
        then(shortUrlCache).should(times(5)).find(shortCode);
        then(shortUrlRepository).should(times(6)).findByShortCode(shortCode);
    }

    @Test
    void Circuit이_OPEN이면_Redis를_조회하지_않고_DB로_Fallback한다() {
        String shortCode = "2TX";
        String longUrl = "https://www.google.com";

        ShortUrl shortUrl = ShortUrl.create(longUrl);

        given(shortUrlRepository.findByShortCode(shortCode)).willReturn(Optional.of(shortUrl));

        circuitBreaker.transitionToOpenState();

        String result = redirectService.findLongUrl(shortCode);

        assertThat(result).isEqualTo(longUrl);
        then(shortUrlCache).shouldHaveNoInteractions();
        then(shortUrlRepository).should().findByShortCode(shortCode);
    }
}