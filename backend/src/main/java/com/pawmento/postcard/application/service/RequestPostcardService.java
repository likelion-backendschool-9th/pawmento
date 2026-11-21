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

@Service
@RequiredArgsConstructor
public class RequestPostcardService implements RequestPostcardUseCase {
  private final LoadPostcardPetPort pets;
  private final LoadPostcardSourcePort sources;
  private final SavePostcardPort save;
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
            null,
            clock.instant());
    Long id = save.save(postcard);
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
