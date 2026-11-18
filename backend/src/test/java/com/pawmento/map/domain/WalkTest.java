package com.pawmento.map.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.map.domain.exception.WalkNotInProgressException;
import com.pawmento.map.domain.exception.WalkPointBatchTooLargeException;
import com.pawmento.map.domain.exception.WalkPointLimitExceededException;

// 시나리오는 docs/map/walk-route.md에 있다. 산책 객체가 스스로 지키는 규칙만 확인한다
class WalkTest {

	private static final Instant START = Instant.parse("2026-11-17T01:00:00Z");

	private static Walk startedAt(Instant now) {
		return Walk.start(10L, 1L, now);
	}

	private static Walk restored(Instant endedAt, Instant lastActivityAt) {
		return Walk.restore(5L, 10L, 1L, START, endedAt, lastActivityAt);
	}

	@Test
	@DisplayName("#73-S1 새로 시작한 산책은 id가 없고 진행 중이며 끝난 시각이 비어 있다")
	void start() {
		Walk walk = startedAt(START);

		assertThat(walk.getId()).isNull();
		assertThat(walk.getPetId()).isEqualTo(10L);
		assertThat(walk.getCreatedBy()).isEqualTo(1L);
		assertThat(walk.getStartedAt()).isEqualTo(START);
		assertThat(walk.getEndedAt()).isNull();
		assertThat(walk.getLastActivityAt()).isEqualTo(START);
		assertThat(walk.status(START)).isEqualTo(WalkStatus.IN_PROGRESS);
	}

	@Test
	@DisplayName("#73-S1 반려동물, 작성자, 시작 시각이 없으면 만들 수 없다")
	void startRequiresValues() {
		assertThatThrownBy(() -> Walk.start(null, 1L, START)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Walk.start(10L, null, START)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Walk.start(10L, 1L, null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Walk.restore(null, 10L, 1L, START, null, START))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("#73-S12 마지막 활동 후 5분 이내(정확히 5분 포함)면 진행 중, 5분을 넘으면 중단이다")
	void statusByLastActivity() {
		Walk walk = startedAt(START);

		assertThat(walk.status(START.plusSeconds(60))).isEqualTo(WalkStatus.IN_PROGRESS);
		assertThat(walk.status(START.plusSeconds(300))).isEqualTo(WalkStatus.IN_PROGRESS);
		assertThat(walk.status(START.plusSeconds(301))).isEqualTo(WalkStatus.INTERRUPTED);
	}

	@Test
	@DisplayName("#73-S2 진행 중인 산책을 끝내면 끝난 시각이 정해지고, 다시 끝내도 바뀌지 않는다")
	void endInProgress() {
		Walk walk = startedAt(START);
		Instant endedAt = START.plusSeconds(120);

		walk.end(endedAt);
		walk.end(endedAt.plusSeconds(30));

		assertThat(walk.getEndedAt()).isEqualTo(endedAt);
		assertThat(walk.status(endedAt.plusSeconds(3600))).isEqualTo(WalkStatus.ENDED);
	}

	@Test
	@DisplayName("#73-S2 이미 중단된 산책을 끝내려 해도 끝난 시각은 비어 있고 중단 상태 그대로다")
	void endInterruptedKeepsInterrupted() {
		Walk walk = startedAt(START);
		Instant later = START.plusSeconds(600);

		walk.end(later);

		assertThat(walk.getEndedAt()).isNull();
		assertThat(walk.status(later)).isEqualTo(WalkStatus.INTERRUPTED);
	}

	@Test
	@DisplayName("#73-S4 좌표를 받으면 마지막 활동 시각이 갱신되어 중단 시각이 뒤로 밀린다")
	void acceptPointsRefreshesActivity() {
		Walk walk = startedAt(START);
		Instant later = START.plusSeconds(240);

		walk.acceptPoints(later, 0, 4);

		assertThat(walk.getLastActivityAt()).isEqualTo(later);
		assertThat(walk.status(later.plusSeconds(300))).isEqualTo(WalkStatus.IN_PROGRESS);
	}

	@Test
	@DisplayName("#73-S3 끝난 산책이나 중단된 산책에는 좌표를 쌓을 수 없다")
	void acceptPointsRejectedWhenNotInProgress() {
		Walk ended = restored(START.plusSeconds(60), START.plusSeconds(60));
		Walk interrupted = restored(null, START);
		Instant now = START.plusSeconds(600);

		assertThatThrownBy(() -> ended.acceptPoints(now, 0, 1)).isInstanceOf(WalkNotInProgressException.class);
		assertThatThrownBy(() -> interrupted.acceptPoints(now, 0, 1)).isInstanceOf(WalkNotInProgressException.class);
		assertThat(interrupted.getLastActivityAt()).isEqualTo(START);
	}

	@Test
	@DisplayName("#73-S14 한 요청에 101개를 보내면 거절하고, 산책이 이미 끝났어도 개수 규칙을 먼저 본다")
	void batchLimitComesFirst() {
		Walk ended = restored(START.plusSeconds(60), START.plusSeconds(60));

		assertThatThrownBy(() -> startedAt(START).acceptPoints(START, 0, 101))
			.isInstanceOf(WalkPointBatchTooLargeException.class);
		assertThatThrownBy(() -> ended.acceptPoints(START.plusSeconds(61), 0, 101))
			.isInstanceOf(WalkPointBatchTooLargeException.class);
		startedAt(START).acceptPoints(START, 0, 100);
	}

	@Test
	@DisplayName("#73-S14 누적이 5,000개를 넘기면 거절하고, 딱 5,000개까지는 받는다")
	void totalLimit() {
		Walk walk = startedAt(START);

		assertThatThrownBy(() -> walk.acceptPoints(START, 4950, 100))
			.isInstanceOf(WalkPointLimitExceededException.class);
		walk.acceptPoints(START, 4900, 100);
		assertThat(walk.getLastActivityAt()).isEqualTo(START);
	}

	@Test
	@DisplayName("#73-S14 거절된 요청은 마지막 활동 시각을 바꾸지 않는다")
	void rejectedRequestKeepsActivity() {
		Walk walk = startedAt(START);
		Instant later = START.plusSeconds(60);

		assertThatThrownBy(() -> walk.acceptPoints(later, 4950, 100))
			.isInstanceOf(WalkPointLimitExceededException.class);

		assertThat(walk.getLastActivityAt()).isEqualTo(START);
	}

	@Test
	@DisplayName("#73-S4 좌표 개수가 1보다 작으면 거절한다")
	void emptyRequest() {
		assertThatThrownBy(() -> startedAt(START).acceptPoints(START, 0, 0))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
