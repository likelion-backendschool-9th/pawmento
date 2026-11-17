package com.pawmento.media.application.service;

import com.pawmento.media.application.port.in.*;
import com.pawmento.media.application.port.out.*;
import com.pawmento.media.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessMediaWorkService implements ProcessMediaWorkUseCase {
  private final MediaWorkTransactions transactions;
  private final LoadMediaAssetPort load;
  private final ValidateMediaFilePort validator;
  private final DeleteMediaFilesPort deletion;
  private final DeleteMediaUploadPort temporary;

  public boolean processNext() {
    var next = transactions.claim();
    if (next.isEmpty()) return false;
    var w = next.get();
    try {
      var a = load.findById(w.assetId()).orElseThrow();
      if (w.kind().equals("DELETE")) {
        if (a.getStatus() != MediaUploadStatus.DELETING)
          throw new IllegalStateException("DELETE_STATE");
        deletion.deleteAll(
            a.getTemporaryStorageKey(),
            a.getVerified() == null ? null : a.getVerified().storageKey());
        transactions.deleted(w);
      } else {
        if (a.getStatus() == MediaUploadStatus.PROCESSING) {
          try {
            var v = validator.validate(a, w.token());
            if (!transactions.success(w, v)) return true;
          } catch (MediaValidationException e) {
            if (!transactions.rejected(w, e.getMessage())) return true;
          }
        }
        // Deletion failure is retried without decoding again once READY/REJECTED is durable.
        temporary.delete(a.getTemporaryStorageKey());
        transactions.cleaned(a.getId());
        transactions.finished(w);
      }
    } catch (RuntimeException e) {
      log.warn(
          "Media work failed: assetId={}, kind={}, attempt={}, error={}",
          w.assetId(),
          w.kind(),
          w.attempt(),
          e.getClass().getSimpleName());
      transactions.retry(w, e.getClass().getSimpleName());
    }
    return true;
  }
}
