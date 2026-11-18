package com.pawmento.media.application.service;

import com.pawmento.media.application.exception.*;
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
  public GetMediaUploadResult get(GetMediaUploadQuery q) {
    access.requireWriteAccess(q.memberId(), q.petId());
    MediaAsset asset =
        load.findById(q.assetId()).orElseThrow(() -> new MediaAssetNotFoundException());
    asset.requireOwner(q.memberId(), q.petId());
    return GetMediaUploadResult.from(asset);
  }
}
