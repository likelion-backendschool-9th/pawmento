package com.pawmento.map.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.pawmento.map.application.port.out.LoadWalkPointsPort;
import com.pawmento.map.application.port.out.LoadWalkPort;
import com.pawmento.map.application.port.out.LockPetWalksPort;
import com.pawmento.map.application.port.out.SaveWalkPointsPort;
import com.pawmento.map.application.port.out.SaveWalkPort;
import com.pawmento.map.domain.Walk;
import com.pawmento.map.domain.WalkPoint;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class WalkPersistenceAdapter
		implements SaveWalkPort, LoadWalkPort, SaveWalkPointsPort, LoadWalkPointsPort, LockPetWalksPort {

	// 다른 용도의 advisory lock과 키가 겹치지 않도록 산책 전용 값을 더한다
	private static final long WALK_LOCK_NAMESPACE = 7_300_000_000_000L;

	private final WalkJpaRepository walkJpaRepository;
	private final WalkPointJpaRepository walkPointJpaRepository;
	private final JdbcTemplate jdbcTemplate;

	@Override
	public Long save(Walk walk) {
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
	}

	@Override
	public Optional<Walk> loadById(Long walkId) {
		return walkJpaRepository.findById(walkId).map(WalkJpaEntity::toDomain);
	}

	@Override
	public Optional<Walk> loadByIdForUpdate(Long walkId) {
		return walkJpaRepository.findForUpdate(walkId).map(WalkJpaEntity::toDomain);
	}

	@Override
	public List<Walk> loadStartedBetween(Long createdBy, Long petId, Instant from, Instant to) {
		if (createdBy == null || petId == null || from == null || to == null) {
			throw new IllegalArgumentException("회원, 반려동물, 기간이 필요합니다.");
		}
		return walkJpaRepository.findStartedBetween(createdBy, petId, from, to)
			.stream()
			.map(WalkJpaEntity::toDomain)
			.toList();
	}

	@Override
	public boolean existsOpenWalkActiveSince(Long petId, Instant activeSince) {
		return walkJpaRepository.existsByPetIdAndEndedAtIsNullAndLastActivityAtGreaterThanEqual(petId, activeSince);
	}

	@Override
	public int insertIfAbsent(List<WalkPoint> points) {
		int inserted = 0;
		for (WalkPoint point : points) {
			inserted += walkPointJpaRepository.insertIfAbsent(point.getWalkId(), point.getLatitude(),
					point.getLongitude(), point.getRecordedAt());
		}
		return inserted;
	}

	@Override
	public List<WalkPoint> loadByWalkId(Long walkId) {
		return walkPointJpaRepository.findByWalkIdOrderByRecordedAtAsc(walkId)
			.stream()
			.map(WalkPointJpaEntity::toDomain)
			.toList();
	}

	@Override
	public long countByWalkId(Long walkId) {
		return walkPointJpaRepository.countByWalkId(walkId);
	}

	// 반려동물 단위로 잠근다. 트랜잭션이 끝나면 자동으로 풀리므로 호출하는 쪽에 트랜잭션이 있어야 한다
	@Override
	public void lock(Long petId) {
		jdbcTemplate.query("select pg_advisory_xact_lock(?)", resultSet -> {
		}, WALK_LOCK_NAMESPACE + petId);
	}

}
