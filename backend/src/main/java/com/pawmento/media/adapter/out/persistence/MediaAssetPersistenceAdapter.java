package com.pawmento.media.adapter.out.persistence;

import com.pawmento.media.application.port.out.*;
import com.pawmento.media.domain.*;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MediaAssetPersistenceAdapter
    implements SaveMediaAssetPort, LoadMediaAssetPort, LockMediaAssetPort, CheckMediaQuotaPort {
  private final MediaAssetJpaRepository repository;
  private final EntityManager entityManager;

  @Override
  public MediaAsset save(MediaAsset asset) {
    return MediaPersistenceOperations.execute(
        () -> {
          return repository.saveAndFlush(MediaAssetJpaEntity.from(asset)).toDomain();
        });
  }

  @Override
  public Optional<MediaAsset> findById(Long id) {
    return MediaPersistenceOperations.execute(
        () -> {
          return repository.findById(id).map(MediaAssetJpaEntity::toDomain);
        });
  }

  @Override
  public Optional<MediaAsset> findForUpdate(Long id) {
    return MediaPersistenceOperations.execute(
        () -> {
          return repository.findForUpdate(id).map(MediaAssetJpaEntity::toDomain);
        });
  }

  @Override
  public void lockMember(Long memberId) {
    MediaPersistenceOperations.execute(
        () -> {
          // PostgreSQL 트랜잭션 잠금: 같은 업로더의 동시 세션 생성이 건수 제한을 우회하지 못한다.
          entityManager
              .createNativeQuery(
                  "select 1 from (select pg_advisory_xact_lock(hashtextextended('media-upload:' ||"
                      + " cast(:memberId as text), 0))) as locked",
                  Integer.class)
              .setParameter("memberId", memberId)
              .getSingleResult();
        });
  }

  @Override
  public long countOpenUploads(Long memberId, Instant now) {
    return MediaPersistenceOperations.execute(
        () -> {
          return repository.countOpen(
              memberId, now, MediaUploadStatus.PENDING, MediaUploadStatus.REJECTED);
        });
  }
}
