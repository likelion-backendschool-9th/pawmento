package com.pawmento.postcard.application.port.out;

public interface ExistsPostcardsPort {
  boolean existsByPetId(Long petId);
}
