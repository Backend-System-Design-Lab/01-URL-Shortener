package com.backendsystemdesignlab.urlshortener.service;

import com.backendsystemdesignlab.urlshortener.encoding.Base62Encoder;
import com.backendsystemdesignlab.urlshortener.exception.ShortUrlNotFoundException;
import com.backendsystemdesignlab.urlshortener.url.domain.ShortUrl;
import com.backendsystemdesignlab.urlshortener.url.repository.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShortUrlService {

    private final ShortUrlRepository shortUrlRepository;
    private final Base62Encoder base62Encoder;

    @Transactional
    public String createShortUrl(String longUrl) {
        validateUrl(longUrl);

        ShortUrl shortUrl = ShortUrl.create(longUrl);
        ShortUrl savedShortUrl = shortUrlRepository.save(shortUrl);

        return base62Encoder.encode(savedShortUrl.getId());
    }

    @Transactional(readOnly = true)
    public String getLongUrl(String shortCode) {
        long id;

        try {
            id = base62Encoder.decode(shortCode);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new ShortUrlNotFoundException();
        }

        return shortUrlRepository.findById(id)
                .map(ShortUrl::getLongUrl)
                .orElseThrow(ShortUrlNotFoundException::new);
    }

    private void validateUrl(String longUrl) {
        try {
            URI uri = URI.create(longUrl);
            String scheme = uri.getScheme(); // 프로토콜 (http or https)

            boolean validScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme); // 대소문자 무시

            if (!validScheme || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바른 http 또는 https URL을 입력해야 합니다."
            );
        }
    }
}
