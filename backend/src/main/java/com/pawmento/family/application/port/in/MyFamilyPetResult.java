package com.pawmento.family.application.port.in;

import java.time.Instant;

public record MyFamilyPetResult(Long petId, String name, String tag, Instant joinedAt) {

}
