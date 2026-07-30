package com.backendsystemdesignlab.urlshortener.controller;

import com.backendsystemdesignlab.urlshortener.service.ShortUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class RedirectController {

    private final ShortUrlService shortUrlService;

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(
            @PathVariable String shortCode
    ) {
        String longUrl = shortUrlService.getLongUrl(shortCode);

        return ResponseEntity
                .status(HttpStatus.FOUND) // 302 FOUND
                .location(URI.create(longUrl))
                .build();
    }

}
