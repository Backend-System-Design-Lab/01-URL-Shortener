package com.backendsystemdesignlab.urlshortener.exception;

public class ShortCodeGenerationException extends RuntimeException {
    public ShortCodeGenerationException() {
        super("충돌로 인해 단축 코드를 생성하지 못했습니다.");
    }
}
