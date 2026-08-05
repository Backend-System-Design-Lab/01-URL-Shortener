package com.backendsystemdesignlab.urlshortener.creation;

import com.backendsystemdesignlab.urlshortener.url.domain.ShortUrl;
import com.backendsystemdesignlab.urlshortener.url.repository.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ShortUrlWriter {

    private final ShortUrlRepository shortUrlRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW) // 기존 트랜잭션과 분리된 새 트랜잭션을 시작
    public void save(String shortCode, String longUrl) {
        ShortUrl shortUrl = ShortUrl.create(shortCode, longUrl);
        shortUrlRepository.saveAndFlush(shortUrl);
    }
}
