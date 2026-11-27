package com.pawmento.postcard.adapter.out.persistence;

import com.pawmento.postcard.application.port.out.StoredPostcardFiles;
import com.pawmento.postcard.domain.PostcardStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Limit;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

interface PostcardJpaRepository extends JpaRepository<PostcardJpaEntity, Long> {
  boolean existsByPetId(Long petId);

  List<PostcardJpaEntity> findByPetIdOrderByIdDesc(Long petId, Limit limit);

  List<PostcardJpaEntity> findByPetIdAndIdLessThanOrderByIdDesc(Long petId, Long id, Limit limit);

  @Transactional
  @Modifying
  @Query("delete from PostcardJpaEntity p where p.id = :id")
  int deleteRow(@Param("id") Long id);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update PostcardJpaEntity p set p.sourcePhotoKey = :key where p.id = :id")
  int attachSourcePhoto(@Param("id") Long id, @Param("key") String key);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update PostcardJpaEntity p set p.status = :completed, p.imageKey = :key,"
          + " p.imageContentType = :type, p.completedAt = :now"
          + " where p.id = :id and p.status = :processing")
  int complete(
      @Param("id") Long id,
      @Param("key") String key,
      @Param("type") String contentType,
      @Param("now") Instant now,
      @Param("completed") PostcardStatus completed,
      @Param("processing") PostcardStatus processing);

  @Query(
      "select new com.pawmento.postcard.application.port.out.StoredPostcardFiles("
          + "p.id, p.status, p.imageKey, p.sourcePhotoKey) from PostcardJpaEntity p where p.id in :ids")
  List<StoredPostcardFiles> findFiles(@Param("ids") Collection<Long> ids);

  @Query(
      "select p.id from PostcardJpaEntity p where p.status = :processing"
          + " and p.processingStartedAt < :cutoff order by p.id")
  List<Long> startedBefore(
      @Param("cutoff") Instant cutoff, @Param("processing") PostcardStatus processing);
}
