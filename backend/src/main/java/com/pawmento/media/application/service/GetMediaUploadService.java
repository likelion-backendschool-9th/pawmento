package com.pawmento.media.application.service;

import com.pawmento.media.application.port.in.*;
import com.pawmento.media.application.port.out.*;
import com.pawmento.media.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetMediaUploadService implements GetMediaUploadUseCase {
  private final CheckMediaPetAccessPort access;
  private final LoadMediaAssetPort load;

  @Override
  @Transactional(readOnly = true)
  public MediaUploadResult get(GetMediaUploadQuery q) {
    access.requireWriteAccess(q.memberId(), q.petId());
    MediaAsset asset =
        load.findById(q.assetId())
            .orElseThrow(
                () ->
                    new MediaUploadException(MediaUploadException.Kind.NOT_FOUND, "파일을 찾을 수 없어요."));
    try {
      asset.requireOwner(q.memberId(), q.petId());
      return MediaUploadResult.from(asset);
    } catch (MediaViolation e) {
      throw MediaUploadException.from(e);
    }
  }
}
