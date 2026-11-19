package com.pawmento.map.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface DiaryLocationJpaRepository extends JpaRepository<DiaryLocationJpaEntity, Long> {

	Optional<DiaryLocationJpaEntity> findByDiaryId(Long diaryId);

	// 인덱스(created_by, pet_id, latitude, longitude)의 앞쪽 두 칸은 같음 비교, 위도는 범위 비교로 쓴다
	@Query("""
			select l from DiaryLocationJpaEntity l
			where l.createdBy = :createdBy and l.petId = :petId
			  and l.latitude between :minLatitude and :maxLatitude
			  and l.longitude between :minLongitude and :maxLongitude
			order by l.id
			""")
	List<DiaryLocationJpaEntity> findInArea(@Param("createdBy") Long createdBy, @Param("petId") Long petId,
			@Param("minLatitude") double minLatitude, @Param("minLongitude") double minLongitude,
			@Param("maxLatitude") double maxLatitude, @Param("maxLongitude") double maxLongitude, Pageable pageable);

}
