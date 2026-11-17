package com.pawmento.media.application.service;

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
    access.requireWriteAccess(memberId, petId);
    MediaAsset a = locked(id);
    try {
      a.requireOwner(memberId, petId);
      a.begin(clock.instant());
      return save.save(a);
    } catch (MediaViolation e) {
      throw MediaUploadException.from(e);
    }
  }

  @Transactional
  public MediaUploadResult complete(Long memberId, Long petId, Long id, StoredUpload receipt) {
    access.requireWriteAccess(memberId, petId);
    MediaAsset a = locked(id);
    try {
      a.requireOwner(memberId, petId);
      a.received(receipt, clock.instant());
      a = save.save(a);
      jobs.enqueue(id, clock.instant());
      return MediaUploadResult.from(a);
    } catch (MediaViolation e) {
      throw MediaUploadException.from(e);
    }
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
    return load.findForUpdate(id)
        .orElseThrow(
            () -> new MediaUploadException(MediaUploadException.Kind.NOT_FOUND, "파일을 찾을 수 없어요."));
  }
}
