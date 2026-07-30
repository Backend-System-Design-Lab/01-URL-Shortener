package com.backendsystemdesignlab.urlshortener.dto;

public record CreateShortUrlResponse(
        String shortCode,
        String shortUrl
) {
}
