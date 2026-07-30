package com.backendsystemdesignlab.urlshortener.url.repository;

import com.backendsystemdesignlab.urlshortener.url.domain.ShortUrl;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {
}
