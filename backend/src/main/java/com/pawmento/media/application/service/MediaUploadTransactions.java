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
public class MediaUploadTransactions {
  private final CheckMediaPetAccessPort access;
  private final LockMediaAssetPort load;
  private final SaveMediaAssetPort save;
  private final EnqueueMediaValidationPort jobs;
  private final Clock clock;

  @Transactional
  public MediaAsset claim(Long memberId, Long petId, Long id) {
    access.requireLockedWriteAccess(memberId, petId);
    MediaAsset a = locked(id);
    a.requireOwner(memberId, petId);
    a.begin(clock.instant());
    return save.save(a);
  }

  @Transactional
  public GetMediaUploadResult complete(Long memberId, Long petId, Long id, StoredUpload receipt) {
    access.requireLockedWriteAccess(memberId, petId);
    MediaAsset a = locked(id);
    a.requireOwner(memberId, petId);
    a.received(receipt, clock.instant());
    a = save.save(a);
    jobs.enqueue(id, clock.instant());
    return GetMediaUploadResult.from(a);
  }

  @Transactional
  public boolean reject(Long id) {
    MediaAsset a = locked(id);
    if (!a.rejectUpload(clock.instant())) return false;
    save.save(a);
    return true;
  }

  @Transactional
  public void cleaned(Long id) {
    MediaAsset a = locked(id);
    a.temporaryRemoved(clock.instant());
    save.save(a);
  }

  private MediaAsset locked(Long id) {
    return load.findForUpdate(id).orElseThrow(() -> new MediaAssetNotFoundException());
  }
}
