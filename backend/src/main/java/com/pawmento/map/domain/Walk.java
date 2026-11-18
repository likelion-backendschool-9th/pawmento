package com.pawmento.map.domain;

import java.time.Duration;
import java.time.Instant;

import com.pawmento.map.domain.exception.WalkNotInProgressException;
import com.pawmento.map.domain.exception.WalkPointBatchTooLargeException;
import com.pawmento.map.domain.exception.WalkPointLimitExceededException;

import lombok.Getter;

/**
 * 산책 하나. 좌표(WalkPoint)는 산책 id로 따로 저장한다.
 * 상태는 저장하지 않고 끝낸 시각과 마지막으로 좌표를 받은 시각에서 계산한다.
 * 다른 도메인의 엔티티를 참조하지 않고 반려동물·회원의 id만 가진다.
 */
@Getter
public class Walk {

	// 마지막 좌표를 받은 지 이 시간이 넘으면 중단된 산책이다. 15초 간격 수집, 묶음 전송, 잠깐의 신호 끊김을 견디는 값이다
	public static final Duration INTERRUPTED_AFTER = Duration.ofMinutes(5);

	public static final int MAX_POINTS_PER_WALK = 5000;

	public static final int MAX_POINTS_PER_REQUEST = 100;

	private final Long id;
	private final Long petId;
	private final Long createdBy;
	private final Instant startedAt;

	// 보호자가 끝낸 시각. 비어 있으면 끝내지 않은 산책이다(진행 중이거나 중단됐다)
	private Instant endedAt;

	// 산책을 시작했거나 마지막으로 좌표를 받은 시각. 중단 판정에 쓴다
	private Instant lastActivityAt;

	private Walk(Long id, Long petId, Long createdBy, Instant startedAt, Instant endedAt, Instant lastActivityAt) {
		if (petId == null || createdBy == null || startedAt == null || lastActivityAt == null) {
			throw new IllegalArgumentException("반려동물, 작성자, 시작 시각, 마지막 활동 시각이 필요합니다.");
		}
		this.id = id;
		this.petId = petId;
		this.createdBy = createdBy;
		this.startedAt = startedAt;
		this.endedAt = endedAt;
		this.lastActivityAt = lastActivityAt;
	}

	// 새로 시작하는 산책. id는 DB에 저장한 뒤 생긴다
	public static Walk start(Long petId, Long createdBy, Instant now) {
		if (now == null) {
			throw new IllegalArgumentException("시작 시각이 필요합니다.");
		}
		return new Walk(null, petId, createdBy, now, null, now);
	}

	// 저장된 값으로 다시 만든다
	public static Walk restore(Long id, Long petId, Long createdBy, Instant startedAt, Instant endedAt,
			Instant lastActivityAt) {
		if (id == null) {
			throw new IllegalArgumentException("저장된 산책에는 id가 필요합니다.");
		}
		return new Walk(id, petId, createdBy, startedAt, endedAt, lastActivityAt);
	}

	// 지금 시각 기준의 상태
	public WalkStatus status(Instant now) {
		if (endedAt != null) {
			return WalkStatus.ENDED;
		}
		if (Duration.between(lastActivityAt, now).compareTo(INTERRUPTED_AFTER) > 0) {
			return WalkStatus.INTERRUPTED;
		}
		return WalkStatus.IN_PROGRESS;
	}

	// 산책을 끝낸다. 이미 끝났거나 중단된 산책은 바꾸지 않는다. 같은 요청을 다시 보내도 같은 결과가 되게 하기 위해서다
	public void end(Instant now) {
		if (status(now) != WalkStatus.IN_PROGRESS) {
			return;
		}
		this.endedAt = now;
	}

	/**
	 * 좌표를 받아도 되는지 검사하고, 받으면 마지막 활동 시각을 갱신한다.
	 * 한 요청의 개수 한도는 입력 규칙이라 먼저 보고, 그다음에 산책의 상태와 누적 개수를 본다.
	 *
	 * @param currentCount 이 산책에 이미 쌓인 좌표 개수
	 * @param incomingCount 이번 요청의 좌표 개수
	 */
	public void acceptPoints(Instant now, long currentCount, int incomingCount) {
		if (incomingCount < 1) {
			throw new IllegalArgumentException("좌표는 한 개 이상이어야 합니다.");
		}
		if (incomingCount > MAX_POINTS_PER_REQUEST) {
			throw new WalkPointBatchTooLargeException(MAX_POINTS_PER_REQUEST);
		}
		if (status(now) != WalkStatus.IN_PROGRESS) {
			throw new WalkNotInProgressException();
		}
		if (currentCount + incomingCount > MAX_POINTS_PER_WALK) {
			throw new WalkPointLimitExceededException(MAX_POINTS_PER_WALK);
		}
		this.lastActivityAt = now;
	}

}
