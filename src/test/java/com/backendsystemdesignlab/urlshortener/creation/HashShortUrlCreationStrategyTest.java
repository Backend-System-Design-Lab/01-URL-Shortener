package com.backendsystemdesignlab.urlshortener.creation;

import com.backendsystemdesignlab.urlshortener.exception.ShortCodeGenerationException;
import com.backendsystemdesignlab.urlshortener.generator.HashShortCodeGenerator;
import com.backendsystemdesignlab.urlshortener.generator.ShortCodeGenerationContext;
import com.backendsystemdesignlab.urlshortener.url.domain.ShortUrl;
import com.backendsystemdesignlab.urlshortener.url.repository.ShortUrlRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class HashShortUrlCreationStrategyTest {

    @InjectMocks
    private HashShortUrlCreationStrategy strategy;

    @Mock
    private ShortUrlRepository shortUrlRepository;

    @Mock
    private HashShortCodeGenerator hashShortCodeGenerator;

    @Mock
    private ShortUrlWriter shortUrlWriter;

    @Test
    void 충돌이_없으면_생성한_코드로_저장한다() {
        String longUrl = "https://www.google.com";
        String shortCode = "abc1234";

        ShortCodeGenerationContext context = ShortCodeGenerationContext.hash(longUrl,0);

        given(hashShortCodeGenerator.generate(context)).willReturn(shortCode);

        given(shortUrlRepository.findByShortCode(shortCode)).willReturn(Optional.empty());

        String result = strategy.create(longUrl);

        assertThat(result).isEqualTo(shortCode);
        then(hashShortCodeGenerator).should().generate(context);
        then(shortUrlRepository).should().findByShortCode(shortCode);
        then(shortUrlWriter).should().save(shortCode, longUrl);
    }

    @Test
    void 같은_URL의_코드가_이미_있으면_기존_코드를_반환한다() {
        String longUrl = "https://www.google.com";
        String shortCode = "abc1234";

        ShortCodeGenerationContext context = ShortCodeGenerationContext.hash(longUrl,0);
        ShortUrl existingShortUrl = ShortUrl.create(shortCode, longUrl);

        given(hashShortCodeGenerator.generate(context)).willReturn(shortCode);
        given(shortUrlRepository.findByShortCode(shortCode)).willReturn(Optional.of(existingShortUrl));

        String result = strategy.create(longUrl);

        assertThat(result).isEqualTo(shortCode);
        then(hashShortCodeGenerator).should().generate(context);
        then(shortUrlRepository).should().findByShortCode(shortCode);
        then(shortUrlWriter).shouldHaveNoInteractions();
    }

    @Test
    void 다른_URL과_충돌하면_attempt를_증가시켜_재시도한다() {
        String longUrl = "https://www.google.com";

        String collidedCode = "abc1234";
        String retryCode = "xyz9876";

        ShortCodeGenerationContext firstContext = ShortCodeGenerationContext.hash(longUrl,0);
        ShortCodeGenerationContext secondContext = ShortCodeGenerationContext.hash(longUrl,1);

        ShortUrl otherShortUrl = ShortUrl.create(collidedCode, "https://www.naver.com");

        given(hashShortCodeGenerator.generate(firstContext)).willReturn(collidedCode);
        given(hashShortCodeGenerator.generate(secondContext)).willReturn(retryCode);
        given(shortUrlRepository.findByShortCode(collidedCode)).willReturn(Optional.of(otherShortUrl));
        given(shortUrlRepository.findByShortCode(retryCode)).willReturn(Optional.empty());

        String result = strategy.create(longUrl);

        assertThat(result).isEqualTo(retryCode);
        then(hashShortCodeGenerator).should().generate(firstContext);
        then(hashShortCodeGenerator).should().generate(secondContext);
        then(shortUrlRepository).should().findByShortCode(collidedCode);
        then(shortUrlRepository).should().findByShortCode(retryCode);
        then(shortUrlWriter).should().save(retryCode, longUrl);
    }

    @Test
    void 최대_재시도_횟수까지_충돌하면_예외가_발생한다() {
        String longUrl = "https://www.google.com";

        for (int attempt = 0; attempt < 5; attempt++) {
            String shortCode = "code00" + attempt;
            ShortCodeGenerationContext context = ShortCodeGenerationContext.hash(longUrl, attempt);
            ShortUrl existingShortUrl = ShortUrl.create(shortCode, "https://other-" + attempt + ".com");

            given(hashShortCodeGenerator.generate(context)).willReturn(shortCode);
            given(shortUrlRepository.findByShortCode(shortCode)).willReturn(Optional.of(existingShortUrl));
        }

        assertThatThrownBy(() -> strategy.create(longUrl))
                .isInstanceOf(ShortCodeGenerationException.class);
        then(hashShortCodeGenerator).should(times(5)).generate(any(ShortCodeGenerationContext.class));
        then(shortUrlWriter).shouldHaveNoInteractions();
    }

    @Test
    void 저장_직전에_UNIQUE_충돌이_발생하면_다음_attempt로_재시도한다() {
        String longUrl = "https://www.google.com";

        String firstCode ="abc1234";
        String secondCode = "xyz9876";

        ShortCodeGenerationContext firstContext = ShortCodeGenerationContext.hash(longUrl,0);
        ShortCodeGenerationContext secondContext = ShortCodeGenerationContext.hash(longUrl,1);

        given(hashShortCodeGenerator.generate(firstContext)).willReturn(firstCode);
        given(hashShortCodeGenerator.generate(secondContext)).willReturn(secondCode);

        given(shortUrlRepository.findByShortCode(firstCode)).willReturn(Optional.empty(), Optional.empty());
        given(shortUrlRepository.findByShortCode(secondCode)).willReturn(Optional.empty());

        willThrow(new DataIntegrityViolationException("short_code UNIQUE 제약조건 위반")).given(shortUrlWriter).save(firstCode, longUrl);

        String result = strategy.create(longUrl);

        assertThat(result).isEqualTo(secondCode);
        then(hashShortCodeGenerator).should().generate(firstContext);
        then(hashShortCodeGenerator).should().generate(secondContext);
        then(shortUrlWriter).should().save(firstCode, longUrl);
        then(shortUrlWriter).should().save(secondCode, longUrl);
    }

    @Test
    void 저장_중_동일_URL이_먼저_저장되면_기존_코드를_반환한다() {
        String longUrl = "https://www.google.com";
        String shortCode = "abc1234";

        ShortCodeGenerationContext context = ShortCodeGenerationContext.hash(longUrl,0);
        ShortUrl concurrentlySaved = ShortUrl.create(shortCode, longUrl);

        given(hashShortCodeGenerator.generate(context)).willReturn(shortCode);
        given(shortUrlRepository.findByShortCode(shortCode)).willReturn(Optional.empty(), Optional.of(concurrentlySaved));

        willThrow(new DataIntegrityViolationException("short_code UNIQUE 제약조건 위반")).given(shortUrlWriter).save(shortCode, longUrl);

        String result = strategy.create(longUrl);

        assertThat(result).isEqualTo(shortCode);
        then(shortUrlRepository).should(times(2)).findByShortCode(shortCode);
        then(shortUrlWriter).should().save(shortCode, longUrl);
    }
}