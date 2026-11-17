package com.pawmento.media.application.service;

import com.pawmento.media.application.exception.*;
import com.pawmento.media.application.port.in.*;
import com.pawmento.media.application.port.out.*;
import com.pawmento.media.domain.*;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateMediaUploadService implements CreateMediaUploadUseCase {
  private final CheckMediaPetAccessPort access;
  private final CheckMediaQuotaPort quota;
  private final SaveMediaAssetPort save;
  private final Clock clock;
  private final com.pawmento.media.domain.MediaPolicy policy;

  @Override
  @Transactional
  public GetMediaUploadResult create(CreateMediaUploadCommand c) {
    access.requireWriteAccess(c.memberId(), c.petId());
    if (c.locationConsent()) throw new MediaLocationConsentUnavailableException();
    MediaAsset asset =
        MediaAsset.create(
            c.memberId(),
            c.petId(),
            c.purpose(),
            c.filename(),
            c.mimeType(),
            c.sizeBytes(),
            UUID.randomUUID().toString(),
            clock.instant(),
            policy);
    quota.lockMember(c.memberId());
    policy.requireUploadSlot(quota.countOpenUploads(c.memberId(), clock.instant()));
    return GetMediaUploadResult.from(save.save(asset));
  }
}
