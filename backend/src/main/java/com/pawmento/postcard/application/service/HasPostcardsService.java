package com.pawmento.postcard.application.service;

import com.pawmento.postcard.application.port.in.*;
import com.pawmento.postcard.application.port.out.ExistsPostcardsPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HasPostcardsService implements HasPostcardsUseCase {
  private final ExistsPostcardsPort postcards;

  @Override
  @Transactional(readOnly = true)
  public boolean exists(HasPostcardsQuery query) {
    return postcards.existsByPetId(query.petId());
  }
}
