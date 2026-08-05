package com.backendsystemdesignlab.urlshortener.generator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

class HashShortCodeGeneratorTest {

    private final HashShortCodeGenerator generator = new HashShortCodeGenerator();

    @Test
    void 동일한_URL과_attempt는_동일한_코드를_생성한다() {
        ShortCodeGenerationContext context = ShortCodeGenerationContext.hash("https://www.google.com", 0);

        String first = generator.generate(context);
        String second = generator.generate(context);

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSize(7);
    }

    @Test
    void attempt가_다르면_다른_코드를_생성한다() {
        String first = generator.generate(ShortCodeGenerationContext.hash("https://www.google.com", 0));
        String second = generator.generate(ShortCodeGenerationContext.hash("https://www.google.com", 1));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void 생성된_코드는_Base62_문자로만_구성된다() {
        String shortCode = generator.generate(ShortCodeGenerationContext.hash("https://www.google.com", 0));
        assertThat(shortCode).matches("[0-9a-zA-Z]{7}");
    }

    @Test
    void URL이_비어있으면_예외가_발생한다() {
        assertThatThrownBy(
                () -> generator.generate(ShortCodeGenerationContext.hash("", 0))
        ).isInstanceOf(IllegalArgumentException.class);
    }
}