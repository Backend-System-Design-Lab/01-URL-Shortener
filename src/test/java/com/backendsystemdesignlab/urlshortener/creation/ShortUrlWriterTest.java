package com.backendsystemdesignlab.urlshortener.creation;

import com.backendsystemdesignlab.urlshortener.url.domain.ShortUrl;
import com.backendsystemdesignlab.urlshortener.url.repository.ShortUrlRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ShortUrlWriterTest {

    @InjectMocks
    private ShortUrlWriter shortUrlWriter;

    @Mock
    private ShortUrlRepository shortUrlRepository;

    @Test
    void shortCode와_longUrl을_저장한다() {
        String shortCode = "abc1234";
        String longUrl = "https://www.google.com";

        shortUrlWriter.save(shortCode, longUrl);

        ArgumentCaptor<ShortUrl> shortUrlCaptor = ArgumentCaptor.forClass(ShortUrl.class);
        then(shortUrlRepository).should().saveAndFlush(shortUrlCaptor.capture());

        ShortUrl savedShortUrl = shortUrlCaptor.getValue();
        assertThat(savedShortUrl.getShortCode()).isEqualTo(shortCode);
        assertThat(savedShortUrl.getLongUrl()).isEqualTo(longUrl);

    }

}