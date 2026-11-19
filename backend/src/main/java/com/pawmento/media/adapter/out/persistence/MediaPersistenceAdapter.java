package com.pawmento.media.adapter.out.persistence;

import com.pawmento.media.application.port.out.*;
import com.pawmento.media.domain.*;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MediaPersistenceAdapter
    implements SaveMediaAssetPort,
        LoadMediaAssetPort,
        LockMediaAssetPort,
        CheckMediaQuotaPort,
        FindMediaCleanupPort,
        CheckMediaStorageReferencePort,
        EnqueueMediaValidationPort,
        MediaWorkQueuePort {
  private final MediaAssetJpaRepository repository;
  private final EntityManager entityManager;
  private final MediaValidationJobJpaRepository jobs;
  private final MediaPolicy policy;

  @Override
  public MediaAsset save(MediaAsset asset) {
    return MediaPersistenceOperations.execute(
        () -> {
          return repository.saveAndFlush(MediaAssetJpaEntity.from(asset)).toDomain();
        });
  }

  @Override
  public List<MediaAsset> findAll(List<Long> ids) {
    if (ids.isEmpty()) return List.of();
    return MediaPersistenceOperations.execute(
        () -> repository.findAllById(ids).stream().map(MediaAssetJpaEntity::toDomain).toList());
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

  public List<Long> candidates(Instant now, int limit) {
    return MediaPersistenceOperations.execute(
        () -> {
          return entityManager
              .createQuery(
                  "select a.id from MediaAssetJpaEntity a where (a.status='PENDING' and"
                      + " a.expiresAt<=:now) or (a.status='UPLOADING' and"
                      + " a.updatedAt<=:uploadCutoff) or (a.status='READY' and a.referenceCount=0"
                      + " and a.unattachedExpiresAt<=:now) or a.status='REJECTED' order by a.id",
                  Long.class)
              .setParameter("now", now)
              .setParameter("uploadCutoff", now.minus(policy.uploadTimeout()))
              .setMaxResults(limit)
              .getResultList();
        });
  }

  public boolean contains(String key) {
    return MediaPersistenceOperations.execute(
        () -> {
          Long assets =
              entityManager
                  .createQuery(
                      "select count(a) from MediaAssetJpaEntity a where a.status<>'DELETED' and"
                          + " (a.temporaryStorageKey=:key or a.verified.storageKey=:key)",
                      Long.class)
                  .setParameter("key", key)
                  .getSingleResult();
          if (assets > 0) return true;
          return entityManager
                  .createQuery(
                      "select count(j) from MediaValidationJobJpaEntity j where j.token=:key and"
                          + " j.status in ('RUNNING','RETRY')",
                      Long.class)
                  .setParameter("key", key)
                  .getSingleResult()
              > 0;
        });
  }

  public void enqueue(Long id, Instant now) {
    MediaPersistenceOperations.execute(
        () -> jobs.saveAndFlush(MediaValidationJobJpaEntity.from(MediaJob.pending(id, now))));
  }

  public Optional<MediaWork> claim(Instant now) {
    return MediaPersistenceOperations.execute(
        () -> {
          var list =
              entityManager
                  .createNativeQuery(
                      "select * from media_validation_jobs where ((status in ('PENDING','RETRY')"
                          + " and (next_attempt_at is null or next_attempt_at<=:now)) or"
                          + " (status='RUNNING' and lease_until<=:now)) order by requested_at limit"
                          + " 1 for update skip locked",
                      MediaValidationJobJpaEntity.class)
                  .setParameter("now", now)
                  .getResultList();
          if (list.isEmpty()) return Optional.empty();
          var entity = (MediaValidationJobJpaEntity) list.getFirst();
          var job = entity.toDomain();
          var work = job.claim(now, UUID.randomUUID().toString(), policy);
          entity.copy(job);
          return Optional.of(work);
        });
  }

  private MediaValidationJobJpaEntity lockedJob(Long id) {
    return entityManager.find(
        MediaValidationJobJpaEntity.class, id, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
  }

  public boolean owns(MediaWork work) {
    return MediaPersistenceOperations.execute(
        () -> {
          var entity = lockedJob(work.assetId());
          return entity != null && entity.toDomain().owns(work);
        });
  }

  public void complete(MediaWork work) {
    MediaPersistenceOperations.execute(
        () -> {
          var entity = lockedJob(work.assetId());
          if (entity != null) {
            var job = entity.toDomain();
            if (job.owns(work)) {
              job.complete();
              entity.copy(job);
            }
          }
        });
  }

  public void fail(MediaWork work, Instant now, String error, boolean retryable) {
    MediaPersistenceOperations.execute(
        () -> {
          var entity = lockedJob(work.assetId());
          if (entity != null) {
            var job = entity.toDomain();
            if (job.owns(work)) {
              job.fail(now, error, retryable, policy);
              entity.copy(job);
            }
          }
        });
  }

  public void enqueueDeletion(Long id, Instant now) {
    MediaPersistenceOperations.execute(
        () -> {
          var entity = lockedJob(id);
          var job = entity == null ? MediaJob.pending(id, now) : entity.toDomain();
          job.scheduleDeletion(now);
          if (entity == null) jobs.saveAndFlush(MediaValidationJobJpaEntity.from(job));
          else entity.copy(job);
        });
  }
}
