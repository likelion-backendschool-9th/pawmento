package com.pawmento.media.application.port.out;

public interface InspectPetProfileReferencePort {
  boolean isLinked(Long petId, Long assetId);
}
