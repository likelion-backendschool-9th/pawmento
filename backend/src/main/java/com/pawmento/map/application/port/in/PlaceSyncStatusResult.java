package com.pawmento.map.application.port.in;

import java.time.Instant;

/**
 * 가져오기의 진행 상태.
 *
 * @param category 종류의 이름
 * @param state NONE(이 서버에서 돌린 적 없음), RUNNING, SUCCEEDED, FAILED
 * @param startedAt 시작 시각. NONE이면 null
 * @param finishedAt 끝난 시각. 진행 중이면 null
 * @param result 성공했을 때의 건수. 아니면 null
 * @param failure 실패했을 때의 이유(FETCH_FAILED, FETCH_REJECTED, MISSING_FIELDS, LOCK_LOST 등). 아니면 null
 */
public record PlaceSyncStatusResult(String category, String state, Instant startedAt, Instant finishedAt,
		SyncLocalDataPlacesResult result, String failure) {
}
