package com.pawmento.postcard.application.port.out;

import com.pawmento.postcard.domain.Postcard;
import java.util.Optional;

public interface LoadPostcardPort {
  Optional<Postcard> findById(Long postcardId);
}
