package com.pawmento.map.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

interface WalkJpaRepository extends JpaRepository<WalkJpaEntity, Long> {

	// 엔티티를 영속성 컨텍스트에 올리지 않고 바뀌지 않는 값만 읽는다
	interface OwnershipView {

		Long getPetId();

		Long getCreatedBy();

	}

	@Query("select w.petId as petId, w.createdBy as createdBy from WalkJpaEntity w where w.id = :id")
	Optional<OwnershipView> findOwnershipById(@Param("id") Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select w from WalkJpaEntity w where w.id = :id")
	Optional<WalkJpaEntity> findForUpdate(@Param("id") Long id);

	// 인덱스(created_by, pet_id, started_at)의 앞쪽 두 칸은 같음 비교, 시작 시각은 범위 비교로 쓴다
	@Query("""
			select w from WalkJpaEntity w
			where w.createdBy = :createdBy and w.petId = :petId
			  and w.startedAt >= :from and w.startedAt < :to
			order by w.startedAt, w.id
			""")
	List<WalkJpaEntity> findStartedBetween(@Param("createdBy") Long createdBy, @Param("petId") Long petId,
			@Param("from") Instant from, @Param("to") Instant to);

	// 인덱스(created_by, pet_id, started_at)로 회원·반려동물의 산책을 좁히고, 끝내지 않았고 최근에 활동한 것 중 가장 늦게 시작한 것 하나를 읽는다
	Optional<WalkJpaEntity> findFirstByCreatedByAndPetIdAndEndedAtIsNullAndLastActivityAtGreaterThanEqualOrderByStartedAtDescIdDesc(
			Long createdBy, Long petId, Instant activeSince);

	boolean existsByPetIdAndEndedAtIsNullAndLastActivityAtGreaterThanEqual(Long petId, Instant activeSince);

}
