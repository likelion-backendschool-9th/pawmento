package com.pawmento.media.application.service;

import com.pawmento.media.application.exception.*;
import com.pawmento.media.application.port.in.*;
import com.pawmento.media.application.port.out.*;
import com.pawmento.media.domain.*;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteMediaUploadService implements DeleteMediaUploadUseCase {
  private final CheckMediaPetAccessPort access;
  private final LockMediaAssetPort load;
  private final SaveMediaAssetPort save;
  private final MediaWorkQueuePort jobs;
  private final InspectMediaReferencesPort references;
  private final Clock clock;

  @Transactional
  public void delete(GetMediaUploadQuery q) {
    access.requireWriteAccess(q.memberId(), q.petId());
    var a = load.findForUpdate(q.assetId()).orElseThrow(() -> new MediaAssetNotFoundException());
    a.requireOwner(q.memberId(), q.petId());
    if (references.isReferenced(a.getId())) throw new MediaAttachmentConflictException();
    if (a.getStatus() == MediaUploadStatus.DELETING || a.getStatus() == MediaUploadStatus.DELETED)
      return;
    a.requestDeletion(clock.instant());
    save.save(a);
    jobs.enqueueDeletion(a.getId(), clock.instant());
  }
}
