package com.pawmento.postcard.application.service;

import com.pawmento.postcard.application.port.in.*;
import com.pawmento.postcard.application.port.out.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetPostcardService implements GetPostcardUseCase {
  private final LoadPostcardPetPort pets;
  private final LoadPostcardPort load;

  @Override
  @Transactional(readOnly = true)
  public GetPostcardResult get(GetPostcardQuery q) {
    pets.loadOwned(q.memberId(), q.petId());
    return GetPostcardResult.from(PostcardAccess.load(load, q.petId(), q.postcardId()));
  }
}
