package com.pawmento.postcard.application.port.out;

import com.pawmento.postcard.domain.Postcard;

public interface SavePostcardPort {
  Long save(Postcard postcard);

  void attachSourcePhoto(Long postcardId, String key);
}
