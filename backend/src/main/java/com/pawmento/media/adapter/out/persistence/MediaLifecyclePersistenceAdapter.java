package com.pawmento.media.adapter.out.persistence;

import com.pawmento.media.application.port.out.*;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MediaLifecyclePersistenceAdapter
    implements FindMediaCleanupPort, CheckMediaStorageReferencePort {
  private final EntityManager em;

  public List<Long> candidates(Instant now, int limit) {
    return MediaPersistenceOperations.execute(
        () -> {
          return em.createQuery(
                  "select a.id from MediaAssetJpaEntity a where (a.status='PENDING' and"
                      + " a.expiresAt<=:now) or (a.status='UPLOADING' and"
                      + " a.updatedAt<=:uploadCutoff) or (a.status='READY' and a.referenceCount=0"
                      + " and a.unattachedExpiresAt<=:now) or a.status='REJECTED' order by a.id",
                  Long.class)
              .setParameter("now", now)
              .setParameter(
                  "uploadCutoff", now.minus(com.pawmento.media.domain.MediaAsset.UPLOAD_TIMEOUT))
              .setMaxResults(limit)
              .getResultList();
        });
  }

  public boolean contains(String key) {
    return MediaPersistenceOperations.execute(
        () -> {
          Long assets =
              em.createQuery(
                      "select count(a) from MediaAssetJpaEntity a where a.status<>'DELETED' and"
                          + " (a.temporaryStorageKey=:key or a.verified.key=:key)",
                      Long.class)
                  .setParameter("key", key)
                  .getSingleResult();
          if (assets > 0) return true;
          return em.createQuery(
                      "select count(j) from MediaValidationJobJpaEntity j where j.token=:key and"
                          + " j.status in ('RUNNING','RETRY')",
                      Long.class)
                  .setParameter("key", key)
                  .getSingleResult()
              > 0;
        });
  }
}
