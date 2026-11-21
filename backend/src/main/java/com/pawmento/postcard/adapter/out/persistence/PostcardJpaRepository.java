package com.pawmento.postcard.adapter.out.persistence;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface PostcardJpaRepository extends JpaRepository<PostcardJpaEntity, Long> {
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update PostcardJpaEntity p set p.sourcePhotoKey = :key where p.id = :id")
  int attachSourcePhoto(@Param("id") Long id, @Param("key") String key);
}
