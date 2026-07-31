package com.backendsystemdesignlab.urlshortener.service;

import com.backendsystemdesignlab.urlshortener.cache.ShortUrlCache;
import com.backendsystemdesignlab.urlshortener.encoding.Base62Encoder;
import com.backendsystemdesignlab.urlshortener.exception.ShortUrlNotFoundException;
import com.backendsystemdesignlab.urlshortener.url.domain.ShortUrl;
import com.backendsystemdesignlab.urlshortener.url.repository.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class RedirectService {

    private final ShortUrlRepository shortUrlRepository;
    private final ShortUrlCache shortUrlCache;
    private final Base62Encoder base62Encoder;

    public String findLongUrl(String shortCode) {
        return shortUrlCache.find(shortCode)
                .orElseGet(() -> findFromDatabase(shortCode));
    }

    private String findFromDatabase(String shortCode) {
        long id = base62Encoder.decode(shortCode);

        ShortUrl shortUrl = shortUrlRepository.findById(id)
                .orElseThrow(() -> new ShortUrlNotFoundException(shortCode));

        shortUrlCache.save(shortCode, shortUrl.getLongUrl());

        return shortUrl.getLongUrl();
    }
}
