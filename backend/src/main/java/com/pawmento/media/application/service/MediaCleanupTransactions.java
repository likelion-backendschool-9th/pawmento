package com.pawmento.media.application.service;

import com.pawmento.media.application.exception.MediaAssetNotFoundException;
import com.pawmento.media.application.port.out.*;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MediaCleanupTransactions {
  private final LockMediaAssetPort load;
  private final SaveMediaAssetPort save;
  private final MediaWorkQueuePort jobs;
  private final InspectMediaReferencesPort references;
  private final Clock clock;

  @Transactional
  public void schedule(Long id) {
    var a = load.findForUpdate(id).orElseThrow(MediaAssetNotFoundException::new);
    if (!a.needsCleanup(clock.instant())
        || a.getReferenceCount() != 0
        || references.isReferenced(id)) return;
    a.requestDeletion(clock.instant());
    save.save(a);
    jobs.enqueueDeletion(id, clock.instant());
  }
}
