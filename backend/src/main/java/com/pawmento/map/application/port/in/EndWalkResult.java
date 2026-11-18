package com.pawmento.map.application.port.in;

import java.time.Instant;

/**
 * @param endedAt 보호자가 끝낸 시각. 이미 중단된 산책이면 비어 있다
 * @param status IN_PROGRESS, ENDED, INTERRUPTED 중 하나
 */
public record EndWalkResult(Long walkId, Instant endedAt, String status) {
}
