package com.pawmento.media.application.service;

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

  @Override
  @Transactional
  public MediaUploadResult create(CreateMediaUploadCommand c) {
    access.requireWriteAccess(c.memberId(), c.petId());
    if (c.locationConsent())
      throw new MediaUploadException(
          MediaUploadException.Kind.CONFLICT, "위치 사용 동의 절차가 준비되기 전에는 위치 수집을 사용할 수 없어요.");
    try {
      MediaAsset asset =
          MediaAsset.create(
              c.memberId(),
              c.petId(),
              c.purpose(),
              c.filename(),
              c.mimeType(),
              c.sizeBytes(),
              UUID.randomUUID().toString(),
              clock.instant());
      quota.lockMember(c.memberId());
      if (quota.countOpenUploads(c.memberId(), clock.instant()) >= MediaAsset.MAX_OPEN_UPLOADS)
        throw new MediaUploadException(
            MediaUploadException.Kind.CONFLICT, "처리 중인 파일은 5개까지 보관할 수 있어요.");
      return MediaUploadResult.from(save.save(asset));
    } catch (MediaViolation e) {
      throw MediaUploadException.from(e);
    }
  }
}
