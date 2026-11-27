package com.pawmento.media.application.service;

import com.pawmento.media.application.exception.MediaAssetNotFoundException;
import com.pawmento.media.application.port.in.*;
import com.pawmento.media.application.port.out.*;
import com.pawmento.media.domain.*;
import com.pawmento.media.domain.exception.MediaStateConflictException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ValidateMediaReferencesService implements ValidateMediaReferencesUseCase {
  private final LoadMediaOwnershipPort owners;
  private final LoadMediaAssetPort assets;

  @Override
  @Transactional(readOnly = true)
  public void validate(ValidateMediaReferencesCommand c) {
    var ids = new TreeSet<Long>(c.previous());
    ids.addAll(c.requested());
    if (ids.isEmpty()) return;
    MediaReferenceOwnership.require(
        owners.loadOwnership(List.copyOf(ids)),
        ids,
        c.memberId(),
        c.petId(),
        MediaPurpose.valueOf(c.purpose()),
        c.previous());
    for (var id : c.requested())
      if (!c.previous().contains(id)) {
        var asset = assets.findById(id).orElseThrow(MediaAssetNotFoundException::new);
        if (asset.getStatus() != MediaUploadStatus.READY)
          throw new MediaStateConflictException(
              MediaStateConflictException.Reason.ATTACHMENT_UNAVAILABLE);
      }
  }
}
