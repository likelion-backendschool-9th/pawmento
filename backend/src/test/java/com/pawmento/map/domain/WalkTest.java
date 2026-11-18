package com.pawmento.map.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.map.domain.exception.InvalidWalkPointBatchException;
import com.pawmento.map.domain.exception.WalkNotInProgressException;
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
	@DisplayName("#73-S4 좌표를 받았다고 기록하면 마지막 활동 시각이 갱신되어 중단 시각이 뒤로 밀린다")
	void markPointsReceivedRefreshesActivity() {
		Walk walk = startedAt(START);
		Instant later = START.plusSeconds(240);

		walk.requireAcceptingPoints(later, 4);
		walk.markPointsReceived(later);

		assertThat(walk.getLastActivityAt()).isEqualTo(later);
		assertThat(walk.status(later.plusSeconds(300))).isEqualTo(WalkStatus.IN_PROGRESS);
	}

	@Test
	@DisplayName("#73-S3 검사 없이 직접 불러도 중단된 산책은 활동 시각과 상태가 그대로다")
	void markPointsReceivedKeepsInterruptedWalkInterrupted() {
		Walk interrupted = restored(null, START);
		Instant now = START.plusSeconds(600);

		assertThatThrownBy(() -> interrupted.markPointsReceived(now)).isInstanceOf(WalkNotInProgressException.class);

		assertThat(interrupted.getLastActivityAt()).isEqualTo(START);
		assertThat(interrupted.status(now)).isEqualTo(WalkStatus.INTERRUPTED);
	}

	@Test
	@DisplayName("#73-S3 검사 없이 직접 불러도 끝난 산책은 활동 시각과 상태가 그대로다")
	void markPointsReceivedKeepsEndedWalkEnded() {
		Instant endedAt = START.plusSeconds(60);
		Walk ended = restored(endedAt, endedAt);
		Instant now = endedAt.plusSeconds(30);

		assertThatThrownBy(() -> ended.markPointsReceived(now)).isInstanceOf(WalkNotInProgressException.class);

		assertThat(ended.getLastActivityAt()).isEqualTo(endedAt);
		assertThat(ended.status(now)).isEqualTo(WalkStatus.ENDED);
	}

	@Test
	@DisplayName("#73-S4 정확히 5분 전에 마지막 활동한 산책은 아직 진행 중이라 활동 시각을 갱신할 수 있다")
	void markPointsReceivedAtExactBoundary() {
		Walk walk = startedAt(START);
		Instant boundary = START.plusSeconds(300);

		walk.markPointsReceived(boundary);

		assertThat(walk.getLastActivityAt()).isEqualTo(boundary);
	}

	@Test
	@DisplayName("#73-S4 받아도 되는지 검사만 하는 메서드는 마지막 활동 시각을 바꾸지 않는다")
	void requireMethodsDoNotChangeState() {
		Walk walk = startedAt(START);
		Instant later = START.plusSeconds(120);

		walk.requireAcceptingPoints(later, 4);
		walk.requireCapacity(0, 4);

		assertThat(walk.getLastActivityAt()).isEqualTo(START);
		assertThat(walk.getEndedAt()).isNull();
	}

	@Test
	@DisplayName("#73-S3 끝난 산책이나 중단된 산책에는 좌표를 쌓을 수 없고 마지막 활동 시각은 그대로다")
	void rejectedWhenNotInProgress() {
		Walk ended = restored(START.plusSeconds(60), START.plusSeconds(60));
		Walk interrupted = restored(null, START);
		Instant now = START.plusSeconds(600);

		assertThatThrownBy(() -> ended.requireAcceptingPoints(now, 1)).isInstanceOf(WalkNotInProgressException.class);
		assertThatThrownBy(() -> interrupted.requireAcceptingPoints(now, 1))
			.isInstanceOf(WalkNotInProgressException.class);
		assertThat(ended.getLastActivityAt()).isEqualTo(START.plusSeconds(60));
		assertThat(interrupted.getLastActivityAt()).isEqualTo(START);
	}

	@Test
	@DisplayName("#73-S14 한 요청에 101개를 보내면 거절하고, 산책이 이미 끝났어도 개수 규칙을 먼저 본다")
	void batchLimitComesFirst() {
		Walk ended = restored(START.plusSeconds(60), START.plusSeconds(60));

		assertThatThrownBy(() -> startedAt(START).requireAcceptingPoints(START, 101))
			.isInstanceOf(InvalidWalkPointBatchException.class);
		assertThatThrownBy(() -> ended.requireAcceptingPoints(START.plusSeconds(61), 101))
			.isInstanceOf(InvalidWalkPointBatchException.class);
		startedAt(START).requireAcceptingPoints(START, 100);
	}

	@Test
	@DisplayName("#73-S14 빈 묶음은 입력 규칙 위반으로 거절하고 마지막 활동 시각을 바꾸지 않는다")
	void emptyBatchIsRejected() {
		Walk walk = startedAt(START);

		assertThatThrownBy(() -> walk.requireAcceptingPoints(START.plusSeconds(60), 0))
			.isInstanceOf(InvalidWalkPointBatchException.class)
			.hasMessage("좌표를 한 개 이상 보내 주세요.");

		assertThat(walk.getLastActivityAt()).isEqualTo(START);
	}

	@Test
	@DisplayName("#73-S14 한 요청이 너무 커서 거절돼도 마지막 활동 시각을 바꾸지 않는다")
	void tooLargeBatchKeepsActivity() {
		Walk walk = startedAt(START);

		assertThatThrownBy(() -> walk.requireAcceptingPoints(START.plusSeconds(60), 101))
			.isInstanceOf(InvalidWalkPointBatchException.class);

		assertThat(walk.getLastActivityAt()).isEqualTo(START);
	}

	@Test
	@DisplayName("#73-S14 새로 쌓인 좌표가 누적 5,000개를 넘기면 거절하고, 딱 5,000개까지는 받는다")
	void totalLimit() {
		Walk walk = startedAt(START);

		assertThatThrownBy(() -> walk.requireCapacity(4950, 51)).isInstanceOf(WalkPointLimitExceededException.class);
		walk.requireCapacity(4950, 50);
		walk.requireCapacity(4900, 100);
	}

	@Test
	@DisplayName("#73-S14 누적 한도를 넘겨 거절돼도 마지막 활동 시각을 바꾸지 않는다(활동 시각은 두 검사를 통과한 뒤에만 기록한다)")
	void capacityRejectionKeepsActivity() {
		Walk walk = startedAt(START);
		Instant later = START.plusSeconds(60);

		// 서비스가 하는 순서: 크기·상태 검사 → 새로 쌓인 개수 검사 → 통과했을 때만 활동 시각 기록
		walk.requireAcceptingPoints(later, 100);
		assertThatThrownBy(() -> walk.requireCapacity(4950, 100)).isInstanceOf(WalkPointLimitExceededException.class);

		assertThat(walk.getLastActivityAt()).isEqualTo(START);
	}

	@Test
	@DisplayName("#73-S6 5,000개를 채운 뒤 같은 묶음을 다시 보내도 새로 쌓인 개수가 0이라 거절하지 않는다")
	void resendAtLimitIsNotRejected() {
		Walk walk = startedAt(START);

		// 4,999개에서 1개를 쌓아 5,000개가 된 뒤, 같은 요청을 다시 보내면 새로 쌓이는 좌표는 0개다
		walk.requireCapacity(4999, 1);
		walk.requireCapacity(5000, 0);
		assertThatThrownBy(() -> walk.requireCapacity(5000, 1)).isInstanceOf(WalkPointLimitExceededException.class);
	}

	@Test
	@DisplayName("#73-S14 새로 쌓인 개수가 음수이면 잘못된 호출이다")
	void negativeNewCount() {
		assertThatThrownBy(() -> startedAt(START).requireCapacity(0, -1)).isInstanceOf(IllegalArgumentException.class);
	}

}
