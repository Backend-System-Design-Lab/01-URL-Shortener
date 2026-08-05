package com.backendsystemdesignlab.urlshortener.creation;

import com.backendsystemdesignlab.urlshortener.encoding.Base62Encoder;
import com.backendsystemdesignlab.urlshortener.generator.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.short-code",
        name = "strategy",
        havingValue = "distributed"
)
public class DistributedShortUrlCreationStrategy implements ShortUrlCreationStrategy {

    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final Base62Encoder base62Encoder;
    private final ShortUrlWriter shortUrlWriter;

    @Override
    public String create(String longUrl) {
        long distributedId = snowflakeIdGenerator.nextId();
        String shortCode = base62Encoder.encode(distributedId);

        shortUrlWriter.save(shortCode, longUrl);

        return shortCode;
    }
}
