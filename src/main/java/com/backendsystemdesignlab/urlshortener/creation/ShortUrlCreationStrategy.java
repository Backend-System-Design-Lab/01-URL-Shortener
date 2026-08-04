package com.backendsystemdesignlab.urlshortener.creation;

public interface ShortUrlCreationStrategy {

    String create(String longUrl);
}
