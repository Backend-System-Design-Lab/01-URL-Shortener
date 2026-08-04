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

    @Column(name = "short_code", unique = true, length = 16)
    private String shortCode;

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

    public void assignShortCode(String shortCode) {
        if (shortCode == null || shortCode.isBlank()) {
            throw new IllegalArgumentException("단축 코드는 비어 있을 수 없습니다.");
        }

        if (this.shortCode != null) {
            throw new IllegalStateException("단축 코드는 이미 할당되었습니다.");
        }

        this.shortCode = shortCode;
    }
}
