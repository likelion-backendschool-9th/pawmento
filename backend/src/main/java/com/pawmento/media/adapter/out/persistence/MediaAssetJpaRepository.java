package com.pawmento.media.adapter.out.persistence;

import com.pawmento.media.domain.MediaUploadStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface MediaAssetJpaRepository extends JpaRepository<MediaAssetJpaEntity, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from MediaAssetJpaEntity a where a.id=:id")
  Optional<MediaAssetJpaEntity> findForUpdate(@Param("id") Long id);

  @Query(
      "select count(a) from MediaAssetJpaEntity a where a.uploaderMemberId=:memberId and"
          + " (a.status<>com.pawmento.media.domain.MediaUploadStatus.DELETED) and"
          + " (a.status<>:pending or a.expiresAt>:now) and (a.status<>:rejected or"
          + " a.temporaryFileDeleted=false)")
  long countOpen(
      @Param("memberId") Long memberId,
      @Param("now") Instant now,
      @Param("pending") MediaUploadStatus pending,
      @Param("rejected") MediaUploadStatus rejected);
}
