package com.pawmento.postcard.adapter.out.pet;

import com.pawmento.pet.application.port.in.GetPetQuery;
import com.pawmento.pet.application.port.in.GetPetUseCase;
import com.pawmento.postcard.application.port.out.LoadPostcardPetPort;
import com.pawmento.postcard.application.port.out.PostcardPet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 가족 접근이 열리기 전에는 다이어리와 같이 실제 소유자만 허용한다.
@Component
@RequiredArgsConstructor
public class PetPostcardAdapter implements LoadPostcardPetPort {
  private final GetPetUseCase pets;

  @Override
  public PostcardPet loadOwned(Long memberId, Long petId) {
    var pet = pets.get(new GetPetQuery(memberId, petId));
    return new PostcardPet(pet.name(), pet.species(), pet.breed(), "GARDEN".equals(pet.mode()));
  }
}
