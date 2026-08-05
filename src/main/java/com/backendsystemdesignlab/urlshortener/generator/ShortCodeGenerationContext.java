package com.backendsystemdesignlab.urlshortener.generator;

public record ShortCodeGenerationContext(
        String longUrl,
        Long sequenceId,
        int attempt
) {
    public static ShortCodeGenerationContext sequece(
            String longUrl,
            Long sequenceId
    ) {
        return new ShortCodeGenerationContext(
                longUrl,
                sequenceId,
                0
        );
    }

    public static ShortCodeGenerationContext hash(
            String longUrl,
            int attempt
    ) {
        return new ShortCodeGenerationContext(
                longUrl,
                null,
                attempt
        );
    }
}
