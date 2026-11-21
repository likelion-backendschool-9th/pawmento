package com.pawmento.postcard.application.port.out;

public interface LoadPostcardPetPort {
  // 소유자가 아니면 403, 반려동물이 없으면 404 예외를 던진다.
  PostcardPet loadOwned(Long memberId, Long petId);
}
