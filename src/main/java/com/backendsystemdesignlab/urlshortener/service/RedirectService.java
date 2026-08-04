package com.backendsystemdesignlab.urlshortener.service;

import com.backendsystemdesignlab.urlshortener.cache.ShortUrlCache;
import com.backendsystemdesignlab.urlshortener.exception.ShortUrlNotFoundException;
import com.backendsystemdesignlab.urlshortener.metrics.RedirectMetrics;
import com.backendsystemdesignlab.urlshortener.url.domain.ShortUrl;
import com.backendsystemdesignlab.urlshortener.url.repository.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RedirectService {

    private final ShortUrlRepository shortUrlRepository;
    private final ShortUrlCache shortUrlCache;
    private final RedirectMetrics redirectMetrics;

    public String findLongUrl(String shortCode) {
        return shortUrlCache.find(shortCode)
                .orElseGet(() -> findFromDatabase(shortCode));
    }

    private String findFromDatabase(String shortCode) {
        redirectMetrics.recordDbLookup();

        ShortUrl shortUrl = shortUrlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortUrlNotFoundException(shortCode));

        shortUrlCache.save(shortCode, shortUrl.getLongUrl());

        return shortUrl.getLongUrl();
    }
}
