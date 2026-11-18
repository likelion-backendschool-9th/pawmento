package com.pawmento.community.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PostCategoryJpaRepository extends JpaRepository<PostCategoryJpaEntity, Long> {

	Optional<PostCategoryJpaEntity> findByCode(String code);

	// 서버가 여러 대 동시에 떠도 겹치지 않게, 같은 code가 있으면 아무것도 하지 않는다
	@Modifying
	@Query(value = """
			INSERT INTO post_categories (code, name, sort_order, active)
			VALUES (:code, :name, :sortOrder, true)
			ON CONFLICT (code) DO NOTHING
			""", nativeQuery = true)
	void insertIfAbsent(@Param("code") String code, @Param("name") String name, @Param("sortOrder") int sortOrder);

	List<PostCategoryJpaEntity> findByActiveTrueOrderBySortOrderAscIdAsc();

}
