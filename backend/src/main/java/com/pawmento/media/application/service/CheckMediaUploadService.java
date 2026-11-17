package com.pawmento.media.application.service;

import com.pawmento.media.application.exception.*;
import com.pawmento.media.application.port.in.*;
import com.pawmento.media.application.port.out.*;
import com.pawmento.media.domain.*;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 파싱 전 접근 확인이다. 실제 claim은 별도 트랜잭션에서 다시 검사한다.
@Service
@RequiredArgsConstructor
public class CheckMediaUploadService implements CheckMediaUploadUseCase {
  private final CheckMediaPetAccessPort access;
  private final LoadMediaAssetPort load;
  private final Clock clock;

  @Override
  @Transactional(readOnly = true)
  public void check(CheckMediaUploadQuery q) {
    access.requireWriteAccess(q.memberId(), q.petId());
    MediaAsset a = load.findById(q.assetId()).orElseThrow(() -> new MediaAssetNotFoundException());
    a.requireOwner(q.memberId(), q.petId());
    a.requirePending(clock.instant());
  }
}
