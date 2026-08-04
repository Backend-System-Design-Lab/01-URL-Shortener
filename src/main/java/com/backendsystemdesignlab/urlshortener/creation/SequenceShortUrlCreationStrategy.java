package com.backendsystemdesignlab.urlshortener.creation;

import com.backendsystemdesignlab.urlshortener.generator.SequenceBase62Generator;
import com.backendsystemdesignlab.urlshortener.generator.ShortCodeGenerationContext;
import com.backendsystemdesignlab.urlshortener.url.domain.ShortUrl;
import com.backendsystemdesignlab.urlshortener.url.repository.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.short-code",
        name = "strategy",
        havingValue = "sequence",
        matchIfMissing = true
)
public class SequenceShortUrlCreationStrategy implements ShortUrlCreationStrategy {

    private final ShortUrlRepository shortUrlRepository;
    private final SequenceBase62Generator sequenceBase62Generator;

    @Override
    @Transactional
    public String create(String longUrl) {
        ShortUrl shortUrl = ShortUrl.create(longUrl);
        ShortUrl savedShortUrl = shortUrlRepository.save(shortUrl);

        String shortCode = sequenceBase62Generator.generate(
                ShortCodeGenerationContext.sequece(
                        longUrl,
                        savedShortUrl.getId()
                )
        );

        savedShortUrl.assignShortCode(shortCode); // Dirty Checking

        return shortCode;
    }
}
