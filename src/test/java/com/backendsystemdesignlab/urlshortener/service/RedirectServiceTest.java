package com.backendsystemdesignlab.urlshortener.service;

import com.backendsystemdesignlab.urlshortener.cache.ShortUrlCache;
import com.backendsystemdesignlab.urlshortener.exception.ShortUrlNotFoundException;
import com.backendsystemdesignlab.urlshortener.metrics.RedirectMetrics;
import com.backendsystemdesignlab.urlshortener.url.domain.ShortUrl;
import com.backendsystemdesignlab.urlshortener.url.repository.ShortUrlRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
}