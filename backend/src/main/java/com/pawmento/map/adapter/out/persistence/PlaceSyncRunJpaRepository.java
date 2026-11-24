package com.pawmento.map.adapter.out.persistence;

import java.time.Instant;
import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pawmento.map.domain.PlaceCategory;

interface PlaceSyncRunJpaRepository extends JpaRepository<PlaceSyncRunJpaEntity, PlaceCategory> {

	// 요청한 종류 중 가장 오래된 가져온 시각. 가져온 적이 있는 종류가 없으면 null이다
	@Query("select min(r.lastSyncedAt) from PlaceSyncRunJpaEntity r where r.category in :categories")
	Instant oldestSyncedAt(@Param("categories") Collection<PlaceCategory> categories);

}
