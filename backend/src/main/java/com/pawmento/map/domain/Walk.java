package com.pawmento.map.domain;

import java.time.Duration;
import java.time.Instant;

import com.pawmento.map.domain.exception.InvalidWalkPointBatchException;
import com.pawmento.map.domain.exception.NotWalkOwnerException;
import com.pawmento.map.domain.exception.WalkNotInProgressException;
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

	/**
	 * 산책을 끝낸다. 시작한 회원만 끝낼 수 있고, 이 검사가 상태 확인보다 먼저다.
	 * 이미 끝났거나 중단된 산책은 바꾸지 않는다. 같은 요청을 다시 보내도 같은 결과가 되게 하기 위해서다.
	 *
	 * @param requesterId 끝내려는 회원
	 */
	public void end(Long requesterId, Instant now) {
		if (!createdBy.equals(requesterId)) {
			throw new NotWalkOwnerException();
		}
		if (status(now) != WalkStatus.IN_PROGRESS) {
			return;
		}
		this.endedAt = now;
	}

	/**
	 * 좌표 묶음을 받아도 되는지 검사한다. 값은 바꾸지 않는다.
	 * 요청 하나의 크기(재전송된 좌표를 포함한 원본 묶음 크기)와 산책의 상태만 본다.
	 * 산책 전체의 개수 한도는 실제로 새로 쌓이는 개수를 알아야 해서 {@link #requireCapacity}가 따로 본다.
	 * 마지막 활동 시각은 두 검사를 모두 통과한 뒤에 {@link #markPointsReceived}로 바꾼다.
	 *
	 * @param requestedCount 이번 요청에 담긴 좌표 개수
	 */
	public void requireAcceptingPoints(Instant now, int requestedCount) {
		if (requestedCount < 1) {
			throw InvalidWalkPointBatchException.empty();
		}
		if (requestedCount > MAX_POINTS_PER_REQUEST) {
			throw InvalidWalkPointBatchException.tooLarge(MAX_POINTS_PER_REQUEST);
		}
		if (status(now) != WalkStatus.IN_PROGRESS) {
			throw new WalkNotInProgressException();
		}
	}

	/**
	 * 한 산책에 쌓을 수 있는 좌표의 최대 개수를 넘지 않는지 검사한다.
	 * 이미 쌓인 좌표를 다시 보낸 재전송은 개수를 늘리지 않으므로, 요청의 크기가 아니라 실제로 새로 쌓인 개수로 센다.
	 * 산책 행을 잠근 상태에서 부르고, 넘으면 호출한 트랜잭션을 되돌려 이번 요청의 좌표를 쌓지 않아야 한다.
	 *
	 * @param currentCount 이번 요청 전에 이 산책에 쌓여 있던 좌표 개수
	 * @param newCount 이번 요청으로 실제로 새로 쌓인 좌표 개수
	 */
	public void requireCapacity(long currentCount, int newCount) {
		if (newCount < 0) {
			throw new IllegalArgumentException("새로 쌓인 좌표 개수는 0 이상이어야 합니다.");
		}
		if (currentCount + newCount > MAX_POINTS_PER_WALK) {
			throw new WalkPointLimitExceededException(MAX_POINTS_PER_WALK);
		}
	}

	/**
	 * 좌표를 받았음을 기록해 마지막 활동 시각을 갱신한다. 이 시각이 중단 판정의 기준이라 중단 시각이 뒤로 밀린다.
	 * 개수와 상태 검사를 모두 통과한 요청에서만 부른다. 거절된 요청은 마지막 활동 시각을 바꾸지 않는다.
	 * 검사를 빠뜨리고 직접 불러도 중단되거나 끝난 산책이 활동 시각 갱신으로 다시 진행 중이 되지 않도록,
	 * 여기서도 진행 중인지 확인하고 아니면 아무것도 바꾸지 않고 예외를 던진다.
	 */
	public void markPointsReceived(Instant now) {
		if (status(now) != WalkStatus.IN_PROGRESS) {
			throw new WalkNotInProgressException();
		}
		this.lastActivityAt = now;
	}

}
