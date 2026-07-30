package com.backendsystemdesignlab.urlshortener.ping;

public record PingResponse(String message) {

    public static PingResponse pong() {
        return new PingResponse("pong");
    }
}
