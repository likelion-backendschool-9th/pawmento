package com.pawmento.map.adapter.out.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

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

	// 정규화한 이름이나 주소에 검색어가 이어서 들어 있는 장소(#195). 패턴은 호출한 쪽이 '!'로 escape해서 만든다
	@Query("""
			select p from PlaceJpaEntity p
			where p.category in :categories
			  and (p.nameKey like :pattern escape '!' or p.addressKey like :pattern escape '!')
			""")
	List<PlaceJpaEntity> searchByKey(@Param("categories") Collection<PlaceCategory> categories,
			@Param("pattern") String pattern);

	// 검색용 열이 비어 있는 이전 행을 id 순서로 한 번에 일정 개수씩 읽는다(#195-S19). afterId보다 큰 것만 읽어서, 채우지 못한 행이
	// 있어도 같은 행을 계속 읽지 않고 끝까지 나아간다
	List<PlaceJpaEntity> findTop500ByNameKeyIsNullAndIdGreaterThanOrderByIdAsc(Long afterId);

	/**
	 * 검색용 열 두 개만, 아직 비어 있는 행에서만 채운다(#195-S19). 읽은 행을 통째로 저장(병합)하면 그 사이 가져오기가 바꾼
	 * 이름·주소까지 옛 값으로 되돌릴 수 있어서, 갱신하는 시점에 `name_key is null`을 다시 확인하는 조건 갱신을 쓴다.
	 * 가져오기가 먼저 그 행을 갱신했다면 열이 이미 채워져 있어 아무것도 바꾸지 않는다(0을 돌려준다).
	 * 벌크 갱신이라 수정 시각(updated_at)은 바뀌지 않는다.
	 */
	@Modifying
	@Transactional
	@Query("""
			update PlaceJpaEntity p set p.nameKey = :nameKey, p.addressKey = :addressKey
			where p.id = :id and p.nameKey is null
			""")
	int fillSearchKeys(@Param("id") Long id, @Param("nameKey") String nameKey,
			@Param("addressKey") String addressKey);

	List<PlaceJpaEntity> findByCategory(PlaceCategory category);

	long countByCategory(PlaceCategory category);

}
