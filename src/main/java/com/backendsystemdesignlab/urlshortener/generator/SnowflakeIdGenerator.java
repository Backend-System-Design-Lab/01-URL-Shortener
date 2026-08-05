package com.backendsystemdesignlab.urlshortener.generator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class SnowflakeIdGenerator {

    // 0 | timestamp 41비트 | nodeId 10비트 | sequence 12비트
    /*
     * 2026-01-01T00:00:00Z
     *
     * Unix Epoch 전체를 저장하지 않고 프로젝트 전용 기준 시각부터의
     * 경과 시간을 저장해 41비트를 더 오래 사용할 수 있게 한다.
     */
    private static final long CUSTOM_EPOCH = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();
    private static final int NODE_ID_BITS = 10; // 최대 1024개의 서버 구분
    private static final int SEQUENCE_BITS = 12; // 한 서버당 1 밀리초에 최대 4096개 ID 생성 가능

    private static final long MAX_NODE_ID = (1L << NODE_ID_BITS) - 1;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;

    private static final int NODE_ID_SHIFT = SEQUENCE_BITS;
    private static final int TIMESTAMP_SHIFT = SEQUENCE_BITS + NODE_ID_BITS;

    private final long nodeId;  // 서버 번호

    private long lastTimestamp = -1L; // 마지막 생성 시각
    private long sequence = 0L; // 같은 밀리초 안에서의 순번

    public SnowflakeIdGenerator(@Value("${app.short-code.node-id:1}") long nodeId) {
        if (nodeId < 0 || nodeId > MAX_NODE_ID) {
            throw new IllegalArgumentException("nodeId는 0 이상 " + MAX_NODE_ID + " 이하여야 합니다.");
        }
        this.nodeId = nodeId;
    }

    public synchronized long nextId() {
        // 한 번에 한 스레드만 이 메서드를 실행
        // 단, synchronized는 현재 애플리케이션 프로세스 내부에서만 동작
        // 서버가 여러 대라면 서버 간 중복은 서로 다른 nodeId로 방지
        long currentTimestamp = currentTimeMillis();

        if (currentTimestamp < lastTimestamp) { // 원인 : NTP 시간 보정, 가상머신 시간 변경, 서버 시간 수동 변경
            throw new IllegalStateException("시스템 시간이 이전 시각으로 이동했습니다.");
        }

        if (currentTimestamp == lastTimestamp) { // 같은 밀리초 안에서 여러 ID를 만들고 있다
            sequence = (sequence + 1) & MAX_SEQUENCE;

            /*
             * 같은 밀리초에 4,096개의 ID를 모두 사용한 경우
             * 다음 밀리초까지 기다린다.
             */
            if (sequence == 0) {
                currentTimestamp = waitUntilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = currentTimestamp;

        long timeStampPart = (currentTimestamp - CUSTOM_EPOCH) << TIMESTAMP_SHIFT;
        long nodePart = nodeId << NODE_ID_SHIFT;

        return timeStampPart | nodePart | sequence;
    }

    private long waitUntilNextMillis(long timestamp) {
        long currentTimestamp = currentTimeMillis();

        while (currentTimestamp <= timestamp) {
            Thread.onSpinWait();    // 현재 스레드는 아주 짧은 시간 동안 반복문을 돌며 시간을 확인
            currentTimestamp = currentTimeMillis();
        }
        return currentTimestamp;
    }

    //테스트할 때 시간을 직접 제어
    protected long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}
