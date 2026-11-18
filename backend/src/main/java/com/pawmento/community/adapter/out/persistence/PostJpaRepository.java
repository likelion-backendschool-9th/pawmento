package com.pawmento.community.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

interface PostJpaRepository extends JpaRepository<PostJpaEntity, Long> {

	Optional<PostJpaEntity> findByIdAndDeletedAtIsNull(Long id);

	List<PostJpaEntity> findByCategoryIdAndDeletedAtIsNullOrderByIdDesc(Long categoryId, Limit limit);

	List<PostJpaEntity> findByCategoryIdAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(Long categoryId, Long cursor,
			Limit limit);

}
