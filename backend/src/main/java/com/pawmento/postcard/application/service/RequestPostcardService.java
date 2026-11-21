package com.pawmento.postcard.application.service;

import com.pawmento.postcard.application.exception.InvalidPostcardSourceException;
import com.pawmento.postcard.application.port.in.*;
import com.pawmento.postcard.application.port.out.*;
import com.pawmento.postcard.domain.Postcard;
import com.pawmento.postcard.domain.PostcardSourceKind;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 한 트랜잭션에서 확인 → 횟수 잠금·증가 → 엽서 저장 → 사진 복사를 한다.
// 어느 단계에서 실패하든 늘린 횟수와 엽서 행이 함께 되돌아간다. 남은 복사 사진은 정리 작업이 지운다.
@Service
@RequiredArgsConstructor
public class RequestPostcardService implements RequestPostcardUseCase {
  private final LoadPostcardPetPort pets;
  private final LoadPostcardSourcePort sources;
  private final LoadPostcardPolicyPort policies;
  private final PostcardUsagePort usage;
  private final SavePostcardPort save;
  private final StorePostcardFilePort files;
  private final GeneratePostcardImagePort generator;
  private final Clock clock;

  @Override
  @Transactional
  public RequestPostcardResult request(RequestPostcardCommand c) {
    var pet = pets.loadOwned(c.memberId(), c.petId());
    var kind = kind(c.sourceKind());
    var source = sources.load(c.memberId(), c.petId(), kind, c.sourceId());
    var postcard =
        Postcard.request(
            c.petId(),
            c.memberId(),
            pet.name(),
            kind,
            c.sourceId(),
            source.content(),
            c.note(),
            pet.species(),
            pet.breed(),
            pet.garden(),
            source.photoAssetId() != null,
            c.consent(),
            generator.name(),
            clock.instant());
    var today = postcard.getRequestedOn();
    var counts = usage.lock(today, c.memberId());
    policies.load().requireRoom(counts.memberCount(), counts.totalCount());
    usage.increment(today, c.memberId());
    Long id = save.save(postcard);
    if (source.photoAssetId() != null) {
      byte[] photo =
          sources.photo(c.memberId(), c.petId(), kind, c.sourceId(), source.photoAssetId());
      save.attachSourcePhoto(id, files.storeSourcePhoto(id, photo));
    }
    return new RequestPostcardResult(id, postcard.getStatus().name());
  }

  private static PostcardSourceKind kind(String value) {
    try {
      return PostcardSourceKind.valueOf(value);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new InvalidPostcardSourceException();
    }
  }
}
