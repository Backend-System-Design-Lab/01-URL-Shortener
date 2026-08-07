package com.backendsystemdesignlab.urlshortener.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;

import java.time.Duration;

@Configuration
public class RedisCircuitBreakerConfig {

    @Bean
    public CircuitBreaker redisCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)   // 실패율 >= 50%
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)  // COUNT_BASED: 최근 N번 호출, TIME_BASED: 최근 N초 동안 호출
                .slidingWindowSize(10)      // 최근 10건 기준
                .minimumNumberOfCalls(5)    // 최소 5건이 쌓인 뒤 (5개중 3개 실패시 OPEN == 차단)
                .waitDurationInOpenState(Duration.ofSeconds(5))     // 5초 동안 Redis 호출 차단 => CallNotPermittedException
                .permittedNumberOfCallsInHalfOpenState(3)           // 5초 후 HALF_OPEN(Redis가 복구됐는지 시험): 3건 시험 호출
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordException(throwable -> throwable instanceof DataAccessException)
                .build();

        return CircuitBreaker.of("redis", config);
    }
}
