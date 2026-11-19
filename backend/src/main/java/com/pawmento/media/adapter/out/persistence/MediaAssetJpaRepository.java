package com.pawmento.media.adapter.out.persistence;

import com.pawmento.media.domain.MediaUploadStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface MediaAssetJpaRepository extends JpaRepository<MediaAssetJpaEntity, Long> {
  interface OwnershipView {
    Long getId();

    Long getUploaderMemberId();

    Long getPetId();

    com.pawmento.media.domain.MediaPurpose getPurpose();
  }

  @Query(
      "select a.id as id,a.uploaderMemberId as uploaderMemberId,a.petId as petId,a.purpose as"
          + " purpose from MediaAssetJpaEntity a where a.id in :ids")
  java.util.List<OwnershipView> findOwnership(@Param("ids") java.util.List<Long> ids);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from MediaAssetJpaEntity a where a.id=:id")
  Optional<MediaAssetJpaEntity> findForUpdate(@Param("id") Long id);

  @Query(
      "select count(a) from MediaAssetJpaEntity a where a.uploaderMemberId=:memberId and"
          + " ((a.status=:pending and a.expiresAt>:now) or"
          + " a.status=com.pawmento.media.domain.MediaUploadStatus.UPLOADING or"
          + " a.status=com.pawmento.media.domain.MediaUploadStatus.PROCESSING or"
          + " a.status=com.pawmento.media.domain.MediaUploadStatus.DELETING or ((a.status=:rejected"
          + " or a.status=com.pawmento.media.domain.MediaUploadStatus.READY) and"
          + " a.temporaryFileDeleted=false))")
  long countOpen(
      @Param("memberId") Long memberId,
      @Param("now") Instant now,
      @Param("pending") MediaUploadStatus pending,
      @Param("rejected") MediaUploadStatus rejected);
}
