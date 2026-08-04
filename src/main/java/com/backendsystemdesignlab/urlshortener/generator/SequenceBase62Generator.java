package com.backendsystemdesignlab.urlshortener.generator;

import com.backendsystemdesignlab.urlshortener.encoding.Base62Encoder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SequenceBase62Generator implements ShortCodeGenerator {

    private final Base62Encoder base62Encoder;

    @Override
    public String generate(String longUrl, Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Sequence 방식은 DB ID가 필요합니다.");
        }

        return base62Encoder.encode(id);
    }
}
