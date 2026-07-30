package com.backendsystemdesignlab.urlshortener.controller;

import com.backendsystemdesignlab.urlshortener.dto.CreateShortUrlRequest;
import com.backendsystemdesignlab.urlshortener.dto.CreateShortUrlResponse;
import com.backendsystemdesignlab.urlshortener.service.ShortUrlService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/data/shorten")
public class ShortUrlController {

    private final ShortUrlService shortUrlService;

    @PostMapping
    public ResponseEntity<CreateShortUrlResponse> createShortUrl(@Valid @RequestBody CreateShortUrlRequest request) {
        String shortCode = shortUrlService.createShortUrl(request.longUrl());

        String shortUrl = ServletUriComponentsBuilder
                .fromCurrentContextPath() // 현재 HTTP 요청을 기준으로 서버의 기본 주소
                .path("/{shortCode}")
                .buildAndExpand(shortCode)
                .toUriString(); // 완성된 URI 객체를 문자열로 변환

        CreateShortUrlResponse response = new CreateShortUrlResponse(shortCode, shortUrl);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
