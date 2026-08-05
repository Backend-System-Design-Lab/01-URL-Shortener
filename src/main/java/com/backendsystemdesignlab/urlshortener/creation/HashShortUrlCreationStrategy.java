package com.backendsystemdesignlab.urlshortener.creation;

import com.backendsystemdesignlab.urlshortener.exception.ShortCodeGenerationException;
import com.backendsystemdesignlab.urlshortener.generator.HashShortCodeGenerator;
import com.backendsystemdesignlab.urlshortener.generator.ShortCodeGenerationContext;
import com.backendsystemdesignlab.urlshortener.url.domain.ShortUrl;
import com.backendsystemdesignlab.urlshortener.url.repository.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.short-code",
        name = "strategy",
        havingValue = "hash"
)
public class HashShortUrlCreationStrategy implements ShortUrlCreationStrategy {

    private static final int MAX_ATTEMPTS = 5;

    private final ShortUrlRepository shortUrlRepository;
    private final HashShortCodeGenerator hashShortCodeGenerator;
    private final ShortUrlWriter shortUrlWriter;

    @Override
    public String create(String longUrl) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String shortCode = hashShortCodeGenerator.generate(ShortCodeGenerationContext.hash(longUrl, attempt));

            Optional<ShortUrl> existing = shortUrlRepository.findByShortCode(shortCode);

            if(existing.isPresent()) {
                if (existing.get().getLongUrl().equals(longUrl)) {
                    return shortCode;
                }
                continue;
            }

            try {
                shortUrlWriter.save(shortCode, longUrl);
                return shortCode;
            } catch (DataIntegrityViolationException exception) {
                /*
                 * findByShortCode() 이후 다른 요청이
                 * 같은 코드를 먼저 저장했을 수 있다. (동시성)
                 */
                Optional<ShortUrl> concurrentResult = shortUrlRepository.findByShortCode(shortCode);
                if (concurrentResult.isPresent() && concurrentResult.get().getLongUrl().equals(longUrl)) {
                    return shortCode;
                }
            }
        }

        throw new ShortCodeGenerationException();
    }

}
