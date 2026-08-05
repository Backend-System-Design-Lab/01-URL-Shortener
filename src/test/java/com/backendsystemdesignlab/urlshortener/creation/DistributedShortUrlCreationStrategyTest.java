package com.backendsystemdesignlab.urlshortener.creation;

import com.backendsystemdesignlab.urlshortener.encoding.Base62Encoder;
import com.backendsystemdesignlab.urlshortener.generator.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class DistributedShortUrlCreationStrategyTest {

    @InjectMocks
    private DistributedShortUrlCreationStrategy strategy;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private Base62Encoder base62Encoder;

    @Mock
    private ShortUrlWriter shortUrlWriter;

    @Test
    void 분산_ID를_BASE62로_변환해_저장한다() {
        String longUrl = "https://example.com/distributed";
        long distributedId = 123456789L;
        String shortCode = "8M0kX";

        given(snowflakeIdGenerator.nextId()).willReturn(distributedId);
        given(base62Encoder.encode(distributedId)).willReturn(shortCode);

        String result = strategy.create(longUrl);

        assertThat(result).isEqualTo(shortCode);
        then(snowflakeIdGenerator).should().nextId();
        then(base62Encoder).should().encode(distributedId);
        then(shortUrlWriter).should().save(shortCode, longUrl);
    }
}