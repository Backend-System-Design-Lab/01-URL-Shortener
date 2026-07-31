package com.backendsystemdesignlab.urlshortener.exception;

public class ShortUrlNotFoundException extends RuntimeException {

    public ShortUrlNotFoundException() {
        super("단축 URL을 찾을 수 없습니다.");
    }

    public ShortUrlNotFoundException(String shortCode) {
        super("단축 URL을 찾을 수 없습니다. shortCode=" + shortCode);
    }
}
