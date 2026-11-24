package com.pawmento.map.adapter.out.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pawmento.map.domain.PlaceCategory;

interface PlaceJpaRepository extends JpaRepository<PlaceJpaEntity, Long> {

	// 인덱스(category, latitude, longitude)로 종류와 위도 범위를 좁힌다. 경계선 위도 포함한다(between)
	@Query("""
			select p from PlaceJpaEntity p
			where p.category in :categories
			  and p.latitude between :minLatitude and :maxLatitude
			  and p.longitude between :minLongitude and :maxLongitude
			""")
	List<PlaceJpaEntity> findInArea(@Param("categories") Collection<PlaceCategory> categories,
			@Param("minLatitude") double minLatitude, @Param("minLongitude") double minLongitude,
			@Param("maxLatitude") double maxLatitude, @Param("maxLongitude") double maxLongitude);

	List<PlaceJpaEntity> findByCategory(PlaceCategory category);

	long countByCategory(PlaceCategory category);

}
