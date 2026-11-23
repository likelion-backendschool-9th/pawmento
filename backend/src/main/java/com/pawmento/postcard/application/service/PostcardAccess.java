package com.pawmento.postcard.application.service;

import com.pawmento.postcard.application.exception.PostcardNotFoundException;
import com.pawmento.postcard.application.port.out.LoadPostcardPort;
import com.pawmento.postcard.domain.Postcard;

// 다른 반려동물의 엽서는 없는 것처럼 다룬다.
final class PostcardAccess {
  private PostcardAccess() {}

  static Postcard load(LoadPostcardPort load, Long petId, Long postcardId) {
    return load.findById(postcardId)
        .filter(p -> p.belongsTo(petId))
        .orElseThrow(PostcardNotFoundException::new);
  }
}
