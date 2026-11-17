package com.pawmento.media.application.service;

import com.pawmento.media.application.exception.MediaAssetNotFoundException;
import com.pawmento.media.application.port.out.*;
import com.pawmento.media.domain.*;
import java.time.Clock;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MediaWorkTransactions {
  private final MediaWorkQueuePort jobs;
  private final LockMediaAssetPort load;
  private final SaveMediaAssetPort save;
  private final Clock clock;

  @Transactional
  public Optional<MediaWork> claim() {
    return jobs.claim(clock.instant());
  }

  @Transactional
  public boolean success(MediaWork work, VerifiedMedia result) {
    var a = load.findForUpdate(work.assetId()).orElseThrow(MediaAssetNotFoundException::new);
    if (!jobs.owns(work)) return false;
    if (a.getStatus() == MediaUploadStatus.PROCESSING) {
      a.validated(result, clock.instant());
      save.save(a);
      return true;
    }
    return false;
  }

  @Transactional
  public boolean rejected(MediaWork work, String reason) {
    var a = load.findForUpdate(work.assetId()).orElseThrow(MediaAssetNotFoundException::new);
    if (!jobs.owns(work)) return false;
    a.invalidMedia(reason, clock.instant());
    save.save(a);
    return true;
  }

  @Transactional
  public void deleted(MediaWork work) {
    var a = load.findForUpdate(work.assetId()).orElseThrow(MediaAssetNotFoundException::new);
    if (!jobs.owns(work)) return;
    a.deleted(clock.instant());
    save.save(a);
    jobs.complete(work);
  }

  @Transactional
  public void finished(MediaWork work) {
    jobs.complete(work);
  }

  @Transactional
  public void retry(MediaWork work, String error) {
    var a = load.findForUpdate(work.assetId()).orElseThrow(MediaAssetNotFoundException::new);
    if (!jobs.owns(work)) return;
    if (work.attempt() >= 5 && a.getStatus() == MediaUploadStatus.PROCESSING) {
      a.invalidMedia("PROCESSING_FAILED", clock.instant());
      save.save(a);
    }
    jobs.retry(work, clock.instant(), error);
  }

  @Transactional
  public void cleaned(Long id) {
    var a = load.findForUpdate(id).orElseThrow(MediaAssetNotFoundException::new);
    if (a.getStatus() == MediaUploadStatus.READY || a.getStatus() == MediaUploadStatus.REJECTED) {
      a.temporaryRemoved(clock.instant());
      save.save(a);
    }
  }
}
