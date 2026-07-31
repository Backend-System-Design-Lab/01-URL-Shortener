package com.backendsystemdesignlab.urlshortener.service;

import com.backendsystemdesignlab.urlshortener.cache.ShortUrlCache;
import com.backendsystemdesignlab.urlshortener.encoding.Base62Encoder;
import com.backendsystemdesignlab.urlshortener.exception.ShortUrlNotFoundException;
import com.backendsystemdesignlab.urlshortener.url.domain.ShortUrl;
import com.backendsystemdesignlab.urlshortener.url.repository.ShortUrlRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RedirectServiceTest {

    @InjectMocks
    private RedirectService redirectService;

    @Mock
    private ShortUrlCache shortUrlCache;
    @Mock
    private ShortUrlRepository shortUrlRepository;
    @Mock
    private Base62Encoder base62Encoder;

    @Test
    void 캐시에_URL이_있으면_DB를_조회하지_않는다() {
        String shortCode = "2TX";
        String longUrl = "https://www.google.com";

        given(shortUrlCache.find(shortCode))
                .willReturn(Optional.of(longUrl));

        String result = redirectService.findLongUrl(shortCode);

        assertThat(result).isEqualTo(longUrl);

        then(shortUrlCache).should().find(shortCode);
        then(base62Encoder).shouldHaveNoInteractions();
        then(shortUrlRepository).shouldHaveNoInteractions();
    }

    @Test
    void 캐시에_URL이_없으면_DB를_조회하고_캐시에_저장한다() {
        String shortCode = "2TX";
        String longUrl = "https://www.google.com";
        long id = 12345L;

        ShortUrl shortUrl = ShortUrl.create(longUrl);

        given(shortUrlCache.find(shortCode))
                .willReturn(Optional.empty());

        given(base62Encoder.decode(shortCode))
                .willReturn(id);

        given(shortUrlRepository.findById(id))
                .willReturn(Optional.of(shortUrl));

        String result = redirectService.findLongUrl(shortCode);

        assertThat(result).isEqualTo(longUrl);

        then(shortUrlCache).should().find(shortCode);
        then(base62Encoder).should().decode(shortCode);
        then(shortUrlRepository).should().findById(id);
        then(shortUrlCache).should().save(shortCode, longUrl);
    }

    @Test
    void 캐시와_DB에_URL이_없으면_예외가_발생한다() {
        String shortCode = "2TX";
        long id = 12345L;

        given(shortUrlCache.find(shortCode))
                .willReturn(Optional.empty());

        given(base62Encoder.decode(shortCode))
                .willReturn(id);

        given(shortUrlRepository.findById(id))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> redirectService.findLongUrl(shortCode))
                .isInstanceOf(ShortUrlNotFoundException.class);

        then(shortUrlCache).should().find(shortCode);
        then(shortUrlRepository).should().findById(id);
        then(shortUrlCache).shouldHaveNoMoreInteractions();
    }
}