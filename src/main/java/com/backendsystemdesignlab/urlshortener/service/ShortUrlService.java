package com.backendsystemdesignlab.urlshortener.service;

import com.backendsystemdesignlab.urlshortener.creation.ShortUrlCreationStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;

@Service
@RequiredArgsConstructor
public class ShortUrlService {

    private final ShortUrlCreationStrategy shortUrlCreationStrategy;

    public String createShortUrl(String longUrl) {
        validateUrl(longUrl);

        return shortUrlCreationStrategy.create(longUrl);
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
