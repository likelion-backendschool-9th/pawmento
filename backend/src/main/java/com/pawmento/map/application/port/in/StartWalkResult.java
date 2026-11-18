package com.pawmento.map.application.port.in;

import java.time.Instant;

/**
 * @param status IN_PROGRESS, ENDED, INTERRUPTED 중 하나
 */
public record StartWalkResult(Long walkId, Instant startedAt, String status) {
}
