package com.backendsystemdesignlab.urlshortener.generator;

public interface ShortCodeGenerator {

    String generate(String longUrl, Long id);
}
