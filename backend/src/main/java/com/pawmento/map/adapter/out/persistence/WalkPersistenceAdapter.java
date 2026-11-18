package com.pawmento.map.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.pawmento.map.application.exception.WalkPersistenceUnavailableException;
import com.pawmento.map.application.port.out.LoadWalkPointsPort;
import com.pawmento.map.application.port.out.LoadWalkPort;
import com.pawmento.map.application.port.out.LockPetWalksPort;
import com.pawmento.map.application.port.out.SaveWalkPointsPort;
import com.pawmento.map.application.port.out.SaveWalkPort;
import com.pawmento.map.domain.Walk;
import com.pawmento.map.domain.WalkPoint;

import jakarta.persistence.EntityManager;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class WalkPersistenceAdapter
		implements SaveWalkPort, LoadWalkPort, SaveWalkPointsPort, LoadWalkPointsPort, LockPetWalksPort {

	// 다른 용도의 advisory lock과 키가 겹치지 않도록 산책 전용 값을 더한다
	private static final long WALK_LOCK_NAMESPACE = 7_300_000_000_000L;

	private final WalkJpaRepository walkJpaRepository;
	private final WalkPointJpaRepository walkPointJpaRepository;
	private final EntityManager entityManager;

	@Override
	public Long save(Walk walk) {
		return execute(() -> {
			if (walk.getId() == null) {
				return walkJpaRepository.save(WalkJpaEntity.from(walk)).getId();
			}
			WalkJpaEntity entity = walkJpaRepository.findById(walk.getId())
				.orElseThrow(() -> new IllegalStateException("저장된 산책을 찾을 수 없습니다. id=" + walk.getId()));
			// 다른 산책의 id로 덮어쓰는 일을 막는다
			if (!entity.hasSameIdentity(walk)) {
				throw new IllegalStateException("저장된 산책과 반려동물·작성자·시작 시각이 다릅니다. id=" + walk.getId());
			}
			entity.updateState(walk);
			return walkJpaRepository.save(entity).getId();
		});
	}

	@Override
	public Optional<Walk> loadById(Long walkId) {
		return execute(() -> walkJpaRepository.findById(walkId).map(WalkJpaEntity::toDomain));
	}

	@Override
	public Optional<Walk> loadByIdForUpdate(Long walkId) {
		return execute(() -> walkJpaRepository.findForUpdate(walkId).map(WalkJpaEntity::toDomain));
	}

	@Override
	public List<Walk> loadStartedBetween(Long createdBy, Long petId, Instant from, Instant to) {
		if (createdBy == null || petId == null || from == null || to == null) {
			throw new IllegalArgumentException("회원, 반려동물, 기간이 필요합니다.");
		}
		return execute(() -> walkJpaRepository.findStartedBetween(createdBy, petId, from, to)
			.stream()
			.map(WalkJpaEntity::toDomain)
			.toList());
	}

	@Override
	public boolean existsOpenWalkActiveSince(Long petId, Instant activeSince) {
		return execute(() -> walkJpaRepository
			.existsByPetIdAndEndedAtIsNullAndLastActivityAtGreaterThanEqual(petId, activeSince));
	}

	@Override
	public int insertIfAbsent(List<WalkPoint> points) {
		return execute(() -> {
			int inserted = 0;
			for (WalkPoint point : points) {
				inserted += walkPointJpaRepository.insertIfAbsent(point.getWalkId(), point.getLatitude(),
						point.getLongitude(), point.getRecordedAt());
			}
			return inserted;
		});
	}

	@Override
	public List<WalkPoint> loadByWalkId(Long walkId) {
		return execute(() -> walkPointJpaRepository.findByWalkIdOrderByRecordedAtAsc(walkId)
			.stream()
			.map(WalkPointJpaEntity::toDomain)
			.toList());
	}

	@Override
	public long countByWalkId(Long walkId) {
		return execute(() -> walkPointJpaRepository.countByWalkId(walkId));
	}

	// 반려동물 단위로 잠근다. 트랜잭션이 끝나면 자동으로 풀리므로 호출하는 쪽에 트랜잭션이 있어야 한다.
	// 잠금 호출이 실패하면 기술 예외를 서비스까지 올리지 않고 응용 예외로 바꾼다
	@Override
	public void lock(Long petId) {
		execute(() -> entityManager
			.createNativeQuery("select 1 from (select pg_advisory_xact_lock(cast(:key as bigint))) as locked",
					Integer.class)
			.setParameter("key", WALK_LOCK_NAMESPACE + petId)
			.getSingleResult());
	}

	// 이 어댑터의 모든 저장소 호출은 같은 응용 예외로 바꾼다
	private static <T> T execute(Supplier<T> action) {
		return MapPersistenceOperations.execute(action, WalkPersistenceUnavailableException::new);
	}

}
