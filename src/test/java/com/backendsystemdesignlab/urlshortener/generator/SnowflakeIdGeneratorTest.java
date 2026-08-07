package com.backendsystemdesignlab.urlshortener.generator;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

class SnowflakeIdGeneratorTest {

    @Test
    void 생성된_ID는_양수이다() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1L);

        long id = generator.nextId();

        assertThat(id).isPositive();
    }

    @Test
    void 연속으로_생성한_ID는_서로_다르다() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1L);

        long firstId = generator.nextId();
        long secondId = generator.nextId();

        assertThat(firstId).isNotEqualTo(secondId);
        assertThat(secondId).isGreaterThan(firstId);
    }

    @Test
    void 많은_ID를_생성해도_중복되지_않는다() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1L);
        int count = 100_000;

        List<Long> ids = IntStream
                .range(0, count)
                .mapToObj(index -> generator.nextId())
                .toList();
        Set<Long> uniqueIds = new HashSet<>(ids);

        assertThat(uniqueIds).hasSize(count);
    }

    @Test
    void 여러_스레드에서_생성해도_중복되지_않는다() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1L);
        int count = 100_000;

        List<Long> ids = IntStream
                .range(0, count)
                .parallel()
                .mapToObj(index -> generator.nextId())
                .toList();
        Set<Long> uniqueIds = new HashSet<>(ids);

        assertThat(uniqueIds).hasSize(count);
    }

    @Test
    void nodeId가_허용_범위를_벗어나면_예외가_발생한다() {
        assertThatThrownBy(
                () -> new SnowflakeIdGenerator(1024L)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 작은_시간_역행은_복구를_기다린다() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1L) {
            private final Queue<Long> times = new ArrayDeque<>(List.of(1_000L, 995L, 998L, 1_000L));

            @Override
            protected long currentTimeMillis() {
                return times.remove();
            }
        };

        long first = generator.nextId();
        long second = generator.nextId();

        assertNotEquals(first, second);
    }

    @Test
    void 큰_시간_역행은_ID_생성을_중단한다() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1L) {
            private final Queue<Long> times = new ArrayDeque<>(List.of(1_000L, 900L));

            @Override
            protected long currentTimeMillis() {
                return times.remove();
            }
        };

        generator.nextId();

        assertThrows(IllegalStateException.class, generator::nextId);
    }
}