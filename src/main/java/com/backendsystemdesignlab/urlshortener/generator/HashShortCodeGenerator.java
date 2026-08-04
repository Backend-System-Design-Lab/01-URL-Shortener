package com.backendsystemdesignlab.urlshortener.generator;

import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
public class HashShortCodeGenerator implements ShortCodeGenerator {

    private static final String ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private static final BigInteger BASE =
            BigInteger.valueOf(ALPHABET.length());

    private static final int CODE_LENGTH = 7;

    private static final BigInteger CODE_SPACE = BASE.pow(CODE_LENGTH);

    @Override
    public String generate(ShortCodeGenerationContext context) {
        String longUrl = context.longUrl();

        if (longUrl == null || longUrl.isBlank()) {
            throw new IllegalArgumentException("Hash 방식에는 원본 URL이 필요합니다.");
        }

        String source = longUrl + ":" + context.attempt();
        byte[] digest = sha256(source);

        BigInteger value = new BigInteger(1, digest) // byte[] -> BigInteger (256진수) 양수인 큰 정수
                .mod(CODE_SPACE); // 0 ~ 62^7 - 1

        return encodeFixedLength(value);
    }

    private byte[] sha256(String source) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return messageDigest.digest(source.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 알고리즘을 사용할 수 없습니다.",
                    exception
            );
        }
    }

    private String encodeFixedLength(BigInteger value) {
        char[] result = new char[CODE_LENGTH];

        for (int index = CODE_LENGTH - 1; index >= 0; index--) {
            BigInteger[] division = value.divideAndRemainder(BASE); // [0]: 몫, [1]: 나머지

            result[index] = ALPHABET.charAt(division[1].intValue());

            value = division[0];
        }

        return new String(result);
    }
}
