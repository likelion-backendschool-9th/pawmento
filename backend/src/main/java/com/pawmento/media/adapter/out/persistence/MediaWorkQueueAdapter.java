package com.pawmento.media.adapter.out.persistence;

import com.pawmento.media.application.port.out.MediaWorkQueuePort;
import com.pawmento.media.domain.MediaWork;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MediaWorkQueueAdapter implements MediaWorkQueuePort {
  private final EntityManager em;
  private final MediaValidationJobJpaRepository repository;

  public Optional<MediaWork> claim(Instant now) {
    return MediaPersistenceOperations.execute(
        () -> {
          var list =
              em.createNativeQuery(
                      "select * from media_validation_jobs where ((status in ('PENDING','RETRY')"
                          + " and (next_attempt_at is null or next_attempt_at<=:now)) or"
                          + " (status='RUNNING' and lease_until<=:now)) order by requested_at limit"
                          + " 1 for update skip locked",
                      MediaValidationJobJpaEntity.class)
                  .setParameter("now", now)
                  .getResultList();
          if (list.isEmpty()) return Optional.empty();
          return Optional.of(((MediaValidationJobJpaEntity) list.getFirst()).claim(now));
        });
  }

  private MediaValidationJobJpaEntity locked(Long id) {
    return em.find(
        MediaValidationJobJpaEntity.class, id, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
  }

  public boolean owns(MediaWork w) {
    return MediaPersistenceOperations.execute(
        () -> {
          var j = locked(w.assetId());
          return j != null && j.owns(w);
        });
  }

  public void complete(MediaWork w) {
    MediaPersistenceOperations.execute(
        () -> {
          var j = locked(w.assetId());
          if (j != null && j.owns(w)) j.complete();
        });
  }

  public void retry(MediaWork w, Instant now, String error) {
    MediaPersistenceOperations.execute(
        () -> {
          var j = locked(w.assetId());
          if (j != null && j.owns(w)) j.retry(now, error);
        });
  }

  public void enqueueDeletion(Long id, Instant now) {
    MediaPersistenceOperations.execute(
        () -> {
          var j = locked(id);
          if (j == null) {
            j = MediaValidationJobJpaEntity.pending(id, now);
            j = repository.save(j);
          }
          j.deletion(now);
        });
  }
}
