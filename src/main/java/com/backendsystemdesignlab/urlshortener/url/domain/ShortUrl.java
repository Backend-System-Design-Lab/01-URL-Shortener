package com.backendsystemdesignlab.urlshortener.url.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "short_url")
public class ShortUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "long_url", nullable = false, length = 2048)
    private String longUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ShortUrl() {}

    private ShortUrl(String longUrl) {
        this.longUrl = longUrl;
        this.createdAt = LocalDateTime.now();
    }

    public static ShortUrl create(String longUrl) {
        return new ShortUrl(longUrl);
    }
}
