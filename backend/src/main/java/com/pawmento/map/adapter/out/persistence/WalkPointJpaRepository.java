package com.pawmento.map.adapter.out.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

interface WalkPointJpaRepository extends JpaRepository<WalkPointJpaEntity, Long> {

	List<WalkPointJpaEntity> findByWalkIdOrderByRecordedAtAsc(Long walkId);

	// 인덱스(walk_id, recorded_at)로 산책별 시간순 조회를 한 번에 한다
	List<WalkPointJpaEntity> findByWalkIdInOrderByWalkIdAscRecordedAtAsc(Collection<Long> walkIds);

	long countByWalkId(Long walkId);

	// 같은 산책에서 기록 시각이 같은 좌표가 이미 있으면 아무것도 하지 않는다. 쌓였으면 1, 아니면 0을 돌려준다
	@Modifying
	@Transactional
	@Query(value = """
			insert into walk_points (walk_id, latitude, longitude, recorded_at)
			values (:walkId, :latitude, :longitude, :recordedAt)
			on conflict (walk_id, recorded_at) do nothing
			""", nativeQuery = true)
	int insertIfAbsent(@Param("walkId") Long walkId, @Param("latitude") double latitude,
			@Param("longitude") double longitude, @Param("recordedAt") Instant recordedAt);

}
