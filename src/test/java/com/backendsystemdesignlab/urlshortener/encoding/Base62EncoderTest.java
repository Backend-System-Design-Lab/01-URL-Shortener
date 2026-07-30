package com.backendsystemdesignlab.urlshortener.encoding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

class Base62EncoderTest {

    private final Base62Encoder base62Encoder = new Base62Encoder();

    @Test
    @DisplayName("숫자 ID를 Base62 문자열로 변환한다")
    void encode() {
        assertThat(base62Encoder.encode(0)).isEqualTo("0");
        assertThat(base62Encoder.encode(61)).isEqualTo("Z");
        assertThat(base62Encoder.encode(62)).isEqualTo("10");
        assertThat(base62Encoder.encode(11157)).isEqualTo("2TX");
    }

    @Test
    @DisplayName("Base62 문자열을 숫자 ID로 변환한다")
    void decode() {
        assertThat(base62Encoder.decode("0")).isZero();
        assertThat(base62Encoder.decode("Z")).isEqualTo(61);
        assertThat(base62Encoder.decode("10")).isEqualTo(62);
        assertThat(base62Encoder.decode("2TX")).isEqualTo(11157);
    }

    @Test
    @DisplayName("숫자를 인코딩한 뒤 디코딩하면 원래 값이 나온다")
    void encodeAndDecode() {
        long id = 123_456_789L;

        String code = base62Encoder.encode(id);
        long decodedId = base62Encoder.decode(code);

        assertThat(decodedId).isEqualTo(id);
    }

    @Test
    @DisplayName("음수는 인코딩할 수 없다")
    void encodeNegativeNumber() {
        assertThatThrownBy(() -> base62Encoder.encode(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Base62에 포함되지 않은 문자는 디코딩할 수 없다")
    void decodeInvalidCharacter() {
        assertThatThrownBy(() -> base62Encoder.decode("abc-"))
                .isInstanceOf(IllegalArgumentException.class);
    }

}