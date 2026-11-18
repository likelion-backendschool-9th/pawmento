package com.pawmento.community.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface PostCategoryJpaRepository extends JpaRepository<PostCategoryJpaEntity, Long> {

	Optional<PostCategoryJpaEntity> findByCode(String code);

	boolean existsByCode(String code);

	List<PostCategoryJpaEntity> findByActiveTrueOrderBySortOrderAscIdAsc();

}
