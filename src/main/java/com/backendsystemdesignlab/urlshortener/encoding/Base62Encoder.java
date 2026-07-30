package com.backendsystemdesignlab.urlshortener.encoding;

import org.springframework.stereotype.Component;

@Component
public class Base62Encoder {

    private static final String ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private static final int BASE = ALPHABET.length();

    public String encode(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("인코딩할 값은 0 이상이어야 합니다.");
        }

        if (value == 0) {
            return "0";
        }

        StringBuilder result = new StringBuilder();

        while (value > 0) {
            int remainder = (int) (value % BASE);
            result.append(ALPHABET.charAt(remainder));
            value /= BASE;
        }

        return result.reverse().toString();
    }

    public long decode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("단축 코드는 비어 있을 수 없습니다.");
        }

        long result = 0;

        for (int i = 0; i < code.length(); i++) {
            int value = ALPHABET.indexOf(code.charAt(i));

            if (value == -1) {
                throw new IllegalArgumentException("Base62에 포함되지 않은 문자입니다: " + code.charAt(i));
            }

            result = Math.addExact(Math.multiplyExact(result, BASE), value);
        }

        return result;
    }
}
