package com.pawmento.media.application.service;

import com.pawmento.media.application.exception.MediaAssetNotFoundException;
import com.pawmento.media.application.port.in.*;
import com.pawmento.media.application.port.out.*;
import com.pawmento.media.domain.MediaAsset;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class ChangeMediaReferencesService
    implements ChangeMediaReferencesUseCase, LockMediaReferencesUseCase {
  private final LockMediaAssetPort load;
  private final SaveMediaAssetPort save;
  private final InspectMediaReferencesPort references;
  private final MediaWorkQueuePort jobs;
  private final Clock clock;

  @Transactional(propagation = Propagation.MANDATORY)
  public void lock(List<Long> ids) {
    for (Long id : new TreeSet<>(ids))
      load.findForUpdate(id).orElseThrow(MediaAssetNotFoundException::new);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void change(ChangeMediaReferencesCommand c) {
    var old = new HashSet<>(c.previous());
    var next = new HashSet<>(c.requested());
    if (old.size() != c.previous().size() || next.size() != c.requested().size())
      throw new IllegalArgumentException("Duplicate reference IDs");
    var ids = new TreeSet<Long>(old);
    ids.addAll(next);
    for (Long id : ids) {
      MediaAsset a = load.findForUpdate(id).orElseThrow(MediaAssetNotFoundException::new);
      a.requireDiaryOwner(c.memberId(), c.petId());
      if (old.contains(id) && next.contains(id)) {
        a.requireAttached(c.petId());
        continue;
      }
      if (next.contains(id)) a.acquireReference(clock.instant());
      else {
        a.releaseReference(clock.instant());
        a = save.save(a);
        if (a.getReferenceCount() == 0 && !references.isReferenced(id)) {
          a.requestDeletion(clock.instant());
          jobs.enqueueDeletion(id, clock.instant());
        }
      }
      save.save(a);
    }
  }
}
