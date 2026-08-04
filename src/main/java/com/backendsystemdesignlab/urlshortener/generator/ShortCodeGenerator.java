package com.backendsystemdesignlab.urlshortener.generator;

public interface ShortCodeGenerator {

    String generate(ShortCodeGenerationContext context);
}
